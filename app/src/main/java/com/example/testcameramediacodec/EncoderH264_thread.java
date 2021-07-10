package com.example.testcameramediacodec;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;

public class EncoderH264_thread {
    private static final String TAG = "EncodeH264";
    private Context context;

    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC; // H.264 AVC encoding
    private static final int FRAME_RATE = 15; // 30fps
    private static final int I_FRAME_INTERVAL = 2;
    private static final int BIT_RATE_VIDEO = 1500000;

    private MediaCodec mediaCodec;
    private Surface encodeSurface;
    private String encodeType = "video/avc";
    private MediaCodec.BufferInfo mBufferInfo;
    private EncodeCallback encodeCallback;
    private boolean isRequestKeyFrame = false;
    private boolean encodeRunning = true;
    long timePresentation = 0;
    ByteBuffer[] inputBuffers;
    ByteBuffer[] outputBuffers;

    public MediaFormat getEncodeMediaFormat() {
        if (mediaCodec == null) {
            return null;
        }
        return mediaCodec.getOutputFormat();
    }

    public void setEncodeCallback(EncodeCallback encodeCallback) {
        this.encodeCallback = encodeCallback;
    }

    protected Surface getEncodeSurface() {
        return encodeSurface;
    }


    public EncoderH264_thread(Context context) {
        this.context = context;
    }

    public synchronized void configure() {
        try {
            mediaCodec = MediaCodec.createEncoderByType(encodeType);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        if (mediaCodec == null) {
            return;
        }
        configureMediaCodecEncoder();

        mediaCodec.start();
        encodeThread.start();


        timePresentation = System.currentTimeMillis();
    }

    private void configureMediaCodecEncoder() {
        mBufferInfo = new MediaCodec.BufferInfo();
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, 1280, 720);
        int colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE_VIDEO);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
//        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024*1024 * 8*10);
        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        encodeSurface = mediaCodec.createInputSurface();
        MainActivity.setSurfaceFormat(encodeSurface);
    }

    private Thread encodeThread = new Thread(new Runnable() {
        @Override
        public void run() {

//            mediaCodec.start();
            while (encodeRunning) {
                int outputBufferId = mediaCodec.dequeueOutputBuffer(mBufferInfo, 10000);

                if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {

                }
                else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat mediaFormat = mediaCodec.getOutputFormat(); // option B
                    Log.d(TAG, "run: Encode format change..!!");
                    encodeCallback.onEncodeFormatChange(mediaFormat);
                }
                else if (outputBufferId == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    Log.d(TAG, "Thread Encode run: info output buffer changed.");
                }
                else if (outputBufferId < 0) {
                    Log.d(TAG, "Thread Encode run: dequeueOutputBuffer does not know value.");
                }
                else if (outputBufferId >= 0) {
                    ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferId);
                    MediaFormat bufferFormat = mediaCodec.getOutputFormat(outputBufferId);
                    encodeCallback.onDataVideoEncodeOutput(outputBuffer, mBufferInfo);
                    if ((mBufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) && (isRequestKeyFrame)) {
                        isRequestKeyFrame = false;
                        releaseKeyFrame();
                    }

                    mediaCodec.releaseOutputBuffer(outputBufferId, false);
                }

            }
            mediaCodec.stop();
            mediaCodec.release();
        }
    });

    private static byte[] getByteArrayFromByteBuffer(ByteBuffer byteBuffer) {
        byte[] bytesArray = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytesArray, 0, bytesArray.length);
        return bytesArray;
    }

    public void stop() {
        encodeRunning = false;
        try {
            encodeThread.join();
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        releaseEncoder();
    }

    public void requestKeyFrame() {
        Bundle b = new Bundle();
        b.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
        mediaCodec.setParameters(b);
        isRequestKeyFrame = true;
    }

    private void releaseKeyFrame() {
        Bundle b = new Bundle();
        b.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, I_FRAME_INTERVAL);
        mediaCodec.setParameters(b);
    }

    private void releaseEncoder() {
        if (mediaCodec != null) {
            mediaCodec.flush();
            mediaCodec.stop();
            mediaCodec.release();
            if (encodeSurface != null) {
                encodeSurface.release();
            }
            mediaCodec = null;
        }
    }

    public interface EncodeCallback {
        void onEncodeFormatChange(MediaFormat videoFormat);

        void onDataVideoEncodeOutput(ByteBuffer buffer, MediaCodec.BufferInfo info);
    }


}
