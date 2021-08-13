package com.example.testcameramediacodec.playback;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoMp4 {
    private final String TAG = "VideoMp4";
    private boolean isKeyFrame;
    private int frameSize;
    private int frameRate;
    private int fileDurationMs;
    private int framePresentationMs; //timeStamp
    private int numOfFrame;
    private int frameIndex;
    private int numTracks;
    private boolean isHasVideo;
    private String pathFile;
    private MediaFormat videoFormat;
    private ByteBuffer inputBuffer;
    private byte[] bufferSPS_PPS;
    private byte[] bufferFrame;
    private boolean isEndOfVideo = false;
    private int lengthBufferSPS_PPS;

    private MediaExtractor extractor;
    public static final int FRAME_SIZE_MAX = 1024*1024;

    public VideoMp4(String filePath) {
        pathFile = filePath;
        inputBuffer = ByteBuffer.allocateDirect(FRAME_SIZE_MAX);
        parseFileMp4();
    }

    public boolean isHasVideoTrack() {
        return isHasVideo;
    }

//        public int getFrameSize() {
//            return frameSize;
//        }

    public int getFrameRate() {
        return frameRate;
    }

    public int getFileDurationMs() {
        return fileDurationMs;
    }

    public int getNumOfFrame() {
        return numOfFrame;
    }

    public int getFrameIndex() {
        return frameIndex;
    }

    public int getFramePresentationMs() {
        return framePresentationMs;
    }

    public boolean isKeyFrame() {
        return isKeyFrame;
    }

    public byte[] getFrameSPS_PPS() {
        return bufferSPS_PPS;
    }
    public int getLengthBufferSPS_PPS(){
        return lengthBufferSPS_PPS;
    }
    public boolean isEndOfVideo(){
        return isEndOfVideo;
    }

    public void close() {
        if (extractor != null) {
            extractor.release();
            extractor = null;
        }
        inputBuffer.clear();
    }

    private void parseFileMp4() {
        extractor = new MediaExtractor();
        try {
            extractor.setDataSource(pathFile);
        } catch (IOException e) {
            e.printStackTrace();
            isHasVideo = false;
            return;
        }

        numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; ++i) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.equals(MediaFormat.MIMETYPE_VIDEO_AVC)) {
                isHasVideo = true;
                extractor.selectTrack(i);
                videoFormat = format;
                ByteBuffer byteBufferSPS = format.getByteBuffer("csd-0");
                if (byteBufferSPS != null && byteBufferSPS.hasArray()) {
                    String debugBuffer = printSomeBytes(byteBufferSPS, byteBufferSPS.array().length);
                    Log.i(TAG, "parseMp4File: SPS..." + debugBuffer);
                }
                ByteBuffer byteBufferPPS = format.getByteBuffer("csd-1");
                if (byteBufferPPS != null && byteBufferPPS.hasArray()) {
                    String debugBuffer = printSomeBytes(byteBufferPPS, byteBufferPPS.array().length);
                    Log.i(TAG, "parseMp4File: PPS..." + debugBuffer);
                }
                if (byteBufferSPS != null && byteBufferPPS != null) {
                    lengthBufferSPS_PPS = byteBufferSPS.array().length + byteBufferPPS.array().length;
                    bufferSPS_PPS = new byte[lengthBufferSPS_PPS];
                    System.arraycopy(byteBufferSPS.array(), 0, bufferSPS_PPS, 0, byteBufferSPS.array().length);
                    System.arraycopy(byteBufferPPS.array(), 0, bufferSPS_PPS, byteBufferSPS.array().length, byteBufferPPS.array().length);
                    byteBufferSPS.clear();
                    byteBufferPPS.clear();
                }
                break;
            }
        }
        if (isHasVideo) {
            this.frameRate = videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
            this.fileDurationMs = (int) (videoFormat.getLong(MediaFormat.KEY_DURATION) / 1000);
            this.numOfFrame = this.fileDurationMs*this.frameRate/1000;
            Log.d(TAG, "parseFileMp4: fps:" + frameRate +"...durationMS: "
                    +fileDurationMs + "...frames: "+ numOfFrame);
        }
    }

    public ByteBuffer getFrameByteBuffer() {
        if (!isHasVideo) {
            return null;
        }
        if (frameSize==0){
            return null;
        }
        return inputBuffer;
    }

    public int getNextFrameSize(){
        if (!isHasVideo){
            return  0;
        }

        frameSize = extractor.readSampleData(inputBuffer, 0);
        if (frameSize > 0) {
            isEndOfVideo = false;
            int flag = extractor.getSampleFlags();
            long presentationTimeUs = extractor.getSampleTime();
//            inputBuffer.get(bufferFrame, 0, frameSize);

            isKeyFrame = (flag == MediaExtractor.SAMPLE_FLAG_SYNC);
            frameIndex++;
            framePresentationMs = (int) (presentationTimeUs / 1000);
            extractor.advance();
        }
        else
        {
            numOfFrame = frameIndex;
            isEndOfVideo = true;
            isKeyFrame = false;
        }


        return frameSize;
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 3];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 3] = HEX_ARRAY[v >>> 4];
            hexChars[j * 3 + 1] = HEX_ARRAY[v & 0x0F];
            hexChars[j * 3 + 2] = ' ';
        }
        return new String(hexChars);
    }

    private String printSomeBytes(ByteBuffer buffer, int length) {
        byte[] b = new byte[length];
        System.arraycopy(buffer.array(), 0, b, 0, length);
        return bytesToHex(b);
    }

    private void parseMp4File(String pathFile) {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(pathFile);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        MediaFormat videoFormat = null;
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; ++i) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.equals(MediaFormat.MIMETYPE_VIDEO_AVC)) {
                extractor.selectTrack(i);
                videoFormat = format;
                ByteBuffer byteBufferSPS = format.getByteBuffer("csd-0");
                if (byteBufferSPS != null && byteBufferSPS.hasArray()) {
                    String debugBuffer = printSomeBytes(byteBufferSPS, byteBufferSPS.array().length);
                    Log.i(TAG, "parseMp4File: SPS..." + debugBuffer);
                }
                ByteBuffer byteBufferPPS = format.getByteBuffer("csd-1");
                if (byteBufferPPS != null && byteBufferPPS.hasArray()) {
                    String debugBuffer = printSomeBytes(byteBufferPPS, byteBufferPPS.array().length);
                    Log.i(TAG, "parseMp4File: PPS..." + debugBuffer);
                }

                lengthBufferSPS_PPS = byteBufferSPS.array().length + byteBufferPPS.array().length;
                bufferSPS_PPS = new byte[lengthBufferSPS_PPS];
                System.arraycopy(byteBufferSPS.array(), 0, bufferSPS_PPS, 0, byteBufferSPS.array().length);
                System.arraycopy(byteBufferPPS.array(), 0, bufferSPS_PPS, byteBufferSPS.array().length, byteBufferPPS.array().length);
                byteBufferSPS.clear();
                byteBufferPPS.clear();
                break;
            }
        }


        int frameRate = videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
        long durationUs = videoFormat.getLong(MediaFormat.KEY_DURATION);

        ByteBuffer inputBuffer = ByteBuffer.allocate(FRAME_SIZE_MAX);
        byte[] frameData = new byte[inputBuffer.remaining()];
        int frameCount = 0;
        int frameSize = 0;
        boolean isKeyFrame = false;
        long presentationMs = 0;
        long lastPresentationMs = 0;
        long presentationTimeUs;
        int flag;
        int trackIndex = extractor.getSampleTrackIndex();

        while ((frameSize = extractor.readSampleData(inputBuffer, 0)) > 0) {
            flag = extractor.getSampleFlags();
            presentationTimeUs = extractor.getSampleTime();
            inputBuffer.get(frameData, 0, frameSize);

            isKeyFrame = (flag == MediaExtractor.SAMPLE_FLAG_SYNC);
            frameCount++;
            presentationMs = presentationTimeUs / 1000;

            if (frameCount < 5) {
                String sNal = printSomeBytes(inputBuffer, 6);
                if (isKeyFrame) {
                    Log.d(TAG, "parseMp4File: I-Key." + sNal);
                } else {
                    Log.d(TAG, "parseMp4File: P-fra." + sNal);
                }
            }

            lastPresentationMs = presentationMs;

            extractor.advance();
        }
        extractor.release();
        inputBuffer.clear();
        Log.i(TAG, "parseMp4File: FINISH File ...frameCount:.." + frameCount + "..." + presentationMs);
    }
}
