package com.htha.testcamsc600;


import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.htha.cameraFeature.CameraThread;
import com.htha.camera_sc600.CameraSC600;
import com.htha.device.DeviceInfo;
import com.htha.httpServer.HttpServer;
import com.htha.mylibcommon.NativeCamera;
import com.htha.mylibcommon.NativeFunction;
import com.htha.playback.PlaybackStream;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ServerHandshake;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    //    private static String LINK_HOST_STREAM = "rtmp://192.168.1.2:1935/live/";
    private static String LINK_HOST_STREAM = "rtmp://192.168.20.101:1935/live/";
    //    private static String LINK_HOST_STREAM = "rtmp://125.212.211.209:1935/live/";
    private static String LINK_HOST = "192.168.1.2";
    private static String LINK_STREAM1 = LINK_HOST_STREAM + "testcamera1";
    private static String LINK_STREAM2 = LINK_HOST_STREAM + "testcamera2";
    private static String LINK_STREAM3 = LINK_HOST_STREAM + "testcamera3";
    private static String LINK_STREAM4 = LINK_HOST_STREAM + "testcamera4";
    private static String LINK_PLAYBACK = LINK_HOST_STREAM + "testplayback";


    private Button btnOnPowerCam;
    private Button btnOffPowerCam;

    private Button btnStreamOn1;
    private Button btnStreamOff1;
    private Button btnStreamOn2;
    private Button btnStreamOff2;
    private Button btnStreamOn3;
    private Button btnStreamOff3;
    private Button btnStreamOn4;
    private Button btnStreamOff4;
    private Button btnSave;
    private Button btnNoSD;


    private Button btnOnPlb;
    private Button btnStopPlb;
    private Button btnOnRTP;
    private Button btnStopRTP;
    private Button btnOnHttp;
    private Button btnOffHttp;

    private HttpServer httpServer;
    private PlaybackStream playbackStream;
    private static final int serialNumber = 999959999;

    private static final int CAMERA_PERMISSION_CODE = 1;//100;
    private static final int STORAGE_PERMISSION_CODE = 1;//101;

    private String cameraId;
    private String cameraIdFront;
    private final int cameraWidth = 1280;
    private final int cameraHeight = 720;
    private int countTimer_record = 0;
    private int countTimer_takePhoto = 0;

    private WebSocketClient webSocketClient;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Example of a call to a native method
        btnOnPowerCam = (Button) this.findViewById(R.id.btn_onPower);
        btnOnPowerCam.setOnClickListener(mOnClickListener);

        btnOffPowerCam = (Button) this.findViewById(R.id.btn_offPower);
        btnOffPowerCam.setOnClickListener(mOnClickListener);

        btnStreamOn1 = (Button) this.findViewById(R.id.button_on1);
        btnStreamOn1.setOnClickListener(mOnClickListener);

        btnStreamOn2 = (Button) this.findViewById(R.id.button_on2);
        btnStreamOn2.setOnClickListener(mOnClickListener);

        btnStreamOff1 = (Button) this.findViewById(R.id.button_off1);
        btnStreamOff1.setOnClickListener(mOnClickListener);

        btnStreamOff2 = (Button) this.findViewById(R.id.button_off2);
        btnStreamOff2.setOnClickListener(mOnClickListener);

        btnStreamOn3 = (Button) this.findViewById(R.id.button_on3);
        btnStreamOn3.setOnClickListener(mOnClickListener);

        btnStreamOn4 = (Button) this.findViewById(R.id.button_on4);
        btnStreamOn4.setOnClickListener(mOnClickListener);

        btnStreamOff3 = (Button) this.findViewById(R.id.button_off3);
        btnStreamOff3.setOnClickListener(mOnClickListener);

        btnStreamOff4 = (Button) this.findViewById(R.id.button_off4);
        btnStreamOff4.setOnClickListener(mOnClickListener);

        btnSave = (Button) this.findViewById(R.id.button_save);
        btnSave.setOnClickListener(mOnClickListener);

        btnNoSD = (Button) this.findViewById(R.id.button_nosd);
        btnNoSD.setOnClickListener(mOnClickListener);

        btnOnPlb = (Button) this.findViewById(R.id.button_OnPlb);
        btnOnPlb.setOnClickListener(mOnClickListener);

        btnStopPlb = (Button) this.findViewById(R.id.button_StopPlb);
        btnStopPlb.setOnClickListener(mOnClickListener);

        btnOnRTP = (Button) this.findViewById(R.id.button_OnRTP1);
        btnOnRTP.setOnClickListener(mOnClickListener);

        btnStopRTP = (Button) this.findViewById(R.id.button_StopRTP1);
        btnStopRTP.setOnClickListener(mOnClickListener);

        btnOnHttp = (Button) this.findViewById(R.id.button_OnHttp);
        btnOnHttp.setOnClickListener(mOnClickListener);

        btnOffHttp = (Button) this.findViewById(R.id.button_OffHttp);
        btnOffHttp.setOnClickListener(mOnClickListener);


//        TextView tv = (TextView) findViewById(R.id.sample_text);
//        tv.setText(stringFromJNI());
        threadTest.start();

        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (cameraThread != null) {
                    Date date = Calendar.getInstance().getTime();
                    latitude = date.getSeconds() * 0.115657f + 10.0f;
                    longitude = date.getSeconds() * 0.14523f + 100.1f;
                    speed = date.getSeconds() * 1.1441f;
                    cameraThread.setInfoLocation(latitude, longitude, speed);
                }
                count++;
                if (count % 20 == 0) {
                    if (cameraThread!=null){ cameraThread.setInfoDeviceStatus(90.0f, "70", "12", "192.168.1.7", 1, 12.2f);}
                    cameraThread.sendInfo();
                }
            }
        }, 1000, 1000);
        NativeCamera.setDriverInfo("BS: ", "LX: N/a");
    }


    int count = 0;

    Timer mTimer = new Timer();
    DeviceInfo deviceInfo;

    CameraThread cameraThread;
    CameraThread.ICameraThreadCallback cameraThreadCallback = new CameraThread.ICameraThreadCallback() {
        @Override
        public void onCameraConnect(int camId) {
            Log.e(TAG, "onCameraConnect: aaaaaaaaaaaaa" + camId );
        }

        @Override
        public void onCameraDisconnect(int camId) {
            Log.d(TAG, "onCameraDisconnect: bbbbbbbbbbbb" + camId);
        }

        @Override
        public void onStreamSuccess(int camId, String url) {

        }

        @Override
        public void onStreamOff(int camId) {

        }

        @Override
        public void onLogCameraThread(String log) {

        }

        @Override
        public void onServerCommand(String cmd) {

        }
    };

    double longitude = 0.0f;
    double latitude = 0.0f;
    double speed = 0.0f;
    private final Thread threadTest = new Thread(new Runnable() {
        @Override
        public void run() {
            if (cameraThread == null) {
                cameraThread = new CameraThread(getApplicationContext(), serialNumber, cameraThreadCallback);
                cameraThread.start();
                cameraThread.setNetworkStatus(true);
            }
        }
    });


    private Timer timeWebSocket;

    private void closeWebSocket() {
        if (timeWebSocket != null) {
            timeWebSocket.cancel();
            timeWebSocket = null;
        }
        if (webSocketClient != null) {
            if (webSocketClient.isOpen()) {
                webSocketClient.close(CloseFrame.NORMAL);
            }
            webSocketClient = null;
        }
    }

    private void connectWebSocket() {
        if (webSocketClient != null) {
            webSocketClient.reconnect();
            return;
        }
        URI uri;
        try {
            uri = new URI("ws://192.168.40.101:8000/");
        }
        catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        webSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handhakedata) {
                if (timeWebSocket != null) {
                    timeWebSocket.cancel();
                    timeWebSocket = null;
                }
                Log.d("Socket", "WebSocket Opened");
                webSocketClient.send("Hello from " + Build.MANUFACTURER + " " + Build.MODEL);
            }

            @Override
            public void onMessage(String message) {
                Log.d("Socket", "WebSocket Receive from sever: " + message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                Log.d("Socket", "WebSocket Closed code: " + code + "..reason: " + reason);

                if (code != CloseFrame.NORMAL) {
                    if (timeWebSocket == null) {
                        timeWebSocket = new Timer();
                        timeWebSocket.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                webSocketClient.reconnect();
                            }
                        }, 10000, 1000);
                    }
                }
                else {
                    closeWebSocket();
                }
            }

            @Override
            public void onError(Exception ex) {
                Log.d("Socket", "WebSocket error ");
                if (webSocketClient != null) {
                    webSocketClient.close(CloseFrame.NEVER_CONNECTED);
                }
            }
        };

        webSocketClient.connect();
    }

    private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btn_onPower:
                    NativeFunction.setGpio(23, true);
                    break;
                case R.id.btn_offPower:
                    NativeFunction.setGpio(23, false);
                    break;
                case R.id.button_on1:
                    Log.i(TAG, "onClick: on1 stream");
                    cameraThread.startStreamRtmp(0, LINK_STREAM1);
                    break;
                case R.id.button_on2:
                    Log.i(TAG, "onClick: on2 stream");
                    cameraThread.startStreamRtmp(1, LINK_STREAM2);
                    break;
                case R.id.button_on3:
                    Log.i(TAG, "onClick: on3 stream");
                    cameraThread.startStreamRtmp(2, LINK_STREAM3);
                    break;
                case R.id.button_on4:
                    Log.i(TAG, "onClick: on4 stream");
                    cameraThread.startStreamRtmp(3, LINK_STREAM4);
                    break;
                case R.id.button_off1:
                    Log.i(TAG, "onClick: off1 stream");
                    cameraThread.stopStream(0);
                    break;
                case R.id.button_off2:
                    Log.i(TAG, "onClick: off2 stream");
                    cameraThread.stopStream(1);
                    break;
                case R.id.button_off3:
                    Log.i(TAG, "onClick: off3 stream");
                    cameraThread.stopStream(2);
                    break;
                case R.id.button_off4:
                    Log.i(TAG, "onClick: off4 stream");
                    cameraThread.stopStream(3);
                    break;
                case R.id.button_save:
                    Log.i(TAG, "onClick: set storage on on");
                    String path = getPathSDCARD();
                    cameraThread.setStorageStatus(true, path);
                    break;
                case R.id.button_nosd:
                    Log.i(TAG, "onClick: set storage off off");
                    cameraThread.setStorageStatus(false, null);
                    break;
                case R.id.button_OnPlb:
                    if (playbackStream == null) {
                        String PathSDCARD = getPathSDCARD();
                        playbackStream = new PlaybackStream(getApplicationContext(), PathSDCARD, new PlaybackStream.IPlaybackCallback() {
                            @Override
                            public void onStreamSuccess(int camId, String url) {

                            }

                            @Override
                            public void onStopPlayback(int camId) {

                            }

                            @Override
                            public void onErrorPlayback(int camId) {

                            }

                            @Override
                            public void onLog(String log) {

                            }
                        });
                        playbackStream.start(LINK_PLAYBACK, 1, 1632359200);
                    }
                    break;
                case R.id.button_StopPlb:
                    if (playbackStream != null) {
                        playbackStream.stop();
                        playbackStream = null;
                    }
                    break;
                case R.id.button_OnRTP1:

                    break;
                case R.id.button_StopRTP1:
//
                    break;
                case R.id.button_OnHttp:
                    if (httpServer == null) {
                        String PathSDCARD = getPathSDCARD();
                        httpServer = new HttpServer(getApplicationContext(), PathSDCARD, serialNumber, 3000,
                                new HttpServer.ServerCallback() {
                                    @Override
                                    public void onTimeout() {

                                    }
                                });
                    }
                    break;
                case R.id.button_OffHttp:
                    if (httpServer != null) {
                        httpServer.stop();
                        httpServer = null;
                    }
                    break;
            }
        }
    };

    private void sendWebSocketMessage(String msg) {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            webSocketClient.send(msg);
        }
    }

    private String getPathSDCARD() {
        File mediaRW = new File("/mnt/media_rw");
        if (mediaRW.exists()) {
            for (File item : Objects.requireNonNull(mediaRW.listFiles())) {
                if (item.getName().length() == 9) {
                    return item.getPath();
                }
            }
        }
        return null;
    }


    @Override
    protected void onStop() {

        super.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "application is onPause");
//        if (cameraThread != null) {
//            cameraThread.stop();
//            cameraThread = null;
//        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: System on destroy");
    }

    public void checkPermission(String permission, int requestCode) {

    }


    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    public static native byte[] getbByteInfo();

    public static native void drawDataToSurface(byte[] dataImage, Surface surface);

    public static native void setSurfaceFormat(Surface surface);

    private native void changeByteArray(byte[] array);

    private native void nCheckCamera();
}