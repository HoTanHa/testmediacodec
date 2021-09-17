package com.example.camera_sc600;

public class CamInfo {
    private int mCamId = 0;
    private int mCsiNum = 0;
    private int mInputChannel = 0;

    public CamInfo(int camId, int csiNum, int input){
        mCamId = camId;
        mCsiNum = csiNum;
        mInputChannel = input;
    }

    public int getCamId() {
        return mCamId;
    }

    public int getInputChannel() {
        return mInputChannel;
    }

    public int getCsiNum() {
        return mCsiNum;
    }

    @Override
    public String toString() {
        return "CamInfo{" +
                "camId=" + mCamId +
                ", csiNum=" + mCsiNum +
                ", inputChannel=" + mInputChannel +
                '}';
    }
}
