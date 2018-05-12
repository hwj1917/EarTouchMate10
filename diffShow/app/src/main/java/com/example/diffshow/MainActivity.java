package com.example.diffshow;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
//import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
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
import java.net.Inet4Address;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener {
    public final boolean RECORD = true;
    public final int WIDTH = 1440, HEIGHT = 2560;

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
    private Sensor sensor_rotation;
    private Sensor sensor_accelerometer;
    private Sensor sensor_gyo;
    private boolean isrecording;
    private boolean running;
    private boolean feedbackFlag = false;
    private int times_save = 0;
    private String username = "test";
    private String[] gesturenames = {"坐姿","站姿","走动","侧卧","仰卧"};
    private String[] taskperGesture = {"单手拇指", "单手食指", "双手拇指", "食指关节", "食指侧面", "手机边缘", "左耳45", "左耳0", "左耳-45", "左耳-90", "左耳半圈", "右耳45", "右耳0", "右耳-45", "右耳-90", "右耳半圈", "左耳肩膀夹住", "右耳肩膀夹住", "左右交换", "放口袋"};
    private String[] tasknames = {"按压中间", "移动按压上", "移动按压下",
    "前滑动", "后滑动", "上滑动", "下滑动", "前上滑动", "后上滑动", "前下滑动", "后下滑动", "单次前旋转", "单次后旋转", "布局2航1列", "布局2航2列", "布局3航2列", "布局3航3列", "布局4航3列", "布局4航4列", "布局5航4列", "布局5航5列", "布局6航5列", "策略2布局2航1列", "策略2布局2航2列", "策略2布局3航2列", "策略2布局3航3列", "策略2布局4航3列", "策略2布局4航4列", "策略2布局5航4列", "策略2布局5航5列", "策略2布局6航5列", "自由"};
    private String[] filenames = {"press", "move-press1", "move-press2",
            "swipe1", "swipe2", "swipe3", "swipe4", "swipe5", "swipe6", "swipe7", "swipe8", "one-spin1", "one-spin2", "layout0", "layout1", "layout2", "layout3", "layout4", "layout5", "layout6", "layout7", "layout8", "layout9", "layout10", "layout11", "layout12", "layout13", "layout14", "layout15", "layout16", "layout17", "free"};
    private int taskSum = 32;
    private float[] gravity = {0,0,0};
    private float[] linear_acceleration = {0,0,0};
    private float[] rotation_vector = {0,0,0,0};
    private int finger_num = gesturenames.length * 6;
    private int finger_index = 6;

    private DrawView mDrawView;
    private EditText mEditText;
    private Vibrator mVibrator;
    private TextToSpeech mTTS;
    private int taskIndex = 0;
    private int taskTimes = 0;
    private final int maxTimes = 6;

    private boolean hasInit = false;
    private long lastNotifyTime;

    private int[] layout_x = {1, 2, 2, 3, 3, 4, 4, 5, 5};
    private int[] layout_y = {2, 2, 3, 3, 4, 4, 5, 5, 6};
    private int[] layout_repeat_times = {3, 3, 2, 1, 1, 1, 1, 1, 1};
    private int index_x = -1, index_y = -1;
    private int layout_index, lx, ly;
    private ArrayList<Integer> pointSeq = new ArrayList<>();

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        getPermissions();
        initSensors();

        LinearLayout ll = findViewById(R.id.linearLayout);
        mDrawView = new DrawView(this);
        mEditText = new EditText(this);
        ll.addView(mEditText);
        if (!RECORD)
            ll.addView(mDrawView);

        if (!hasInit) {
            times_save = 0;
            lastNotifyTime = System.currentTimeMillis();
        /*
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(false);
        mediaPlayer = MediaPlayer.create(this, R.raw.audio);
                */
            isrecording = false;

            mVibrator = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);

            mTTS = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    mTTS.setLanguage(Locale.CHINA);
                }
            });

        /*
        capacityView = findViewById(R.id.capacityView);
        Point size = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(size);
        screenWidth = size.x;
        screenHeight = size.y;
        capacityView.screenHeight = screenHeight;
        capacityView.screenWidth = screenWidth;
        capacityView.diffData = diffData;
        capacityView.task_name = taskperGesture[times_save%taskperGesture.length];
        capacityView.gesture_name = gesturenames[times_save/taskperGesture.length];
        capacityView.task_index = Integer.toString(times_save);
        */
            readDiffStart();
            //new ConnectThread().start();

            File file = new File(Environment.getExternalStorageDirectory().getPath() + "/eartouch");
            if (!file.exists())
                file.mkdirs();
            hasInit = true;
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        readDiffStop();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            getAccelerometer(event);
        }
        else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            getRotation(event);
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (RECORD) {
                    if (isrecording) {
                        isrecording = false;
                        feedbackFlag = false;
                        mTTS.speak("记录结束", TextToSpeech.QUEUE_FLUSH, null, "out");
                        final String tn = filenames[taskIndex];
                        new Thread(new Runnable() {
                            public void run() {
                                String dir = Environment.getExternalStorageDirectory().getPath() + "/eartouch/" + username + "_";
                                File file = new File(dir);
                                if (!file.exists())
                                    file.mkdirs();
                                String filename, sensor_filename;
                                if (tn.equals("free"))
                                {
                                    SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");
                                    Date curDate =  new Date(System.currentTimeMillis());
                                    filename = dir + "/" + username + "_" + tn + "_" + formatter.format(curDate) + ".txt";
                                    sensor_filename = dir + "/" + username + "_" + tn + "_" + formatter.format(curDate) + "_sensor.txt";
                                }
                                else {
                                    filename = dir + "/" + username + "_" + tn + ".txt";
                                    sensor_filename = dir + "/" + username + "_" + tn + "_sensor.txt";
                                }
                                if (tn.equals("sensor"))
                                    writeSensors(sensorData, sensorCountSave, filename);
                                else
                                {
                                    writeSensors(sensorData, sensorCountSave, sensor_filename);
                                    writeIntoFile(saveData, timeDataLong, countSave, filename);
                                }

                                if (tn.substring(0, 3).equals("lay"))
                                    writeSeqIntoFile(dir + "/" + username + "_" + tn + "_seq.txt");

                            }
                        }).start();
                        if (++taskIndex == taskSum)
                            taskIndex = taskSum - 1;
                        mEditText.setEnabled(true);

                    } else {
                        isrecording = true;
                        countSave = 0;
                        sensorCountSave = 0;
                        taskTimes = 0;
                        saveData = new short[amountSave * num_pixel];
                        timeDataLong = new Long[amountSave];
                        String[] str = mEditText.getText().toString().split(" ");
                        username = str[0];
                        if (str.length > 1)
                        {
                            for (int index = 0; index < filenames.length; index++)
                                if (str[1].equals(filenames[index])) {
                                    taskIndex = index;
                                    mEditText.setText(str[0]);
                                    break;
                                }
                        }

                        mEditText.setEnabled(false);
                        String tmp = filenames[taskIndex].substring(0, 3);
                        if (tmp.equals("hov"))
                        {
                            CHECK_SUM = Integer.valueOf(filenames[taskIndex].substring(5, filenames[taskIndex].length()));
                        }
                        else if (tmp.equals("mov") || tmp.equals("lay"))
                        {
                            CHECK_SUM = 1;
                        }
                        else CHECK_SUM = 50;

                        if (tmp.equals("hov") || tmp.equals("lay") || tmp.equals("mov"))
                        {
                            feedbackFlag = true;
                        }
                        else feedbackFlag = false;

                        if (tmp.equals("lay"))
                        {
                            layout_index = Integer.valueOf(filenames[taskIndex].substring(6, filenames[taskIndex].length()));
                            lx = layout_x[layout_index % layout_x.length];
                            ly = layout_y[layout_index % layout_x.length];

                            pointSeq.clear();
                            for (int times = 0; times < layout_repeat_times[layout_index % layout_x.length]; times++) {
                                for (int index = 0; index < lx * ly; index++)
                                    pointSeq.add(index);
                            }
                            Collections.shuffle(pointSeq);
                        }

                        String speak = tasknames[taskIndex] + "记录开始";
                        if (tmp.equals("lay"))
                            speak += "目标第" + Integer.toString(pointSeq.get(0) / lx + 1) + "航第" + Integer.toString(pointSeq.get(0) % lx + 1) + "列";
                        mTTS.speak(speak, TextToSpeech.QUEUE_FLUSH, null, "out");

                    }
                }
                else
                {
                    if (!running) {
                        running = true;
                        String filename = mEditText.getText().toString();
                        readFile(filename);
                    }
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (RECORD) {
                    if (!isrecording) {
                        --taskIndex;
                        if (taskIndex < 0 || taskIndex >= taskSum)
                            taskIndex = taskSum - 1;
                        mTTS.speak("重做" + tasknames[taskIndex], TextToSpeech.QUEUE_FLUSH, null, "out");
                    }
                }
                /*
                if (mediaPlayer.isPlaying() == true) {
                    mediaPlayer.pause();
                }
                capacityView.isrecording = false;
                capacityView.istapping = false;
                capacityView.resetCapacity();
                capacityView.invalidate();
                */
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

    public void writeSeqIntoFile(String filename)
    {
        try {
            FileOutputStream fileout = new FileOutputStream(new File(filename));
            for (int i = 0; i < pointSeq.size(); i++) {
                fileout.write((Integer.toString(pointSeq.get(i)) + "\n").getBytes());
            }
            fileout.close();
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

    private void initSensors()
    {
        sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        sensor_accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        sensor_rotation = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION),
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

    private void handlePointForLayout(int x, int y)
    {
        if (layout_index / layout_x.length == 0)
            return;
        int x_interval = WIDTH / lx;
        int y_interval = HEIGHT / ly;
        int xx = x / x_interval, yy = y / y_interval;
        if (xx == lx) xx--;
        if (yy == ly) yy--;
        if (xx != index_x || yy != index_y)
        {
            mTTS.speak("第" + Integer.toString(yy + 1) + "航第" + Integer.toString(xx + 1) + "列", TextToSpeech.QUEUE_FLUSH, null, "out");
            index_x = xx;
            index_y = yy;
        }

    }

    private boolean clear_flag = true, notify_flag = false;
    private int CHECK_SUM = 50;
    private final int TOUCH_MODE_CHECK = 0;
    private final int TOUCH_MODE_CLICK = TOUCH_MODE_CHECK + 1;
    private final int TOUCH_MODE_EXPLORE = TOUCH_MODE_CLICK + 1;
    private final int TOUCH_MODE_PRESS = TOUCH_MODE_EXPLORE + 1;
    private final int TOUCH_MODE_SWIPE = TOUCH_MODE_PRESS + 1;
    private final int TOUCH_MODE_SPIN = TOUCH_MODE_SWIPE + 1;

    private int touch_mode = TOUCH_MODE_CHECK;
    private int checked = 0;
    private int first_checked_x, first_checked_y, last_checked_x, last_checked_y;

    private boolean checkSwipe()
    {
        int dx = last_checked_x - first_checked_x, dy = last_checked_y - first_checked_y;

        if (dx * dx + dy * dy > 300 * 300)
        {
            if (Math.abs(dx) > Math.abs(dy))
            {
                if (dx > 0)
                {
                    Log.d("hwjj", "swipe right");
                    mTTS.speak("右划", TextToSpeech.QUEUE_FLUSH, null, "out");
                }
                else
                {
                    Log.d("hwjj", "swipe left");
                    mTTS.speak("左划", TextToSpeech.QUEUE_FLUSH, null, "out");
                }
            }
            else
            {
                if (dy > 0)
                {
                    Log.d("hwjj", "swipe down");
                    mTTS.speak("下划", TextToSpeech.QUEUE_FLUSH, null, "out");
                }
                else
                {
                    Log.d("hwjj", "swipe up");
                    mTTS.speak("上划", TextToSpeech.QUEUE_FLUSH, null, "out");
                }
            }
            return true;
        }
        else {
            return false;
        }
    }

    public void processDiff(int x, int y, boolean down){
        Log.d("position", Integer.toString(x) + ' ' + Integer.toString(y));
        if  (x <= -100) {
            Log.d("READ", Integer.toString(x) + ' ' + Integer.toString(y) + ' ' + Boolean.toString(down));
           return;
        }

        if (!feedbackFlag) return;

        String fn = filenames[taskIndex];
        if (down) {
            if (clear_flag)
            {
                DrawView.points_x.clear();
                DrawView.points_y.clear();
                first_checked_x = x;
                first_checked_y = y;
                clear_flag = false;
                notify_flag = false;
            }

            switch (touch_mode) {
                case TOUCH_MODE_CHECK:
                    checked++;
                    last_checked_x = x;
                    last_checked_y = y;

                    /*
                    if (x == -1 && y == -1)
                    {
                        Log.d("hwjj", "press");
                        mTTS.speak("按压", TextToSpeech.QUEUE_FLUSH, null, "out");
                        touch_mode = TOUCH_MODE_PRESS;
                    }
                    else if (x == -1 && y == 0)
                    {
                        Log.d("hwjj", "clockwise");
                        mTTS.speak("顺时针", TextToSpeech.QUEUE_FLUSH, null, "out");
                        touch_mode = TOUCH_MODE_SPIN;
                    }
                    else if (x == 0 && y == -1)
                    {
                        Log.d("hwjj", "anticlockwise");
                        mTTS.speak("逆时针", TextToSpeech.QUEUE_FLUSH, null, "out");
                        touch_mode = TOUCH_MODE_SPIN;
                    }
                    else if (checked == CHECK_SUM) {
                        if (checkSwipe()) touch_mode = TOUCH_MODE_SWIPE;
                        else {
                            touch_mode = TOUCH_MODE_EXPLORE;
                            Log.d("hwjj", "explore");
                            mTTS.speak("触摸浏览", TextToSpeech.QUEUE_FLUSH, null, "out");
                            mVibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
                        }
                        checked = 0;
                    }*/
                    if (checked == CHECK_SUM) {
                        touch_mode = TOUCH_MODE_EXPLORE;
                        Log.d("hwjj", "explore");
                        if (CHECK_SUM != 1) {
                            mTTS.speak("触摸浏览", TextToSpeech.QUEUE_FLUSH, null, "out");
                            mVibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
                        }
                        checked = 0;
                    }
                    break;
                case TOUCH_MODE_PRESS:
                case TOUCH_MODE_EXPLORE:
                    if (x == -1 && y == -1 && false) {
                        Log.d("hwjj", "press");
                        mTTS.speak("按压", TextToSpeech.QUEUE_FLUSH, null, "out");
                        touch_mode = TOUCH_MODE_PRESS;
                    } else {
                        DrawView.points_x.add(x);
                        DrawView.points_y.add(y);
                        mDrawView.postInvalidate();
                    }
                    last_checked_x = x;
                    last_checked_y = y;
                    if (fn.substring(0, 3).equals("mov") && fn.charAt(fn.length() - 1) != '5' && !notify_flag) {
                        int dx = last_checked_x - first_checked_x, dy = last_checked_y - first_checked_y;
                        if (dx * dx + dy * dy > 800 * 800) {
                            //mVibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
                            mTTS.speak("滴", TextToSpeech.QUEUE_FLUSH, null, "out");
                            notify_flag = true;
                        }
                    }
                    if (fn.substring(0, 3).equals("lay")) {
                        handlePointForLayout(x, y);
                    }
                    break;
                case TOUCH_MODE_SPIN:
                    if (x == -1 && y == 0)
                    {
                        Log.d("hwjj", "clockwise");
                        mTTS.speak("顺时针", TextToSpeech.QUEUE_FLUSH, null, "out");
                        touch_mode = TOUCH_MODE_SPIN;
                    }
                    else if (x == 0 && y == -1)
                    {
                        Log.d("hwjj", "anticlockwise");
                        mTTS.speak("逆时针", TextToSpeech.QUEUE_FLUSH, null, "out");
                        touch_mode = TOUCH_MODE_SPIN;
                    }
                    break;
                case TOUCH_MODE_SWIPE:

                    break;
            }

        }
        else {
            switch (touch_mode)
            {
                case TOUCH_MODE_CHECK:
                    if (checked > 3) {
                        //if (checkSwipe()) touch_mode = TOUCH_MODE_SWIPE;
                        //else {
                            touch_mode = TOUCH_MODE_CLICK;
                            if (y > 0 && y < 70)
                            {
                                Log.d("hwjj", "click 1");
                                mTTS.speak("单击1", TextToSpeech.QUEUE_FLUSH, null, "out");
                            }
                            else
                            {
                                Log.d("hwjj", "click 2");
                                mTTS.speak("单击2", TextToSpeech.QUEUE_FLUSH, null, "out");
                            }
                        //}
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
            long t = System.currentTimeMillis();

            if (t - lastNotifyTime > 1000) {
                lastNotifyTime = t;
                if (++taskTimes <= maxTimes && !filenames[taskIndex].equals("sensor") && !filenames[taskIndex].substring(0, 3).equals("lay")) {
                    if (taskTimes == 1) mTTS.speak("尝试结束", TextToSpeech.QUEUE_FLUSH, null, "out");
                    else
                        mTTS.speak(Integer.toString(taskTimes - 1), TextToSpeech.QUEUE_FLUSH, null, "out");
                }
                if (filenames[taskIndex].substring(0, 3).equals("lay"))
                {
                    index_x = -1;
                    index_y = -1;
                    if (taskTimes < pointSeq.size()) {
                        int tmp = pointSeq.get(taskTimes);
                        mTTS.speak("目标第" + Integer.toString(tmp / lx + 1) + "航第" + Integer.toString(tmp % lx + 1) + "列", TextToSpeech.QUEUE_FLUSH, null, "out");
                    }
                    else
                    {
                        mTTS.speak("目标完成", TextToSpeech.QUEUE_FLUSH, null, "out");
                    }
                }
            }
            return;
        }

        //Log.d("data","come");
        if(countSave >= amountSave)
        {
            Log.d("save","too many items");
            return;
        }

        long time=System.currentTimeMillis();

        timeDataLong[countSave] = time * 10;

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

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native void readDiffStart();
    public native void readDiffStop();
    public native void readFile(String filename);

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
