package com.htha.cameraFeature;

import com.htha.httpServer.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;

public class ShareImage {

    private static ShareImage shareImage_instance = null;
    private ByteArrayOutputStream[] outputStreams;
    private ShareImage(){
        int cam = HttpServer.NUM_CAM;
        outputStreams = new ByteArrayOutputStream[cam];
    }

    public static synchronized ShareImage getInstance(){
        if (shareImage_instance==null){
            shareImage_instance = new ShareImage();
        }
        return shareImage_instance;
    }

    public void setImageByte(byte[] rawImageNV21, int camId, int width, int height) {
        byte[] rawImage = new byte[rawImageNV21.length];// Arrays.copyOf(rawImageNV21, rawImageNV21.length);
        byte[] finalRawImage = ImageUtil.YUV420SPtoNV21(rawImageNV21, rawImage, width, height);
        ByteArrayOutputStream imageOS = ImageHttp.NV21toJPEG(finalRawImage, width, height, 70);
        outputStreams[camId] = imageOS;
    }

    public byte[] getImageByte(int camId) {
        if (outputStreams[camId] !=null) {
            return outputStreams[camId].toByteArray();
        }
        return null;
    }
}
