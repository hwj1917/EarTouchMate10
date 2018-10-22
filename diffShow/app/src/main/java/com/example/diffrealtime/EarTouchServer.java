package com.example.diffrealtime;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Queue;

public class EarTouchServer extends Thread {
    final int PORT = 11250;
    public final Queue<EarTouchEvent> events;
    boolean hasFinished;
    ServerSocket serverSocket;
    Socket socket;
    BufferedReader reader;
    PrintStream writer;
    EarTouchEvent crtEvent;
    EarTouchServer(){
        events = new LinkedList<>();
    }

    @Override
    public void run() {
        while (!hasFinished){
            try {
                serverSocket = new ServerSocket(PORT);
                Log.i("socketState", "listening");
                socket = serverSocket.accept();
                Log.i("socketState", "accepted");
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                writer = new PrintStream(socket.getOutputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }

            if(serverSocket == null || reader == null || writer == null){
                continue;
            }

            while (!hasFinished){
                synchronized (events){
                    if(events.isEmpty()){
                        try {
                            events.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                }
                crtEvent = events.poll();
                writer.print(String.format("%d#%d#%d\n", crtEvent.event_type, crtEvent.x, crtEvent.y));
            }

        }
    }
}

class EarTouchEvent{
    public static final int EVENT_EAR_TOUCH_TAP = 1000;
    public static final int EVENT_EAR_TOUCH_DOUBLE_TAP = 1001;
    public static final int EVENT_EAR_TOUCH_PRESS = 1002;
    public static final int EVENT_EAR_TOUCH_SWIPE_UP = 1003;
    public static final int EVENT_EAR_TOUCH_SWIPE_DOWN = 1004;
    public static final int EVENT_EAR_TOUCH_SWIPE_FORWARD = 1005;
    public static final int EVENT_EAR_TOUCH_SWIPE_BACKWARD = 1006;
    public static final int EVENT_EAR_TOUCH_START = 1007;
    public static final int EVENT_EAR_TOUCH_FINISH = 1008;
    public static final int EVENT_EAR_TOUCH_SPIN_CLKWISE = 1009;
    public static final int EVENT_EAR_TOUCH_SPIN_ANTICLKWISE = 1010;
    public static final int EVENT_EAR_TOUCH_EXPLORE = 1011;
    public static final int EVENT_EAR_TOUCH_LONG_PRESS = 1012;
    public static final int EVENT_EAR_TOUCH_MODE_ENTER = 1013;
    public static final int EVENT_EAR_TOUCH_MODE_EXIT = 1014;

    int event_type;
    int x;
    int y;

    EarTouchEvent(int type, int x, int y){
        event_type = type;
        this.x = x;
        this.y = y;
    }
}