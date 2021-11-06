package com.htha.camera_sc600;

import android.content.Context;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.htha.mylibcommon.CommonFunction;
import com.htha.playback.PlaybackStream;
import com.quectel.qcarapi.cb.IQCarCamInStatusCB;
import com.quectel.qcarapi.helper.QCarCamInDetectHelper;
import com.quectel.qcarapi.stream.QCarCamera;
import com.quectel.qcarapi.util.QCarLog;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CameraSC600 implements IQCarCamInStatusCB {
    private static final String TAG = "CameraSC600";
    private static CameraSC600 cameraSC600_instance = null;
    private static volatile boolean isRunning = false;
    private static Thread camSC600Thread;
    private static final int USE_BUS_CSI = 2;
    private static int[] CSI_NUMS;
    private static final Map<Integer, QCarCamera> qCarCameraMap = new ConcurrentHashMap<Integer, QCarCamera>();

    private static final ArrayList<CamInfo> listCamInfo = new ArrayList<>();

    private QCarCamInDetectHelper detectInsert;
    private boolean[] isInsertCam;
    private static final boolean[] typeOfCamera = {true, true, true, true};
    private boolean isChange = false;
    private static String mPathConfig = null;

    public static void setPathConfig(String pathConfig) {
        mPathConfig = pathConfig;
    }

    private int readConfig() {
        int type = 0;
        if (new File(mPathConfig).exists()) {
            String sType = CommonFunction.readFile(mPathConfig);
            type = CommonFunction.parseStringToInt(sType);
        }
        else {
            type = 15;
        }
        for (int i = 0; i < 4; i++) {
            typeOfCamera[i] = (type & (0b0001 << i)) > 0;
        }
        return type;
    }

    private void saveConfig() {
        int inputType = 0;
        for (int i = 0; i < 4; i++) {
            if (typeOfCamera[i]) {
                inputType += (0b0001 << i);
            }
        }
        CommonFunction.writeToNewFile(mPathConfig, Integer.toString(inputType));
    }

    static {
        System.loadLibrary("mmqcar_qcar_jni");
    }

    private CameraSC600() {
        if (isRunning) {
            return;
        }

        isRunning = true;
        isError = false;
        QCarLog.setTagLogLevel(Log.INFO);
//        QCarLog.setModuleLogLevel(QCarLog.LOG_MODULE_APP);

        CSI_NUMS = new int[USE_BUS_CSI];
        if (USE_BUS_CSI == 1) {
            CSI_NUMS[0] = SC600Params.CSI_NUM.CAMERA_CSI0;
        }
        else if (USE_BUS_CSI == 2) {
            CSI_NUMS[0] = SC600Params.CSI_NUM.CAMERA_CSI0;
            CSI_NUMS[1] = SC600Params.CSI_NUM.CAMERA_CSI2;
        }
        for (int value : CSI_NUMS) {
            QCarCamera qCarCamera = new QCarCamera(value);
            qCarCameraMap.put(value, qCarCamera);
        }

        if (USE_BUS_CSI == 1) {
            listCamInfo.add(new CamInfo(SC600Params.CameraId.CAMERA1,
                    SC600Params.CSI_NUM.CAMERA_CSI0, SC600Params.InputChannel.CHANNEL_0));
            listCamInfo.add(new CamInfo(SC600Params.CameraId.CAMERA2,
                    SC600Params.CSI_NUM.CAMERA_CSI0, SC600Params.InputChannel.CHANNEL_1));
            listCamInfo.add(new CamInfo(SC600Params.CameraId.CAMERA3,
                    SC600Params.CSI_NUM.CAMERA_CSI0, SC600Params.InputChannel.CHANNEL_2));
            listCamInfo.add(new CamInfo(SC600Params.CameraId.CAMERA4,
                    SC600Params.CSI_NUM.CAMERA_CSI0, SC600Params.InputChannel.CHANNEL_3));
        }
        else if (USE_BUS_CSI == 2) {
            listCamInfo.add(new CamInfo(SC600Params.CameraId.CAMERA1,
                    SC600Params.CSI_NUM.CAMERA_CSI0, SC600Params.InputChannel.CHANNEL_0));
            listCamInfo.add(new CamInfo(SC600Params.CameraId.CAMERA2,
                    SC600Params.CSI_NUM.CAMERA_CSI0, SC600Params.InputChannel.CHANNEL_1));
            listCamInfo.add(new CamInfo(SC600Params.CameraId.CAMERA3,
                    SC600Params.CSI_NUM.CAMERA_CSI2, SC600Params.InputChannel.CHANNEL_0));
            listCamInfo.add(new CamInfo(SC600Params.CameraId.CAMERA4,
                    SC600Params.CSI_NUM.CAMERA_CSI2, SC600Params.InputChannel.CHANNEL_1));
        }

        int typeConfig = readConfig();

        for (int value : CSI_NUMS) {

            QCarCamera qCarCamera = qCarCameraMap.get(value);

            int count = 0;
            int numCam = 0;
            int type = 0;
            while (count < 10) {
                int ret = 0;
                if (value == SC600Params.CSI_NUM.CAMERA_CSI0) {
                    if (USE_BUS_CSI == 1) {
                        numCam = 4;
                        type = SC600Params.InputType.CSI0_CH0CH1CH2CH3_720P;
                    }
                    else {
                        numCam = 2;
                        type = SC600Params.InputType.CSI0_CH0CH1CH2CH3_0000 + typeConfig;
                    }
                }
                else {//if (value == SC600Params.CSI_NUM.CAMERA_CSI2) {
                    numCam = 2;
                    type = SC600Params.InputType.CSI2_CH0CH1_00 + (typeConfig >> 2);
                }
                Log.d(TAG, "CameraSC600:  csiNum = " + value + " inputNum = " +
                        numCam + " inputType = " + type);

                ret = qCarCamera.cameraOpen(numCam, type);

                if (ret == 0) {
                    isError = false;
                    Log.d(TAG, "CameraSC600:  Open csi " + value + " Success");
                    break;
                }
                else {
                    isError = true;
                    count++;
                    try {
                        Thread.sleep(500);
                    }
                    catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Log.d(TAG, "CameraSC600:  Open Failed, cameraOpen csi " + value + " return = " + ret);
                }
                qCarCamera.registerOnErrorCB(errorHandler);
            }
        }

        isInsertCam = new boolean[4];
        Arrays.fill(isInsertCam, false);
//        if (USE_BUS_CSI == 1) {
//            addCameraDetect(SC600Params.CSI_NUM.CAMERA_CSI0, 4);
//        }
//        else {
//            addCameraDetect(SC600Params.CSI_NUM.CAMERA_CSI0, 4);
////            addCameraDetect(SC600Params.CSI_NUM.CAMERA_CSI2, 4);
//        }
//        detectInsert.startDetectThread();

    }

    private void addCameraDetect(int csiNum, int inputNum) {
        QCarCamInDetectHelper.InputParam inputParam = new QCarCamInDetectHelper.InputParam();
        inputParam.detectTime = 1000;
        inputParam.inputNum = inputNum;
        inputParam.qCarCamera = qCarCameraMap.get(csiNum);
        detectInsert = QCarCamInDetectHelper.getInstance(this);
        detectInsert.setInputParam(inputParam);
    }

    private final ErrorHandler errorHandler = new ErrorHandler();

    public CamInfo getCamInfo(int camId) {
        for (CamInfo item : listCamInfo) {
            if (item.getCamId() == camId) {
                return item;
            }
        }
        return null;
    }

    public QCarCamera getQCarCamera(int csiphy_num) {
        if (USE_BUS_CSI == 2) {
            if (csiphy_num != SC600Params.CSI_NUM.CAMERA_CSI0
                    && csiphy_num != SC600Params.CSI_NUM.CAMERA_CSI2) {
                return null;
            }
            return qCarCameraMap.get(csiphy_num);
        }
        else {
            return qCarCameraMap.get(SC600Params.CSI_NUM.CAMERA_CSI0);
        }
    }

    public void getCameraDetect() {
        QCarCamera qCarCamera = getQCarCamera(SC600Params.CSI_NUM.CAMERA_CSI0);
        boolean[] arrInput = new boolean[8];
        assert qCarCamera != null;
        int ret = qCarCamera.detectCamInputStatus(arrInput, 8);
        if (ret >= 0) {
            System.arraycopy(arrInput, 0, isInsertCam, 0, 4);
            for (int i = 0; i < 4; i++) {
                if (isInsertCam[i]) {
                    if (arrInput[4 + i] != typeOfCamera[i]) {
                        isChange = true;
                        Log.d(TAG, "getCameraDetect: camera is Changed.." + i);
                    }
                    typeOfCamera[i] = arrInput[4 + i];
                }
            }
        }
        else {
            isError = true;
        }
    }

    public boolean isChangeCamera() {
        return isChange;
    }

    public static boolean isCamSC600Running() {
        return isRunning;
    }

    public static synchronized CameraSC600 getInstance() {
        if (cameraSC600_instance == null) {
            cameraSC600_instance = new CameraSC600();
        }
        return cameraSC600_instance;
    }

    public void stop() {

//        detectInsert = QCarCamInDetectHelper.getInstance(this);
//        detectInsert.clearInputParam();
//        detectInsert.stopDetectThread();
        QCarCamera qCarCamera = getQCarCamera(SC600Params.CSI_NUM.CAMERA_CSI0);
        qCarCamera.cameraClose();
//        qCarCamera.cameraForceCloseHw();
        qCarCamera.release();
        if (USE_BUS_CSI == 2) {
            QCarCamera qCarCamera1 = getQCarCamera(SC600Params.CSI_NUM.CAMERA_CSI2);
            assert qCarCamera1 != null;
            qCarCamera1.cameraClose();
//            qCarCamera1.cameraForceCloseHw();
            qCarCamera1.release();
        }
        qCarCameraMap.clear();
        saveConfig();
    }

    public static void clearObject() {
        isRunning = false;
        cameraSC600_instance = null;
    }


    public boolean isCamInsert(int camId) {
        if (camId >= 0 && camId < 4) {
            return isInsertCam[camId];
        }
        return false;
    }

    public static boolean isCameraError() {
        return isError;
    }

    private static volatile boolean isError = false;

    @Override
    public void statusCB(int csi_num, int channel_num, int detectResult, boolean isInsert) {
        if (detectResult > -1) {
            if (channel_num < isInsertCam.length) {
                if (isInsertCam[channel_num] != isInsert) {
                    isInsertCam[channel_num] = isInsert;
                }
            }
        }
        else {
            isError = true;
        }
    }
}
