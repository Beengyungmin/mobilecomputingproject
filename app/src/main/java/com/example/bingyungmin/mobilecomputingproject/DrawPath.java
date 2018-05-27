package com.example.bingyungmin.mobilecomputingproject;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.graphics.Path;
import android.widget.Toast;

public class DrawPath extends View {

    boolean is_start = false;

    float x = 0;
    float y = 0;

    Paint pt = new Paint();
    Paint path_pt = new Paint();
    Path path = new Path();

    public void init() {
        pt.setStyle(Paint.Style.STROKE);
        pt.setColor(Color.RED);
        pt.setStrokeWidth(40);
        path_pt.setStyle(Paint.Style.STROKE);
        path_pt.setColor(Color.GRAY);
        path_pt.setStrokeWidth(25);
    }

    public DrawPath(Context context, AttributeSet attr) {
        super(context, attr);
        init();
    }

//    @Override
//    public boolean onTouchEvent(MotionEvent event) {
//        super.onTouchEvent(event);
//
//        int action = event.getAction();
//
//        if(action == MotionEvent.ACTION_DOWN) {
//            if(!is_start) {
//                x = event.getX();
//                y = event.getY();
//                path.moveTo(x, y);
//                is_start = true;
//                MainActivity.SM.registerListener(MainActivity.SL, MainActivity.SM.getDefaultSensor(Sensor.TYPE_STEP_COUNTER), SensorManager.SENSOR_DELAY_NORMAL);
//                MainActivity.SM.registerListener(MainActivity.SL, MainActivity.SM.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST);
//                MainActivity.SM.registerListener(MainActivity.SL, MainActivity.SM.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_FASTEST);
//            }
//        }
//        invalidate();
//
//        return true;
//    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(x != 0 && y != 0)
            canvas.drawCircle(x, y, 1, pt);

        canvas.drawPath(path, path_pt);
    }
}
