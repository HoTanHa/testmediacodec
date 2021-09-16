package com.example.camera_sc600;

public class  {
    public enum Input {
        /*********RN6864**********/
        /*CSI0 config choose*/
        CSI0_CH0CH1CH2CH3_720P,
        /*CSI1 config choose*/
        CSI1_CH0CH1CH2CH3_720P,
        /*CSI2 config choose*/
        CSI2_CH0CH1CH2CH3_720P,

        /**********N4***************/
        /*CSI1 config choose*/
        CSI1_CH0CH1CH2CH3_1080P_N4,
        CSI1_CH0CH1CH2CH3_720P_N4,

        /**********TP2854**************/

        INPUT_TYPE_MUX,}
    /*********RN6864**********/
    /*CSI0 config choose*/
    private static final int CSI0_CH0CH1CH2CH3_720P = 0;
    /*CSI1 config choose*/
    private static final int CSI1_CH0CH1CH2CH3_720P = 1;
    /*CSI2 config choose*/
    private static final int CSI2_CH0CH1CH2CH3_720P = 2;
    /**********N4***************/
    /*CSI1 config choose*/
    private static final int CSI1_CH0CH1CH2CH3_1080P_N4 = 3;
    private static final int CSI1_CH0CH1CH2CH3_720P_N4 = 4;
    /**********TP2854**************/
    private static final int INPUT_TYPE_MUX = 5;
}
