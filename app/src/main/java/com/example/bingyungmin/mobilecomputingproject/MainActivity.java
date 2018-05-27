package com.example.bingyungmin.mobilecomputingproject;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.hardware.SensorManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.content.pm.ActivityInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    static SensorManager SM;
    static SensorEventListener SL;

    private int map_North = 170;

    double previous_distance;
    double distance;

    int steps = 0;
    int step_count = 0;

    private float[] mAccelerometerData = new float[3];
    private float[] mMagnetometerData = new float[3];

    private KalmanFilter mKalmanAccX;
    private KalmanFilter mKalmanAccY;
    private KalmanFilter mKalmanAccZ;

    private KalmanFilter mKalmanMagX;
    private KalmanFilter mKalmanMagY;
    private KalmanFilter mKalmanMagZ;

    double azimuth = 0;
    double azimuth_radian = 0;

    List<ScanResult> scanDatas;
    WifiManager wifiManager;

    DBHelper dbhelper;
    SQLiteDatabase sqLiteDatabase;

    int start_position = 0;

    List<HashMap<String, Integer>> Positions = new ArrayList<>();

    private int FindCurrentPosition() {

        //read data from database
        dbhelper.readAP(sqLiteDatabase);

        int min_position = 0;
        double min = 100000000;

        for(int i = 0; i < 9; i++) {
            double level_difference = 0;

            for(int j = 0; j < scanDatas.size(); j++) {
                int stored_level;
                int current_level = scanDatas.get(j).level;

                //if bssid is in db, get value and calculate
                if(Positions.get(i).get(scanDatas.get(j).BSSID) != null) {
                    stored_level = Positions.get(i).get(scanDatas.get(j).BSSID);
                    level_difference += Math.pow((double)(stored_level-current_level), 2);
                }
            }
            level_difference = Math.sqrt(level_difference);

            //find minimum level difference
            if(level_difference < min) {
                min = level_difference;
                min_position = i+1;
            }
        }

        return min_position;
    }

    /* wifi scan */
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if(action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                scanDatas = wifiManager.getScanResults();
                Toast.makeText(getApplicationContext(), "Scanned AP: " + scanDatas.size(), Toast.LENGTH_SHORT).show();
                start_position = FindCurrentPosition();
                getApplicationContext().unregisterReceiver(receiver);
            }
        }
    };



    //SQLite DB helper
    public class DBHelper extends SQLiteOpenHelper {

        public DBHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
            super(context, name, factory, version);
        }

        public void createTable(SQLiteDatabase db, String table_name) {
            String sql = "CREATE TABLE " + table_name + " (SSID text," + "BSSID text," + "level int);";
            try {
                db.execSQL(sql);
                Toast.makeText(getApplicationContext(), "Creating "+table_name+" Success", Toast.LENGTH_SHORT).show();
            }
            catch (SQLException e) {
                Toast.makeText(getApplicationContext(), "Can't create table",Toast.LENGTH_SHORT).show();
            }
        }

        public void insertAP(SQLiteDatabase db, int position_number, String ssid, String bssid, int level) {
            String table_name = "Position" + position_number;
            ContentValues contentValues = new ContentValues();
            contentValues.put("SSID", ssid);
            contentValues.put("BSSID", bssid);
            contentValues.put("level", level);

            try {
                db.insert(table_name, null, contentValues);
            } catch(SQLException e) {
                Toast.makeText(getApplicationContext(), "Can't insert values",Toast.LENGTH_SHORT).show();
            }
        }

        public void readAP(SQLiteDatabase db) {
            for(int i = 1; i <= 9; i++) {
                HashMap<String, Integer> position = new HashMap<>();
                String table_name = "Position"+i;
                Cursor result = db.query(table_name, null, null, null, null, null, null);

                while (result.moveToNext()) {
                    String bssid = result.getString(result.getColumnIndex("BSSID"));
                    int level = result.getInt(result.getColumnIndex("level"));
                    position.put(bssid, level);
                }
                Positions.add(position);
            }
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            for(int i = 1; i <= 9; i++) {
                String table_name = "Position" + i;
                createTable(db, table_name);
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        }
    }

    public class KalmanFilter
    {
        private double Q = 0.00001;
        private double R = 0.001;
        private double P = 1;
        private double X = 0;
        private double K;


        KalmanFilter(double initValue) {
            X = initValue;
        }

        private void measurementUpdate() {
            K = (P + Q) / (P + Q + R);
            P = R * (P + Q) / (P + Q + R);
        }

        public double update(double measurement) {
            measurementUpdate();
            X = X + (measurement - X) * K;
            return X;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        dbhelper = new DBHelper(getApplicationContext(), "fingerprinting", null, 1);
        sqLiteDatabase = dbhelper.getWritableDatabase();

        SM = (SensorManager)getSystemService(SENSOR_SERVICE);

        final DrawPath dp = findViewById(R.id.draw_path);

        mKalmanAccX = new KalmanFilter(0.0f);
        mKalmanAccY = new KalmanFilter(0.0f);
        mKalmanAccZ = new KalmanFilter(0.0f);
        mKalmanMagX = new KalmanFilter(0.0f);
        mKalmanMagY = new KalmanFilter(0.0f);
        mKalmanMagZ = new KalmanFilter(0.0f);

        SL =  new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {

                switch (event.sensor.getType()) {

                    case Sensor.TYPE_STEP_COUNTER: {
                        if(steps < 1) {
                            steps = (int)event.values[0];
                        }

                        step_count = (int)event.values[0] - steps;

                        //Calculate distance multiply with step size
                        distance = (step_count * 0.7);

                        if(distance != previous_distance && dp.is_start) {

                            //next location in map
                            dp.y -= ((distance-previous_distance)*30)*Math.cos(azimuth_radian);
                            dp.x += ((distance-previous_distance)*30)*Math.sin(azimuth_radian);

                            //draw path and invalidate
                            dp.path.lineTo(dp.x, dp.y);
                            dp.invalidate();
                            previous_distance = distance;
                        }
                        break;
                    }

                    case Sensor.TYPE_ACCELEROMETER: {
//                        //Kalman Filter
                        mAccelerometerData[0] = (float)mKalmanAccX.update(event.values[0]);
                        mAccelerometerData[1] = (float)mKalmanAccY.update(event.values[1]);
                        mAccelerometerData[2] = (float)mKalmanAccZ.update(event.values[2]);

                        break;
                    }

                    case Sensor.TYPE_MAGNETIC_FIELD: {
                        //Kalman Filter
                        mMagnetometerData[0] = (float)mKalmanMagX.update(event.values[0]);
                        mMagnetometerData[1] = (float)mKalmanMagY.update(event.values[1]);
                        mMagnetometerData[2] = (float)mKalmanMagZ.update(event.values[2]);

                        break;
                    }
                }

                float[] rMatrix = new float[9];
                float orientationValues[] = new float[3];

                if(mAccelerometerData != null && mMagnetometerData != null) {

                    boolean is_rotation = SensorManager.getRotationMatrix(rMatrix, null, mAccelerometerData, mMagnetometerData);

                    if (is_rotation) {
                        SensorManager.getOrientation(rMatrix, orientationValues);
                        azimuth = Math.toDegrees(orientationValues[0])-map_North;
                        if(azimuth < 0)
                            azimuth += 360;

                        if((azimuth >= 0 && azimuth <= 40) || azimuth >= 320 && azimuth <= 360)
                            azimuth = 0;
                        else if (azimuth >= 50 && azimuth <= 130)
                            azimuth = 90;
                        else if (azimuth >= 140 && azimuth <= 220)
                            azimuth = 180;
                        else if (azimuth >= 230 && azimuth <= 310)
                            azimuth = 270;

                        azimuth_radian = azimuth * Math.PI / 180;
                    }
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };

        Button wifiScan_btn = findViewById(R.id.button);
        wifiScan_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wifiManager = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
                IntentFilter intentFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
                intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
                getApplicationContext().registerReceiver(receiver, intentFilter);
                wifiManager.startScan();
            }
        });

        Button start_btn = findViewById(R.id.button2);
        start_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!dp.is_start) {
                    if (start_position == 1) {
                        dp.x = 940;
                        dp.y = 1840;
                    } else if (start_position == 2) {
                        dp.x = 940;
                        dp.y = 1300;
                    } else if (start_position == 3) {
                        dp.x = 940;
                        dp.y = 955;
                    } else if (start_position == 4) {
                        dp.x = 655;
                        dp.y = 2100;
                    } else if (start_position == 5) {
                        dp.x = 655;
                        dp.y = 1840;
                    } else if (start_position == 6) {
                        dp.x = 655;
                        dp.y = 1300;
                    } else if (start_position == 7) {
                        dp.x = 655;
                        dp.y = 940;
                    } else if (start_position == 8) {
                        dp.x = 400;
                        dp.y = 1300;
                    } else if (start_position == 9) {
                        dp.x = 150;
                        dp.y = 1300;
                    }

                    dp.path.moveTo(dp.x, dp.y);
                    dp.is_start = true;
                    dp.invalidate();

                    SM.registerListener(SL, SM.getDefaultSensor(Sensor.TYPE_STEP_COUNTER), SensorManager.SENSOR_DELAY_NORMAL);
                }
            }
        });

        Button btn = findViewById(R.id.button3);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(), "방위각: " + azimuth, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        SM.registerListener(SL, SM.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
        SM.registerListener(SL, SM.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        SM.unregisterListener(SL);
    }

}
