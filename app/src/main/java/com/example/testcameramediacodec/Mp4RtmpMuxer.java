package com.example.testcameramediacodec;


import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.rtmp_client.RTMPMuxer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Mp4RtmpMuxer {
    private final String TAG = "Mp4RtmpMuxer";
    private String camId;
    private MediaFormat videoFormat;
    private MediaFormat audioFormat;
    private String rtmpUrl;
    private int videoWidth;
    private int videoHeigh;

    private Lock muxerLock;
    private RTMPMuxer rtmpMuxer;
    private boolean isSendVideoConfig = false;
    private TimeIndexCounter videoTimeIndexCounter = null;
    private TimeIndexCounter audioTimeIndexCounter = null;

    private MediaMuxer mMediaMuxer;
    private String folderPath;
    private Integer videoTrack = null;
    private Integer audioTrack = null;

    private boolean hasAudio = false;
    private boolean mMuxerStarted = false;
    private MuxerMP4Callback muxerCallback;

    private byte[] byteBufferConfig;
    private boolean saveBufferConfig = false;

    public Mp4RtmpMuxer(String camId) {
        this.camId = camId;
        muxerLock = new ReentrantLock();
    }

    public void setMuxerMP4Callback(MuxerMP4Callback callback) {
        this.muxerCallback = callback;
    }

    public void setVideoFormat(MediaFormat format) {
        this.videoFormat = format;
        videoHeigh = format.getInteger("height");
        videoWidth = format.getInteger("width");
    }

    public synchronized void startSaveMp4(String folder) {
        if (videoFormat==null){
            return;
        }
        folderPath = folder;
        if (folderPath == null) {
            throw new RuntimeException("Folder path is null");
        }
        String fileName = getOutputMediaFileName();
        try {
            mMediaMuxer = new MediaMuxer(fileName, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        Log.d(TAG, "startSaveMp4: file name: " + fileName);
        muxerLock.lock();
        videoTrack = mMediaMuxer.addTrack(videoFormat);
        if (hasAudio) {
            audioTrack = mMediaMuxer.addTrack(audioFormat);
        }
        mMediaMuxer.start();
        mMuxerStarted = true;
        muxerLock.unlock();
        muxerCallback.createMuxerFileComplete();
    }

    public synchronized void stopSaveMp4() {
        muxerLock.lock();
        mMuxerStarted = false;
        if (mMediaMuxer != null) {
            mMediaMuxer.stop();
            mMediaMuxer.release();
            mMediaMuxer = null;
        }
        videoTrack = null;
        audioTrack = null;
        muxerLock.unlock();
    }

    public void startRtmpStream(@NonNull String Url) {
        if (rtmpMuxer != null) {
            return;
        }
        rtmpUrl = Url;
        rtmpMuxer = new RTMPMuxer();
        videoTimeIndexCounter = new TimeIndexCounter();
        audioTimeIndexCounter = new TimeIndexCounter();
        videoTimeIndexCounter.reset();
        audioTimeIndexCounter.reset();
        rtmpMuxer.open(rtmpUrl, videoWidth, videoHeigh);
        isSendVideoConfig = false;
    }

    private void sendRtmpVideoConfig() {
        if ((rtmpMuxer != null) && (rtmpMuxer.isConnected())) {
            rtmpMuxer.writeVideo(byteBufferConfig, 0, byteBufferConfig.length, videoTimeIndexCounter.getTimeIndex());
        }
    }

    public void stopRtmpStream() {
        if (rtmpMuxer != null) {
            rtmpMuxer.close();
            rtmpMuxer = null;
        }
        videoTimeIndexCounter = null;
        audioTimeIndexCounter = null;

    }

    private String getOutputMediaFileName() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String name = folderPath + File.separator + "VID_" + camId + "_" + timeStamp + ".mp4";
        return name;
    }

    public void writeVideo(ByteBuffer buffer, int offset, int length, MediaCodec.BufferInfo bufferInfo) {
        if (buffer == null) {
            throw new RuntimeException("encoderOutputBuffer was null");
        }

        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            if (!saveBufferConfig) {
                saveBufferConfig = true;
                buffer.position(bufferInfo.offset);
                buffer.limit(bufferInfo.offset + bufferInfo.size);
                byteBufferConfig = new byte[buffer.remaining()];
                buffer.get(byteBufferConfig, 0, byteBufferConfig.length);
//                for (byte b : byteBufferConfig) {
//                    Log.i(TAG, String.format("0x%02X", b));
//                }
            }
            bufferInfo.size = 0;
        }

        if (bufferInfo.size != 0) {
            buffer.position(bufferInfo.offset);
            buffer.limit(bufferInfo.offset + bufferInfo.size);

            if ((rtmpMuxer != null) && (rtmpMuxer.isConnected())) {
//                if (isSendVideoConfig == false) {
//                    isSendVideoConfig = true;
//                    sendRtmpVideoConfig();
//                }

                if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                    if (saveBufferConfig) {
                        sendRtmpVideoConfig();
                    }
                }

                videoTimeIndexCounter.calcTotalTime(bufferInfo.presentationTimeUs);
                byte[] array = new byte[buffer.remaining()];
                buffer.get(array, 0, array.length);
                rtmpMuxer.writeVideo(array, 0, array.length, videoTimeIndexCounter.getTimeIndex());
            }

            if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                mMuxerStarted = false;
                if (mMediaMuxer != null) {
                    mMediaMuxer.stop();
                    mMediaMuxer.release();
                    mMediaMuxer = null;
                }
            }
            muxerLock.lock();
            if (mMuxerStarted && (videoTrack != null)) {
                mMediaMuxer.writeSampleData(videoTrack, buffer, bufferInfo);
            }
            muxerLock.unlock();
        }
    }

    public void writeAudio(ByteBuffer buffer, int offset, int length, MediaCodec.BufferInfo bufferInfo) {
        if (this.hasAudio) {
            mMediaMuxer.writeSampleData(audioTrack, buffer, bufferInfo);
        }
    }

    public int close() {
        stopRtmpStream();
        stopSaveMp4();
        return 0;
    }

    private class TimeIndexCounter {
        private long lastTimeUs;
        private int timeIndex;

        public void calcTotalTime(long currentTimeUs) {
            if (lastTimeUs <= 0) {
                this.lastTimeUs = currentTimeUs;
            }
            int delta = (int) (currentTimeUs - lastTimeUs);
            this.lastTimeUs = currentTimeUs;
            timeIndex += Math.abs(delta / 1000);
        }

        public void reset() {
            lastTimeUs = 0;
            timeIndex = 0;
        }

        public int getTimeIndex() {
            return timeIndex;
        }
    }

    public interface MuxerMP4Callback {
        void createMuxerFileComplete();
    }
}
