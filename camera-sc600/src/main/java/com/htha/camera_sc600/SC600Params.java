package com.htha.camera_sc600;

public class SC600Params {
    public class InputType {
        /*********RN6864**********/
        /*CSI0 config choose*/
        public static final int CSI0_CH0CH1CH2CH3_720P = 0;
        public static final int CSI0_CH0CH1CH2CH3_1080P = 1;
        /*CSI1 config choose*/
        public static final int CSI1_CH0CH1CH2CH3_720P = 2;
        /*CSI2 config choose*/
        public static final int CSI2_CH0CH1CH2CH3_720P = 3;

        /**********N4***************/
        /*CSI1 config choose*/
        public static final int  CSI1_CH0CH1CH2CH3_1080P_N4 = 4;
        public static final int  CSI1_CH0CH1CH2CH3_720P_N4 = 5;

        /**********ADSUN**************/
        public static final int  CSI2_ADSUN = 6;
        public static final int  CSI0_ADSUN_720P720P720P720P = 7;
        public static final int  INPUT_TYPE_MUX = 8;
    }

    public class InputChannel {
        public static final int CHANNEL_0 = 0;
        public static final int CHANNEL_1 = 1;
        public static final int CHANNEL_2 = 2;
        public static final int CHANNEL_3 = 3;
        public static final int CHANNEL_MUX = 4;
    }

    public class CSI_NUM {
        public static final int CAMERA_CSI0 = 0;
        public static final int CAMERA_CSI1 = 1;
        public static final int CAMERA_CSI2 = 2;
        public static final int CAMERA_MUX = 3;

    }

    public class CameraId {
        public static final int CAMERA1 = 0;
        public static final int CAMERA2 = 1;
        public static final int CAMERA3 = 2;
        public static final int CAMERA4 = 3;

    }
}
