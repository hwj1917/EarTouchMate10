package com.example.diffrealtime;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Message;
//import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.SwitchPreference;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.support.v4.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.view.KeyEvent;
import android.hardware.Sensor;
import android.hardware.SensorEventListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

public class MainActivity extends Activity implements SensorEventListener {
    public final boolean RECORD = false;

    public final String TAG = "READ_DIFF_JAVA";
    public final int MY_PERMISSIONS_REQUEST_WRITE_CONTACTS = 1;
    int num_pixel = 32 * 18;
    short diffData[] = new short[num_pixel];
    int amountSave = 120 * 120;
    short saveData[] = new short[num_pixel * amountSave];
    short sendData[] = new short[num_pixel / 2];
    Long[] timeDataLong = new Long[amountSave];
    String[] sensorData = new String[amountSave];
    int countSave = 0, sensorCountSave = 0;

    CapacityView capacityView;
    int screenWidth;
    int screenHeight;

    private MediaPlayer mediaPlayer = new MediaPlayer();
    private SensorManager sensorManager;
    private boolean isrecording;
    private boolean running;
    private int times_save = 0;
    private String username = "test";
    private String[] gesturenames = {"坐姿","站姿","走动","侧卧","仰卧"};
    private String[] taskperGesture = {"单手拇指", "单手食指", "双手拇指", "食指关节", "食指侧面", "手机边缘", "左耳45", "左耳0", "左耳-45", "左耳-90", "左耳半圈", "右耳45", "右耳0", "右耳-45", "右耳-90", "右耳半圈", "左耳肩膀夹住", "右耳肩膀夹住", "左右交换", "放口袋"};
    private String[] tasknames = {"绝对点击", "相对点击", "传感器"};
    private String[] filenames = {"swipe", "press", "sensor"};
    private String[] allTypes = {"press", "swipe1", "swipe2", "swipe3", "swipe4", "move-press1", "move-press2", "layout0", "layout1", "layout2", "layout3", "layout4", "layout5", "layout6", "layout7"};
    private int taskSum = 3;
    private float[] gravity = {0,0,0};
    private float[] linear_acceleration = {0,0,0};
    private float[] rotation_vector = {0,0,0,0};
    private int finger_num = gesturenames.length * 6;
    private int finger_index = 6;

    private DrawView mDrawView;
    private EditText mEditText;
    private Vibrator mVibrator;
    private TextToSpeech mTTS;
    private Calendar calendar;
    private BatteryManager batteryManager;
    private int taskIndex = 0;
    private int taskTimes = 0;
    private final int maxTimes = 5;

    private Statistic st = new Statistic();

    private FileOutputStream logFile = null;
    private FileOutputStream logSumFile = null;
    private FileOutputStream logPointFile = null;
    private boolean earModeFlag = false;

    class Statistic
    {
        public int fileSum = 0;
        public int pressSum = 0;
        public int clickSum = 0;
        public int swipelSum = 0;
        public int swiperSum = 0;
        public int swipeuSum = 0;
        public int swipedSum = 0;
        public int clkwiseSum = 0;
        public int anticlkwiseSum = 0;
        public int exploreSum = 0;
        private List<String> pressList = new LinkedList<>();
        private List<String> clickList = new LinkedList<>();
        private List<String> swipelrList = new LinkedList<>();
        private List<String> swipeudList = new LinkedList<>();
        private List<String> exploreList = new LinkedList<>();
        private List<String> onlyExploreList = new LinkedList<>();
        private List<String> clkList = new LinkedList<>();
        private List<String> emptyList = new LinkedList<>();

        private int bfpressSum = 0;
        private int bfclickSum = 0;
        private int bfswipelSum = 0;
        private int bfswiperSum = 0;
        private int bfswipeuSum = 0;
        private int bfswipedSum = 0;
        private int bfclkwiseSum = 0;
        private int bfanticlkwiseSum = 0;
        private int bfexploreSum = 0;

        private String checkingType;

        public void reset(String s)
        {
            checkingType = s;
            fileSum = pressSum = clickSum = swipelSum = swiperSum = swipeuSum = swipedSum = clkwiseSum = anticlkwiseSum = exploreSum = 0;
            pressList.clear();
            clickList.clear();
            swipelrList.clear();
            swipeudList.clear();
            exploreList.clear();
            onlyExploreList.clear();
            emptyList.clear();
            clkList.clear();
        }

        public void beforeRun()
        {
            bfpressSum = pressSum;
            bfclickSum = clickSum;
            bfswipelSum = swipelSum;
            bfswiperSum = swiperSum;
            bfswipeuSum = swipeuSum;
            bfswipedSum = swipedSum;
            bfexploreSum = exploreSum;
            bfclkwiseSum = clkwiseSum;
            bfanticlkwiseSum = anticlkwiseSum;
        }

        public void afterRun(String filename)
        {
            boolean flag = false;
            if (pressSum > bfpressSum) {
                pressList.add(filename);
                flag = true;
            }
            if (clickSum > bfclickSum) {
                clickList.add(filename);
                flag = true;
            }
            if (swipelSum > bfswipelSum || swiperSum > bfswiperSum) {
                swipelrList.add(filename);
                flag = true;
            }
            if (swipeuSum > bfswipeuSum || swipedSum > bfswipedSum) {
                swipeudList.add(filename);
                flag = true;
            }
            if (clkwiseSum > bfclkwiseSum || anticlkwiseSum > bfanticlkwiseSum)
            {
                clkList.add(filename);
                flag = true;
            }
            if (exploreSum > bfexploreSum && !flag)
                onlyExploreList.add(filename);
            if (exploreSum > bfexploreSum) {
                exploreList.add(filename);
                flag = true;
            }
            if (!flag)
                emptyList.add(filename);
        }

        public void printRes()
        {
            Log.d("hwjj", "press Sum: " + Integer.toString(pressSum));
            Log.d("hwjj", "click Sum: " + Integer.toString(clickSum));
            Log.d("hwjj", "swipe left Sum: " + Integer.toString(swipelSum));
            Log.d("hwjj", "swipe right Sum: " + Integer.toString(swiperSum));
            Log.d("hwjj", "swipe up Sum: " + Integer.toString(swipeuSum));
            Log.d("hwjj", "swipe down Sum: " + Integer.toString(swipedSum));
            Log.d("hwjj", "clockwise Sum: " + Integer.toString(clkwiseSum));
            Log.d("hwjj", "anticlockwise Sum: " + Integer.toString(anticlkwiseSum));
            Log.d("hwjj", "explore Sum: " + exploreSum);
            Log.d("hwjj", "only explore Sum: " + onlyExploreList.size());
            writeLog(logFile, "press file:");
            for (String n : pressList)
                writeLog(logFile, n);
            writeLog(logFile, "click file:");
            for (String n : clickList)
                writeLog(logFile, n);
            writeLog(logFile, "swipe left and right file:");
            for (String n : swipelrList)
                writeLog(logFile, n);
            writeLog(logFile, "swipe up and down file:");
            for (String n : swipeudList)
                writeLog(logFile, n);
            writeLog(logFile, "explore file:");
            for (String n : exploreList)
                writeLog(logFile, n);
            writeLog(logFile, "only explore file:");
            for (String n : onlyExploreList)
                writeLog(logFile, n);
            writeLog(logFile, "clockwise and anticlockwise file:");
            for (String n : clkList)
                writeLog(logFile, n);
            writeLog(logFile, "empty file:");
            for (String n : emptyList)
                writeLog(logFile, n);
            writeLog(logSumFile, checkingType + "," + pressList.size() + "," + clickList.size() + "," + swipelrList.size() + "," + swipeudList.size() + "," + clkList.size() + "," + exploreList.size() + "," + onlyExploreList.size() + "," + emptyList.size());
        }
    }


    //Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @SuppressLint("HandlerLeak")


    private Handler myHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            capacityView.invalidate();

        }
    };

    private SceneHandler sceneHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        getPermissions();
        iniateSensors();
        times_save = 0;

        isrecording = false;

        LinearLayout ll = findViewById(R.id.linearLayout);
        mDrawView = new DrawView(this);
        mEditText = new EditText(this);
        //ll.addView(mEditText);
        if (!RECORD)
            ll.addView(mDrawView);

        mVibrator = (Vibrator)this.getSystemService(VIBRATOR_SERVICE);

        mTTS = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                mTTS.setLanguage(Locale.CHINA);
            }
        });

        batteryManager = (BatteryManager)getSystemService(BATTERY_SERVICE);
        calendar = Calendar.getInstance();
        sceneHandler = new SceneHandler(mTTS, calendar, batteryManager);

        readDiffStart();
        //new ConnectThread().start();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        readDiffStop();
    }

    private void writeLog(FileOutputStream fo, String log)
    {
        if (fo != null) {
            try {
                fo.write((log + '\n').getBytes());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private int lastChecked = 0;

    private double last_angle = -1;
    private double total_angle = 0;
    private int clkwise = 0;
    private int anticlkwise = 0;
    private boolean spinFlag = false;

    private long lastSpinEndTime = 0;
    private final int BIG_SPIN_INTERVAL = 30;
    private final int SMALL_SPIN_INTERVAL = 10;
    private int firstSpinInterval = BIG_SPIN_INTERVAL;
    private final int CONTINUOUS_SPIN_TIME = 2000;
    private final int QUIT_SENSOR_THRESHOULD = 8;

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            getAccelerometer(event);
        }
        else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            getRotation(event);
        }
        else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
        {
            float[] values = event.values;
            float ax = values[0];
            float ay = values[1];
            float az = values[2];
            Log.d("spin", "" + values[0] + " " + values[1] + " " + values[2]);

            if (Math.abs(az) > QUIT_SENSOR_THRESHOULD && earModeFlag)
            {
                mTTS.speak("退出耳朵模式", TextToSpeech.QUEUE_FLUSH, null, "out");
                earModeFlag = false;
                quitEarMode();
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
                sceneHandler.handleOP(sceneHandler.OP_CLKWISE, -1, -1);
                spinFlag = true;
                anticlkwise = 0;
                total_angle = 0;
            }

            if (clkwise == 1)
            {
                Log.d("hwjj", "anticlockwise");
                sceneHandler.handleOP(sceneHandler.OP_ANTICLKWISE, -1, -1);
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

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                sceneHandler.nextScene();
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                sceneHandler.reloadScene();
                return true;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void writeSensors(String[] sensorData,int sum, String filename){
        try {
            FileOutputStream fileout = new FileOutputStream(new File(filename));
            for (int i = 0; i < sum; i++) {
                fileout.write(sensorData[i].getBytes());
            }
            fileout.close();
            Log.d("record", "Write into files " + filename);
        }catch (IOException e) {
            e.printStackTrace();
        }

    }
    public void writeIntoFile(short[] saveData, Long[] timeDataLong, int countSave, String filename) {
        try {
            //Log.d("record","Write into files");
            FileOutputStream fileout = new FileOutputStream(new File(filename));
            Log.d("hwjj", Integer.toString(countSave));
            for (int i = 0; i < countSave; i++) {
                //Log.d("test","in while");
                /*
                for (int j = 0; j < num_pixel/2; j++) {
                    //long value = saveData[i*num_pixel+4*j]*1000000000000L+saveData[i*num_pixel+4*j+1]*100000000L+saveData[4*j+2]*10000L+saveData[4*j+3];
                    //byte[] bytes = ByteBuffer.allocate(8).putLong(value).array();
                    //int value = saveData[i*num_pixel+2*j]*10000+saveData[i*num_pixel+2*j+1];
                    //byte[] bytes = ByteBuffer.allocate(4).putInt(value).array();
                    //fileout.write(bytes);
                    //fileout.write(saveData[i*num_pixel+2*j]);
                    //fileout.write(saveData[i*num_pixel+2*j+1]);
                    //fileout.write(Short.toString(saveData[i*num_pixel+j]).getBytes());
                    //fileout.write(' ');
                }
                */
                byte[] timebyte = ByteBuffer.allocate(8).putLong(timeDataLong[i]).array();
                fileout.write(timebyte);
                for (int j = 0; j < num_pixel / 64; j++) {
                    byte[] bytes = new byte[128];
                    for (int k = 0; k < 16; k++) {
                        //int value = saveData[i*num_pixel+j*8+k*2]*10000+saveData[i*num_pixel+j*8+k*2+1];
                        //byte[] newbytes = ByteBuffer.allocate(4).putInt(value).array();
                        long value = saveData[i * num_pixel + j * 64 + k * 4] * 1000000000000L + saveData[i * num_pixel + j * 64 + k * 4 + 1] * 100000000L + saveData[i * num_pixel + j * 64 + k * 4 + 2] * 10000L + saveData[i * num_pixel + j * 64 + k * 4 + 3];
                        byte[] newbytes = ByteBuffer.allocate(8).putLong(value).array();
                        System.arraycopy(newbytes, 0, bytes, k * 8, 8);
                    }
                    fileout.write(bytes);
                }
            }
            fileout.close();
            Log.d("record", "Write into files " + filename);
            //fileout.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void getAccelerometer(SensorEvent event) {
        if (sensorCountSave == amountSave)
        {
            Log.d("sensor", "too many data.");
            return;
        }

        //Log.d("sensor","acc");
        final float alpha = 0.8f;
        // Isolate the force of gravity with the low-pass filter.
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

        // Remove the gravity contribution with the high-pass filter.
        linear_acceleration[0] = event.values[0] - gravity[0];
        linear_acceleration[1] = event.values[1] - gravity[1];
        linear_acceleration[2] = event.values[2] - gravity[2];
        sensorData[sensorCountSave++] = Float.toString(linear_acceleration[0]) + " " + Float.toString(linear_acceleration[1]) + " " +Float.toString(linear_acceleration[2]) + '\n';

    }

    private void getRotation(SensorEvent event){
        //Log.d("sensor","rot");
        rotation_vector[0] = event.values[0];
        rotation_vector[1] = event.values[1];
        rotation_vector[2] = event.values[2];
        rotation_vector[3] = event.values[3];
    }

    private void iniateSensors()
    {
        sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION),
                SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL);

    }
    private void getPermissions()
    {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this,Manifest.permission.MODIFY_AUDIO_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d("Permission", "Write Fail");
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {


            } else {

                // No explanation needed, we can request the permission.
                Log.d("Permission", "Request Write");
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.MODIFY_AUDIO_SETTINGS},
                        MY_PERMISSIONS_REQUEST_WRITE_CONTACTS);

            }
        }
        else
        {
            Log.d("Permission", "Write Success");
        }
    }

    private boolean clear_flag = true;
    private final int CHECK_TIME = 1000;
    private final int TOUCH_MODE_CHECK = 0;
    private final int TOUCH_MODE_CLICK = TOUCH_MODE_CHECK + 1;
    private final int TOUCH_MODE_EXPLORE = TOUCH_MODE_CLICK + 1;
    private final int TOUCH_MODE_PRESS = TOUCH_MODE_EXPLORE + 1;
    private final int TOUCH_MODE_SWIPE = TOUCH_MODE_PRESS + 1;
    private final int TOUCH_MODE_SPIN = TOUCH_MODE_SWIPE + 1;
    private final int TOUCH_MODE_LONG = TOUCH_MODE_SPIN + 1;

    private int touch_mode = TOUCH_MODE_CHECK;
    private int checked = 0;
    private int first_checked_x, first_checked_y, last_checked_x, last_checked_y;

    private final int MIN_SWIPE_DIST = 240;
    private final int MIN_CHECKED = 2;
    private long check_start = 0;


    private boolean checkSwipe()
    {
        int dx = last_checked_x - first_checked_x, dy = last_checked_y - first_checked_y;

        writeLog(logFile, "dist: " + Math.sqrt(dx * dx + dy * dy));

        if (dx * dx + dy * dy > MIN_SWIPE_DIST * MIN_SWIPE_DIST)
        {
            if (Math.abs(dx) > Math.abs(dy))
            {
                if (dx > 0)
                {
                    Log.d("hwjj", "swipe right");
                    writeLog(logFile, "swipe right");
                    sceneHandler.handleOP(sceneHandler.OP_SWIPE_RIGHT, -1, -1);
                    st.swiperSum++;
                }
                else
                {
                    Log.d("hwjj", "swipe left");
                    writeLog(logFile, "swipe left");
                    sceneHandler.handleOP(sceneHandler.OP_SWIPE_LEFT, -1, -1);
                    st.swipelSum++;
                }
            }
            else
            {
                if (dy > 0)
                {
                    Log.d("hwjj", "swipe down");
                    writeLog(logFile, "swipe down");
                    sceneHandler.handleOP(sceneHandler.OP_SWIPE_DOWN, -1, -1);
                    st.swipedSum++;
                }
                else
                {
                    Log.d("hwjj", "swipe up");
                    writeLog(logFile, "swipe up");
                    sceneHandler.handleOP(sceneHandler.OP_SWIPE_UP, -1, -1);
                    st.swipeuSum++;
                }
            }
            return true;
        }
        else {
            return false;
        }
    }

    private boolean nextEarModeFLag = false;

    public void processDiff(int x, int y, boolean down){
        if  (x <= -100) {
           Log.d("LOGGGG", Integer.toString(x) + ' ' + Integer.toString(y) + ' ' + Boolean.toString(down));
           if (x == -1000) writeLog(logFile, "sum: " + y + " " + down);
           if (x == -10000) writeLog(logFile, "          press dist: " + y);
           if (x == -100000) writeLog(logFile, "FLAG: " + y);
           if (x == -1000000) writeLog(logFile, "pressregion: " + y);
           if (x == -10000000)
           {
               nextEarModeFLag = true;
               mTTS.speak("进入耳朵模式", TextToSpeech.QUEUE_FLUSH, null, "out");
           }
           return;
        }

        if (!down && nextEarModeFLag)
        {
            earModeFlag = true;
            nextEarModeFLag = false;
        }

        if (!earModeFlag) return;

        if (down) {
            if (clear_flag)
            {
                DrawView.points_x.clear();
                DrawView.points_y.clear();
                first_checked_x = x;
                first_checked_y = y;
                clear_flag = false;
            }

            switch (touch_mode)
            {
                case TOUCH_MODE_CHECK:
                    checked++;
                    last_checked_x = x;
                    last_checked_y = y;
                    long now = System.currentTimeMillis();
                    if (checked == 1) {
                        check_start = now;
                        sceneHandler.handleOP(sceneHandler.OP_FIRST_TOUCH, -1, -1);
                    }
                    if (x == -1 && y == -1)
                    {
                        Log.d("hwjj", "press");
                        writeLog(logFile, "press");
                        mTTS.speak("按压", TextToSpeech.QUEUE_FLUSH, null, "out");
                        st.pressSum++;
                        touch_mode = TOUCH_MODE_PRESS;
                    }
                    else if (x == -1 && y == 0)
                    {
                        Log.d("hwjj", "clockwise");
                        writeLog(logFile, "clockwise");
                        mTTS.speak("顺时针", TextToSpeech.QUEUE_FLUSH, null, "out");
                        st.clkwiseSum++;
                        touch_mode = TOUCH_MODE_SPIN;
                    }
                    else if (x == 0 && y == -1)
                    {
                        Log.d("hwjj", "anticlockwise");
                        writeLog(logFile, "anticlockwise");
                        mTTS.speak("逆时针", TextToSpeech.QUEUE_FLUSH, null, "out");
                        st.anticlkwiseSum++;
                        touch_mode = TOUCH_MODE_SPIN;
                    }
                    else if (checked > 0 && now - check_start > CHECK_TIME) {
                        if (spinFlag)
                        {
                            touch_mode = TOUCH_MODE_SPIN;
                        }
                        else {
                            if (checkSwipe()) {
                                touch_mode = TOUCH_MODE_EXPLORE;
                                Log.d("hwjj", "explore");
                                writeLog(logFile, "explore");
                                st.exploreSum++;
                                sceneHandler.handleOP(sceneHandler.OP_EXPLORE, x, y);
                            }
                            else {
                                touch_mode = TOUCH_MODE_LONG;
                                Log.d("hwjj", "long");
                                sceneHandler.handleOP(sceneHandler.OP_LONG_PRESS, x, y);
                                //mVibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
                            }
                            checked = 0;
                        }
                    }
                    break;
                case TOUCH_MODE_PRESS:
                case TOUCH_MODE_EXPLORE:
                    if (x == -1 && y == -1)
                    {
                        Log.d("hwjj", "press");
                        writeLog(logFile, "press");
                        mTTS.speak("按压", TextToSpeech.QUEUE_FLUSH, null, "out");
                        st.pressSum++;
                        touch_mode = TOUCH_MODE_PRESS;
                    }
                    else {
                        DrawView.points_x.add(x);
                        DrawView.points_y.add(y);
                        mDrawView.postInvalidate();
                        sceneHandler.handleOP(sceneHandler.OP_EXPLORE, x, y);
                    }
                    break;
                case TOUCH_MODE_SPIN:
                    if (x == -1 && y == 0)
                    {
                        Log.d("hwjj", "clockwise");
                        writeLog(logFile, "clockwise");
                        mTTS.speak("顺时针", TextToSpeech.QUEUE_FLUSH, null, "out");
                        st.clkwiseSum++;
                        touch_mode = TOUCH_MODE_SPIN;
                    }
                    else if (x == 0 && y == -1)
                    {
                        Log.d("hwjj", "anticlockwise");
                        writeLog(logFile, "anticlockwise");
                        mTTS.speak("逆时针", TextToSpeech.QUEUE_FLUSH, null, "out");
                        st.anticlkwiseSum++;
                        touch_mode = TOUCH_MODE_SPIN;
                    }
                    break;
                case TOUCH_MODE_SWIPE:
                    break;
                case TOUCH_MODE_LONG:
                    sceneHandler.handleOP(sceneHandler.OP_LONG_PRESS, x, y);
                    break;
            }

        }
        else {
            switch (touch_mode)
            {
                case TOUCH_MODE_LONG:
                    sceneHandler.handleOP(sceneHandler.OP_LEAVE, -1, -1);
                    break;
                case TOUCH_MODE_SPIN:
                    lastSpinEndTime = System.currentTimeMillis();
                    sceneHandler.handleOP(sceneHandler.OP_LEAVE, -1, -1);
                    break;
                case TOUCH_MODE_EXPLORE:
                    sceneHandler.handleOP(sceneHandler.OP_LEAVE, -1, -1);
                    break;
                case TOUCH_MODE_CHECK:
                    if (checked > MIN_CHECKED) {
                        if (spinFlag)
                        {
                            touch_mode = TOUCH_MODE_SPIN;
                            lastSpinEndTime = System.currentTimeMillis();
                        }
                        else {
                            if (checkSwipe()) touch_mode = TOUCH_MODE_SWIPE;
                            else {
                                touch_mode = TOUCH_MODE_CLICK;
                                if (++clickState == 1)
                                    new ClickThread().start();
                                if (y > 0 && y < 70) {
                                    writeLog(logFile, "click1");
                                    writeLog(logPointFile, "" + last_checked_x + " " + last_checked_y + " ");
                                    st.clickSum++;
                                } else {
                                    writeLog(logFile, "click2");
                                    writeLog(logPointFile, "" + last_checked_x + " " + last_checked_y + " ");
                                    st.clickSum++;
                                }
                            }
                        }
                    }
                    break;
            }
            checked = 0;
            clear_flag = true;
            touch_mode = TOUCH_MODE_CHECK;
        }


        //Log.d("para",Short.toString(data[num_pixel]));
        //Log.d("time",Long.toString(time)); // for test time

        // not update the capacity data
        //capacityView.diffData =  data;

        /*
        for (int i = 0; i < num_pixel / 2; i++) {
            sendData[i] = data[i];
        }
        new SendThread().start();
        */

    }

    public void record(short[] data, boolean finish)
    {
        if(!isrecording)
            return;

        if (finish)
        {
            if (++taskTimes <= maxTimes && !filenames[taskIndex].equals("sensor"))
                mTTS.speak(Integer.toString(taskTimes), TextToSpeech.QUEUE_FLUSH, null, "out");
            return;
        }

        //Log.d("data","come");
        if(countSave >= amountSave)
        {
            Log.d("save","too many items");
            return;
        }

        long time=System.currentTimeMillis();

        timeDataLong[countSave] = time*10+data[num_pixel];

        /*
        sensorData[countSave] = Float.toString(linear_acceleration[0]) + " " + Float.toString(linear_acceleration[1]) + " " +Float.toString(linear_acceleration[2]) + " " + Float.toString(rotation_vector[0]) + " " + Float.toString(rotation_vector[1]) + " " + Float.toString(rotation_vector[2]) + " " + Float.toString(rotation_vector[3]) + " ";
        for(int i = 0;i < 8;i++)
        {
            sensorData[countSave] += Integer.toString(data[num_pixel+i+1]) + " ";
        }
        sensorData[countSave] += "\n";
        */
        //Log.d("data",Short.toString(data[num_pixel/2+5]));
        for(int i = 0;i < num_pixel;i++) {
            saveData[countSave * num_pixel + i] = data[i];
        }
        countSave++;
        // not update the capacity view
        //myHandler.obtainMessage(0).sendToTarget();
        //Log.d(TAG,"processDiff touchNum :"+data.touchNum);

    }

    public void notifiedEnd()
    {
        running = !running;
    }

    private int clickState = 0;
    private final int CLICK_TIME = 300;
    class ClickThread extends Thread
    {
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
                sceneHandler.handleOP(sceneHandler.OP_DOUBLE_CLICK, -1, -1);
            }
            else {
                Log.d("hwjj", "click");
                sceneHandler.handleOP(sceneHandler.OP_CLICK, x, y);
                sceneHandler.handleOP(sceneHandler.OP_LEAVE, x, y);
            }
            clickState = 0;
        }
    }


    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native void readDiffStart();
    public native void readDiffStop();
    public native void readFile(String filename);
    public native void quitEarMode();

    private Socket socket = null;
    private OutputStream outputStream;

    class ConnectThread extends Thread
    {
        @Override
        public void run()
        {
            try
            {
                socket = new Socket("59.66.132.61", 8086);
                outputStream = socket.getOutputStream();
            } catch (Exception e) {
                Log.d("hwjj", e.toString());
                e.printStackTrace();
            }

        }
    }

    class SendThread extends Thread{

        @Override
        public void run()
        {
            try {
                byte[] bytes = new byte[32 * 18 * 2];
                for (int k = 0; k < 32 * 18; k++) {
                    byte[] newbytes = ByteBuffer.allocate(2).putShort(sendData[k]).array();
                    System.arraycopy(newbytes, 0, bytes, k * 2, 2);
                }
                outputStream.write(bytes);
                outputStream.flush();

            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }
}
