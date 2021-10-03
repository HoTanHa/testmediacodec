package com.htha.camera_sc600;

import android.util.Log;

import com.htha.playback.PlaybackStream;
import com.quectel.qcarapi.cb.IQCarCamInStatusCB;
import com.quectel.qcarapi.helper.QCarCamInDetectHelper;
import com.quectel.qcarapi.stream.QCarCamera;
import com.quectel.qcarapi.util.QCarLog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CameraSC600 implements IQCarCamInStatusCB {
    private static final String TAG = "CameraSC600";
    private static CameraSC600 cameraSC600_instance = null;
    private static boolean isRunning = false;
    private static Thread camSC600Thread;
    private static final int USE_BUS_CSI = 1;
    private static int CSI_NUMS[];
    private static Map<Integer, QCarCamera> qCarCameraMap = new ConcurrentHashMap<Integer, QCarCamera>();

    private static ArrayList<CamInfo> listCamInfo = new ArrayList<>();

    private QCarCamInDetectHelper detectInsert;
    private boolean[] isInsertCam;

    static {
        System.loadLibrary("mmqcar_qcar_jni");
    }

    private CameraSC600() {
        if (isRunning == true) {
            return;
        }

        isRunning = true;
        QCarLog.setTagLogLevel(Log.INFO);
        QCarLog.setModuleLogLevel(QCarLog.LOG_MODULE_APP);

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
                    SC600Params.CSI_NUM.CAMERA_CSI2, SC600Params.InputChannel.CHANNEL_2));
            listCamInfo.add(new CamInfo(SC600Params.CameraId.CAMERA4,
                    SC600Params.CSI_NUM.CAMERA_CSI2, SC600Params.InputChannel.CHANNEL_3));
        }

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
                        type = 1;//SC600Params.InputType.CSI0_CH0CH1CH2CH3_720P;
                    }
                }
                else {//if (value == SC600Params.CSI_NUM.CAMERA_CSI2) {
                    numCam = 2;
                    type = 1;//SC600Params.InputType.CSI0_CH0CH1CH2CH3_720P;
                }
                Log.d(TAG, "CameraSC600:  csiNum = " + value + " inputNum = " +
                        numCam + " inputType = " + type);

                ret = qCarCamera.cameraOpen(numCam, type);

                if (ret == 0) {
                    Log.d(TAG, "CameraSC600:  Open csi " + value + " Success");
                    break;
                }
                else {
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
        if (USE_BUS_CSI==1){
            addCameraDetect(SC600Params.CSI_NUM.CAMERA_CSI0, 4);
        }
        else {
            addCameraDetect(SC600Params.CSI_NUM.CAMERA_CSI0, 4);
//            addCameraDetect(SC600Params.CSI_NUM.CAMERA_CSI2, 2);
        }
        detectInsert.startDetectThread();

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


    public static synchronized CameraSC600 getInstance() {
        if (cameraSC600_instance == null) {
            cameraSC600_instance = new CameraSC600();
        }
        return cameraSC600_instance;
    }

    public void stop() {
        isRunning = false;
        cameraSC600_instance = null;
        qCarCameraMap.clear();
        detectInsert.clearInputParam();
        detectInsert.stopDetectThread();
    }

    public boolean isCamInsert(int camId) {
        if (camId >= 0 && camId < 4) {
            return isInsertCam[camId];
        }
        return false;
    }

    private int count = 0;

    @Override
    public void statusCB(int csi_num, int channel_num, int detectResult, boolean isInsert) {
        if (detectResult > -1) {
            if (channel_num < isInsertCam.length) {
                if (isInsertCam[channel_num] != isInsert) {
                    isInsertCam[channel_num] = isInsert;
                    Log.i(TAG, "csi_num = " + csi_num + ", channel_num = " + channel_num + ", isInsert = " + isInsert);
                }
            }
        }
        else {
            count++;
            if (count > 10) {
                count = 0;
                Log.d(TAG, "statusCB: i2c error....");
            }
        }
    }
}
