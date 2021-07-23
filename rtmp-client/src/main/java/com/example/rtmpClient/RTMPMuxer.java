package com.example.rtmpClient;

import android.util.Log;

public class RTMPMuxer {
    static {
        System.loadLibrary("rtmp-jni");
    }
    private final String TAG = "RTMPMuxer";
    private long rtmpPointer = 0;
    private RTMPMuxerCallback rtmpMuxerCallback;
    private boolean isSetupOk = false;

    public RTMPMuxer(RTMPMuxerCallback callback) {
        this.rtmpMuxerCallback = callback;
    }

    private native long nativeAlloc();

    private native int nOpen(long rtmpPointer, String url, int video_width, int video_height);

    private synchronized void syncOpen(String url, int video_width, int video_height){
        int result = nOpen(rtmpPointer, url, video_width, video_height);
        if (result == 0) {
            Log.i(TAG, "syncOpen: S");
            rtmpMuxerCallback.onStreamSuccess();
            isSetupOk = true;
        } else {
            syncClose();
            isSetupOk = false;
        }
    }

    public int open(String url, int video_width, int video_height) {
        if (rtmpPointer == 0) {
            rtmpPointer = nativeAlloc();
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                syncOpen(url, video_width, video_height);
            }
        }
        ).start();
        return 0;
    }

    /**
     * write h264 nal units
     *
     * @param data
     * @param offset
     * @param length
     * @param timestamp
     * @return 0 if it writes network successfully
     * -1 if it could not write
     */
    private native int nWriteVideo(long rtmpPointer, byte[] data, int offset, int length, long timestamp);

    public int writeVideo(byte[] data, int offset, int length, long timestamp) {
        if (rtmpPointer==0 || (!isSetupOk)){
            return 0;
        }
        return nWriteVideo(rtmpPointer, data, offset, length, timestamp);
    }

    /**
     * Write raw aac data
     *
     * @param data
     * @param offset
     * @param length
     * @param timestamp
     * @return 0 if it writes network successfully
     * -1 if it could not write
     */
    private native int nWriteAudio(long rtmpPointer, byte[] data, int offset, int length, long timestamp);

    public int writeAudio(byte[] data, int offset, int length, long timestamp) {
        if (rtmpPointer==0 || (!isSetupOk)){
            return 0;
        }
        return nWriteAudio(rtmpPointer, data, offset, length, timestamp);
    }

    /**
     * @return 1 if it is connected
     * 0 if it is not connected
     */
    private native int nClose(long rtmpPointer);

    private synchronized void syncClose() {
        isSetupOk = false;
        nClose(rtmpPointer);
        rtmpMuxerCallback.onStopStream();
        rtmpPointer = 0;
    }

    public synchronized int close() {
        if ((rtmpPointer == 0)|| (!isSetupOk)){
            return 0;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                syncClose();
            }
        }).start();
        return 0;
    }

    private native boolean nIsConnected(long rtmpPointer);

    public boolean isConnected() {
        if ((rtmpPointer == 0) || (!isSetupOk)){
            return false;
        }
        return nIsConnected(rtmpPointer);
    }

    public interface RTMPMuxerCallback {
        public void onStreamSuccess();

        public void onStopStream();
    }

}
