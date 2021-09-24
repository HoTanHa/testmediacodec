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
    private static final int FPS_SET = 12;
    private volatile boolean isRunning = false;
    private QCarCamera qCarCamera;
    private ByteBuffer byteBufferData;
    private byte[] byteArray = new byte[WIDTH * HEIGHT * 3 / 2];
    private CameraCsiCallback mCallback = null;

    private CamInfo mCamInfo;
    private int inputChannel = 0;
    private int csiNum = 0;
    private int inputType = 0;
    private volatile long frameID = 0;
    private Date date = new Date();

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

        if (csiNum == SC600Params.CSI_NUM.CAMERA_CSI0) {
            inputType = SC600Params.InputType.CSI0_CH0CH1CH2CH3_720P;
        }
        else {
            inputType = SC600Params.InputType.CSI2_CH0CH1CH2CH3_720P;
        }

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
        byteBufferData = ByteBuffer.wrap(byteArray);
        new Thread(new Runnable() {
            @Override
            public void run() {

                    isRunning = true;
                    mCallback.onCameraOpen();
                    boolean bCheckFrame;
                    while (isRunning) {
                        try {
                            Thread.sleep(20);
                        }
                        catch (InterruptedException ignored) {
                        }

                        bCheckFrame = getData();
                        if (bCheckFrame && (frameID % 1000 == 1)) {
                            if (LOG_DEBUG) {
                                date.setTime(System.currentTimeMillis());
                                Log.d(TAG, "CameraCsi:.." + frameID + ".." + mCamInfo.toString() + "..." + date);
                            }
                        }
                    }
                    qCarCamera.stopVideoStream(inputChannel);
                }}
        ).start();
    }

    private boolean getData() {
        QCarCamera.FrameInfo frameInfo = qCarCamera.getVideoFrameInfo(inputChannel, byteBufferData);
        if (frameInfo != null && frameID < frameInfo.frameID) {
            frameID = frameInfo.frameID;
            mCallback.onRawData(byteBufferData);
            return true;
        }
        return false;
    }

    public void close() {
        isRunning = false;
    }

    public interface CameraCsiCallback {
        void onCameraOpen();

        void onCameraClose();

        void onRawData(ByteBuffer buffer);
    }
}
