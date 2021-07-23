package com.example.testcameramediacodec;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Environment;
import android.util.Log;

import com.example.mylibcommon.NativeCamera;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

public final class CameraThread {
    private static final String TAG = "CameraThread";
    private Context mContext = null;
    private static int serialNumber = 0;
    private boolean isRunning = false;

    private boolean storageStatus = false;
    private String pathStorage;
    private boolean isStorageStatusChange = false;
    private long timeSetStorage = 0;
    private boolean printLog = true;

    private final ArrayList<String> camIdStr = new ArrayList<>();
    private final ArrayList<CameraEncode> cameraEncodes = new ArrayList<>();

    private static ICameraThreadCallback cameraThreadCallback;

    public void setPrintLog(boolean isDebug) {
        printLog = isDebug;
    }

    public CameraThread(Context context, int sn) {
        this.mContext = context;
        CameraThread.serialNumber = sn;
        CameraEncode.setSerialNumber(sn);
    }

    public void setCameraThreadCallback(ICameraThreadCallback callback) {
        cameraThreadCallback = callback;
    }

    public synchronized void start() {
        if (cameraThreadCallback == null) {
            Log.w(TAG, "start: Callback cameraThread is null");
            return;
        }
        isRunning = true;
        cameraThread.start();
    }

    public void stop() {
        for (CameraEncode cameraEncode : cameraEncodes) {
            cameraEncode.close();
        }

        isRunning = false;
        try {
            cameraThread.join();
        }
        catch (InterruptedException e) {
            Log.e(TAG, "stop: " + e.getMessage());
        }
        cameraEncodes.clear();
        camIdStr.clear();
    }

    public void setInfoLocation(double lat, double lon, double speed) {
        NativeCamera.setInfoLocation(lat, lon, speed);
    }

    public void setDriverInfo(String sBsXe, String sInfo) {
        if (sBsXe != null && sInfo != null) {
            NativeCamera.setDriverInfo(sBsXe, sInfo);
        }
        else {
            NativeCamera.setDriverInfo("BS: n/a", "LX: n/a");
        }
    }

    public void setStorageStatus(boolean status, String path) {
        if (status) {
            File fSdcard = new File(path);
            if (!fSdcard.exists()) {
                Log.d(TAG, "setStorageStatus: Path Storage does not exit..!!!!!");
                return;
            }

            if (storageStatus) {
                if (path.equals(pathStorage)) {
                    return;
                }
                else {
                    CameraEncode.setPathStorage(false, null);
                }
            }
            Log.i(TAG, "setStorageStatus: " + path);
        }
        else {
            CameraEncode.setPathStorage(false, null);
        }

        storageStatus = status;
        this.pathStorage = path;
        timeSetStorage = System.currentTimeMillis() / 1000;
        isStorageStatusChange = true;
    }

    public void startStreamRtmp(int camId, String url) {
        if (camId >= cameraEncodes.size()) {
            return;
        }
        if (cameraEncodes.get(camId).isCameraExist()) {
            cameraEncodes.get(camId).startStreamRtmp(url);
        }
    }

    public void stopStream(int camId) {
        if (camId >= cameraEncodes.size()) {
            return;
        }
        if (cameraEncodes.get(camId).isCameraExist()) {
            cameraEncodes.get(camId).stopStreamRtmp();
        }
    }


    private final Thread cameraThread = new Thread(new Runnable() {
        @Override
        public void run() {
            getCameraIdList();
            for (int i = 0; i <1;i++){// camIdStr.size(); i++) {
                cameraEncodes.add(i, new CameraEncode(mContext, camIdStr.get(i), i));
            }
            long time = (System.currentTimeMillis()) / 1000;
            long timeCheckRunning = 0;
            long timeCheckCamExist = 0;

            for (CameraEncode cameraEncode : cameraEncodes) {
                cameraEncode.setCameraEncodeCallback(cameraEncodeCallback);
                cameraEncode.setCameraExist(true);
            }
//            CameraEncode.setPathStorage(true, Environment.getExternalStorageDirectory().getPath());
            while (isRunning) {
                time = System.currentTimeMillis() / 1000;

                if (time >= timeCheckRunning) {
                    timeCheckRunning = time + 60;
                    Log.d(TAG, "run: CameraThread is running ");
                }
                if (time >= timeCheckCamExist) {
                    timeCheckCamExist = time + 2;
                    checkCameraExist();
                }
                if (isStorageStatusChange) {
                    if (storageStatus && (time > (timeSetStorage + 2))) {
                        isStorageStatusChange = false;
                        CameraEncode.setPathStorage(true, pathStorage);
                    }
                    else if (!storageStatus) {
                        isStorageStatusChange = false;
                        CameraEncode.setPathStorage(false, null);
                    }
                }

                try {
                    Thread.sleep(100);
                }
                catch (InterruptedException ignored) {
                }
            }
        }
    });

    private void checkCameraExist() {


    }

    private void getCameraIdList() {
        CameraManager cameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            camIdStr.addAll(Arrays.asList(cameraManager.getCameraIdList()));
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private final CameraEncode.ICameraEncodeCallback cameraEncodeCallback = new CameraEncode.ICameraEncodeCallback() {
        @Override
        public void onVideoStartSave(int camId, String path, long time) {

        }

        @Override
        public void onVideoStop(int camId) {

        }

        @Override
        public void onStreamSuccess(int camId, String urlStream) {

        }

        @Override
        public void onStreamError(int camId) {

        }

        @Override
        public void onImageSaveStorage(String path) {

        }
    };

    public interface ICameraThreadCallback {
        void onCameraConnect(int camId);

        void onCameraDisconnect(int camId);

        void onStreamSuccess(int camId, String url);

        void onStreamOff(int camId);

        void onLogCameraThread(String log);

    }
}
