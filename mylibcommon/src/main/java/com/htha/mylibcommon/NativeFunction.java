package com.htha.mylibcommon;

public class NativeFunction {
    static {
        System.loadLibrary("myNative-jni");
    }

    private static native int nResetUsb(String name);
    public static int resetUsb(String name){
        return nResetUsb(name);
    }

    public native static int setGpio(int pin, boolean onOff);

}
