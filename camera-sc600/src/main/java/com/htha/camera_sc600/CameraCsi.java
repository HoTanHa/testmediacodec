


package com.htha.camera_sc600;

import android.util.Log;

import com.quectel.qcarapi.stream.QCarCamera;

import java.nio.ByteBuffer;
import java.util.Date;

public class CameraCsi {

    private static final String TAG = "CameraCsi";
    private static final boolean LOG_DEBUG = true;
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int FPS_SET = 15;
    private volatile boolean isRunning = false;
    private QCarCamera qCarCamera;
    private ByteBuffer byteBufferData;
    private byte[] byteArray = new byte[WIDTH * HEIGHT * 3 / 2];
    private CameraCsiCallback mCallback = null;


    private static final int WIDTH_sub = 640;
    private static final int HEIGHT_sub = 480;
    private ByteBuffer byteBufferData_sub;
    private byte[] byteArray_sub = new byte[WIDTH_sub * HEIGHT_sub * 3 / 2];
    private boolean isGetSub = false;
    private int countFrameTmp = 0;

    private CamInfo mCamInfo;
    private int inputChannel = 0;
    private int csiNum = 0;
    private volatile long frameID = 0;
    private volatile long frameID_sub = 0;
    private Date date = new Date();

    QCarCamera.FrameInfo frameInfo;
    QCarCamera.FrameInfo frameInfo_sub;

    public CameraCsi(int camId, CameraCsiCallback callback) {
        CameraSC600 cameraSC600 = CameraSC600.getInstance();
        this.mCamInfo = cameraSC600.getCamInfo(camId);
        if (mCamInfo == null) {
            return;
        }
        this.mCallback = callback;
        int result;

        csiNum = mCamInfo.getCsiNum();
        inputChannel = mCamInfo.getInputChannel();

        qCarCamera = cameraSC600.getQCarCamera(csiNum);
        if (qCarCamera == null) {
            return;
        }

        result = qCarCamera.setVideoColorFormat(inputChannel, QCarCamera.YUV420_NV12);
        if (result != 0) {
            if (LOG_DEBUG) {
                Log.d(TAG, "CameraCsi: Set VideoColorFormat Fail..." + mCamInfo.toString());
            }
        }

        result = qCarCamera.setFpsLogDebug(inputChannel, 0);
        if (result != 0) {
            Log.d(TAG, "CameraCsi: Set fps log debug fail.");
        }

        result = qCarCamera.setVideoSize(inputChannel, WIDTH, HEIGHT);
        if (result != 0) {
            if (LOG_DEBUG) {
                Log.d(TAG, "CameraCsi: Set VideoSize Fail..." + mCamInfo.toString());
            }
        }

        result = qCarCamera.setFps(inputChannel, FPS_SET);
        if (result != 0) {
            if (LOG_DEBUG) {
                Log.d(TAG, "CameraCsi: Set Fps Fail..." + mCamInfo.toString());
            }
        }

        result = qCarCamera.startVideoStream(inputChannel);
        if (result != 0) {
            if (LOG_DEBUG) {
                Log.d(TAG, "CameraCsi: startVideoStream Fail..." + mCamInfo.toString());
            }
            return;
        }

        result = qCarCamera.setSubStreamSize(inputChannel, WIDTH_sub, HEIGHT_sub);
        if (result != 0) {
            if (LOG_DEBUG) {
                Log.d(TAG, "CameraCsi: set SubStreamSize fail.." + mCamInfo.toString());
            }
        }

        result = qCarCamera.startSubStream(inputChannel);
        if (result != 0) {
            if (LOG_DEBUG) {
                Log.d(TAG, "CameraCsi: startVideoStream Fail..." + mCamInfo.toString());
            }
            return;
        }

        byteBufferData_sub = ByteBuffer.wrap(byteArray_sub);

        byteBufferData = ByteBuffer.wrap(byteArray);
        isRunning = true;
        mCallback.onCameraOpen();
        camCsiThread.start();
    }

    private final Thread camCsiThread = new Thread(() -> {
        while (isRunning) {
            try {
                Thread.sleep(10);
            }
            catch (InterruptedException ignored) {
            }
            if (CameraSC600.isCameraError()){
                continue;
            }
            if (!isGetSub) {
                frameInfo = qCarCamera.getVideoFrameInfo(inputChannel, byteBufferData);
                if (frameInfo != null) {
                    if (frameID < frameInfo.frameID) {
                        frameID = frameInfo.frameID;
                        mCallback.onRawData(byteBufferData);
                        if ((frameID % 1000 == 1)) {
                            if (LOG_DEBUG) {
                                date.setTime(System.currentTimeMillis());
                                Log.d(TAG, "CameraCsi:.." + frameID + ".." + mCamInfo.toString() + "..." + date);
                            }
                        }
                        countFrameTmp++;
                        if (countFrameTmp == 5) {
                            countFrameTmp = 0;
                            isGetSub = true;
                        }
                    }
                    else {
                        frameID = frameInfo.frameID;
                    }
                }
            }
            else {
                frameInfo_sub = qCarCamera.getSubFrameInfo(inputChannel, byteBufferData_sub);
                if (frameInfo_sub != null) {
                    if (frameID_sub < frameInfo_sub.frameID) {
                        frameID_sub = frameInfo_sub.frameID;
                        mCallback.onRawDataSub(byteBufferData_sub);
                        isGetSub = false;
                    }
                    else {
                        frameID_sub = frameInfo_sub.frameID;
                    }
                }
            }
        }
        qCarCamera.stopSubStream(inputChannel);
        qCarCamera.stopVideoStream(inputChannel);
    });

    public void close() {
        isRunning = false;
        try {
            camCsiThread.join();
        }
        catch (InterruptedException ignored) {
        }
        byteBufferData_sub.clear();
        byteBufferData.clear();
        byteArray = null;
        byteArray_sub = null;


        Log.d(TAG, "close: cameraCsi get frame close." + mCamInfo.toString());
    }

    public interface CameraCsiCallback {
        void onCameraOpen();

        void onCameraClose();

        void onRawData(ByteBuffer buffer);

        void onRawDataSub(ByteBuffer buffer);
    }
}
