package com.htha.cameraFeature;

import android.content.Context;
import android.os.Environment;

public class FileConfig {
    private static final String File_TypeCam = "/config.txt";
    private static final String File_TypeImage = "/imageType.txt";

    public static String getFile_TypeCam(Context context) {
        return (context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS).getPath() + File_TypeCam);
    }

    public static String getFile_TypeImage(Context context) {
        return (context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS).getPath() + File_TypeImage);
    }
}
