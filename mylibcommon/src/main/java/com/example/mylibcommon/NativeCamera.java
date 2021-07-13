package com.example.mylibcommon;

import android.util.Log;
import android.view.Surface;

public class NativeCamera {
    static {
        System.loadLibrary("nativeCamera-jni");
    }
    private static final String TAG = "NativeCamera";
    private long nPointer = 0;
    private int camId = 0;

    private native long nativeAllocAndInit();
    private native void nClose(long pointer);

    public NativeCamera(int camId) {
        nPointer = nativeAllocAndInit();
        if (nPointer == 0) {
            Log.d(TAG, "NativeCamera: Cannot alloc for native camera..!!");
        }
        this.camId = camId;
    }

    public synchronized void close(){
        nClose(nPointer);
        nPointer = 0;
    }
    private native void nSetInfoLocation(long pointer, double lat, double lon, double speed);

    private native void nDrawBufferInfoToImage(long pointer, byte[] imageBuffer);

    private native void nSetDriverInfo(long pointer, String bs, String gplx);

    private native void nSetMainSurface(long pointer, Surface surface);

    private native void nSetStreamSurface(long pointer, Surface surface);

    private native void nCloseStream(long pointer);
}
