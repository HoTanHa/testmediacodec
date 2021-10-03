package com.htha.httpServer;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import fi.iki.elonen.NanoHTTPD;

public final class HttpServer extends NanoHTTPD {
    private static final String TAG = "HttpServer";
    private static final int PORT = 8080;
    private static final int NUM_CAM = 4;
    private String pathStorage = null;
    private static final int CAM_ID_1 = 0;
    private static final int CAM_ID_2 = 1;
    private static final int CAM_ID_3 = 2;
    private static final int CAM_ID_4 = 3;
    private ArrayList<ArrayList<Long>> listHourFolder = new ArrayList<>();
    private ArrayList<ArrayList<Long>> listTimeVideo = new ArrayList<>();
    private Map<String, String> mapToken = new HashMap<>();
    private ArrayList<ImageData> listImage = new ArrayList<>();
    private static final String folderHttp = "/storage/emulated/0/web";
//    private static final String folderHttp = "/system/etc/web_hop_chuan";

    private long sdCardSize = 0;
    private long sdCardFreeSpace = 0;

    private int serialNumber = 0;
    private Context mContext;

    private int TIMEOUT_SERVER = 5 * 60 * 1000;
    private ServerCallback mCallback;
    private static volatile boolean isRunning = false;
    private Thread serverThread;
    private long timeCheckTimeout = 0;
    private long timeLastRequestMs = 0;

    public HttpServer(Context context, String pathSdCard, int sn, int timeoutS, ServerCallback callback) {
        super(PORT);
        this.mContext = context;
        this.serialNumber = sn;
        this.TIMEOUT_SERVER = timeoutS * 1000;
        this.mCallback = callback;
        Log.d(TAG, "HttpServer: Server starting");
        try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);
            isRunning = true;
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        if (pathSdCard != null) {
            File file = new File(pathSdCard);
            if (file.exists()) {
                this.pathStorage = pathSdCard;
                Log.d(TAG, "HttpServer: Set path sdCard:.." + pathSdCard);
            }
            else {
                Log.d(TAG, "HttpServer: Path sdCard do not exist..!!");
            }
        }
        else {
            Log.d(TAG, "HttpServer: Path sdCard is null..!");
        }

        timeCheckTimeout = System.currentTimeMillis();
        timeLastRequestMs = timeCheckTimeout;
        serverThread = new Thread(() -> {
            for (int i = 0; i < NUM_CAM; i++) {
                ArrayList<Long> item = new ArrayList<Long>();
                listHourFolder.add(item);
            }
            for (int i = 0; i < NUM_CAM; i++) {
                ArrayList<Long> item = new ArrayList<Long>();
                listTimeVideo.add(item);
            }
            for (int i = 0; i < NUM_CAM; i++) {
                readListVideoStorage(i);
            }
            readListImage();
            getInfoStorage();
            while (isRunning) {
                timeCheckTimeout = System.currentTimeMillis();
                if ((timeCheckTimeout - timeLastRequestMs) > TIMEOUT_SERVER) {
                    Date lastRequest = new Date(timeLastRequestMs);
                    Log.d(TAG, "HttpServer: timeout request.." + TIMEOUT_SERVER + ".." + lastRequest.toString());
                    new Thread(() -> mCallback.onTimeout()).start();
                    isRunning = false;
                    break;
                }

                try {
                    Thread.sleep(10000);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        serverThread.start();
    }

    @Override
    public void stop() {
        super.stop();
        isRunning = false;
        if (serverThread != null && serverThread.isAlive()) {
            try {
                serverThread.join();
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
            serverThread = null;
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        Method method = session.getMethod();
        String uri = session.getUri();
        Map<String, String> header = session.getHeaders();
        Map<String, List<String>> param = session.getParameters();
        String ipAddress = session.getRemoteIpAddress();
        String host = header.get("host");
        String token = header.get("token");
        Log.d(TAG, "serve: " + ipAddress + "..." + method + ":.." + uri + ".." + token + ".." + param);
        if (!method.equals(Method.GET)) {
            return responseNotFound();
        }
        timeLastRequestMs = System.currentTimeMillis();

        if (uri.equals("/")) {
//            Response response = newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "ADSUN CAMERA BOX");
//            response.addHeader("Access-Control-Allow-Origin", "*");
//            return response;
            String pathFile = folderHttp + "/index.html";
            FileInputStream fis = null;
            File file = new File(pathFile);
            try {
                fis = new FileInputStream(file);
            }
            catch (FileNotFoundException e) {
                return responseNotFound();
            }
            String type = pathFile.substring(pathFile.lastIndexOf(".") + 1).toLowerCase();

            if (type.isEmpty()) {
                return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, fis, file.length());
            }
            String mineType = null;
            try {
                mineType = NanoHTTPD.mimeTypes().get(type);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            if (mineType == null) {
                return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, fis, file.length());
            }
            Response response = newFixedLengthResponse(Response.Status.OK, MIME_TYPES.get(type), fis, file.length());
//            Response response = newFixedLengthResponse(Response.Status.OK, "application/octet-stream", fis, file.length());
            response.closeConnection(true);
            return response;
        }
        else if (uri.equals("/test")) {
            JSONObject object = getVideoIntervalGroup();
            return newFixedLengthResponse(Response.Status.OK, "application/json", object.toString());
        }
        else if (uri.equals("/getKey")) {
            String key = getKey();
            String key64 = encodeKeyToTokenKey(key);
            mapToken.put(ipAddress, key64);
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("status", 1);
                jsonObject.put("key", key);
                jsonObject.put("key64", key64);
            }
            catch (JSONException e) {
                e.printStackTrace();
            }
            return newFixedLengthResponse(Response.Status.OK, "application/json", jsonObject.toString());
        }
        else if (uri.equals("/api/getVideo")) {
            int cam = 0;
            long time = 0;
            if (param.containsKey("camera") && param.containsKey("time")) {
                try {
                    cam = Integer.parseInt(param.get("camera").get(0));
                    time = Long.parseLong(param.get("time").get(0));
                }
                catch (NumberFormatException exception) {
                    return responseParamError();
                }
//                Log.d(TAG, "serve: Query video..." + cam + "....." + time);
                return responseVideoFile(cam, time);
            }
            return responseParamError();
        }
        else if (uri.equals("/api/listHour")) {
            int cam = 0;
            if (param.containsKey("camera")) {
                try {
                    cam = Integer.parseInt(param.get("camera").get(0));
                }
                catch (NumberFormatException exception) {
                    return responseParamError();
                }
                return responseListHour(cam);
            }
            return responseParamError();
        }
        else if (uri.equals("/api/timeInterval")) {
            int cam = 0;
            String sDate;
            Date date;
            if (param.containsKey("camera") && param.containsKey("date")) {
                try {
                    cam = Integer.parseInt(param.get("camera").get(0));
                    sDate = param.get("date").get(0);
                    date = new SimpleDateFormat("yyyyMMdd", Locale.ROOT).parse(sDate);
//                    Log.d(TAG, "serve: " + date);
                }
                catch (NumberFormatException | ParseException exception) {
                    return responseParamError();
                }
                return responseTimeInterval(cam, date.getTime() / 1000);
            }
            return responseParamError();
        }
//        else if (uri.equals("/favicon.ico")) {
//            String pathFile = folderHttp + "/favicon.ico";
//            FileInputStream fis = null;
//            File file = new File(pathFile);
//            try {
//                fis = new FileInputStream(file);
//            }
//            catch (FileNotFoundException e) {
//                e.printStackTrace();
//                return responseNotFound();
//            }
//            Response response = newFixedLengthResponse(Response.Status.OK, "image/x-icon", fis, file.length());
//            response.closeConnection(true);
//            return response;
//        }
        else if (uri.equals("/favicon_bm.ico")) {
            String pathFile = folderHttp + "/favicon_bm.ico";
            FileInputStream fis = null;
            File file = new File(pathFile);
            try {
                fis = new FileInputStream(file);
            }
            catch (FileNotFoundException e) {
                e.printStackTrace();
                return responseNotFound();
            }
            Response response = newFixedLengthResponse(Response.Status.OK, "image/x-icon", fis, file.length());
            response.closeConnection(true);
            return response;
        }
        else if (uri.equals("/bootstrap.min.css")) {
            String pathFile = folderHttp + "/bootstrap.min.css";
            FileInputStream fis = null;
            File file = new File(pathFile);
            try {
                fis = new FileInputStream(file);
            }
            catch (FileNotFoundException e) {
                e.printStackTrace();
                return responseNotFound();
            }
            Response response = newFixedLengthResponse(Response.Status.OK, "text/css", fis, file.length());
            response.closeConnection(true);
            return response;
        }
        else if (uri.equals("/jquery.min.js")) {
            String pathFile = folderHttp + "/jquery.min.js";
            FileInputStream fis = null;
            File file = new File(pathFile);
            try {
                fis = new FileInputStream(file);
            }
            catch (FileNotFoundException e) {
                e.printStackTrace();
                return responseNotFound();
            }
            Response response = newFixedLengthResponse(Response.Status.OK, "text/javascript", fis, file.length());
            response.closeConnection(true);
            return response;
        }
        else if (uri.equals("/bootstrap.min.js")) {
            String pathFile = folderHttp + "/bootstrap.min.js";
            FileInputStream fis = null;
            File file = new File(pathFile);
            try {
                fis = new FileInputStream(file);
            }
            catch (FileNotFoundException e) {
                e.printStackTrace();
                return responseNotFound();
            }
            Response response = newFixedLengthResponse(Response.Status.OK, "text/javascript", fis, file.length());
            response.closeConnection(true);
            return response;
        }
        else if (uri.equals("/vis-timeline-graph2d.min.js")) {
            String pathFile = folderHttp + "/vis-timeline-graph2d.min.js";
            FileInputStream fis = null;
            File file = new File(pathFile);
            try {
                fis = new FileInputStream(file);
            }
            catch (FileNotFoundException e) {
                e.printStackTrace();
                return responseNotFound();
            }
            Response response = newFixedLengthResponse(Response.Status.OK, "text/javascript", fis, file.length());
            response.closeConnection(true);
            return response;
        }
        else if (uri.equals("/vis-timeline-graph2d.min.css")) {
            String pathFile = folderHttp + "/vis-timeline-graph2d.min.css";
            FileInputStream fis = null;
            File file = new File(pathFile);
            try {
                fis = new FileInputStream(file);
            }
            catch (FileNotFoundException e) {
                e.printStackTrace();
                return responseNotFound();
            }
            Response response = newFixedLengthResponse(Response.Status.OK, "text/css", fis, file.length());
            response.closeConnection(true);
            return response;
        }
        else if (uri.startsWith("/root")) {
            return responseRootDirectory(uri);
        }
        else if (uri.equals("/api/getListHour")) {
            return responseDataHour();
        }
        else if (uri.equals("/api/getListVideo")) {
            int cam = 0;
            long time = 0;
            String sDate;
            Date date;
            String hour;
            try {
                if (param.containsKey("camera")) {
                    cam = Integer.parseInt(param.get("camera").get(0));
                }
                if (param.containsKey("time")) {
                    time = Long.parseLong(param.get("time").get(0));
                }
                else if (param.containsKey("date") && param.containsKey("hour")) {
                    sDate = param.get("date").get(0);
                    hour = param.get("hour").get(0);
                    String sTimeTemp = sDate + "_" + hour;
                    date = new SimpleDateFormat("yyyyMMdd_HH", Locale.ROOT).parse(sTimeTemp);
                    time = date.getTime() / 1000;
                }
            }
            catch (NumberFormatException exception) {
                return responseParamError();
            }
            catch (ParseException e) {
                e.printStackTrace();
            }
//            Log.d(TAG, "serve: Query video..." + cam + "....." + time);
            return responseListVideoInHour(cam, time);
        }
        else if (uri.equals("/api/info")) {
            return responseInfo();
        }
        else if (uri.equals("/api/image")) {
            if (param.containsKey("timeStart") && param.containsKey("timeEnd")) {
                try {
                    long timeStart = Long.parseLong(param.get("timeStart").get(0));
                    long timeEnd = Long.parseLong(param.get("timeEnd").get(0));
                    return responseListImage(timeStart, timeEnd);
                }
                catch (Exception e) {
                    return responseParamError();
                }
            }
            return responseParamError();
        }
        else if (uri.equals("/api/getImage")) {
            if (param.containsKey("time") && param.containsKey("camera")) {
                try {
                    long timeMs = Long.parseLong(param.get("time").get(0));//+ 7 * 3600000;
                    int cam = Integer.parseInt(param.get("camera").get(0));
                    return responseImageFile(cam, timeMs);
                }
                catch (Exception e) {
                    return responseParamError();
                }
            }
            return responseParamError();
        }
        else if (uri.equals("/api/infoDevice")) {
            return responseInfoDevice();
        }
        else if (uri.equals("/api/dataWorkTime")) {
            if (param.containsKey("date")) {
                try {
                    long timeMs = Long.parseLong(param.get("date").get(0));
                    return responseDataWorkTime(timeMs);
                }
                catch (Exception e) {
                    return responseParamError();
                }
            }
            return responseParamError();
        }
        else if (uri.equals("/api/dataDungDo")) {
            if (param.containsKey("date")) {
                try {
                    long timeMs = Long.parseLong(param.get("date").get(0));
                    return responseDataDungDo(timeMs);
                }
                catch (Exception e) {
                    return responseParamError();
                }
            }
            return responseParamError();
        }
        else if (uri.equals("/api/dataHanhTrinh")) {
            if (param.containsKey("date")) {
                try {
                    long timeMs = Long.parseLong(param.get("date").get(0));
                    return responseDataHanhTrinh(timeMs);
                }
                catch (Exception e) {
                    return responseParamError();
                }
            }
            return responseParamError();
        }
        else if (uri.equals("/api/dataSpeed")) {
            if (param.containsKey("date")) {
                try {
                    long timeMs = Long.parseLong(param.get("date").get(0));
                    return responseDataSpeed(timeMs);
                }
                catch (Exception e) {
                    return responseParamError();
                }
            }
            return responseParamError();
        }
        else if (uri.equals("/api/overSpeed")) {
            if (param.containsKey("date") && param.containsKey("limit")) {
                try {
                    long timeMs = Long.parseLong(param.get("date").get(0));
                    int speed = Integer.parseInt(param.get("limit").get(0));
                    return responseOverSpeed(timeMs, speed);
                }
                catch (Exception e) {
                    return responseParamError();
                }
            }
            return responseParamError();
        }

        return responseNotFound();
    }

//    "videoStorage":{"size":{"width":1280,"height":720},"fps":20,"time":1},
//            "videoStream":{"size":{"width":640,"height":360},"fps":10},
//            "image":{"size":{"width":1280,"height":720},"time":1},

    private Response responseInfoDevice() {
        String responseData = LocationData.getThongTinThietBi();
        return newFixedLengthResponse(Response.Status.OK, "application/json", responseData);
    }

    private Response responseDataWorkTime(long timeMs) {
        Log.d(TAG, "responseDataWorkTime: " + timeMs);
        String responseData = LocationData.getThoiGianLamViec(new Date(timeMs));
        Response response = newFixedLengthResponse(Response.Status.OK, "application/json", responseData);
        response.closeConnection(true);
        return response;
    }

    private Response responseDataHanhTrinh(long timeMs) {
        Log.d(TAG, "responseDataHanhTrinh: " + timeMs);
        String responseData = LocationData.getDuLieuHanhTrinh(new Date(timeMs));
        Response response = newFixedLengthResponse(Response.Status.OK, "application/json", responseData);
        response.closeConnection(true);
        return response;
    }

    private Response responseDataDungDo(long timeMs) {
        Log.d(TAG, "responseDataDungDo: " + timeMs);
        String responseData = LocationData.getDuLieuDungDo(new Date(timeMs));
        Response response = newFixedLengthResponse(Response.Status.OK, "application/json", responseData);
        response.closeConnection(true);
        return response;
    }

    private Response responseDataSpeed(long timeMs) {
        Log.d(TAG, "responseDataSpeed: " + timeMs);
        String responseData = LocationData.getDuLieuTocDoTungGiay(new Date(timeMs));
        Response response = newFixedLengthResponse(Response.Status.OK, "application/json", responseData);
        response.closeConnection(true);
        return response;
    }

    private Response responseOverSpeed(long timeMs, int limitSpeed) {
        Log.d(TAG, "responseOverSpeed: " + timeMs + "....." + limitSpeed);
        String responseData = LocationData.getQuaTocDo(new Date(timeMs), limitSpeed);
        Response response = newFixedLengthResponse(Response.Status.OK, "application/json", responseData);
        response.closeConnection(true);
        return response;
    }

    private Response responseImageFile(int camId, long timeMs) {
        long timeExactly = 0;
        String pathFile = null;
        for (ImageData item : listImage) {
            if ((timeMs / 1000) == (item.getImageTimeMs() / 1000)) {
                timeExactly = item.getImageTimeMs();
                pathFile = item.getImagePath();
                break;
            }
        }
        if (timeExactly == 0) {
            return responseParamError();
        }
        FileInputStream fis = null;
        File file = new File(pathFile);
        Log.d(TAG, "responseImageFile: " + pathFile);
        try {
            fis = new FileInputStream(file);
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
            return responseParamError();
        }
        Response response = newFixedLengthResponse(Response.Status.OK, "image/jpeg", fis, file.length());
        response.closeConnection(true);
        return response;
    }

    private Response responseListImage(long timeStart, long timeEnd) {
        JSONArray imagesJson = new JSONArray();
        int idCount = 1;
        for (int i = 0; i < listImage.size(); i++) {
            long time = listImage.get(i).getImageTimeMs();
            if (time > timeStart && time < timeEnd) {
                listImage.get(i).setId(idCount++);
                imagesJson.put(listImage.get(i).getStringJSON());
            }
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", imagesJson.toString());
    }

    private Response responseInfo() {
        JSONObject jsonInfo = new JSONObject();
        JSONObject jsonStorage = new JSONObject();
        JSONObject jsonVideoStorage = new JSONObject();
        JSONObject jsonSizeVideo = new JSONObject();
        JSONObject jsonVideoStream = new JSONObject();
        JSONObject jsonSizeStream = new JSONObject();
        JSONObject jsonImage = new JSONObject();
        JSONObject jsonSizeImage = new JSONObject();
        try {
            jsonInfo.put("status", 1);
            jsonInfo.put("name", "ADSUN_CAMERA_BOX");

            jsonStorage.put("total", sdCardSize);
            jsonStorage.put("free", sdCardFreeSpace);
            jsonInfo.put("storage", jsonStorage);

            jsonSizeVideo.put("width", 1280);
            jsonSizeVideo.put("height", 720);
            jsonVideoStorage.put("size", jsonSizeVideo);
            jsonVideoStorage.put("fps", 10);
            jsonVideoStorage.put("time", 1);
            jsonInfo.put("videoStorage", jsonVideoStorage);

            jsonSizeStream.put("width", 640);
            jsonSizeStream.put("height", 360);
            jsonVideoStream.put("size", jsonSizeStream);
            jsonInfo.put("videoStream", jsonVideoStream);


            jsonSizeImage.put("width", 1280);
            jsonSizeImage.put("height", 720);
            jsonImage.put("size", jsonSizeImage);
            jsonImage.put("time", 1);
            jsonInfo.put("image", jsonImage);
        }
        catch (JSONException e) {
            e.printStackTrace();
        }

        return newFixedLengthResponse(Response.Status.OK, "application/json", jsonInfo.toString());
    }

    private Response newRedirectResponse(String urlToRedirectTo) {
        String html = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "<title>Redirect</title>\n" +
                "<script type=\"text/javascript\">\n" +
                "window.location = " + "\"" + urlToRedirectTo + "\"" + ";\n" +
                "</script>\n" +
                "</head>\n" +
                "</html>";
        return newFixedLengthResponse(Response.Status.TEMPORARY_REDIRECT, "text/html", html);
    }

    private Response responseNotToken() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("status", 3);
            jsonObject.put("description", "Your request has no token. @@@");
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", jsonObject.toString());
    }

    private Response responseDataHour() {
        JSONArray jsonListCam = new JSONArray();
        for (int i = 0; i < listHourFolder.size(); i++) {
            JSONArray data = new JSONArray(listHourFolder.get(i));
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("camera", i);
                jsonObject.put("time", data);
                jsonListCam.put(jsonObject);
            }
            catch (JSONException e) {
            }
        }
        JSONObject jsonRes = new JSONObject();
        try {
            jsonRes.put("status", 1);
            jsonRes.put("data", jsonListCam);
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", jsonRes.toString());
    }

    private Response responseListVideoInHour(int camId, long timeUnix) {
        long timeStart = timeUnix - timeUnix % 3600;
        long timeEnd = timeStart + 3600;
        int camIdFolder = camId + 1;
        ArrayList<Long> list = listTimeVideo.get(camId);
        ArrayList<String> listStringVideo = new ArrayList<String>();
        for (long item : list) {
            if (item > timeStart && item < timeEnd) {
                Date date = new Date(item * 1000);
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(date);
                String name = "VID_" + camIdFolder + "_" + timeStamp + ".mp4";
                listStringVideo.add(name);
            }
        }
        JSONArray jsonArrayListVideo = new JSONArray(listStringVideo);
        String sTimeFolder = new SimpleDateFormat("yyyyMMdd_HH", Locale.ROOT).format(new Date(timeStart * 1000));
        String path = "root/cam" + camIdFolder + "/cam" + camIdFolder + "_" + sTimeFolder + "_" + timeStart;
        JSONObject object = new JSONObject();
        try {
            object.put("path", path);
            object.put("list_file", jsonArrayListVideo);
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
        JSONArray arrayStorage = new JSONArray();
        arrayStorage.put(object);

        JSONObject jsonRes = new JSONObject();
        try {
            jsonRes.put("status", 1);
            jsonRes.put("camera", camId);
            jsonRes.put("date", sTimeFolder.substring(0, 8));
            jsonRes.put("hour", sTimeFolder.substring(9));
            jsonRes.put("files", arrayStorage);
        }
        catch (JSONException e) {
            e.printStackTrace();
        }

        return newFixedLengthResponse(Response.Status.OK, "application/json", jsonRes.toString());
    }

    private Response getForbiddenResponse(String s) {
        return newFixedLengthResponse(Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "FORBIDDEN: " + s);
    }

    private Response getInternalErrorResponse(String s) {
        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "INTERNAL ERROR: " + s);
    }

    private Response responseRootDirectory(String uri) {
        String path1 = uri.substring(5);
        String path = path1.replaceAll("//", "/");
        File rootDir = new File(pathStorage + path);
        if (rootDir.isDirectory()) {
            File[] files2 = rootDir.listFiles();
            StringBuilder answer = new StringBuilder("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"><title>WiFi Hotspot</title>");
            answer.append("<h3>Directory ").append(path).append("</h3><hr>");
            for (File itemFiles : files2) {
                if (itemFiles.isFile()) {
                    answer.append("<a href=\"");
                    answer.append("/root").append(path).append(File.separator).append(itemFiles.getName());
                    answer.append("\" alt = \"\">").append(itemFiles.getName());
                    answer.append(" (").append(itemFiles.length() / 1024).append(" kB) ").append("</a><br>");
                }
                else {
                    answer.append("<a href=\"");
                    answer.append("/root").append(path).append(File.separator).append(itemFiles.getName());
                    answer.append("\" alt = \"\">").append(itemFiles.getName()).append("</a><br>");
                }
            }
            answer.append("</head></html>");
            return newFixedLengthResponse(answer.toString());
        }
        else if (rootDir.isFile()) {
            String pathFile = rootDir.getPath();
            FileInputStream fis = null;
            File file = new File(pathFile);
            try {
                fis = new FileInputStream(file);
            }
            catch (FileNotFoundException e) {
                e.printStackTrace();
                responseParamError();
            }
            String type = pathFile.substring(pathFile.lastIndexOf(".") + 1).toLowerCase();

            if (type.isEmpty()) {
                return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, fis, file.length());
            }
            String mineType = null;
            try {
                mineType = NanoHTTPD.mimeTypes().get(type);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            if (mineType == null) {
                return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, fis, file.length());
            }
//            else if (mineType.equals("video/mp4")){
//                return newChunkedResponse(Response.Status.OK, mineType, fis);
//            }
//                Response response = newFixedLengthResponse(Response.Status.OK, MIME_TYPES.get(type), fis, file.length());
            Response response = newFixedLengthResponse(Response.Status.OK, "application/octet-stream", fis, file.length());
            response.closeConnection(true);
            return response;

        }
        return responseNotFound();
    }

    private Response responseParamError() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("status", 2);
            jsonObject.put("description", "Parameters may be wrong");
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
        String responseData = jsonObject.toString();
        return newFixedLengthResponse(Response.Status.OK, "application/json", responseData);
    }

    private Response responseNotFound() {
        return newFixedLengthResponse(Response.Status.NOT_FOUND,
                NanoHTTPD.MIME_PLAINTEXT, "Error 404, file not found.");
    }

    private Response responseVideoFile(int camId, long time) {
        if (pathStorage == null) {
            return responseNotFound();
        }
        long timeInList = searchTimeVideoInList(camId, time);
        if (timeInList == 0) {
            return responseParamError();
        }
        String pathVideo = getPathTimeVideo(camId, timeInList);
        if (pathVideo == null) {
            return responseParamError();
        }

        FileInputStream fis = null;
        File file = new File(pathVideo);

        if (file.exists()) {
            String redirect = "/root/" + pathVideo.substring(pathStorage.length() + 1);
            return newRedirectResponse(redirect);
        }
//        try {
//            fis = new FileInputStream(file);
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//            responseParamError();
//        }
//
//        Response response = null;
//        try {
////            return newFixedLengthResponse(Response.Status.OK, "video/mp4", fis, fis.available());
//            return newFixedLengthResponse(Response.Status.OK, "application/octet-stream", fis, fis.available());
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        return responseNotFound();
    }

    private Response responseListHour(int camId) {
        JSONArray data = new JSONArray(listHourFolder.get(camId));
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("data", data);
        }
        catch (JSONException e) {
            responseNotFound();
        }
        String responseData = jsonObject.toString();
        return newFixedLengthResponse(Response.Status.OK, "application/json", responseData);
    }

    private Response responseTimeInterval(int camId, long timeDate) {
        if (camId >= 2) {
            return responseParamError();
        }
        long time = timeDate + 3600 * 7;
        JSONObject jsonData = getVideoTimeInterval(camId, time);
        String res;
        res = jsonData.toString();
        return newFixedLengthResponse(Response.Status.OK, "application/json", res);
    }

    private String getKey() {
        byte[] key = new byte[21];
        Random rand = new Random();
        for (int i = 0; i < 20; i++) {
            key[i] = (byte) ((byte) rand.nextInt(10) + 0x30);
        }
        String sKey = new String(key, 0, 20);
        return sKey;
    }

    private String encodeKeyToTokenKey(String sKey) {
        byte[] key = sKey.getBytes();
        byte[] key2 = new byte[key.length + 1];
        byte x;
        for (int i = 0; i < key.length; i++) {
            x = key[i];
            key2[i] = (byte) ((i % 3 == 0) ? (x * 2 + x % 8 + 1) : ((i % 3 == 1) ? (x + 25 + i % 10 - i % 4) : ((x < 0x35) ? (x + 33 - i % 10) : (x + 54 + i % 10))));
        }
        String sKey2 = new String(key2, 0, key.length);
//        Log.d(TAG, "encodeKeyToTokenKey: key2: " + sKey2);
        String sKey3 = "ADSUN-" + sKey2 + "-" + sKey.substring(15);
//        Log.d(TAG, "encodeKeyToTokenKey: key3: " + sKey3);
        String encodedBytes = Base64.encodeToString(sKey3.getBytes(), Base64.NO_WRAP);
//        Log.d(TAG, "encodeKeyToTokenKey: key4: " + encodedBytes);
        return encodedBytes;
    }

    private void readListVideoStorage(int camId) {
        if (pathStorage == null) {
            return;
        }
        File sdCard = new File(pathStorage);
        if (!sdCard.exists()) {
            return;
        }

        int camIdFolder = camId + 1;
        File folderCam = new File(pathStorage, "cam" + camIdFolder);
        if (!folderCam.exists()) {
            return;
        }
        String sPrefixFolderHour = "cam" + camIdFolder + "_";
        String sPrefixFileVideo = "VID_" + camIdFolder + "_";
        long timeStampFile = 0;
        int index = 0;
        for (final File folderHour : folderCam.listFiles()) {
            if (!folderHour.getName().startsWith(sPrefixFolderHour)) {
                continue;
            }
            String sTimeStampHour = folderHour.getName().substring(folderHour.getName().lastIndexOf("_") + 1);
            long timeStampHour = Long.parseLong(sTimeStampHour);
            listHourFolder.get(camId).add(timeStampHour);
            for (final File fileVideo : folderHour.listFiles()) {
                if (!fileVideo.getName().startsWith(sPrefixFileVideo) && fileVideo.getName().length() != 23) {
                    continue;
                }

                index = fileVideo.getName().lastIndexOf("_") + 1 + 2;
                byte[] nameByte = fileVideo.getName().getBytes();
                timeStampFile = timeStampHour +
                        ((nameByte[index++] - 0x30) * 10 + (nameByte[index++] - 0x30)) * 60 +
                        (nameByte[index++] - 0x30) * 10 + (nameByte[index] - 0x30);
                listTimeVideo.get(camId).add(timeStampFile);
            }
        }
        Collections.sort(listHourFolder.get(camId));
        Collections.sort(listTimeVideo.get(camId));
    }

    private void readListImage() {
        readImageExternal();
        readImageSdCard();
        Collections.sort(listImage, ImageData.imageDataComparator);
    }

    private void readImageSdCard() {
        if (pathStorage == null) {
            return;
        }
        String imageDirPath = pathStorage + File.separator + "image_" + serialNumber;
        File imageDir = new File(imageDirPath);
        if (!imageDir.exists()) {
            return;
        }
        File[] listOfFiles = imageDir.listFiles();
        for (File item : listOfFiles) {
            String name = item.getName();
            boolean isNameImageSave = name.startsWith("img");
            boolean isImageJpg = name.substring(name.lastIndexOf('.')).equals(".jpg");
            if (isNameImageSave && isImageJpg) {
                listImage.add(new ImageData(item.getPath()));
            }
        }
    }

    private void readImageExternal() {
        String imgFolder = "image_" + serialNumber;
        File imageDir = new File(mContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES), imgFolder);
        if (!imageDir.exists()) {
            return;
        }
        File[] listOfFiles = imageDir.listFiles();
        for (File item : listOfFiles) {
            String name = item.getName();
            boolean isNameImageSave = name.startsWith("img");
            boolean isImageJpg = name.substring(name.lastIndexOf('.')).equals(".jpg");
            if (isNameImageSave && isImageJpg) {
                listImage.add(new ImageData(item.getPath()));
            }
        }
    }

    private JSONObject getVideoTimeInterval(int camId, long timeDate) {
        JSONObject dataJsonInterval = new JSONObject();
        JSONArray arrayJsonInterval = new JSONArray();
        ArrayList<Long> list = listTimeVideo.get(camId);
        if (list == null || list.size() == 0) {
            return dataJsonInterval;
        }
        int numOfVideo = list.size();
        int i = 0;
        int j = 0;

        long dateStart = timeDate - timeDate % 86400;
        long dateEnd = dateStart + 86400 - 60;
        for (; i < numOfVideo; i++) {
            if (list.get(i) > dateStart) {
                break;
            }
        }

        long begin, end;
        int count = 1;
        while ((i < numOfVideo) && (list.get(i) < dateEnd)) {
            begin = list.get(i);
            j = i + 1;
            for (; (j < numOfVideo) && (list.get(j) < dateEnd); j++) {
                if (list.get(j) > (list.get(j - 1) + 60)) {
                    break;
                }
            }
            end = list.get(j - 1) + 60;
            TimeInterval timeInterval = new TimeInterval(count++, begin, end);
            arrayJsonInterval.put(timeInterval.toJson());
            i = j;
        }
        Date d = new Date(timeDate * 1000);
        String sTimeFolder = new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(d);
        try {
            dataJsonInterval.put("status", 1);
            dataJsonInterval.put("camera", camId);
            dataJsonInterval.put("date", sTimeFolder);
            dataJsonInterval.put("data", arrayJsonInterval);
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
        return dataJsonInterval;
    }

    private JSONObject getVideoIntervalGroup() {
        JSONObject dataJsonInterval = new JSONObject();
        JSONArray arrayJsonInterval = new JSONArray();
        int count = 1;
        for (int idGroup = 0; idGroup < listTimeVideo.size(); idGroup++) {
            ArrayList<Long> list = listTimeVideo.get(idGroup);
            if (list == null || list.size() == 0) {
                continue;
            }
            int group = idGroup + 1;
            int numOfVideo = list.size();
            if (numOfVideo == 0) {
                continue;
            }
            int i = 0;
            int j = 0;

            long dateStart = list.get(0);
            long dateEnd = list.get(numOfVideo - 1);

            long begin, end;
            while ((i < numOfVideo) && (list.get(i) < dateEnd)) {
                begin = list.get(i);
                j = i + 1;
                for (; (j < numOfVideo) && (list.get(j) < dateEnd); j++) {
                    if (list.get(j) > (list.get(j - 1) + 60)) {
                        break;
                    }
                }
                end = list.get(j - 1) + 60;
                VideoInterval videoInterval = new VideoInterval(count++, group, begin, end);
                arrayJsonInterval.put(videoInterval.toJson());
                i = j;
            }
        }
        try {
            dataJsonInterval.put("dataInterval", arrayJsonInterval);
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
        return dataJsonInterval;
    }

    private long searchTimeVideoInList(int camId, long time) {
        ArrayList<Long> list = listTimeVideo.get(camId);
        long timeReturn = 0;
        if (list.size() == 0) {
            return 0;
        }
        int index = Collections.binarySearch(list, time);
        if (index < -1) {
            index += 2;
            index *= -1;
            if ((list.get(index)) + 60 > time) {
                Log.d(TAG, "searchTimeVideo: between, bigger in 60s..." + time + ".." + timeReturn);
                timeReturn = list.get(index);
            }
            else if (index == (list.size() - 1)) {
                Log.d(TAG, "searchTimeVideo: bigger than maxValue..." + time + ".." + timeReturn);
            }
            else {
                Log.d(TAG, "searchTimeVideo: between, bigger than 60s..." + time + ".." + timeReturn);
            }
        }
        else if (index == -1) {
            Log.d(TAG, "searchTimeVideo: less than minValue..." + time + ".." + timeReturn);
        }
        else {
            Log.d(TAG, "searchTimeVideo: exist in list..." + time + ".." + timeReturn);
            timeReturn = time;
        }
        return timeReturn;
    }

    private String getPathTimeVideo(int camId, long timeUnix_search) {
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
        return fileName;
    }

    private void getInfoStorage() {
        if (pathStorage == null) {
            return;
        }

        StatFs stat = new StatFs(pathStorage);
        sdCardSize = stat.getTotalBytes() / (1024 * 1024);
        sdCardFreeSpace = stat.getFreeBytes() / (1024 * 1024);
        Log.d(TAG, "getInfoStorage: Total: " + sdCardSize + "...Free: " + sdCardFreeSpace);
    }

    private class TimeInterval {
        private long timeBegin = 0;
        private long timeEnd = 0;
        private int ID = 0;

        public TimeInterval(int id, long timeBegin, long timeEnd) {
            this.timeBegin = timeBegin;
            this.timeEnd = timeEnd;
            this.ID = id;
        }

        public JSONObject toJson() {
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("id", ID);
                jsonObject.put("start", timeBegin * 1000);
                jsonObject.put("end", (timeEnd - 59) * 1000);
            }
            catch (JSONException e) {
                e.printStackTrace();
                return null;
            }
            return jsonObject;
        }
    }

    public interface ServerCallback {
        void onTimeout();
    }
}
