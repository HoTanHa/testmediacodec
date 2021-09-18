package com.example.camera_sc600;

import com.quectel.qcarapi.stream.QCarCamera;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CameraSC600 {
    private static final String TAG = "CameraSC600";
    private static CameraSC600 cameraSC600_instance = null;
    private static boolean isRunning = true;
    private static Thread camSC600Thread;
    private static int CSI_NUMS[] = new int[2];
    private static Map<Integer, QCarCamera> qCarCameraMap = new ConcurrentHashMap<Integer, QCarCamera>();

    private static ArrayList<CamInfo> listCamInfo = new ArrayList<>();

    private CameraSC600() {
        isRunning = true;

        camSC600Thread = new Thread(new Runnable() {
            @Override
            public void run() {

            CSI_NUMS[0] = SC600Params.CSI_NUM.CAMERA_CSI0;
            CSI_NUMS[1] = SC600Params.CSI_NUM.CAMERA_CSI2;
            for (int value : CSI_NUMS) {
                QCarCamera qCarCamera = new QCarCamera(value);
                qCarCameraMap.put(value, qCarCamera);
            }
            listCamInfo.add(new CamInfo(SC600Params.CameraId.CAMERA1,
                    SC600Params.CSI_NUM.CAMERA_CSI0, SC600Params.InputChannel.CHANNEL_0));
            listCamInfo.add(new CamInfo(SC600Params.CameraId.CAMERA2,
                    SC600Params.CSI_NUM.CAMERA_CSI0, SC600Params.InputChannel.CHANNEL_1));
            listCamInfo.add(new CamInfo(SC600Params.CameraId.CAMERA3,
                    SC600Params.CSI_NUM.CAMERA_CSI2, SC600Params.InputChannel.CHANNEL_0));
            listCamInfo.add(new CamInfo(SC600Params.CameraId.CAMERA4,
                    SC600Params.CSI_NUM.CAMERA_CSI2, SC600Params.InputChannel.CHANNEL_1));
            while (isRunning) {

            }
        }});
    }

    public CamInfo getCamInfo(int camId) {
        for (CamInfo item : listCamInfo) {
            if (item.getCamId() == camId) {
                return item;
            }
        }
        return null;
    }

    public QCarCamera getQCarCamera(int csiphy_num) {
        if (csiphy_num != SC600Params.CSI_NUM.CAMERA_CSI0
                && csiphy_num != SC600Params.CSI_NUM.CAMERA_CSI2) {
            return null;
        }
        QCarCamera qCarCamera = qCarCameraMap.get(csiphy_num);
        return qCarCamera;
    }


    public static CameraSC600 getInstance() {
        if (cameraSC600_instance == null)
            cameraSC600_instance = new CameraSC600();

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
