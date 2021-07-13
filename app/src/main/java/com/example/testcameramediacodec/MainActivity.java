package com.example.testcameramediacodec;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ServerHandshake;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    private Button btnStreamOn;
    private Button btnStreamOff;
    private Button btnStreamOn2;
    private Button btnStreamOff2;
    private Button btnConnectWebSk;
    private Button btnCloseWebSk;


    private static final int CAMERA_PERMISSION_CODE = 1;//100;
    private static final int STORAGE_PERMISSION_CODE = 1;//101;

    private String cameraId;
    private String cameraIdFront;
    private CameraDevice cameraDevice;
    private CameraManager cameraManager;
    private final int cameraWidth = 1280;
    private final int cameraHeight = 720;
    private Timer timer;
    private int countTimer_record = 0;
    private int countTimer_takePhoto = 0;

    CameraObj cameraObj = null;
    EncoderH264_thread encoderH264 = null;
    CameraEncode cameraEncode = null;
    private final boolean notUseService = false;
    private WebSocketClient webSocketClient;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Example of a call to a native method
        btnStreamOn = this.findViewById(R.id.buttonStreamOn);

        btnStreamOff = this.findViewById(R.id.buttonStreamOff);

        btnStreamOn2 = this.findViewById(R.id.buttonStream0n2);

        btnStreamOff2 = this.findViewById(R.id.buttonStreamOff2);

        btnConnectWebSk = this.findViewById(R.id.btnConnectWebsk);

        btnCloseWebSk = this.findViewById(R.id.btnCloseWebsk);


        TextView tv = findViewById(R.id.sample_text);
        tv.setText(stringFromJNI());
        checkLogicalMultiCamera();
        cameraEncode = new CameraEncode(this.getApplicationContext(), cameraId,
        Environment.getExternalStorageDirectory().getPath());

        cameraEncode.configure();

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {

                cameraEncode.closeFileMP4();;
                cameraEncode.saveFileMP4();
            }
        },  10000, 30000);



        byte[] arr = new byte[20];
        StringBuilder debug1 = new StringBuilder();
        for (byte item : arr) {
            item = 0x00;
            debug1.append(item).append(" ");
        }
        Log.d(TAG, "onCreate: begin:  " + debug1.toString());
        this.changeByteArray(arr);

        StringBuilder debug2 = new StringBuilder();
        for (byte item : arr) {
            debug2.append(item).append(" ");
        }
        Log.d(TAG, "onCreate: change:  " + debug2.toString());
    }

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


    private void sendWebSocketMessage(String msg) {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            webSocketClient.send(msg);
        }
    }

    private void sendMessageBroadcast(String msg) {
        Log.d("sender", "Broadcasting message");
        Intent intent = new Intent("custom-event-name");
        intent.putExtra("message", msg);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "system is on Stop");
        encoderH264.stop();
        cameraObj.closeCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "application is onPause");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: System on destroy");
    }

    public void checkPermission(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(MainActivity.this, permission)
                == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{permission},
                    requestCode);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


    private void checkLogicalMultiCamera() {
        CameraManager cameraManager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(id);
                int[] capabilities = cameraCharacteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);

                boolean supportLogicalCamera = Collections.singletonList(capabilities).contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA);
                if (supportLogicalCamera) {
                    Set<String> physicalCameraIds = cameraCharacteristics.getPhysicalCameraIds();
                    Log.i(TAG, "checkLogicalMultiCamera: android.logicalCam.physicalCameraIds shouldn't be null " +
                            physicalCameraIds.toString());
                }
                for (int i : capabilities) {
                    if (i == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) {
                        Set<String> physicalCameraIds = cameraCharacteristics.getPhysicalCameraIds();
                        for (String item : physicalCameraIds) {
                            Log.i(TAG, "checkLogicalMultiCamera: physicalCameraIds: " + item);
                            int sensor = cameraCharacteristics.get(CameraCharacteristics.LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE);
                            if (sensor == CameraMetadata.LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE_APPROXIMATE) {
                                Log.i(TAG, "Camera " + id + " Logical multi camera sensor sync type approximate");
                            }
                            else if (sensor == CameraMetadata.LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE_CALIBRATED) {
                                Log.i(TAG, "Camera " + id + "Logical multi camera sensor sync type calibrated");
                            }
                            else {
                                Log.i(TAG, "Camera " + id + " noLogical");
                            }
                        }
                    }
                }
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_FRONT) {
                    cameraIdFront = id;
                    Log.d(TAG, "checkLogicalMultiCamera: camera front.." + id);
                }
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id;
                    Log.d(TAG, "checkLogicalMultiCamera: camera back..." + id);
                }
            }
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
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
}