package com.example.playback;

import android.content.Context;
import android.util.Log;

import com.example.rtmpClient.RTMPMuxer;

import java.io.File;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

public class PlaybackStream {

    private static final String TAG = "Playback";
    private static final int NUM_CAM = 4;
    private static final boolean DEBUG = true;
    private Context mContext;
    private String pathStorage;
    private int camId_t;
    private long timeUnix_t;
    private String urlRTMP;
    private volatile boolean isConnectSuccess = false;
    private volatile boolean isStoppedStream = false;
    private RTMPMuxer rtmpMuxer;
    private volatile boolean isRunningPlayback = false;


    private long timeFileBefore = 0;
    private boolean isStartPlayback = true;
    private IPlaybackCallback playbackCallback = null;

    private ArrayList<Long> arrayListCam;

    public PlaybackStream(Context context, String pathSdcard, IPlaybackCallback callback) {
        this.mContext = context;
        this.pathStorage = pathSdcard;
        this.playbackCallback = callback;
        Log.i(TAG, "PlaybackStream: " + pathSdcard);
    }

    public void start(String url, int camId, long timeUnix) {
        if (streamThread.isAlive()) {
            return;
        }
        if (camId < 0 || camId >= NUM_CAM) {
            Log.d(TAG, "start: camId is not belong from 0 to NUM_CAM");
            return;
        }
        if (url == null || !(url.startsWith("rtmp"))) {
            Log.d(TAG, "start: urlStream is wrong");
            return;
        }
        this.urlRTMP = url;
        this.camId_t = camId;
        this.timeUnix_t = timeUnix;
        streamThread.start();
    }

    private long getTimeSearchFileInArrayList() {
        if (pathStorage == null) {
            return 0;
        }
        File file = new File(pathStorage);
        if (!file.exists()) {
            return 0;
        }
        int maxIndex = this.arrayListCam.size() - 1;
        int index;
        if (isStartPlayback) {
            index = 0;
            for (int i = 0; i < maxIndex; i++) {
                if (this.arrayListCam.get(i) <= timeUnix_t && timeUnix_t < this.arrayListCam.get(i + 1)) {
                    index = i;
                    break;
                }
            }
        }
        else {
            index = 0;
            for (int i = 0; i < maxIndex; i++) {
                if (this.arrayListCam.get(i) <= timeFileBefore && timeFileBefore < this.arrayListCam.get(i + 1)) {
                    index = i + 1;
                    break;
                }
            }
            if (index == 0) {
                index = maxIndex;
            }
        }
        long time = this.arrayListCam.get(index);
        long timeNow = new Date().getTime() / 1000;
        if (time >= (timeNow - 120)) {
            time = this.arrayListCam.get(index - 1);
        }
        return time;
    }

    private final Thread streamThread = new Thread(new Runnable() {
        @Override
        public void run() {
            arrayListCam = getListVideo(camId_t);
            if (arrayListCam == null || arrayListCam.size() == 0) {
                Log.d(TAG, "run: PathStorage is null or no file in Day has this time");
                playbackError();
                return;
            }
            isRunningPlayback = true;
            if (rtmpMuxer != null) {
                rtmpMuxer.close();
                rtmpMuxer = null;
            }
            isConnectSuccess = false;
            isStoppedStream = false;
            rtmpMuxer = new RTMPMuxer(rtmpMuxerCallback);
            rtmpMuxer.open(urlRTMP, 1280, 720);
            while (true) {
                if (isConnectSuccess || isStoppedStream) {
                    break;
                }
            }
            if (isStoppedStream) {
                playbackError();
                return;
            }
            int timeStampMsRtmp = 0;
            long fileDuration_Sec = 0;
            long fileDuration_Ms = 0;
            int timeStampFrameMs = 0;
            int lastTimeStampFrameMs = 0;
            int frameSize = 0;
            VideoMp4 videoMp4;
            long timeSearchFile = 0;
            int countFileNoExits = 0;
            int countFileError = 0;
            isStartPlayback = true;
            timeFileBefore = timeUnix_t;
            while (isRunningPlayback) {
                timeStampMsRtmp += fileDuration_Ms;
//                timeSearchFile = timeFileBefore + fileDuration_Sec;
                timeSearchFile = getTimeSearchFileInArrayList();
                if (timeSearchFile == 0 || (!isStartPlayback && timeSearchFile <= timeFileBefore)) {
                    Log.i(TAG, "run: Path storage no exist.");
                    playbackError();
                    break;
                }
//                String fileName = searchFile(camId_t, timeSearchFile);
                String fileName = searchFile_exactTime(camId_t, timeSearchFile);
                isStartPlayback = false;
                if (fileName == null) {
                    fileDuration_Ms = 0;
                    fileDuration_Sec = 0;
                    countFileNoExits++;
                    if (countFileNoExits >= 10) {
                        Log.d(TAG, "run: Too much file Exist...may be Error Storage");
                        playbackError();
                        break;
                    }
                    continue;
                }
                countFileNoExits = 0;
                videoMp4 = new VideoMp4(fileName);
                if (!videoMp4.isHasVideoTrack()) {
                    fileDuration_Ms = 0;
                    fileDuration_Sec = 0;
                    countFileError++;
                    if (countFileError >= 5) {
                        Log.d(TAG, "run: Too much file Error Video");
                        playbackError();
                        break;
                    }
                    continue;
                }
                countFileError = 0;
                int lengthBufferSPS_PPS = videoMp4.getLengthBufferSPS_PPS();
                byte[] bufferSPS_PPS = videoMp4.getFrameSPS_PPS();
                while (isRunningPlayback) {
                    frameSize = videoMp4.getNextFrameSize();
                    if (frameSize > 0 && frameSize < VideoMp4.FRAME_SIZE_MAX) {
                        timeStampFrameMs = videoMp4.getFramePresentationMs();
                        int timeStamp = timeStampMsRtmp + timeStampFrameMs;
                        if (videoMp4.isKeyFrame()) {
                            rtmpMuxer.writeVideo(bufferSPS_PPS, 0, lengthBufferSPS_PPS, timeStamp);
                        }
                        byte[] buffer = new byte[frameSize];
                        videoMp4.getFrameByteBuffer().get(buffer, 0, frameSize);
                        rtmpMuxer.writeVideo(buffer, 0, frameSize, timeStamp);

                        try {
                            int sleepTime = Math.max(timeStamp - lastTimeStampFrameMs - 10, 5);
                            Thread.sleep(sleepTime);
                        }
                        catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        lastTimeStampFrameMs = timeStamp;
                    }
                    else if (videoMp4.isEndOfVideo()) {
                        break;
                    }
                }
                fileDuration_Ms = videoMp4.getFileDurationMs();
                fileDuration_Sec = fileDuration_Ms / 1000 + 1;
                Log.i(TAG, "FINISH FILE MP4: frames: " + videoMp4.getNumOfFrame()
                        + "..DurationMs:" + videoMp4.getFileDurationMs());
                videoMp4.close();
                videoMp4 = null;
            }
        }
    }, "playbackStream");

    private void playbackError() {
        new Thread(() -> playbackCallback.onErrorPlayback(camId_t)).start();
    }

    private boolean isStopping = false;

    public void stop() {
        if (isStopping) {
            return;
        }
        isStopping = true;

        new Thread(() -> {
            while (true) {
                if (isConnectSuccess || isStoppedStream) {
                    break;
                }
            }
            isRunningPlayback = false;
            if (streamThread.isAlive()) {
                try {
                    streamThread.join();
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (rtmpMuxer != null) {
                rtmpMuxer.close();
                rtmpMuxer = null;
            }
            isStopping = false;
        }).start();
    }

    private RTMPMuxer.RTMPMuxerCallback rtmpMuxerCallback = new RTMPMuxer.RTMPMuxerCallback() {
        @Override
        public void onStreamSuccess() {
            Log.i(TAG, "onStreamSuccess: " + urlRTMP);
            isConnectSuccess = true;
            playbackCallback.onStreamSuccess(camId_t, urlRTMP);
        }

        @Override
        public void onStopStream() {
            Log.i(TAG, "onStopStream: " + urlRTMP);
            isConnectSuccess = false;
            isStoppedStream = true;
            playbackCallback.onStopPlayback(camId_t);
        }
    };

    private String searchFile_exactTime(int camId, long timeUnix_search) {
        if (pathStorage == null) {
            return null;
        }
        String fileName = null;
        int camIdFolder = camId + 1;
        long timeFile = timeUnix_search;
        long timeFolder_unix = timeFile - timeFile % 3600;
        Date mDate = new Date(timeFile * 1000);
        String path1 = pathStorage + File.separator + "cam" + camIdFolder;
        String sTimeFolder = new SimpleDateFormat("yyyyMMdd_HH", Locale.ROOT).format(mDate);
        String path2 = path1 + File.separator + "cam" + camIdFolder + "_" + sTimeFolder + "_" + timeFolder_unix;
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(mDate);
        String name = path2 + File.separator + "VID_" + camIdFolder + "_" + timeStamp + ".mp4";
        File fileMp4 = new File(name);
        if (fileMp4.exists()) {
            fileName = fileMp4.getPath();
            Log.i(TAG, "searchFile_exactTime: " + fileName);
        }
        timeFileBefore = timeUnix_search;
        return fileName;
    }

    private String searchFile(int camId, long timeUnix_search) {
//        Log.i(TAG, "searchFile: " + camId + "....." + timeUnix_search);
        if (pathStorage == null) {
            return null;
        }

        long timeStart = timeUnix_search - 1;// 5;
        long timeEnd = timeUnix_search + 10;//60;
        long timeSearch = timeUnix_search;

        int camIdFolder = camId + 1;
        String fileName = null;

        while (timeSearch <= timeEnd) {
            Date mDate = new Date(timeSearch * 1000);
            String path1 = pathStorage + File.separator + "cam" + camIdFolder;
            String sTimeFolder = new SimpleDateFormat("yyyyMMdd_HH", Locale.ROOT).format(mDate);
            long time_unix = mDate.getTime() / 1000;
            long timeFolder_unix = time_unix - time_unix % 3600;
            String path2 = path1 + File.separator + "cam" + camIdFolder + "_" + sTimeFolder + "_" + timeFolder_unix;
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(mDate);
            String name = path2 + File.separator + "VID_" + camIdFolder + "_" + timeStamp + ".mp4";
            File fileMp4 = new File(name);
            if (fileMp4.exists()) {
                fileName = fileMp4.getPath();
                Log.i(TAG, "searchFile: " + fileName);
                break;
            }
            timeSearch++;
        }
        timeFileBefore = timeSearch;
        return fileName;
    }

    private ArrayList<Long> getListVideo(int camId) {
        if (pathStorage == null) {
            return null;
        }
        long timeTmp = timeUnix_t - 60;
        long timeHourStart = timeTmp - timeTmp % 3600;
        int camIdFolder = camId + 1;
        Date mDate = new Date(timeTmp * 1000);
        String sTimeDate = new SimpleDateFormat("yyyyMMdd").format(mDate);
        String sPrefixDate = "cam" + camIdFolder + "_" + sTimeDate;
        ArrayList<Long> arrayList = new ArrayList<>();
        String path1 = pathStorage + File.separator + "cam" + camIdFolder;
        File folderCam = new File(path1);
        if (!folderCam.exists()) {
            return null;
        }
        for (final File folderHour : folderCam.listFiles()) {
            if (!folderHour.getName().startsWith(sPrefixDate)) {
                continue;
            }
            String sTimeStampHour = folderHour.getName().substring(folderHour.getName().lastIndexOf("_") + 1);
            long timeStampHour = Long.parseLong(sTimeStampHour);
            if (timeStampHour < timeHourStart) {
                continue;
            }
            for (final File fileVideo : folderHour.listFiles()) {
                if (!fileVideo.getName().startsWith("VID") && fileVideo.getName().length() != 23) {
                    continue;
                }
                int index = fileVideo.getName().lastIndexOf("_") + 1 + 2;
                byte[] nameByte = fileVideo.getName().getBytes();
                long timeStampFile = timeStampHour +
                        ((nameByte[index++] - 0x30) * 10 + (nameByte[index++] - 0x30)) * 60 +
                        (nameByte[index++] - 0x30) * 10 + (nameByte[index] - 0x30);
                if (timeStampFile >= timeTmp) {
                    arrayList.add(timeStampFile);
                }
            }
        }
        Collections.sort(arrayList);
        return arrayList;
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

    public interface IPlaybackCallback {
        void onStreamSuccess(int camId, String url);

        void onStopPlayback(int camId);

        void onErrorPlayback(int camId);

        void onLog(String log);
    }

}
