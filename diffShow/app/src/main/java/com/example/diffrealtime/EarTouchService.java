package com.example.diffrealtime;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.Locale;

public class EarTouchService extends AccessibilityService implements SensorEventListener {

    static {
        System.loadLibrary("native-lib");
    }

    private SensorManager sensorManager;
    private EarTouchServer server;
    private TextToSpeech mTTS;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        return START_STICKY;
    }

    @Override
    protected void onServiceConnected() {
        mTTS = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                mTTS.setLanguage(Locale.CHINA);
            }
        });
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL);
        server = new EarTouchServer();
        server.start();
        earModeFlag = false;
        readDiffStart();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        readDiffStop();
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN && !earModeFlag)
        {
            Log.d("hwjj", "enter");
            mTTS.speak("进入耳朵模式", TextToSpeech.QUEUE_FLUSH, null, "out");
            earModeFlag = true;
            enterEarMode();
            addEvent(EarTouchEvent.EVENT_EAR_TOUCH_MODE_ENTER, -1, -1);
        }
        return false;
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

    private boolean earModeFlag = false;

    public void addEvent(int eventType, int x, int y) {
        final EarTouchEvent event = new EarTouchEvent(eventType, x, y);
        new Thread() {
            @Override
            public void run() {
                synchronized (server.events){
                    server.events.offer(event);
                    server.events.notify();
                }
            }
        }.start();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
        {
            float[] values = event.values;
            float ax = values[0];
            float ay = values[1];
            float az = values[2];
            Log.d("sensor", "" + az);
            if (Math.abs(az) > QUIT_SENSOR_THRESHOULD && earModeFlag)
            {
                Log.d("hwjj", "quit");
                mTTS.speak("退出耳朵模式", TextToSpeech.QUEUE_FLUSH, null, "out");
                earModeFlag = false;
                quitEarMode();
                addEvent(EarTouchEvent.EVENT_EAR_TOUCH_MODE_EXIT, -1, -1);
            }

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
                addEvent(EarTouchEvent.EVENT_EAR_TOUCH_SPIN_CLKWISE, -1, -1);
                spinFlag = true;
                anticlkwise = 0;
                total_angle = 0;
            }

            if (clkwise == 1)
            {
                Log.d("hwjj", "anticlockwise");
                addEvent(EarTouchEvent.EVENT_EAR_TOUCH_SPIN_ANTICLKWISE, -1, -1);
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
                    addEvent(EarTouchEvent.EVENT_EAR_TOUCH_SWIPE_FORWARD, -1, -1);
                }
                else
                {
                    Log.d("hwjj", "swipe left");
                    addEvent(EarTouchEvent.EVENT_EAR_TOUCH_SWIPE_BACKWARD, -1, -1);
                }
            }
            else
            {
                if (dy > 0)
                {
                    Log.d("hwjj", "swipe down");
                    addEvent(EarTouchEvent.EVENT_EAR_TOUCH_SWIPE_DOWN, -1, -1);
                }
                else
                {
                    Log.d("hwjj", "swipe up");
                    addEvent(EarTouchEvent.EVENT_EAR_TOUCH_SWIPE_UP, -1, -1);
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
                        addEvent(EarTouchEvent.EVENT_EAR_TOUCH_START, -1, -1);
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
                                addEvent(EarTouchEvent.EVENT_EAR_TOUCH_EXPLORE, x, y);
                            } else {
                                touch_mode = TOUCH_MODE_LONG;
                                Log.d("hwjj", "long");
                                addEvent(EarTouchEvent.EVENT_EAR_TOUCH_LONG_PRESS, x, y);
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
                        addEvent(EarTouchEvent.EVENT_EAR_TOUCH_EXPLORE, x, y);
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
                    addEvent(EarTouchEvent.EVENT_EAR_TOUCH_LONG_PRESS, x, y);
                    break;
            }

        } else {
            switch (touch_mode) {
                case TOUCH_MODE_LONG:
                    addEvent(EarTouchEvent.EVENT_EAR_TOUCH_FINISH, -1, -1);
                    break;
                case TOUCH_MODE_SPIN:
                    lastSpinEndTime = System.currentTimeMillis();
                    addEvent(EarTouchEvent.EVENT_EAR_TOUCH_FINISH, -1, -1);
                    break;
                case TOUCH_MODE_EXPLORE:
                    addEvent(EarTouchEvent.EVENT_EAR_TOUCH_FINISH, -1, -1);
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
                addEvent(EarTouchEvent.EVENT_EAR_TOUCH_DOUBLE_TAP, -1, -1);
                addEvent(EarTouchEvent.EVENT_EAR_TOUCH_FINISH, -1, -1);
            }
            else {
                Log.d("hwjj", "click");
                addEvent(EarTouchEvent.EVENT_EAR_TOUCH_TAP, x, y);
                addEvent(EarTouchEvent.EVENT_EAR_TOUCH_FINISH, -1, -1);
            }
            clickState = 0;
        }
    }
}
