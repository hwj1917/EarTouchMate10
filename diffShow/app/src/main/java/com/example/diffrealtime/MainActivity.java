package com.example.diffrealtime;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
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
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Locale;

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
    private Sensor sensor_rotation;
    private Sensor sensor_accelerometer;
    private Sensor sensor_gyo;
    private boolean isrecording;
    private boolean running;
    private int times_save = 0;
    private String username = "test";
    private String[] gesturenames = {"坐姿","站姿","走动","侧卧","仰卧"};
    private String[] taskperGesture = {"单手拇指", "单手食指", "双手拇指", "食指关节", "食指侧面", "手机边缘", "左耳45", "左耳0", "左耳-45", "左耳-90", "左耳半圈", "右耳45", "右耳0", "右耳-45", "右耳-90", "右耳半圈", "左耳肩膀夹住", "右耳肩膀夹住", "左右交换", "放口袋"};
    private String[] tasknames = {"绝对点击", "相对点击", "传感器"};
    private String[] filenames = {"swipe", "press", "sensor"};
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
    private int taskIndex = 0;
    private int taskTimes = 0;
    private final int maxTimes = 5;

    private boolean modeFlag = false;

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
        iniateSensors();
        times_save = 0;

        /*
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(false);
        mediaPlayer = MediaPlayer.create(this, R.raw.audio);
                */
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
                modeFlag = !modeFlag;
                switchMode();
                mTTS.speak("模式已切换" , TextToSpeech.QUEUE_FLUSH, null, "out");

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

    private boolean clear_flag = true;
    private final int CHECK_SUM = 50;
    private final int TOUCH_MODE_CHECK = 0;
    private final int TOUCH_MODE_CLICK = TOUCH_MODE_CHECK + 1;
    private final int TOUCH_MODE_EXPLORE = TOUCH_MODE_CLICK + 1;
    private final int TOUCH_MODE_PRESS = TOUCH_MODE_EXPLORE + 1;
    private final int TOUCH_MODE_SWIPE = TOUCH_MODE_PRESS + 1;
    private final int TOUCH_MODE_SPIN = TOUCH_MODE_SWIPE + 1;

    private int touch_mode = TOUCH_MODE_CHECK;
    private int checked = 0;
    private int first_checked_x, first_checked_y, last_checked_x, last_checked_y;


    private final int maxX = 1440;
    private final int maxY = 2560;
    private String[] appNames = {"微信", "今日头条", "网易云音乐", "浏览器", "支付宝", "地图", "微博", "美团", "联系人", "短信", "拨号", "设置"};
    private final int appSum = 12;
    private final int xSum = 3;
    private final int ySum = appSum / xSum;
    private final int xInv = maxX / xSum;
    private final int yInv = maxY / ySum;
    private int appIndex = 0;

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
                    if (++appIndex == appSum)
                        appIndex = 0;
                    if (!modeFlag)
                        mTTS.speak(appNames[appIndex], TextToSpeech.QUEUE_FLUSH, null, "out");
                }
                else
                {
                    Log.d("hwjj", "swipe left");
                    if (--appIndex == -1)
                        appIndex = appSum - 1;
                    if (!modeFlag)
                        mTTS.speak(appNames[appIndex], TextToSpeech.QUEUE_FLUSH, null, "out");

                }
            }
            else
            {
                if (dy > 0)
                {
                    Log.d("hwjj", "swipe down");
                    //mTTS.speak("下划", TextToSpeech.QUEUE_FLUSH, null, "out");
                }
                else
                {
                    Log.d("hwjj", "swipe up");
                    //mTTS.speak("上划", TextToSpeech.QUEUE_FLUSH, null, "out");
                }
            }
            return true;
        }
        else {
            return false;
        }
    }

    public void processDiff(int x, int y, boolean down){

        if  (x <= -100) {
           Log.d("READ", Integer.toString(x) + ' ' + Integer.toString(y) + ' ' + Boolean.toString(down));
           return;
        }


        //mode1
        if (down) {
            if (clear_flag) {
                DrawView.points_x.clear();
                DrawView.points_y.clear();
                first_checked_x = x;
                first_checked_y = y;
                clear_flag = false;
            }

            switch (touch_mode) {
                case TOUCH_MODE_CHECK:
                    checked++;
                    last_checked_x = x;
                    last_checked_y = y;
                    if (x == -1 && y == -1) {
                        Log.d("hwjj", "press");
                        mTTS.speak("按压", TextToSpeech.QUEUE_FLUSH, null, "out");
                        touch_mode = TOUCH_MODE_PRESS;
                    } else if (x == -1 && y == 0) {
                        Log.d("hwjj", "clockwise");
                        mTTS.speak("顺时针", TextToSpeech.QUEUE_FLUSH, null, "out");
                        touch_mode = TOUCH_MODE_SPIN;
                    } else if (x == 0 && y == -1) {
                        Log.d("hwjj", "anticlockwise");
                        mTTS.speak("逆时针", TextToSpeech.QUEUE_FLUSH, null, "out");
                        touch_mode = TOUCH_MODE_SPIN;
                    } else if (checked == CHECK_SUM) {
                        if (checkSwipe()) touch_mode = TOUCH_MODE_SWIPE;
                        else {
                            touch_mode = TOUCH_MODE_PRESS;
                            Log.d("hwjj", "press");
                            if (!modeFlag)
                                mTTS.speak("进入" + appNames[appIndex] + "应用", TextToSpeech.QUEUE_FLUSH, null, "out");
                            touch_mode = TOUCH_MODE_PRESS;
                        }
                        checked = 0;
                    }
                    break;
                case TOUCH_MODE_PRESS:
                case TOUCH_MODE_EXPLORE:
                    if (x == -1 && y == -1) {
                        Log.d("hwjj", "press");
                        mTTS.speak("按压", TextToSpeech.QUEUE_FLUSH, null, "out");
                        touch_mode = TOUCH_MODE_PRESS;
                    } else {
                        DrawView.points_x.add(x);
                        DrawView.points_y.add(y);
                        mDrawView.postInvalidate();
                    }
                    break;
                case TOUCH_MODE_SPIN:
                    if (x == -1 && y == 0) {
                        Log.d("hwjj", "clockwise");
                        mTTS.speak("顺时针", TextToSpeech.QUEUE_FLUSH, null, "out");
                        touch_mode = TOUCH_MODE_SPIN;
                    } else if (x == 0 && y == -1) {
                        Log.d("hwjj", "anticlockwise");
                        mTTS.speak("逆时针", TextToSpeech.QUEUE_FLUSH, null, "out");
                        touch_mode = TOUCH_MODE_SPIN;
                    }
                    break;
                case TOUCH_MODE_SWIPE:

                    break;
            }

        } else {
            switch (touch_mode) {
                case TOUCH_MODE_CHECK:
                    if (checked > 3) {
                        if (checkSwipe()) touch_mode = TOUCH_MODE_SWIPE;
                        else {
                            touch_mode = TOUCH_MODE_CLICK;
                            if (last_checked_y < 1280)
                                mTTS.speak("现在是十二点二十二分", TextToSpeech.QUEUE_FLUSH, null, "out");
                            else mTTS.speak("剩余电量百分之八十", TextToSpeech.QUEUE_FLUSH, null, "out");
                        }
                    }
                    break;
            }
            checked = 0;
            clear_flag = true;
            touch_mode = TOUCH_MODE_CHECK;
        }

        //mode2
        if (down)
        {
            if (x == -1 && y == -1) {
                if (modeFlag)
                    mTTS.speak("进入" + appNames[appIndex] + "应用" , TextToSpeech.QUEUE_FLUSH, null, "out");
            }
            else
            {
                int _x = x / xInv;
                int _y = y / yInv;

                if (modeFlag) {
                    int lastIndex = appIndex;
                    appIndex = _y * xSum + _x;
                    if (appIndex != lastIndex)
                        mTTS.speak(appNames[appIndex], TextToSpeech.QUEUE_FLUSH, null, "out");
                }
            }
        }
        else
        {

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

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native void readDiffStart();
    public native void readDiffStop();
    public native void switchMode();
    //public native void readFile(String filename);

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
