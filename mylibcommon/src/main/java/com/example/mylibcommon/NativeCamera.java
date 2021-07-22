package com.example.mylibcommon;

import android.util.Log;
import android.view.Surface;

public class NativeCamera {
    static {
        System.loadLibrary("nativeCamera-jni");
    }

    private static final String TAG = "NativeCamera";
    private long nPointer = 0;
    private int camId_t = 0;

    public static native void nCheckCameraList();


    private native long nativeAllocAndInit();

    public NativeCamera(int camId) {
        nPointer = nativeAllocAndInit();
        if (nPointer == 0) {
            Log.d(TAG, "NativeCamera: Cannot alloc for native camera..!!");
            return;
        }
        this.camId_t = camId;
        nSetCamId(nPointer, camId_t);
    }

    private native void nClose(long pointer);

    public synchronized void close() {
        if (nPointer > 0) {
            nClose(nPointer);
        }
        nPointer = 0;
    }

    private native void nSetCamId(long pointer, int camId);

    private static native void nSetInfoLocation(double lat, double lon, double speed);

    public static void setInfoLocation(double lat, double lon, double speed) {
        nSetInfoLocation(lat, lon, speed);
    }

    private native void nDrawBufferInfoToImage(long pointer, byte[] imageBuffer);

    public void drawBufferInfoToImage(byte[] imageBuffer) {
        if (nPointer > 0) {
            nDrawBufferInfoToImage(nPointer, imageBuffer);
        }
    }

    private static native void nSetDriverInfo(String bs, String gplx);

    public static void setDriverInfo(String bs, String gplx) {
        nSetDriverInfo(bs, gplx);

    }

    private native void nSetMainSurface(long pointer, Surface surface);

    public void setMainSurface(Surface surface) {
        if (nPointer > 0) {
            nSetMainSurface(nPointer, surface);
        }
    }

    private native void nSetStreamSurface(long pointer, Surface surface);

    public void setStreamSurface(Surface surface) {
        if (nPointer > 0) {
            nSetStreamSurface(nPointer, surface);
        }
    }

    private native void nCloseStream(long pointer);

    public void closeStream() {
        if (nPointer > 0) {
            nCloseStream(nPointer);
        }

    }
}
