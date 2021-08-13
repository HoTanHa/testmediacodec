package com.example.testcameramediacodec;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.testcameramediacodec.rtp.H264Packetizer;
import com.example.rtmpClient.RTMPMuxer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class VideoMuxer {
    private final String TAG = "VideoEncode";
    private String mFolderPath = null;
    private boolean mSdcardStatus = false;
    private int camId;

    private byte[] bufferConfig;
    private boolean isGetBufferConfig = false;
    private TimeCounter videoTimeCounter = null;
    private TimeCounter audioTimeCounter = null;

    private RTMPMuxer rtmpMuxer = null;
    private String urlStreamRTMP = null;

    private MediaMuxer mediaMuxer = null;
    private Integer videoTrack = null;
    private Integer audioTrack = null;
    private MediaFormat videoFormat = null;
    private MediaFormat audioFormat = null;
    private volatile boolean mMuxerStarted = false;
    private Lock muxerLock;
    private long timeStartSave;
    private boolean isStartCreateFile = false;

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

    public boolean isSaving() {
        return mMuxerStarted;
    }

    private IVideoMuxerCallback videoMuxerCallback = null;

    /* Constructor */
    public VideoMuxer(int camId) {
        this.camId = camId;
        muxerLock = new ReentrantLock();

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
            }
            catch (UnknownHostException e) {
                e.printStackTrace();
            }
            if (inetAddress != null) {
                h264Packetizer.setDestination(inetAddress, 9998 + camId * 2, 8998 + camId * 2);
                h264Packetizer.start();
            }
        }
    });

    public void setVideoFormat(MediaFormat format) {
        this.videoFormat = format;

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

    public void setVideoEncodeCallback(IVideoMuxerCallback callback) {
        this.videoMuxerCallback = callback;
    }

    public void startRtmpStream(String Url) {
        if (rtmpMuxer != null) {
            return;
        }

        Log.i(TAG, "startRtmpStream: " + Url);
        urlStreamRTMP = Url;
        rtmpMuxer = new RTMPMuxer(rtmpMuxerCallback);
        videoTimeCounter = new TimeCounter();
        audioTimeCounter = new TimeCounter();
        videoTimeCounter.reset();
        audioTimeCounter.reset();
        rtmpMuxer.open(urlStreamRTMP, 1280, 720);
    }

    public void stopRtmpStream() {
        if (rtmpMuxer != null) {
            rtmpMuxer.close();
            rtmpMuxer = null;
        }
        videoTimeCounter = null;
        audioTimeCounter = null;

    }

    public void setVideoFolderPath(boolean sdcardStatus, String path) {
        if (!this.mSdcardStatus && sdcardStatus) {
            this.mFolderPath = path;
        }
        else if (!sdcardStatus) {
            this.mFolderPath = null;
            //// TODO : stop the mediaMuxer if save video
        }
        this.mSdcardStatus = sdcardStatus;
    }

    private RTMPMuxer.RTMPMuxerCallback rtmpMuxerCallback = new RTMPMuxer.RTMPMuxerCallback() {
        @Override
        public void onStreamSuccess() {
            videoMuxerCallback.onStreamSuccess(camId, urlStreamRTMP);
        }

        @Override
        public void onStopStream() {
            videoMuxerCallback.onStreamError(camId);
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
            if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                stopSaveMp4();
            }
            muxerLock.lock();
            if (mMuxerStarted && mediaMuxer != null && videoTrack != null) {
                mediaMuxer.writeSampleData(videoTrack, buffer, bufferInfo);
            }
            muxerLock.unlock();

             if (rtmpMuxer != null || h264Packetizer != null) {
                byte[] frameData = new byte[buffer.remaining()];
                buffer.get(frameData, 0, frameData.length);

                if (h264Packetizer != null) {
                    buffLock.lock();
                    this.sizeFrame = frameData.length;
                    indexStream = 0;
                    System.arraycopy(frameData, 0, bufferFrameTCP, 0, this.sizeFrame);
                    buffLock.unlock();
                }
                if ((rtmpMuxer != null) && (rtmpMuxer.isConnected())) {
                    if ((isGetBufferConfig) && bufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                        sendBufferConfigLiveStream();
                    }
                    videoTimeCounter.calcTotalTime(bufferInfo.presentationTimeUs);
                    rtmpMuxer.writeVideo(frameData, 0, frameData.length, videoTimeCounter.getTimeIndex());
                }
            }
        }
    }

    public void startSaveVideoFile() {
        if (isStartCreateFile) {
            return;
        }
        isStartCreateFile = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                saveVideoFile_t();
                isStartCreateFile = false;
            }
        }).start();
    }

    private synchronized void saveVideoFile_t() {
        String fileName = getOutputMediaFileName();
        if (fileName == null) {
            return;
        }
        try {
            mediaMuxer = new MediaMuxer(fileName, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        }
        catch (IOException e) {
            e.printStackTrace();
            return;
        }
        Log.d(TAG, "startSaveMp4: file name: " + fileName);
        if (videoFormat == null) {
            mediaMuxer = null;
            return;
        }
        muxerLock.lock();
        videoTrack = mediaMuxer.addTrack(videoFormat);
//        if (hasAudio) {
//            audioTrack = mMediaMuxer.addTrack(audioFormat);
//        }

        try {
            mediaMuxer.stop();
        }
        catch (IllegalStateException ignored) {

        }
        videoMuxerCallback.onVideoStartSave(fileName, timeStartSave);
        mediaMuxer.start();
        mMuxerStarted = true;
        muxerLock.unlock();
    }

    public synchronized void stopSaveMp4() {
        muxerLock.lock();
        if (mMuxerStarted) {
            mMuxerStarted = false;

            if (mediaMuxer != null) {
                try {
                    mediaMuxer.stop();
                    mediaMuxer.release();
                }
                catch (Exception ignored) {

                }
                mediaMuxer = null;
            }

        }
        videoTrack = null;
        audioTrack = null;
        videoMuxerCallback.onVideoStop();
        muxerLock.unlock();
    }

    public int close() {
        stopStreamRtp();
        stopSaveMp4();
        stopRtmpStream();
        return 0;
    }

    private String getOutputMediaFileName() {
        int camIdFolder = camId + 1;
        String path1 = mFolderPath + File.separator + "cam" + camIdFolder;
        File file1 = new File(path1);
        if (!file1.exists()) {
            if (!file1.mkdir()) {
                return null;
            }
        }
        Date mDate = new Date();
        String sTimeFolder = new SimpleDateFormat("yyyyMMdd_HH", Locale.ROOT).format(mDate);
        long time_unix = mDate.getTime() / 1000;
        long timeFolder_unix = time_unix - time_unix % 3600;
        String path2 = path1 + File.separator + "cam" + camIdFolder + "_" + sTimeFolder + "_" + timeFolder_unix;
        File file2 = new File(path2);
        if (!file2.exists()) {
            if (!file2.mkdir()) {
                return null;
            }
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(mDate);
        String name = path2 + File.separator + "VID_" + camIdFolder + "_" + timeStamp + ".mp4";
        timeStartSave = mDate.getTime() / 1000;
        return name;
    }

    private static class TimeCounter {
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

    public interface IVideoMuxerCallback {
        void onVideoStartSave(String path, long time);

        void onVideoStop();

        void onStreamSuccess(int camId, String urlStream);

        void onStreamError(int camId);
    }
}
