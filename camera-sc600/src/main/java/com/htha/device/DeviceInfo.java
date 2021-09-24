package com.htha.device;

import android.util.Log;

import com.htha.cameraFeature.ImageHttp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.Locale;

public class DeviceInfo {
    private static final String TAG = "DeviceInfo";
    private int serialNumber = 0;
    private static DeviceInfo instance = null;
    private boolean isStreaming[] = new boolean[4];
    private boolean isPlugin[] = new boolean[4];
    private static boolean isSending = false;

    private double latitude_t = 0.0f;
    private double longitude_t = 0.0f;
    private double speed_t = 0.0f;
    private String tempDevice;
    private static int countSendInfo = 0;
    private String cpuPercent_t;
    private float diskPercent_t = 0.0f;
    private int keyStatus_t = 0;
    private String ipDevice_t;
    private double adc = 0.0f;

    private DeviceInfo() {

    }

    public static synchronized DeviceInfo getInstance() {
        if (instance == null) {
            instance = new DeviceInfo();
        }
        return instance;
    }

    public void setStreamCamera(int camId, boolean isStream) {
        if (camId >= 4) {
            return;
        }
        isStreaming[camId] = isStream;
    }

    public void setCameraStatus(int camId, boolean isExist) {
        if (camId >= 4) {
            return;
        }
        isPlugin[camId] = isExist;
    }

    public void setInfoLocation(int serial, double lat, double lon, double speed) {
        this.serialNumber = serial;
        this.latitude_t = lat;
        this.longitude_t = lon;
        this.speed_t = speed;
    }

    public void sendInfo(){
        if (isSending){
            return;
        }
        isSending = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    sendHttpInformationDevice();
                    Thread.sleep(1000);
                }
                catch (IOException | InterruptedException ignored) {
                }

                isSending = false;
            }
        }, "sendInfoThread").start();
    }

    public void setInfoDeviceStatus(float diskPercent, String cpuPercent, String temp,
                                    String ipDevice, int keyStatus, double adc) {
        this.diskPercent_t = diskPercent;
        this.cpuPercent_t = cpuPercent;
        this.tempDevice = temp;
        this.ipDevice_t = ipDevice;
        this.keyStatus_t = keyStatus;
        this.adc = adc;
    }
    private void sendHttpInformationDevice() throws IOException {
        if (tempDevice == null || ipDevice_t == null) {
            return;
        }
        countSendInfo++;
        Date time = new Date();
        int camCnt = 4;
        int liveStreamCnt = 4;
        String query1 = "id=" + serialNumber + "&lat=" + String.format(Locale.ROOT, "%.6f", latitude_t) +
                "&lon=" + String.format(Locale.ROOT, "%.6f", longitude_t) +
                "&timestamp=" + time.getTime() +
                "&hdop=0.000&altitude=0.0" +
                "&speed=" + String.format(Locale.ROOT, "%.1f", speed_t) +
                "&heading=0.0&adc=" + String.format(Locale.ROOT, "%.1f", adc) +
                "&cam=" + camCnt + "&live=" + liveStreamCnt + "&life=" + countSendInfo +
                "&svn=1.8&ip=" + ipDevice_t + "&disk=" + String.format(Locale.ROOT, "%.1f", diskPercent_t) +
                "&cpu=" + cpuPercent_t + "&temp=" + tempDevice +
                "&key=" + keyStatus_t + "&sd=1&T90=1" +
                "&cam1def=30,16,60,0&cam2def=30,16,60,0" +
                "&cam1set=40,20,80,0&cam2set=50,20,80,0 ";

        String sUrl;
        if (ImageHttp.isSendBinhMinh()) {
//            sUrl = "http://125.212.211.209:8102/api/manage/deviceInfo";
            sUrl = "http://live1.gpsbinhminh.vn:8102/api/manage/deviceInfo";
        }
        else if (ImageHttp.isSendServerTest()) {
//            sUrl = "http://125.212.211.209:8200/api/manage/deviceInfo";
            sUrl = "http://live1.adsun.vn:8200/api/manage/deviceInfo";
        }
        else {
//            sUrl = "http://125.212.211.209:8100/api/manage/deviceInfo";
            sUrl = "http://live1.adsun.vn:8100/api/manage/deviceInfo";
        }
        HttpURLConnection connection;
        URL url = new URL(sUrl + "?" + query1);
        connection = (HttpURLConnection) url.openConnection();
        connection.setDoInput(true);
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(1000);
        connection.setRequestProperty("Connection", "Close");
        int serverResponseCode = connection.getResponseCode();
        BufferedReader br = null;
        if (100 <= serverResponseCode && serverResponseCode <= 399) {
            br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        }
        else {
            br = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
        }
        String body = br.readLine();
        String log = "Send Info Device: code " + serverResponseCode + "..Body: " + body;
        Log.i(TAG, "sendHttpInformationDevice:" + log);
    }

}