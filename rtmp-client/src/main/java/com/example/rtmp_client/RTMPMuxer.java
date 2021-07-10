package com.example.rtmp_client;

public class RTMPMuxer {
    static {
        System.loadLibrary("rtmp-jni");
    }

    private long rtmpPointer = 0;
    public RTMPMuxer(){

    }
    private native long  nativeAlloc();

    private native int nOpen(long rtmpPointer, String url, int video_width, int video_height);
    public int open(String url, int video_width, int video_height){
        if (rtmpPointer==0){
            rtmpPointer = nativeAlloc();
        }
        return nOpen(rtmpPointer, url, video_width, video_height);
    }

    /**
     * write h264 nal units
     * @param data
     * @param offset
     * @param length
     * @param timestamp
     * @return 0 if it writes network successfully
     * -1 if it could not write
     */
    private native int nWriteVideo(long rtmpPointer, byte[] data, int offset, int length, long timestamp);
    public int writeVideo(byte[] data, int offset, int length, long timestamp){
        return nWriteVideo(rtmpPointer, data, offset, length, timestamp);
    }

    /**
     * Write raw aac data
     * @param data
     * @param offset
     * @param length
     * @param timestamp
     * @return 0 if it writes network successfully
     * -1 if it could not write
     */
    private native int nWriteAudio(long rtmpPointer, byte[] data, int offset, int length, long timestamp);
    public int writeAudio(byte[] data, int offset, int length, long timestamp){
        return nWriteAudio(rtmpPointer, data, offset, length, timestamp);
    }

     /**
     *
     * @return 1 if it is connected
     * 0 if it is not connected
     */
    private native int nClose(long rtmpPointer);
    public int close(){
        nClose(rtmpPointer);
        rtmpPointer = 0;
        return 0;
    }

    private native boolean nIsConnected(long rtmpPointer);
    public boolean isConnected(){
        return nIsConnected(rtmpPointer);
    }
}
