package com.example.diffshow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.app.Activity;
import android.os.Environment;
import android.util.Log;

import android.os.Bundle;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;


/**
 * Created by dWX465903 on 2017/12/23.
 */


public class CapacityView extends View {

    final static int tpWidth = 6;
    final static int tpHeight = 8;

    int screenWidth;
    int screenHeight;

    int maxValue = -10000;
    int minValue = 30000;

    float capWidth;
    float capHeight;

    short diffData[];

    Paint fillPaint[];
    Paint strokePaint;
    Paint textPaint;
    Paint taskPaint;
    Paint titlePaint;
    public String task_name = "";
    public String gesture_name = "";
    public String task_index = "";
    public boolean isrecording = false;
    public boolean istapping = false;


    public CapacityView(Context context) {
        super(context);
        init();
    }

    public CapacityView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CapacityView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public void init() {
        diffData = new short[tpWidth * tpHeight];
        fillPaint = new Paint[tpWidth * tpHeight];
        for (int i = 0; i < fillPaint.length; ++i) {
            fillPaint[i] = new Paint();
            fillPaint[i].setStyle(Paint.Style.FILL_AND_STROKE);
            fillPaint[i].setColor(Color.GRAY);
        }

        strokePaint = new Paint();
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.BLACK);
        textPaint = new Paint();
        textPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(30);
        taskPaint = new Paint();
        taskPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        taskPaint.setColor(Color.BLACK);
        taskPaint.setTextSize(80);
        titlePaint = new Paint();
        titlePaint.setStyle(Paint.Style.FILL_AND_STROKE);
        titlePaint.setTextSize(80);
    }

    public void resetCapacity(){
        for(int i = 0;i < tpWidth*tpHeight;i++){
            diffData[i] = 0;
        }
    }
    public void setColor(int i) {
        int maxVal = 1275 + 2700;
        //int maxVal = 255;
        int minVal = 2700;
        int r, g, b;
        r = 255;
        g = 255;
        b = 255;
        if (diffData[i] > maxVal) {
            g = 0;
        } else if (diffData[i] < minVal) {
            r = 255;
            g = 255;
            b = 255;
            /*
            r = 205;
            g = 190;
            b = 112;
            */
        } else {
            int tmp = (diffData[i] - minVal) / 5;
            g = 255 - tmp;
        }
        fillPaint[i].setColor(Color.rgb(r, g, b));
    }

    private void setMaxMin() {
        //maxValue = diffData[0];
        minValue = diffData[0];
        for(int i = 1;i < tpWidth*tpHeight;i++){
            if (maxValue < diffData[i]/4*3)
            {
                maxValue = diffData[i]/4*3;
            }
            if(minValue > diffData[i])
            {
                minValue = diffData[i];
            }
        }
    }





    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        capHeight = screenHeight / tpHeight;
        capWidth = screenWidth / tpWidth;
        //setMaxMin();
        String lineToWrite = "";
        for (int i = 0; i < tpWidth * tpHeight; ++i) {
            int capacityY = i / tpWidth;
            int capacityX = i % tpWidth;

            Rect rect = new Rect((int) (capacityX * capWidth), (int) (capacityY * capHeight), (int) ((capacityX + 1) * capWidth), (int) ((capacityY + 1) * capHeight));
            setColor(i);
            canvas.drawRect(rect, fillPaint[i]);
            canvas.drawRect(rect, strokePaint);
            //canvas.drawText(""+diffData[i],(int) (capacityX * capWidth),(int) ((capacityY+0.7) * capHeight),textPaint);
            //lineToWrite += Integer.toString(diffData[i]) + " ";
        }
        if(isrecording == false)
        {
            titlePaint.setColor(Color.RED);
            canvas.drawText("按音量键开始",tpWidth*capWidth/3,capHeight/2,titlePaint);
            canvas.drawText(gesture_name,tpWidth*capWidth/3,capHeight,taskPaint);
            canvas.drawText(task_name,tpWidth*capWidth/3,capHeight*3/2,taskPaint);
            canvas.drawText(task_index, tpWidth*capWidth/3,capHeight*5/2,taskPaint);
        }
        else
        {
            titlePaint.setColor(Color.GREEN);
            canvas.drawText("数据采集中...",tpWidth*capWidth/3,capHeight/2,titlePaint);
            canvas.drawText(gesture_name,tpWidth*capWidth/3,capHeight,taskPaint);
            canvas.drawText(task_name,tpWidth*capWidth/3,capHeight*3/2,taskPaint);
            canvas.drawText(task_index, tpWidth*capWidth/3,capHeight*5/2,taskPaint);
        }
        //canvas.drawText(task_name,tpWidth*capWidth/3,capHeight,taskPaint);
       // canvas.drawText("" + maxValue + " " + minValue, (int)(capWidth/2), (int)(capHeight/2),textPaint);
        /*try {
            writeFile(lineToWrite);
        } catch (IOException e) {
            e.printStackTrace();
        }*/
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (istapping == false)
            return super.onTouchEvent(event);
        float x = event.getX();
        float y = event.getY();
        int capacityX = (int)(x / capWidth);
        int capacityY = (int)(y / capHeight);
        int i = capacityY*tpWidth+capacityX;
        diffData[i] = 7000;
        invalidate();
        return super.onTouchEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        //invalidate();
        return super.dispatchTouchEvent(event);
    }
}