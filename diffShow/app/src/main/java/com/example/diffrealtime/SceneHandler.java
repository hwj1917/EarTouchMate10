package com.example.diffrealtime;

import android.bluetooth.le.ScanRecord;
import android.speech.tts.TextToSpeech;

import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

/**
 * Created by Administrator on 2018/6/27.
 */

public class SceneHandler {
    public final int OP_CLICK = 0;
    public final int OP_DOUBLE_CLICK = OP_CLICK + 1;
    public final int OP_SWIPE_UP = OP_DOUBLE_CLICK + 1;
    public final int OP_SWIPE_DOWN = OP_SWIPE_UP + 1;
    public final int OP_SWIPE_LEFT = OP_SWIPE_DOWN + 1;
    public final int OP_SWIPE_RIGHT = OP_SWIPE_LEFT + 1;
    public final int OP_CLKWISE = OP_SWIPE_RIGHT + 1;
    public final int OP_ANTICLKWISE = OP_CLKWISE + 1;
    public final int OP_EXPLORE = OP_ANTICLKWISE + 1;
    public final int OP_LONG_PRESS = OP_EXPLORE + 1;
    public final int OP_FIRST_TOUCH = OP_LONG_PRESS + 1;
    public final int OP_LEAVE = OP_FIRST_TOUCH + 1;

    private String[] sceneNames = {"电话呼入", "电话呼出", "微信语音", "地图导航", "主屏幕"};
    private final int SCENE_SUM = 5;
    private int sceneIndex = 0;

    private TextToSpeech mTTS;

    SceneHandler(TextToSpeech tts)
    {
        mTTS = tts;
    }

    public void nextScene()
    {
        clearScene();
        if (++sceneIndex == SCENE_SUM)
            sceneIndex = 0;
        mTTS.speak(sceneNames[sceneIndex], TextToSpeech.QUEUE_FLUSH, null, "out");
    }

    public String getCurrentScene()
    {
        return sceneNames[sceneIndex];
    }

    public void handleOP(int type, int x, int y)
    {
        switch (sceneNames[sceneIndex])
        {
            case "电话呼入":
                handleOPForCallIn(type);
                break;
            case "电话呼出":
                handleOPForCallOut(type);
                break;
            case "微信语音":
                handleOPForWeChat(type, x, y);
                break;
            case "地图导航":
                handleOPForMap(type);
                break;
            case "主屏幕":
                handleOPForMain(type, x, y);
            default:
                break;
        }
    }

    private void clearScene()
    {
        clearForCallIn();
        clearForCallOut();
        clearForWeChat();
        clearForMap();
        clearForMain();
    }

    private boolean callInFlag = true;
    private boolean callInTalkingFlag = false;

    private void handleOPForCallIn(int type)
    {
        switch (type)
        {
            case OP_CLICK:
                if (callInFlag)
                    mTTS.speak("来电信息", TextToSpeech.QUEUE_FLUSH, null, "out");
                break;
            case OP_DOUBLE_CLICK:
                if (callInFlag) {
                    mTTS.speak("已接通", TextToSpeech.QUEUE_FLUSH, null, "out");
                    callInFlag = false;
                    callInTalkingFlag = true;
                }
                break;
            case OP_SWIPE_UP:
            case OP_SWIPE_DOWN:
            case OP_SWIPE_LEFT:
            case OP_SWIPE_RIGHT:
                if (callInFlag)
                {
                    mTTS.speak("已拒接", TextToSpeech.QUEUE_FLUSH, null, "out");
                    callInFlag = false;
                }
                else if (callInTalkingFlag)
                {
                    mTTS.speak("已挂断", TextToSpeech.QUEUE_FLUSH, null, "out");
                    callInTalkingFlag = false;
                }
                break;
            default:
                break;
        }
    }

    private void clearForCallIn()
    {
        callInFlag = true;
        callInTalkingFlag = false;
    }


    private LinkedList<Integer> callOutNumberStack = new LinkedList<>();
    private int callOutNumber = 5;
    private boolean callOutFlag = true;
    private final int CALLOUT_MIN_NUMBER = 0;
    private final int CALLOUT_MAX_NUMBER = 9;
    private void handleOPForCallOut(int type)
    {
        if (!callOutFlag) return;

        switch (type) {
            case OP_ANTICLKWISE:
                if (++callOutNumber > CALLOUT_MAX_NUMBER)
                    callOutNumber = CALLOUT_MIN_NUMBER;
                mTTS.speak("" + callOutNumber, TextToSpeech.QUEUE_FLUSH, null, "out");
                break;
            case OP_CLKWISE:
                if (--callOutNumber < CALLOUT_MIN_NUMBER)
                    callOutNumber = CALLOUT_MAX_NUMBER;
                mTTS.speak("" + callOutNumber, TextToSpeech.QUEUE_FLUSH, null, "out");
                break;
            case OP_LEAVE:
                callOutNumberStack.addFirst(callOutNumber);
                mTTS.speak("输入" + callOutNumber, TextToSpeech.QUEUE_FLUSH, null, "out");
                callOutNumber = 5;
                break;
            case OP_SWIPE_LEFT:
                mTTS.speak("删除" + callOutNumberStack.pop(), TextToSpeech.QUEUE_FLUSH, null, "out");
                break;
            case OP_SWIPE_DOWN:
                mTTS.speak("已输入", TextToSpeech.QUEUE_FLUSH, null, "out");
                for (int i = callOutNumberStack.size() - 1; i >= 0; i--)
                    mTTS.speak("" + callOutNumberStack.get(i), TextToSpeech.QUEUE_ADD, null, "out");
                break;
            case OP_DOUBLE_CLICK:
                mTTS.speak("拨打", TextToSpeech.QUEUE_FLUSH, null, "out");
                for (int i = callOutNumberStack.size() - 1; i >= 0; i--)
                    mTTS.speak("" + callOutNumberStack.get(i), TextToSpeech.QUEUE_ADD, null, "out");
                callOutFlag = false;
                break;
            default:
                break;
        }
    }

    private void clearForCallOut()
    {
        callOutNumberStack.clear();
        callOutNumber = 5;
        callOutFlag = true;
    }

    private boolean weChatLongPressFlag = false;
    private boolean weChatCancelFlag = false;
    private int weChatLastOp = -1;
    private int weChatFirstY;
    private final int WECHAT_CANCEL_DIST = 300;

    private void handleOPForWeChat(int type, int x, int y)
    {
        switch (type)
        {
            case OP_EXPLORE:
                break;
            case OP_LONG_PRESS:
                weChatLongPressFlag = true;
                if (weChatLastOp != OP_LONG_PRESS)
                    weChatFirstY = y;
                if (!weChatCancelFlag && weChatFirstY - y > WECHAT_CANCEL_DIST)
                {
                    mTTS.speak("已撤销", TextToSpeech.QUEUE_FLUSH, null, "out");
                    weChatCancelFlag  = true;
                }
                break;
            case OP_LEAVE:
                if (weChatLongPressFlag)
                {
                    if (!weChatCancelFlag)
                        mTTS.speak("已发送", TextToSpeech.QUEUE_FLUSH, null, "out");
                    else weChatCancelFlag = false;
                    weChatLongPressFlag = false;
                }
                break;
        }
        weChatLastOp = type;
    }

    private void clearForWeChat()
    {
        weChatLongPressFlag = false;
        weChatCancelFlag = false;
        weChatLastOp = -1;
    }

    private boolean mapFlag = false;
    private boolean mapSpinFlag = false;
    private boolean mapLongPressFlag = false;
    private boolean mapSearchFlag = false;
    private boolean mapConfirmFlag = false;

    private String[] mapDest = {"目的地1", "目的地2"};
    private final int MAP_DEST_SUM = 2;
    private int mapDestNum = 0;

    private String[] mapVehicle = {"步行", "公交"};
    private final int MAP_VEHICLE_SUM = 2;
    private int mapVehicleNum = 0;

    private void handleOPForMap(int type)
    {
        if (mapFlag) return;
        switch (type)
        {
            case OP_CLKWISE:
            case OP_ANTICLKWISE:
                mTTS.speak("语音输入", TextToSpeech.QUEUE_FLUSH, null, "out");
                mapSpinFlag = true;
                break;
            case OP_LONG_PRESS:
                if (mapSpinFlag) {
                    mTTS.speak("请输入目的地", TextToSpeech.QUEUE_FLUSH, null, "out");
                    mapLongPressFlag = true;
                    mapSearchFlag = false;
                    mapConfirmFlag = false;
                }
                break;
            case OP_LEAVE:
                if (mapLongPressFlag)
                {
                    mTTS.speak("已搜索。左右滑动以选择目的地", TextToSpeech.QUEUE_FLUSH, null, "out");
                    mapSpinFlag = false;
                    mapLongPressFlag = false;
                    mapSearchFlag = true;
                    mapConfirmFlag = false;
                }
                break;
            case OP_SWIPE_LEFT:
                if (mapSearchFlag) {
                    if (--mapDestNum < 0)
                        mapDestNum = MAP_DEST_SUM - 1;

                    mTTS.speak(mapDest[mapDestNum], TextToSpeech.QUEUE_FLUSH, null, "out");
                } else if (mapConfirmFlag) {
                    if (--mapVehicleNum < 0)
                        mapVehicleNum = MAP_VEHICLE_SUM - 1;

                    mTTS.speak(mapVehicle[mapVehicleNum], TextToSpeech.QUEUE_FLUSH, null, "out");
                }
                break;
            case OP_SWIPE_RIGHT:
                if (mapSearchFlag) {
                    if (++mapDestNum == MAP_DEST_SUM)
                        mapDestNum = 0;
                    mTTS.speak(mapDest[mapDestNum], TextToSpeech.QUEUE_FLUSH, null, "out");

                } else if (mapConfirmFlag) {
                    if (++mapVehicleNum == MAP_VEHICLE_SUM)
                        mapVehicleNum = 0;
                    mTTS.speak(mapVehicle[mapVehicleNum], TextToSpeech.QUEUE_FLUSH, null, "out");

                }
                break;
            case OP_DOUBLE_CLICK:
                if (mapSearchFlag)
                {
                    mTTS.speak("已选择。左右滑动以选择出行方式" + mapDest[mapDestNum], TextToSpeech.QUEUE_FLUSH, null, "out");
                    mapSearchFlag = false;
                    mapConfirmFlag = true;
                }
                break;
            case OP_SWIPE_DOWN:
                if (mapConfirmFlag)
                {
                    mTTS.speak("开始导航", TextToSpeech.QUEUE_FLUSH, null, "out");
                    mapFlag = true;
                }
                break;
        }
    }

    private void clearForMap()
    {
        mapFlag = false;
        mapSpinFlag = false;
        mapLongPressFlag = false;
        mapSearchFlag = false;
        mapConfirmFlag = false;
        mapDestNum = 0;
        mapVehicleNum = 0;
    }

    private final int MAIN_WIDTH = 1440;
    private final int MAIN_HEIGHT = 2560;
    private final int MAIN_X_SUM = 2;
    private final int MAIN_Y_SUM = 3;
    private final int MAIN_X_INTERVAL = MAIN_WIDTH / MAIN_X_SUM;
    private final int MAIN_Y_INTERVAL = MAIN_HEIGHT / MAIN_Y_SUM;
    private int mainLastIndexX = -1;
    private int mainLastIndexY = -1;
    private String[] mainAppNames = {"微信", "地图", "支付宝", "qq", "滴滴打车", "微博"};

    private void handleOPForMain(int type, int x, int y)
    {
        switch (type) {
            case OP_EXPLORE:
                int xx = x / MAIN_X_INTERVAL, yy = y / MAIN_Y_INTERVAL;
                if (xx == MAIN_X_SUM) xx--;
                if (yy == MAIN_Y_SUM) yy--;
                if (xx != mainLastIndexX || yy != mainLastIndexY) {
                    mTTS.speak(mainAppNames[yy * MAIN_X_SUM + xx], TextToSpeech.QUEUE_FLUSH, null, "out");
                    mainLastIndexX = xx;
                    mainLastIndexY = yy;
                }
                break;
        }
    }

    private void clearForMain()
    {
        mainLastIndexX = -1;
        mainLastIndexY = -1;
    }
}
