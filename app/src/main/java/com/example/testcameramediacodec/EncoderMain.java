package com.example.testcameramediacodec;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;

public class EncoderMain {
    private static final String TAG = "EncodeH264";

    private static final String ENCODE_VIDEO_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC; // H.264 AVC encoding
    private static final int FRAME_RATE = 10; // 30fps
    private static final int I_FRAME_INTERVAL = 1;
    private static final int BIT_RATE_VIDEO = 1000000;
    private static final int MAX_BIT_RATE_VIDEO = 1100000;
    private static final int BITRATE_MODE = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ;

    private MediaCodec mediaCodec;
    private Surface encodeSurface;
    private MediaCodec.BufferInfo mBufferInfo;
    private EncodeCallback encodeCallback;
    private boolean encodeRunning = true;

    private boolean isVideoFormatChange;
    private MediaFormat mediaFormat = null;


    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    public EncoderMain() {
        configure();
        isVideoFormatChange = false;
    }

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

    private synchronized void configure() {

        backgroundThread = new HandlerThread("CodecStream");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        try {
            mediaCodec = MediaCodec.createEncoderByType(ENCODE_VIDEO_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (mediaCodec == null) {
            return;
        }
        configureMediaCodecEncoder();
    }

    public void start() {
        mediaCodec.start();
//        encodeThread.setPriority(Thread.MAX_PRIORITY - 1);
//        encodeThread.start();
    }

    private void configureMediaCodecEncoder() {
        mBufferInfo = new MediaCodec.BufferInfo();
        MediaFormat format = MediaFormat.createVideoFormat(ENCODE_VIDEO_TYPE, 1280, 720);
        int colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE_VIDEO);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
//        format.setInteger(MediaFormat.KEY_BITRATE_MODE, BITRATE_MODE);
//        format.setInteger("max-bitrate", MAX_BIT_RATE_VIDEO);
//        format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 120000L);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            format.setInteger(MediaFormat.KEY_PRIORITY, 0);
        }
        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        // create Surface encode
        encodeSurface = mediaCodec.createInputSurface();
        mediaCodec.setCallback(callback, backgroundHandler);
    }

    private MediaCodec.Callback callback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {

        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
                ByteBuffer outputBuffer = codec.getOutputBuffer(index);
                encodeCallback.onDataVideoEncodeOutput(outputBuffer, info);
                mediaCodec.releaseOutputBuffer(index, false);

        }

        @Override
        public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {

        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
            if (!isVideoFormatChange) {
                mediaFormat = format;
                isVideoFormatChange = true;
                encodeCallback.onEncodeFormatChange(format);
            }
        }
    };

    private final Thread encodeThread = new Thread(new Runnable() {
        @Override
        public void run() {
            while (encodeRunning) {
                int outputBufferId = mediaCodec.dequeueOutputBuffer(mBufferInfo, 10000);

                if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!isVideoFormatChange) {
                        mediaFormat = mediaCodec.getOutputFormat();
                        isVideoFormatChange = true;
                        encodeCallback.onEncodeFormatChange(mediaFormat);
                    }
                } else if (outputBufferId < 0) {
                    Log.d(TAG, "Thread Encode run: dequeueOutputBuffer does not know value.");
                } else {
                    ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferId);

                    mBufferInfo.presentationTimeUs = getPTSUs();
                    encodeCallback.onDataVideoEncodeOutput(outputBuffer, mBufferInfo);
                    prevOutputPTSUs = mBufferInfo.presentationTimeUs;
                    mediaCodec.releaseOutputBuffer(outputBufferId, false);
                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;      // out of while
                    }
                }
            }
            mediaCodec.flush();
            mediaCodec.stop();
            mediaCodec.release();
        }
    }, "EncodeH264");

    private static byte[] getByteArrayFromByteBuffer(ByteBuffer byteBuffer) {
        byte[] bytesArray = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytesArray, 0, bytesArray.length);
        return bytesArray;
    }

    public synchronized void stop() {
        if (mediaCodec != null) {
            mediaCodec.signalEndOfInputStream();
        }
        encodeRunning = false;
        try {
            encodeThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        releaseEncoder();

        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        }
        catch (InterruptedException e) {
            Log.e(TAG, "stopBackgroundThread: " + e.getMessage());
        }
    }

    public void requestKeyFrame() {
        if (mediaCodec != null) {
            Bundle b = new Bundle();
            b.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
            mediaCodec.setParameters(b);
        }
    }

    private void releaseKeyFrame() {
        Bundle b = new Bundle();
        b.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, I_FRAME_INTERVAL);
        mediaCodec.setParameters(b);
    }

    private void releaseEncoder() {
        if (mediaCodec != null) {
//            mediaCodec.flush();
//            mediaCodec.stop();
//            mediaCodec.release();
            if (encodeSurface != null) {
                encodeSurface.release();
            }
            mediaCodec = null;
        }
    }

    private long prevOutputPTSUs = 0;

    private long getPTSUs() {
        long result = System.nanoTime() / 1000L;
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        if (result < prevOutputPTSUs)
            result = (prevOutputPTSUs - result) + result;
        return result;
    }

    public interface EncodeCallback {
        void onEncodeFormatChange(MediaFormat videoFormat);

        void onDataVideoEncodeOutput(ByteBuffer buffer, MediaCodec.BufferInfo bufferInfo);
    }

}
