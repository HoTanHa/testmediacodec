package com.example.testcameramediacodec;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class CameraEncode {
    private final String TAG = "Camera Encode";
    private String cameraId;
    private EncoderH264_thread encoderH264;
    private Surface encodeInputSurface;
    private CameraObj cameraObj;

    private Mp4RtmpMuxer muxer;

    private String videoPath;
    private String imagePath;
    private MediaFormat format;

    private Context context;
    private String folder;


    public CameraEncode(Context context, String cameraId, String folder) {
        this.context = context;
        this.cameraId = cameraId;
        this.folder = folder;
    }

    public synchronized void configure() {
        muxer = new Mp4RtmpMuxer(this.cameraId);
        muxer.setMuxerMP4Callback(new Mp4RtmpMuxer.MuxerMP4Callback() {
            @Override
            public void createMuxerFileComplete() {
                if (encoderH264 != null) {
                    encoderH264.requestKeyFrame();
                }
            }
        });

        encoderH264 = new EncoderH264_thread(this.context);
        encoderH264.setEncodeCallback(new EncoderH264_thread.EncodeCallback() {
            @Override
            public void onEncodeFormatChange(MediaFormat videoFormat) {
                format = videoFormat;
                if (muxer != null) {
                    muxer.setVideoFormat(videoFormat);
                }
            }

            @Override
            public void onDataVideoEncodeOutput(ByteBuffer buffer, MediaCodec.BufferInfo info) {
                if (muxer != null) {
                    muxer.writeVideo(buffer, info.offset, info.size, info);
                }
            }
        });

        encoderH264.configure();
        encodeInputSurface = encoderH264.getEncodeSurface();

        imagePath = this.folder + "/image";
        File imageFolder = new File(imagePath);
        if (!imageFolder.exists()) {
            imageFolder.mkdir();
        }
        videoPath = this.folder + "/video";
        File videoFolder = new File(videoPath);
        if (!videoFolder.exists()) {
            videoFolder.mkdir();
        }

        cameraObj = new CameraObj(this.context, this.cameraId, encodeInputSurface, imagePath);
        cameraObj.setCameraObjCallback(new CameraObj.CameraObjCallback() {
            @Override
            public void cameraOpened() {

            }

            @Override
            public void cameraError() {
                CameraEncode.this.stopAll();
            }

            @Override
            public void cameraDisconnected() {
                CameraEncode.this.stopAll();
            }

            @Override
            public void onPhotoComplete(String path) {

            }

            @Override
            public void onDataCamera(byte[] data) {
                if (encodeInputSurface != null) {
                    MainActivity.drawDataToSurface(data, encodeInputSurface);
                }
            }
        });
        cameraObj.openCamera();
    }

    public void saveFileMP4() {

        if (muxer != null) {
            if (format != null) {
                muxer.setVideoFormat(format);
            }
            muxer.stopSaveMp4();
            muxer.startSaveMp4(videoPath);
        }
    }

    public void closeFileMP4() {

        if (muxer != null) {
            muxer.stopSaveMp4();
        }
    }

    public void startStreamingVideo(String url) {
        if (url == null) {
            Log.d(TAG, "startStreamingVideo: String url is null");
            return;
        }
        if (muxer != null) {
            muxer.startRtmpStream(url);
        }
    }

    public void stopStreamingVideo() {

        if (muxer != null) {
            muxer.stopRtmpStream();
        }
    }

    public void takePhoto() {
        if (cameraObj != null) {
            cameraObj.takePhoto();
        }
    }

    public void stopAll() {
        if (muxer != null) {
            muxer.close();
            muxer = null;
        }
        if (cameraObj != null) {
            cameraObj.closeCamera();
            cameraObj = null;
        }
        if (encoderH264 != null) {
            encoderH264.stop();
            encoderH264 = null;
        }
    }
}
