package com.example.testcameramediacodec;

import android.content.Context;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import com.example.mylibcommon.NativeCamera;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Date;

public class CameraEncode {
    private static final String TAG = "Camera Encode";
    private String sCameraId;
    private CameraData cameraData;
    private NativeCamera nativeCamera;
    private Context context;
    private static int serialNumber = 0;
    private static final int TIME_IMAGE = 60;
    private static final int TIME_VIDEO = 60;

    private MediaFormat formatMain;
    private EncoderMain encoderMain;
    private VideoMuxer videoMuxer;
    private volatile boolean isGetVideoMainFormat = false;

    private static volatile boolean storageStatus = false;
    private static String pathStorage;
    private static long sTimeSetPathStorage;
    private static boolean isCreatingImageAll = false;
    private boolean isCameraExist = false;

    private MediaFormat formatStream = null;
    private EncoderStream encoderStream;
    private VideoStream videoStream;
    private String urlServer;
    private int camId;
    private volatile boolean isGetVideoFormatRtmp = false;
    private volatile boolean isGetResultSetupStream = false;
    private boolean isLiveStream = false;

    private long timeStartVideo = 0;
    private long timeLastImage = 0;
    private long timeOpenCamera = 0;
    private boolean isCamOpened = false;
    private volatile boolean isRunning = false;

    private static final int IMAGE_FAIL_MAX = 10;
    private long mTimeImage = 0;
    private volatile boolean getImage = false;
    private volatile boolean isGettingImage = false;
    private static int numImageStorage = 0;
    private int imageCreateFail = 0;
    private boolean cmdStartStream = false;
    private boolean cmdStopStream = false;
    private volatile boolean isStartingStream = false;
    private volatile boolean isStoppingStream = false;


    private CameraEncode.ICameraEncodeCallback cameraEncodeCallback;

    private byte[] byteImage = new byte[1280 * 720 * 3 / 2];
    private byte[] byteImageCopy = new byte[1280 * 720 * 3 / 2];

    public CameraEncode(Context context, String cameraId, int iCamId,
                        CameraEncode.ICameraEncodeCallback camEncCallback) {
        this.context = context;
        this.sCameraId = cameraId;
        this.camId = iCamId;
        cameraEncodeCallback = camEncCallback;
    }

    public synchronized void setCameraExist(boolean exist) {
        isCameraExist = exist;
        if (isCameraExist) {
            //// TODO: -- start configure or open camera
            if (cameraEncThread.isAlive()) {
                return;
            }
            cameraEncThread.start();
        }
        else {
            //// TODO: -- stop camera
            if (isRunning) {
                isRunning = false;
                try {
                    cameraEncThread.join();
                }
                catch (InterruptedException e) {
                    Log.e(TAG, "setCameraExist: " + e.getMessage());
                }
            }
            close();
        }
    }

    public boolean isCameraExist() {
        return isCameraExist;
    }

    public static void setSerialNumber(int sn) {
        serialNumber = sn;
    }

    public static void setPathStorage(boolean status, String path) {
        sTimeSetPathStorage = System.currentTimeMillis() / 1000;
        if (!status || path == null || !(new File(path).exists())) {
            Log.d(TAG, "setPathStorage:  false...path null...not exists");
            storageStatus = false;
            pathStorage = null;
            return;
        }
        pathStorage = path;
        storageStatus = true;
    }

    private final Thread cameraEncThread = new Thread(new Runnable() {
        @Override
        public void run() {
            configure();
            long time = System.currentTimeMillis() / 1000;
            long timeTmpSetPath = time;
            isRunning = true;
            long timeCheckRunning = 0;
            long timeStream = time;

            while (isRunning) {
                time = System.currentTimeMillis() / 1000;
                if (time >= (timeCheckRunning + 60)) {
                    timeCheckRunning = time;
                    Log.d(TAG, "run: check camera encode running..." + camId);
                }
                if (storageStatus && (time >= (sTimeSetPathStorage + 5)) && isCamOpened) {
                    if (isSavingVideo()) {
                        if (time >= (timeStartVideo + TIME_VIDEO)) {
                            stopSaveMp4();
                            startSaveMp4();
                        }
                    }
                    else if (time >= timeTmpSetPath) {
                        timeTmpSetPath = time + 1;
                        if (videoMuxer != null) {
                            startSaveMp4();
                        }
                    }
                }
                else if (!storageStatus && isSavingVideo()) {
                    stopSaveMp4();
                }

                if (time >= timeStream) {
                    timeStream = time + 1;
                    if (cmdStartStream && !isStartingStream && !isStoppingStream) {
                        cmdStartStream = false;
//                            startStream.start();
                        new Thread(() -> startStream()).start();

                    }
                    if (cmdStopStream && !isStartingStream && !isStoppingStream) {
                        cmdStopStream = false;
//                        stopStream.start();
                        new Thread(() -> stopStream()).start();
                    }
                }

                if (isCameraExist && (time > (timeOpenCamera + 5))
                        && (time >= (timeLastImage + TIME_IMAGE))) {
                    timeLastImage = time;
                    Log.d(TAG, "run: image image.." + camId);
                    //--TODO: create Image
                    //        getImage = true;
                }
                try {
                    Thread.sleep(100);
                }
                catch (InterruptedException e) {
                    Log.d(TAG, "Thread camEnc..sleep:" + e.getMessage());
                }
            }
            close();
        }
    }, "camEncodeThread");

    private synchronized void configure() {
        isGetVideoMainFormat = false;
        encoderMain = new EncoderMain();
        encoderMain.setEncodeCallback(encMainCallback);
        encoderMain.start();

        nativeCamera = new NativeCamera(camId);
        nativeCamera.setMainSurface(encoderMain.getEncodeSurface());

        cameraData = new CameraData(this.context, this.sCameraId, this.cameraDataCallback);
        cameraData.openCamera();

        while (!isGetVideoMainFormat) {
            try {
                Thread.sleep(100);
            }
            catch (InterruptedException ignored) {
            }
        }

        videoMuxer = new VideoMuxer(camId);
        videoMuxer.setVideoEncodeCallback(videoMuxerCallback);
        videoMuxer.setVideoFormat(formatMain);
    }

    private final CameraData.CameraDataCallback cameraDataCallback = new CameraData.CameraDataCallback() {
        @Override
        public void cameraOpened(String sCamId) {
            if (sCamId.equals(sCameraId)) {
                isCamOpened = true;
                timeOpenCamera = System.currentTimeMillis() / 1000;
            }
        }

        @Override
        public void cameraError(String sCamId) {
            if (sCamId.equals(sCameraId)) {
                isCamOpened = false;
            }
        }

        @Override
        public void cameraDisconnected(String sCamId) {
            if (sCamId.equals(sCameraId)) {
                isCamOpened = false;
            }
        }

        @Override
        public void onRawImage(String sCamId, Image image) {
            if (sCamId.equals(sCameraId)) {
                byte[] iBuffer = ImageUtil.imageGetByteYUV_420_888(image, byteImage);
                if (nativeCamera != null) {
                    nativeCamera.drawBufferInfoToImage(iBuffer);
                    if (getImage) {
                        Date date = new Date();
                        long timeTmp = date.getTime() / 1000;
                        if (mTimeImage == timeTmp) {
                            CameraEncode.isCreatingImageAll = true;
                            isGettingImage = true;
                            getImage = false;
                            System.arraycopy(byteImage, 0, byteImageCopy, 0, byteImage.length);
                            createImage(date);
                        }
                        mTimeImage = timeTmp;
                    }
                }
                image.close();
            }
        }
    };

    public boolean isSavingVideo() {
        if (videoMuxer != null) {
            return (videoMuxer.isSaving());
        }
        return false;
    }

    public static boolean isCreatingImage() {
        return isCreatingImageAll;
    }

    public static void setNumImageStorage(int num){
        numImageStorage = num;
    }

    private void createImage(Date date) {
        ImageHttp imageHttp = new ImageHttp(serialNumber, pathStorage, context);
        imageHttp.setImageSendCallBack(imageSendCallBack);
        boolean sendBuffer = (numImageStorage == 0);
        imageHttp.send(byteImageCopy, camId, date, sendBuffer);

    }

    private void startSaveMp4() {
        if (videoMuxer != null && formatMain != null) {
            videoMuxer.startSaveVideoFile(pathStorage);
            if (encoderMain != null) {
                encoderMain.requestKeyFrame();
            }
        }
    }

    private void stopSaveMp4() {
        if (videoMuxer != null) {
            videoMuxer.stopSaveMp4();
        }
    }

    public boolean isLiveStream() {
        return isLiveStream;
    }

    public synchronized void startStreamRtmp(String url) {
        if (encoderStream != null || videoStream != null || isLiveStream) {
            return;
        }
        if (isStartingStream) {
            return;
        }
        urlServer = url + "?device=" + serialNumber + "&camera=" + camId;
        cmdStartStream = true;
    }

    public synchronized void stopStreamRtmp() {
        if (isStoppingStream) {
            return;
        }
        if (encoderStream != null || videoStream != null || isLiveStream) {
            isLiveStream = false;
            cmdStopStream = true;
        }
    }

//    private Thread startStream = new Thread(new Runnable() {
//        @Override
//        public void run() {
    private void startStream(){
            isStartingStream = true;
            isGetVideoFormatRtmp = false;
            encoderStream = new EncoderStream();
            encoderStream.setEncodeDataCallback(encStreamCallback);

            encoderStream.start();
            Surface surface = encoderStream.getEncodeSurface();
            if (surface==null){
                Log.e(TAG, "run: Surface to stream is null");
                    cmdStopStream = true;
                isStartingStream = false;
            }
            nativeCamera.setStreamSurface(surface);
            int tmp = 0;
            while (!isGetVideoFormatRtmp) {
                try {
                    Thread.sleep(100);
                }
                catch (InterruptedException ignored) {
                }
                tmp++;
                if (tmp > 100) {
                    break;
                }
            }
            if (isGetVideoFormatRtmp) {
                videoStream = new VideoStream(camId);
                videoStream.setVideoEncodeCallback(videoStreamCallback);
                if (formatStream != null) {
                    videoStream.setVideoFormat(formatStream);
                }
                isGetResultSetupStream = false;
                videoStream.startRtmpStream(urlServer);
                while (!isGetResultSetupStream) {
                    try {
                        Thread.sleep(100);
                    }
                    catch (InterruptedException ignored) {
                    }
                }
            }

            if (!isLiveStream) {
                cmdStopStream = true;
            }
            isStartingStream = false;
        }
//    }, "startStreamThread");

//    private Thread stopStream = new Thread(new Runnable() {
//        @Override
//        public void run() {
    private void stopStream(){
            isStoppingStream = true;
            if (nativeCamera != null) {
                nativeCamera.closeStream();
            }
            int tmp = 0;
            while ((!isGetResultSetupStream) || (isStartingStream)) {
                try {
                    Thread.sleep(10);
                }
                catch (InterruptedException ignored) {
                }
                tmp++;
                if (tmp >= 1000) {
                    break;
                }
            }
            if (videoStream != null) {
                videoStream.stopRtmpStream();
                videoStream = null;
            }
            if (encoderStream != null) {
                encoderStream.stop();
                encoderStream = null;
            }
            isStoppingStream = false;
        }
//    }, "stopStreamThread");

    public synchronized void close() {
        if (isLiveStream &&!isStoppingStream) {
//            if (!stopStream.isAlive()) {
//                stopStream.start();
//            }
            new Thread(() -> stopStream()).start();
        }
        isRunning = false;
        try {
            cameraEncThread.join();
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (cameraData != null) {
            cameraData.closeCamera();
            cameraData = null;
        }

        if (isGettingImage && isCreatingImageAll) {
            isCreatingImageAll = false;
        }

        if (nativeCamera != null) {
            nativeCamera.close();
            nativeCamera = null;
        }

        if (videoMuxer != null) {
            videoMuxer.close();
            videoMuxer = null;
        }

        if (encoderMain != null) {
            encoderMain.stop();
            encoderMain = null;
        }

        formatMain = null;
        formatStream = null;
    }

    private final ImageHttp.ImageSendCallBack imageSendCallBack = new ImageHttp.ImageSendCallBack() {
        @Override
        public void onImageSendStorage(String path, boolean result) {

        }

        @Override
        public void onImageSendSuccess(int camId, Date date) {
            imageCreateFail = 0;
            CameraEncode.isCreatingImageAll = false;
            isGettingImage = false;
        }

        @Override
        public void onImageSave(String path) {
            numImageStorage++;
            imageCreateFail = 0;
            CameraEncode.isCreatingImageAll = false;
            isGettingImage = false;
            cameraEncodeCallback.onImageSaveStorage(camId, path);
        }

        @Override
        public void onImageCreateFail(int camIdCb) {
            imageCreateFail++;
            if (camId == camIdCb) {
                if (imageCreateFail < IMAGE_FAIL_MAX) {
                    getImage = true;
                }
                else {
                    Log.d(TAG, "onImageCreateFail: TO MUCH IMAGE CREATE FAIL.!!!.." + camId);
                    imageCreateFail = 0;
                    CameraEncode.isCreatingImageAll = false;
                    isGettingImage = false;
                }
            }
        }

        @Override
        public void onLogResult(String log) {

        }
    };

    private final EncoderStream.EncodeDataCallback encStreamCallback = new EncoderStream.EncodeDataCallback() {
        @Override
        public void onEncodeFormatChange(MediaFormat videoFormat) {
            Log.d(TAG, "onEncodeFormatChange: get video encode format stream rtmp.." + camId);
            formatStream = videoFormat;
            isGetVideoFormatRtmp = true;
        }

        @Override
        public void onDataVideoEncodeOutput(ByteBuffer buffer, MediaCodec.BufferInfo bufferInfo) {
            if (isGetVideoFormatRtmp && videoStream != null && isLiveStream) {
                videoStream.writeVideoData(buffer, bufferInfo);
            }
        }
    };

    private final VideoStream.IVideoStreamCallback videoStreamCallback = new VideoStream.IVideoStreamCallback() {
        @Override
        public void onStreamSuccess(int camId, String urlStream) {
            if (encoderStream != null) {
                encoderStream.requestKeyFrame();
            }
            if (cameraEncodeCallback != null) {
                cameraEncodeCallback.onStreamSuccess(camId, urlStream);
            }
            isLiveStream = true;
            isGetResultSetupStream = true;
        }

        @Override
        public void onStreamError(int camId) {
            if (cameraEncodeCallback != null) {
                cameraEncodeCallback.onStreamError(camId);
            }
            isLiveStream = false;
            isGetResultSetupStream = true;
        }
    };

    private final EncoderMain.EncodeCallback encMainCallback = new EncoderMain.EncodeCallback() {

        @Override
        public void onEncodeFormatChange(MediaFormat videoFormat) {
            Log.d(TAG, "onEncodeFormatChange: Get video format encode Ok.." + camId);
            formatMain = videoFormat;
            isGetVideoMainFormat = true;
        }

        @Override
        public void onDataVideoEncodeOutput(ByteBuffer buffer, MediaCodec.BufferInfo bufferInfo) {
            if (videoMuxer != null) {
                videoMuxer.writeVideoData(buffer, bufferInfo);
            }
        }
    };

    private final VideoMuxer.IVideoMuxerCallback videoMuxerCallback = new VideoMuxer.IVideoMuxerCallback() {
        @Override
        public void onVideoStartSave(String path, long time) {
            timeStartVideo = time;
            if (cameraEncodeCallback != null) {
                cameraEncodeCallback.onVideoStartSave(camId, path, time);
            }
        }

        @Override
        public void onVideoStop() {
            cameraEncodeCallback.onVideoStop(camId);
        }

        @Override
        public void onStreamSuccess(int camId, String urlStream) {
        }

        @Override
        public void onStreamError(int camId) {
        }
    };

    public interface ICameraEncodeCallback {
        void onVideoStartSave(int camId, String path, long time);

        void onVideoStop(int camId);

        void onStreamSuccess(int camId, String urlStream);

        void onStreamError(int camId);

        void onImageSaveStorage(int camId, String path);
    }
}
