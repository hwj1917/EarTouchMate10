package com.example.diffrealtime;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class EarTouchService extends AccessibilityService implements SensorEventListener {

    static {
        System.loadLibrary("native-lib");
    }

    private SensorManager sensorManager;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        return START_STICKY;
    }

    @Override
    protected void onServiceConnected() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL);
        readDiffStart();
        enterEarMode();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        Log.d("hwjj", "stop");
        readDiffStop();
    }



    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native void readDiffStart();
    public native void readDiffStop();
    public native void quitEarMode();
    public native void enterEarMode();

    private final int CONTINUOUS_SPIN_TIME = 2000;
    private final int QUIT_SENSOR_THRESHOULD = 8;
    private final int CHECK_TIME = 1000;
    private final int TOUCH_MODE_CHECK = 0;
    private final int TOUCH_MODE_CLICK = TOUCH_MODE_CHECK + 1;
    private final int TOUCH_MODE_EXPLORE = TOUCH_MODE_CLICK + 1;
    private final int TOUCH_MODE_PRESS = TOUCH_MODE_EXPLORE + 1;
    private final int TOUCH_MODE_SWIPE = TOUCH_MODE_PRESS + 1;
    private final int TOUCH_MODE_SPIN = TOUCH_MODE_SWIPE + 1;
    private final int TOUCH_MODE_LONG = TOUCH_MODE_SPIN + 1;
    private final int BIG_SPIN_INTERVAL = 30;
    private final int SMALL_SPIN_INTERVAL = 10;
    private final int MIN_SWIPE_DIST = 240;
    private final int MIN_CHECKED = 2;

    public final String OP_CLICK = "click";
    public final String OP_DOUBLE_CLICK = "double";
    public final String OP_SWIPE_UP = "up";
    public final String OP_SWIPE_DOWN = "down";
    public final String OP_SWIPE_LEFT = "left";
    public final String OP_SWIPE_RIGHT = "right";
    public final String OP_CLKWISE = "clkwise";
    public final String OP_ANTICLKWISE = "anticlkwise";
    public final String OP_EXPLORE = "explore";
    public final String OP_LONG_PRESS = "long";
    public final String OP_FIRST_TOUCH = "first";
    public final String OP_LEAVE = "leave";

    private int lastChecked = 0;
    private double last_angle = -1;
    private double total_angle = 0;
    private int clkwise = 0;
    private int anticlkwise = 0;
    private boolean spinFlag = false;
    private long lastSpinEndTime = 0;
    private int firstSpinInterval = BIG_SPIN_INTERVAL;
    private boolean clear_flag = true;

    private int touch_mode = TOUCH_MODE_CHECK;
    private int checked = 0;
    private int first_checked_x, first_checked_y, last_checked_x, last_checked_y;
    private long check_start = 0;

    private boolean earModeFlag = true;

    public void sendEvent(String eventType, String para) {

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && earModeFlag)
        {
            float[] values = event.values;
            float ax = values[0];
            float ay = values[1];

            double g = Math.sqrt(ax * ax + ay * ay);
            double cos = ay / g;
            if (cos > 1) {
                cos = 1;
            } else if (cos < -1) {
                cos = -1;
            }
            double rad = Math.acos(cos);
            if (ax < 0) {
                rad = 2 * Math.PI - rad;
            }

            //here we go
            rad = rad / Math.PI * 180;

            if (lastChecked == 0 && checked > 0)
            {
                clkwise = anticlkwise = 0;
                total_angle = 0;
                last_angle = -1;
                spinFlag = false;
                long now = System.currentTimeMillis();
                if (now - lastSpinEndTime > CONTINUOUS_SPIN_TIME)
                    firstSpinInterval = BIG_SPIN_INTERVAL;
                else firstSpinInterval = SMALL_SPIN_INTERVAL;
            }

            int spinInterval = (spinFlag ? SMALL_SPIN_INTERVAL : firstSpinInterval);

            if (checked > 0 && last_angle != -1)
            {
                double diff = rad - last_angle;
                if (Math.abs(diff) > 90)
                {
                    if (last_angle < 180) diff -= 360;
                    else diff += 360;
                }

                double tmp = total_angle;

                total_angle += diff;

                if ((tmp > 0) == (total_angle > 0))
                {
                    int in = Double.valueOf(total_angle / spinInterval).intValue() - Double.valueOf(tmp / spinInterval).intValue();
                    if (in > 0) {
                        anticlkwise++;
                        clkwise = 0;
                    }
                    if (in < 0) {
                        clkwise++;
                        anticlkwise = 0;
                    }
                }
            }

            if (anticlkwise == 1)
            {
                Log.d("hwjj", "clockwise");
                sendEvent(OP_CLKWISE, "");
                spinFlag = true;
                anticlkwise = 0;
                total_angle = 0;
            }

            if (clkwise == 1)
            {
                Log.d("hwjj", "anticlockwise");
                sendEvent(OP_ANTICLKWISE, "");
                spinFlag = true;
                clkwise = 0;
                total_angle = 0;
            }

            last_angle = rad;
            lastChecked = checked;

        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private boolean checkSwipe()
    {
        int dx = last_checked_x - first_checked_x, dy = last_checked_y - first_checked_y;

        if (dx * dx + dy * dy > MIN_SWIPE_DIST * MIN_SWIPE_DIST)
        {
            if (Math.abs(dx) > Math.abs(dy))
            {
                if (dx > 0)
                {
                    Log.d("hwjj", "swipe right");
                    sendEvent(OP_SWIPE_RIGHT, "");
                }
                else
                {
                    Log.d("hwjj", "swipe left");
                    sendEvent(OP_SWIPE_LEFT, "");
                }
            }
            else
            {
                if (dy > 0)
                {
                    Log.d("hwjj", "swipe down");
                    sendEvent(OP_SWIPE_DOWN, "");
                }
                else
                {
                    Log.d("hwjj", "swipe up");
                    sendEvent(OP_SWIPE_UP, "");
                }
            }
            return true;
        }
        else {
            return false;
        }
    }

    public void processDiff(int x, int y, boolean down) {
        if (!earModeFlag) return;

        if (down) {
            if (clear_flag) {
                first_checked_x = x;
                first_checked_y = y;
                clear_flag = false;
            }

            switch (touch_mode) {
                case TOUCH_MODE_CHECK:
                    checked++;
                    last_checked_x = x;
                    last_checked_y = y;
                    long now = System.currentTimeMillis();
                    if (checked == 1) {
                        check_start = now;
                        sendEvent(OP_FIRST_TOUCH, "");
                    }
                    if (x == -1 && y == -1) {
                        Log.d("hwjj", "press");
                        touch_mode = TOUCH_MODE_PRESS;
                    } else if (x == -1 && y == 0) {
                        Log.d("hwjj", "clockwise");
                        touch_mode = TOUCH_MODE_SPIN;
                    } else if (x == 0 && y == -1) {
                        Log.d("hwjj", "anticlockwise");
                        touch_mode = TOUCH_MODE_SPIN;
                    } else if (checked > 0 && now - check_start > CHECK_TIME) {
                        if (spinFlag) {
                            touch_mode = TOUCH_MODE_SPIN;
                        } else {
                            if (checkSwipe()) {
                                touch_mode = TOUCH_MODE_EXPLORE;
                                Log.d("hwjj", "explore");
                                sendEvent(OP_EXPLORE, "" + x + " " + y);
                            } else {
                                touch_mode = TOUCH_MODE_LONG;
                                Log.d("hwjj", "long");
                                sendEvent(OP_LONG_PRESS, "" + x + " " + y);
                            }                            }
                            checked = 0;
                    }
                    break;
                case TOUCH_MODE_PRESS:
                case TOUCH_MODE_EXPLORE:
                    if (x == -1 && y == -1) {
                        Log.d("hwjj", "press");
                        touch_mode = TOUCH_MODE_PRESS;
                    } else {
                        sendEvent(OP_EXPLORE, "" + x + " " + y);
                    }
                    break;
                case TOUCH_MODE_SPIN:
                    if (x == -1 && y == 0) {
                        Log.d("hwjj", "clockwise");
                        touch_mode = TOUCH_MODE_SPIN;
                    } else if (x == 0 && y == -1) {
                        Log.d("hwjj", "anticlockwise");
                        touch_mode = TOUCH_MODE_SPIN;
                    }
                    break;
                case TOUCH_MODE_SWIPE:
                    break;
                case TOUCH_MODE_LONG:
                    sendEvent(OP_LONG_PRESS, "" + x + " " + y);
                    break;
            }

        } else {
            switch (touch_mode) {
                case TOUCH_MODE_LONG:
                    sendEvent(OP_LEAVE, "");
                    break;
                case TOUCH_MODE_SPIN:
                    lastSpinEndTime = System.currentTimeMillis();
                    sendEvent(OP_LEAVE, "");
                    break;
                case TOUCH_MODE_EXPLORE:
                    sendEvent(OP_LEAVE, "");
                    break;
                case TOUCH_MODE_CHECK:
                    if (checked > MIN_CHECKED) {
                        if (spinFlag) {
                            touch_mode = TOUCH_MODE_SPIN;
                            lastSpinEndTime = System.currentTimeMillis();
                        } else {
                            if (checkSwipe()) touch_mode = TOUCH_MODE_SWIPE;
                            else {
                                touch_mode = TOUCH_MODE_CLICK;
                                if (++clickState == 1)
                                    new ClickThread().start();
                            }
                        }
                    }
                    break;
            }
            checked = 0;
            clear_flag = true;
            touch_mode = TOUCH_MODE_CHECK;
        }
    }

    private int clickState = 0;
    private final int CLICK_TIME = 300;
    class ClickThread extends Thread {
        @Override
        public void run()
        {
            int x = last_checked_x;
            int y = last_checked_y;
            try {
                sleep(CLICK_TIME);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            if (clickState > 1) {
                Log.d("hwjj", "double click");
                sendEvent(OP_DOUBLE_CLICK, "");
            }
            else {
                Log.d("hwjj", "click");
                sendEvent(OP_CLICK, "" + x + " " + y);
                sendEvent(OP_LEAVE, "" + x + " " + y);
            }
            clickState = 0;
        }
    }
}
