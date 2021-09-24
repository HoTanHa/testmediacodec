package com.htha.camera_sc600;

import android.util.Log;

import com.quectel.qcarapi.cb.IQCarCamInStatusCB;
import com.quectel.qcarapi.stream.QCarCamera;
import com.quectel.qcarapi.util.QCarLog;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CameraSC600 {
    //    implements IQCarCamInStatusCB {
    private static final String TAG = "CameraSC600";
    private static CameraSC600 cameraSC600_instance = null;
    private static boolean isRunning = false;
    private static Thread camSC600Thread;
    private static final int USE_BUS_CSI = 1;
    private static int CSI_NUMS[];
    private static Map<Integer, QCarCamera> qCarCameraMap = new ConcurrentHashMap<Integer, QCarCamera>();

    private static ArrayList<CamInfo> listCamInfo = new ArrayList<>();

    static {
        System.loadLibrary("mmqcar_qcar_jni");
    }

    private CameraSC600() {
        if (isRunning == true) {
            return;
        }

        isRunning = true;
        QCarLog.setTagLogLevel(Log.INFO);

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

        for (int value : CSI_NUMS) {

            QCarCamera qCarCamera = qCarCameraMap.get(value);

            int count = 0;
            while (count < 10) {
                Log.d(TAG, "CameraSC600:  csiNum = " + 0 + " inputNum = " + 4 + " inputType = " + 4);
                int ret = 0;
                if (value == SC600Params.CSI_NUM.CAMERA_CSI0) {
                    if (USE_BUS_CSI == 1) {
                        ret = qCarCamera.cameraOpen(4, SC600Params.InputType.CSI0_CH0CH1CH2CH3_720P);
                    }
                    else {
                        ret = qCarCamera.cameraOpen(2, SC600Params.InputType.CSI0_CH0CH1CH2CH3_720P);
                    }
                }
                else if (value == SC600Params.CSI_NUM.CAMERA_CSI2) {
                    ret = qCarCamera.cameraOpen(2, SC600Params.InputType.CSI0_CH0CH1CH2CH3_720P);
//                    ret = qCarCamera.cameraOpen(2, SC600Params.InputType.CSI2_CH0CH1CH2CH3_720P);
                }
                if (ret == 0) {
                    Log.d(TAG, "CameraSC600:  Open csi " + 0 + " Success");
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
                    Log.d(TAG, "CameraSC600:  Open Failed, cameraOpen csi " + 0 + " return = " + ret);
                }
                qCarCamera.registerOnErrorCB(errorHandler);
            }
        }


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
    }

//    @Override
//    public void statusCB(int i, int i1, int i2, boolean b) {
//
//    }
}
