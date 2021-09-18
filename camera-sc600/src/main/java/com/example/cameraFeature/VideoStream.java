package com.example.cameraFeature;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.rtmpClient.RTMPMuxer;
import com.example.rtpH264.H264Packetizer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class VideoStream {
    private final String TAG = "VideoStream";
    private String mFolderPath = null;
    private boolean mSdcardStatus = false;
    private int camId;

    private byte[] bufferConfig;
    private boolean isGetBufferConfig = false;
    private TimeCounter videoTimeCounter = null;
    private TimeCounter audioTimeCounter = null;

    private RTMPMuxer rtmpMuxer = null;
    private String urlStreamRTMP = null;
    private volatile boolean isRtmpSetupSuccess = false;

    private byte[] bufferFrameTCP;
    private int sizeFrame;
    private int indexStream;
    private Lock buffLock;
    private byte[] sps_buff;
    private byte[] pps_buff;
    private H264Packetizer h264Packetizer;
    private String hostRTP;
    private boolean isStreamingRTP = false;
    private InputStream inputStream = new InputStream() {
        @Override
        public int read() throws IOException {
            return 0;
        }

        @Override
        public int read(@NonNull byte[] b, int off, int len) throws IOException {
            int min = 0;
            if (len <= 0 || sizeFrame == 0) {
                return 0;
            }
            buffLock.lock();
            if (off == 0 && len == 5) {
                byte[] lena = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(sizeFrame).array();
                System.arraycopy(lena, 0, b, 0, 4);
                b[4] = bufferFrameTCP[4];
                indexStream = 5;
                min = 5;
            } else {
                min = Math.min(len, (sizeFrame - indexStream));
                System.arraycopy(bufferFrameTCP, indexStream, b, off, min);
                indexStream += min;
            }
            if (indexStream >= sizeFrame) {
                sizeFrame = 0;
            }
            buffLock.unlock();
            return min;
        }
    };

    private IVideoStreamCallback videoStreamCallback = null;

    /* Constructor */
    public VideoStream(int camId) {
        this.camId = camId;
        buffLock = new ReentrantLock();
    }

    public void startStreamRtp(String host) {
        if (!isStreamingRTP) {
            isStreamingRTP = true;
            this.hostRTP = host;
            rtpSetupThread.start();
        }
    }

    public void stopStreamRtp() {
        if (h264Packetizer != null) {
            h264Packetizer.stop();
            h264Packetizer = null;
        }
    }

    private Thread rtpSetupThread = new Thread(new Runnable() {
        @Override
        public void run() {
            bufferFrameTCP = new byte[1024 * 1024];
            h264Packetizer = new H264Packetizer();
            h264Packetizer.setInputStream(inputStream);
            h264Packetizer.setStreamParameters(pps_buff, sps_buff);

            InetAddress inetAddress = null;
            try {
                inetAddress = InetAddress.getByName(hostRTP);
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
            if (inetAddress != null) {
                h264Packetizer.setDestination(inetAddress, 9998 + camId * 2, 8998 + camId * 2);
                h264Packetizer.start();
            }
        }
    });

    public void setVideoFormat(MediaFormat format) {
        ByteBuffer byteBufferSPS = format.getByteBuffer("csd-0");
        ByteBuffer byteBufferPPS = format.getByteBuffer("csd-1");
        if (byteBufferSPS != null && byteBufferPPS != null) {
            if (sps_buff == null && pps_buff == null) {
                sps_buff = new byte[byteBufferSPS.array().length];
                pps_buff = new byte[byteBufferPPS.array().length];
                System.arraycopy(byteBufferSPS.array(), 0, sps_buff, 0, byteBufferSPS.array().length);
                System.arraycopy(byteBufferPPS.array(), 0, pps_buff, 0, byteBufferPPS.array().length);
            }
            byteBufferSPS.clear();
            byteBufferPPS.clear();
            if (!isGetBufferConfig) {
                isGetBufferConfig = true;
                bufferConfig = new byte[sps_buff.length + pps_buff.length];
                System.arraycopy(sps_buff, 0, bufferConfig, 0, sps_buff.length);
                System.arraycopy(pps_buff, 0, bufferConfig, sps_buff.length, pps_buff.length);
            }
        }
    }

    public void setVideoEncodeCallback(IVideoStreamCallback callback) {
        this.videoStreamCallback = callback;
    }

    public void startRtmpStream(String Url) {
        if (rtmpMuxer != null) {
            return;
        }
        isRtmpSetupSuccess = false;
        Log.i(TAG, "startRtmpStream: " + Url);
        urlStreamRTMP = Url;
        rtmpMuxer = new RTMPMuxer(rtmpMuxerCallback);
        rtmpMuxer.open(urlStreamRTMP, 640, 360);
    }

    public void stopRtmpStream() {
        isRtmpSetupSuccess = false;
        if (rtmpMuxer != null) {
            rtmpMuxer.close();
            rtmpMuxer = null;
        }
        videoTimeCounter = null;
        audioTimeCounter = null;

    }

    private RTMPMuxer.RTMPMuxerCallback rtmpMuxerCallback = new RTMPMuxer.RTMPMuxerCallback() {
        @Override
        public void onStreamSuccess() {
            videoStreamCallback.onStreamSuccess(camId, urlStreamRTMP);
            videoTimeCounter = new TimeCounter();
            audioTimeCounter = new TimeCounter();
            videoTimeCounter.reset();
            audioTimeCounter.reset();
            isRtmpSetupSuccess = true;
        }

        @Override
        public void onStopStream() {
            videoStreamCallback.onStreamError(camId);
        }
    };

    private void sendBufferConfigLiveStream() {
        rtmpMuxer.writeVideo(bufferConfig, 0, bufferConfig.length, videoTimeCounter.getTimeIndex());
    }

    public void writeVideoData(ByteBuffer buffer, MediaCodec.BufferInfo bufferInfo) {
        if (buffer == null) {
            return;
        }

        if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
            if (!isGetBufferConfig) {
                isGetBufferConfig = true;
                buffer.position(bufferInfo.offset);
                buffer.limit(bufferInfo.offset + bufferInfo.size);
                bufferConfig = new byte[buffer.remaining()];
                buffer.get(bufferConfig, 0, bufferConfig.length);
            }
            bufferInfo.size = 0;
        }

        if (bufferInfo.size != 0) {
            buffer.position(bufferInfo.offset);
            buffer.limit(bufferInfo.offset + bufferInfo.size);

            byte[] frameData = new byte[buffer.remaining()];
            buffer.get(frameData, 0, frameData.length);

            if (h264Packetizer != null) {
                buffLock.lock();
                this.sizeFrame = frameData.length;
                indexStream = 0;
                System.arraycopy(frameData, 0, bufferFrameTCP, 0, this.sizeFrame);
                buffLock.unlock();
            }
            if ((rtmpMuxer != null) && isRtmpSetupSuccess) {
                if ((isGetBufferConfig) && bufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                    sendBufferConfigLiveStream();
                }
                videoTimeCounter.calcTotalTime(bufferInfo.presentationTimeUs);
                rtmpMuxer.writeVideo(frameData, 0, frameData.length, videoTimeCounter.getTimeIndex());
            }
        }
    }

    public int close() {
        stopStreamRtp();
        stopRtmpStream();
        return 0;
    }

    private static class TimeCounter {
        private long lastTimeUs = 0;
        private int timeIndex = 0;

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

    public interface IVideoStreamCallback {

        void onStreamSuccess(int camId, String urlStream);

        void onStreamError(int camId);
    }
}
