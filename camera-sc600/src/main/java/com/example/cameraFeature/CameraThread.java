package com.example.cameraFeature;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.example.camera_sc600.CameraSC600;
import com.example.mylibcommon.NativeCamera;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;

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

    private final ArrayList<String> arrStrCamId = new ArrayList<>();
    private final ArrayList<CameraEncode> cameraEncodes = new ArrayList<>();

    private static int nImageSaved = 0;
    private boolean isSendImageStorage = false;

    private boolean networkStatus = false;

    private static ICameraThreadCallback mCallback;
    private CameraSC600 cameraSC600;

    public void setPrintLog(boolean isDebug) {
        printLog = isDebug;
    }

    public CameraThread(Context context, int sn, ICameraThreadCallback callback) {
        this.mContext = context;
        CameraThread.serialNumber = sn;
        CameraEncode.setSerialNumber(sn);
        mCallback = callback;
    }

    public synchronized void start() {
        if (mCallback == null) {
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
        arrStrCamId.clear();
    }

    public void setNetworkStatus(boolean status) {
        networkStatus = status;
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

    private void setupSendImageStorage(String path) {
        File file = new File(path);
        String name = path.substring(path.lastIndexOf('/') + 1);
        boolean isNameImageSave = name.startsWith("img");
        boolean isImageJpg = name.substring(name.lastIndexOf('.')).equals(".jpg");
        if (file.exists() && isImageJpg && isNameImageSave) {
            isSendImageStorage = true;
            ImageHttp imageHttp = new ImageHttp(serialNumber, pathStorage, mContext);
            imageHttp.setImageSendCallBack(imageSendCallBack);
            imageHttp.send(path);
        }
        else {
            isSendImageStorage = false;
        }
    }

    private void getImageInStorage() {
        String imgSdCard = getImageInStorageSdCard();
        String imgExternal = getImageInStorageExternal();
        CameraThread.nImageSaved = countImageExternal + countImageSdCard;
        if (imgExternal != null && imgSdCard != null) {
            String sTimeImgSdCard = imgSdCard.substring(imgSdCard.lastIndexOf('_') + 1, imgSdCard.lastIndexOf('.'));
            long timeImgSdCard = Long.parseLong(sTimeImgSdCard);

            String sTimeImgExternal = imgExternal.substring(imgExternal.lastIndexOf('_') + 1, imgExternal.lastIndexOf('.'));
            long timeImgExternal = Long.parseLong(sTimeImgExternal);
            if (timeImgExternal < timeImgSdCard) {
                setupSendImageStorage(imgExternal);
            }
            else {
                setupSendImageStorage(imgSdCard);
            }
        }
        else if (imgExternal != null) {
            setupSendImageStorage(imgExternal);
        }
        else if (imgSdCard != null) {
            setupSendImageStorage(imgSdCard);
        }
        else {
            CameraThread.nImageSaved = 0;
        }

        CameraEncode.setNumImageStorage(nImageSaved);
    }

    private int countImageSdCard = 0;

    private String getImageInStorageSdCard() {
        countImageSdCard = 0;
        if (!storageStatus) {
            return null;
        }
        String imageDirPath = pathStorage + File.separator + "image_" + serialNumber;
        File imageDir = new File(imageDirPath);
        if (!imageDir.exists()) {
            return null;
        }
        File[] listOfFiles = imageDir.listFiles();
        if (listOfFiles.length == 0) {
            return null;
        }
        String imageToSend = null;
        long timeCompare = System.currentTimeMillis();
        long timeCompare_old = timeCompare;
        for (File item : listOfFiles) {
            String name = item.getName();
            boolean isNameImageSave = name.startsWith("img");
            boolean isImageJpg = name.substring(name.lastIndexOf('.')).equals(".jpg");
            if (isNameImageSave && isImageJpg) {
                countImageSdCard++;
                String sTimeImageSave = name.substring(name.lastIndexOf('_') + 1, name.lastIndexOf('.'));
                long time = Long.parseLong(sTimeImageSave);
                if (time < timeCompare) {
                    timeCompare = time;
                    imageToSend = item.getPath();
                }
            }
        }
        if (timeCompare < timeCompare_old) {
//            setupSendImageStorage(imageToSend);
            return imageToSend;
        }
        return null;
    }

    private int countImageExternal = 0;

    private String getImageInStorageExternal() {
        countImageExternal = 0;
        String imgFolder = "image_" + serialNumber;
        File imageDir = new File(mContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES), imgFolder);
        if (!imageDir.exists()) {
            return null;
        }
        File[] listOfFiles = imageDir.listFiles();
        if (listOfFiles.length == 0) {
            return null;
        }
        String imageToSend = null;
        long timeCompare = System.currentTimeMillis();
        long timeCompare_old = timeCompare;
        for (File item : listOfFiles) {
            String name = item.getName();
            boolean isNameImageSave = name.startsWith("img");
            boolean isImageJpg = name.substring(name.lastIndexOf('.')).equals(".jpg");
            if (isNameImageSave && isImageJpg) {
                countImageExternal++;
                String sTimeImageSave = name.substring(name.lastIndexOf('_') + 1, name.lastIndexOf('.'));
                long time = Long.parseLong(sTimeImageSave);
                if (time < timeCompare) {
                    timeCompare = time;
                    imageToSend = item.getPath();
                }
            }
        }
        if (timeCompare < timeCompare_old) {
            return imageToSend;
        }
        return null;
    }

    private final Thread cameraThread = new Thread(new Runnable() {
        @Override
        public void run() {


            cameraSC600 = CameraSC600.getInstance();
            for (int i = 0; i < 4; i++) {
                arrStrCamId.add(Integer.toString(i));
            }
            for (int i = 0; i < arrStrCamId.size(); i++) {
                cameraEncodes.add(i, new CameraEncode(mContext, arrStrCamId.get(i), i, cameraEncodeCallback));
            }
            long time = (System.currentTimeMillis()) / 1000;
            long timeCheckRunning = time;
            long timeCheckCamExist = time;
            long timeImageSave = time;

            for (CameraEncode cameraEncode : cameraEncodes) {
                cameraEncode.setCameraExist(true);
            }
//            CameraEncode.setPathStorage(true, Environment.getExternalStorageDirectory().getPath());
            while (isRunning) {
                time = System.currentTimeMillis() / 1000;

                if (time >= timeCheckRunning) {
                    timeCheckRunning = time + 60;
                    Log.d(TAG, "run: CameraThread is running ...." + time);
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

                if (networkStatus && (!isSendImageStorage) && (time >= timeImageSave)) {
                    timeImageSave = time + 2;
                    getImageInStorage();
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

    private String pathTempImage;
    private final ImageHttp.ImageSendCallBack imageSendCallBack = new ImageHttp.ImageSendCallBack() {
        @Override
        public void onImageSendStorage(String path, boolean result) {
            pathTempImage = path;
            if (result) {
                if (printLog) {
                    String log = "Send image storage success: " + path.substring(path.lastIndexOf("/") + 1);
                }
                boolean res = new File(path).delete();
                isSendImageStorage = false;
            }
            else if (!networkStatus) {
                isSendImageStorage = false;
            }
            else {
                new Thread(() -> {
                    for (int i = 0; i < 10; i++) {
                        try {
                            Thread.sleep(1000);
                        }
                        catch (InterruptedException ignored) {
                        }
                    }
                    setupSendImageStorage(pathTempImage);
                }).start();
            }
        }

        @Override
        public void onImageSendSuccess(int camId, Date date) {

        }

        @Override
        public void onImageSave(String path) {

        }

        @Override
        public void onImageCreateFail(int camId) {

        }

        @Override
        public void onLogResult(String log) {
            if (printLog) {
                mCallback.onLogCameraThread(log);
            }
        }
    };

    private final CameraEncode.ICameraEncodeCallback cameraEncodeCallback = new CameraEncode.ICameraEncodeCallback() {
        @Override
        public void onVideoStartSave(int camId, String path, long time) {

        }

        @Override
        public void onVideoStop(int camId) {

        }

        @Override
        public void onStreamSuccess(int camId, String urlStream) {
            mCallback.onStreamSuccess(camId, urlStream);
        }

        @Override
        public void onStreamError(int camId) {
            mCallback.onStreamOff(camId);

        }

        @Override
        public void onImageSaveStorage(int camId, String path) {

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
