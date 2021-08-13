package com.example.testcameramediacodec.httpServer;

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
    private final ArrayList<ArrayList<Long>> listHourFolder = new ArrayList<>();
    private final ArrayList<ArrayList<Long>> listTimeVideo = new ArrayList<>();
    private final Map<String, String> mapToken = new HashMap<>();

    private long sdCardSize = 0;
    private long sdCardFreeSpace = 0;

    public HttpServer(String pathSdCard) {
        super(PORT);
        Log.d(TAG, "HttpServer: Server starting");
        try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);
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
        new Thread(new Runnable() {
            @Override
            public void run() {
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
                getInfoStorage();
            }
        }).start();
    }

    @Override
    public Response serve(IHTTPSession session) {
        NanoHTTPD.Method method = session.getMethod();
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

        if (uri.equals("/")) {
            Response response = newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "ADSUN CAMERA BOX");
            response.addHeader("Access-Control-Allow-Origin", "*");
            return response;
        }
        else if (uri.equals("/test")) {
            if (token == null || !mapToken.containsKey(ipAddress) || !token.equals(mapToken.get(ipAddress))) {
                return responseNotToken();
            }
//            Response r = newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "");
            Response r = newFixedLengthResponse(Response.Status.REDIRECT, MIME_PLAINTEXT, "");
            r.addHeader("Location", "http://" + host + "/");
            return r;
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
                Log.d(TAG, "serve: Query video..." + cam + "....." + time);
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
                }
                catch (NumberFormatException | ParseException exception) {
                    return responseParamError();
                }
                return responseTimeInterval(cam, date.getTime() / 1000);
            }
            return responseParamError();
        }
        else if (uri.equals("/favicon.ico")) {
            responseNotFound();
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
            Log.d(TAG, "serve: Query video..." + cam + "....." + time);
            return responseListVideoInHour(cam, time);
        }
        else if (uri.equals("/api/info")){
            return responseInfo();
        }
        return responseNotFound();
    }

//    "videoStorage":{"size":{"width":1280,"height":720},"fps":20,"time":1},
//            "videoStream":{"size":{"width":640,"height":360},"fps":10},
//            "image":{"size":{"width":1280,"height":720},"time":1},

    private Response responseInfo(){
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
            jsonInfo.put("storage", jsonStorage );

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

    private Response getNotFoundResponse() {
        return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Error 404, file not found.");
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
        return getNotFoundResponse();
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
        JSONObject jsonData = getVideoTimeInterval(camId, timeDate);
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
        Log.d(TAG, "encodeKeyToTokenKey: key2: " + sKey2);
        String sKey3 = "ADSUN-" + sKey2 + "-" + sKey.substring(15);
        Log.d(TAG, "encodeKeyToTokenKey: key3: " + sKey3);
        String encodedBytes = Base64.encodeToString(sKey3.getBytes(), Base64.NO_WRAP);
        Log.d(TAG, "encodeKeyToTokenKey: key4: " + encodedBytes);
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
        while ((i < numOfVideo) && (list.get(i) < dateEnd)) {
            begin = list.get(i);
            j = i + 1;
            for (; (j < numOfVideo) && (list.get(j) < dateEnd); j++) {
                if (list.get(j) > (list.get(j - 1) + 60)) {
                    break;
                }
            }
            end = list.get(j - 1) + 60;
            TimeInterval timeInterval = new TimeInterval(begin, end);
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

    private static class TimeInterval {
        private long timeBegin = 0;
        private long timeEnd = 0;

        public TimeInterval(long timeBegin, long timeEnd) {
            this.timeBegin = timeBegin;
            this.timeEnd = timeEnd;
        }

        public JSONObject toJson() {
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("begin", timeBegin);
                jsonObject.put("end", timeEnd);
            }
            catch (JSONException e) {
                e.printStackTrace();
            }
            return jsonObject;
        }
    }
}
