package com.htha.cameraFeature;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Date;

public class ImageHttp {
    private static final String TAG = "ImageHttp";
    private static int serialNumber = 0;
    private static final int RES_CODE = 200;
    private static final String RES_BODY = "OK!";
    private String pathStorage;
    private ImageSendCallBack imageSendCallBack;

    private static final int QUALITY_JPEG = 70;
    private Context mContext;

    public ImageHttp(int serialNumber, String path, Context context) {
        ImageHttp.serialNumber = serialNumber;
        this.pathStorage = path;
        this.mContext = context;
    }

    public void setImageSendCallBack(ImageSendCallBack callBack) {
        this.imageSendCallBack = callBack;
    }

    String pathImageSend = null;
    public void send(String path) {
        pathImageSend = path;
        Thread sendImagePath =  new Thread(() -> {
            boolean result = false;
            try {
                result = http_post_image(pathImageSend);
            }
            catch (IOException e) {
                Log.e(TAG, "send: image path:.." + e.getMessage());
            }
            finally {
                RouterURL.setCountFail(result);
                imageSendCallBack.onImageSendStorage(pathImageSend, result);
            }
        });
        sendImagePath.start();
    }

    byte[] finalRawImage;
    Date dateImage;
    int mCamIdReal;
    long mLat, mLon;
    boolean sendBuffer;
    public void send(byte[] rawImageNV21, int camId, Date date, boolean send, long dLat, long dLon) {
        mCamIdReal = camId+1;
        dateImage = new Date(date.getTime());
        mLat = dLat;
        mLon = dLon;
        sendBuffer = send;
        byte[] rawImage = new byte[rawImageNV21.length];// Arrays.copyOf(rawImageNV21, rawImageNV21.length);
        finalRawImage = ImageUtil.YUV420SPtoNV21(rawImageNV21, rawImage, 1280, 720);
        Thread sendImage = new Thread( () -> {
            ByteArrayOutputStream imageOS = NV21toJPEG(finalRawImage, 1280, 720, 70);

            boolean result = false;
            if (sendBuffer) {
                try {
                    result = http_post_image(imageOS, mCamIdReal, dateImage);
                }
                catch (IOException e) {
//                        e.printStackTrace();
                    String log = "Send Image Buffer: connection error.. " + e.toString();
                    imageSendCallBack.onLogResult(log);
                }
                finally {
                    RouterURL.setCountFail(result);
                    if (result) {
                        imageSendCallBack.onImageSendSuccess(mCamIdReal, dateImage);
                    }
                    String path = saveImage(imageOS, mCamIdReal, dateImage, mLat, mLon, result);
                    if (path != null) {
                        imageSendCallBack.onImageSave(path);
                    }
                    else {
                        String path2 = saveImageOnExternal(imageOS, mCamIdReal, dateImage, mLat, mLon, result);
                        if (path2 != null) {
                            imageSendCallBack.onImageSave(path2);
                        }
                        else {
                            imageSendCallBack.onImageCreateFail(mCamIdReal-1);
                        }
                    }
                }
            }
            else {
                String path = saveImage(imageOS, mCamIdReal, dateImage, mLat, mLon, false);
                if (path != null) {
                    imageSendCallBack.onImageSave(path);
                }
                else {
                    String path2 = saveImageOnExternal(imageOS, mCamIdReal, dateImage, mLat, mLon, false);
                    if (path2 != null) {
                        imageSendCallBack.onImageSave(path2);
                    }
                    else {
                        imageSendCallBack.onImageCreateFail(mCamIdReal-1);
                    }
                }
            }
        });
        sendImage.start();
    }

    private String saveImageOnExternal(ByteArrayOutputStream outputStream, int camId, Date date, long dLat, long dLon, boolean isSent) {
        String folder = "image_" + serialNumber;
        final File dir = new File(mContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES), folder);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                return null;
            }
        }
        String nameImage;
        if (isSent) {
            nameImage = "imgSent_" + camId + "_" + dLat + "_" + dLon + "_" + date.getTime() + ".jpg";
        }
        else {
            nameImage = "img_" + camId + "_" + dLat + "_" + dLon + "_" + date.getTime() + ".jpg";
        }

        String path = dir.getPath() + File.separator + nameImage;
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(path);
            outputStream.writeTo(fos);
            fos.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            if (fos != null) {
                File checkFile = new File(path);
                if (checkFile.exists() && (checkFile.length() == outputStream.size())) {
                    return path;
                }
                else {
                    return null;
                }
            }
            else {
                return null;
            }
        }
    }

    public static ByteArrayOutputStream NV21toJPEG(byte[] nv21, int width, int height, int quality) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        yuv.compressToJpeg(new Rect(0, 0, width, height), quality, out);
        return out;
    }

    private String saveImage(ByteArrayOutputStream outputStream, int camId, Date date, long dLat, long dLon, boolean isSent) {
        if (pathStorage == null) {
            return null;
        }
        String folder = "image_" + serialNumber;
        final File dir = new File(pathStorage, folder);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                return null;
            }
        }
        String nameImage;
        if (isSent) {
            nameImage = "imgSent_" + camId + "_" + dLat + "_" + dLon + "_" + date.getTime() + ".jpg";
        }
        else {
            nameImage = "img_" + camId + "_" + dLat + "_" + dLon + "_" + date.getTime() + ".jpg";
        }
        String path = dir.getPath() + File.separator + nameImage;
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(path);
            outputStream.writeTo(fos);
            fos.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            if (fos != null) {
                File checkFile = new File(path);
                if (checkFile.exists() && (checkFile.length() == outputStream.size())) {
                    return path;
                }
                else {
                    return null;
                }
            }
            else {
                return null;
            }
        }
    }

    private String ham_10to64(int serial) {
        int tp = serial;
        char[] s64 = new char[10];
        int i = 0;
        if (tp == 0)
            s64[i++] = 64;
        else {
            while (tp > 0) {
                s64[i++] = (char) ((tp % 63) + 64);
                tp /= 63; //64;
            }
        }
        s64[i++] = 0;
        String ret = new String(s64, 0, i - 1);
        return ret;
    }

    private String getDateTime64(Date date) {
        char[] s_date = new char[10];
        s_date[0] = (char) (date.getSeconds() + 64);
        s_date[1] = (char) (date.getMinutes() + 64);
        s_date[2] = (char) (date.getHours() + 64);
        s_date[3] = (char) (date.getDate() + 64);
        s_date[4] = (char) (date.getMonth() + 64 + 1);
        s_date[5] = (char) (date.getYear() - 36); //(+1900-2000+64)
        s_date[6] = 0;                 //null
        String sDate = new String(s_date, 0, 6);
        return sDate;
    }

    private boolean http_post_image(String path) throws IOException {
        File file = new File(path);
        int file_size = (int) file.length();
        String serial64 = ham_10to64(ImageHttp.serialNumber);
        int indexName = path.lastIndexOf("img_");
        String imageName = path.substring(indexName);
        String sTime = imageName.substring(imageName.lastIndexOf('_') + 1, imageName.lastIndexOf('.'));
        int camId = imageName.charAt(imageName.indexOf('_') + 1) - 0x30;
        long time = Long.parseLong(sTime);
        Date date = new Date(time);

        String sDate = getDateTime64(date);
        String cookie = "ID=" + serial64 + ";" + sDate + ";" + camId + ";" + file_size + ";1;" + serial64;

        HttpURLConnection connection = null;
        DataOutputStream outputStream = null;
        DataInputStream inputStream = null;

        String pathToOurFile = path;
        String urlServer = RouterURL.getUrlDomainToSendImage() + serialNumber;
        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        int maxBufferSize = 1024 * 1024;

        FileInputStream fileInputStream = new FileInputStream(new File(pathToOurFile));
        bufferSize = fileInputStream.available();
        buffer = new byte[bufferSize];
        bytesRead = fileInputStream.read(buffer, 0, bufferSize);
        /******************************************************/
//        if (USE_ROUTER) {
        URL url = new URL(urlServer);
        connection = (HttpURLConnection) url.openConnection();
//        }
//        else {
//            int portProxy = DEV_TEST ? PORT_PROXY_DEV : PORT_PROXY;
//            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(HOST_PROXY, portProxy));
//            connection = (HttpURLConnection) new URL(urlServer).openConnection(proxy);
//        }
        /******************************************************/
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setUseCaches(false);

        connection.setRequestMethod("POST");

        connection.setRequestProperty("Connection", "Close");
        connection.setRequestProperty("Token", "adsun4gt2020");
        connection.setRequestProperty("ADSUNSN", serial64);
        connection.setRequestProperty("Cookie", cookie);
        connection.setRequestProperty("Content-Length", Integer.toString(file_size));
        connection.setRequestProperty("Content-Type", "application/x-binary");

        outputStream = new DataOutputStream(connection.getOutputStream());
        outputStream.write(buffer, 0, bufferSize);

        fileInputStream.close();
        outputStream.flush();
        outputStream.close();

        int serverResponseCode = connection.getResponseCode();
        BufferedReader br = null;
        if (100 <= serverResponseCode && serverResponseCode <= 399) {
            br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        }
        else {
            br = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
        }
        String body = br.readLine();
        Log.i(TAG, "http_post_image: " + path);
        String log = "Image Storage: Code " + serverResponseCode
                + "..Body: " + body + "..." + camId + "..." + date;
        Log.i(TAG, "http_post_image_" + log);
        imageSendCallBack.onLogResult(log);
        boolean result = false;
        if ((serverResponseCode == RES_CODE) && (body.equals(RES_BODY))) {
            result = true;
        }
        return result;
    }

    private boolean http_post_image(ByteArrayOutputStream stream, int camId, Date date) throws IOException {
        int file_size = stream.size();
        String serial64 = ham_10to64(serialNumber);
        String sDate = getDateTime64(date);
        String cookie = "ID=" + serial64 + ";" + sDate + ";" + camId + ";" + file_size + ";1;" + serial64;

        HttpURLConnection connection = null;
        DataOutputStream outputStream = null;
        DataInputStream inputStream = null;

        String urlServer = RouterURL.getUrlDomainToSendImage() + serialNumber;
        int bytesRead, bytesAvailable, bufferSize;

        int maxBufferSize = 1024 * 1024;

        byte[] buffer = stream.toByteArray();
        bufferSize = stream.size();
        /******************************************************/
//        if (USE_ROUTER) {
        URL url = new URL(urlServer);
        connection = (HttpURLConnection) url.openConnection();
//        }
//        else {
//            int portProxy = DEV_TEST ? PORT_PROXY_DEV : PORT_PROXY;
//            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(HOST_PROXY, portProxy));
//            connection = (HttpURLConnection) new URL(urlServer).openConnection(proxy);
//        }
        /******************************************************/
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setUseCaches(false);

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Connection", "Close");
        connection.setRequestProperty("Token", "adsun4gt2020");
        connection.setRequestProperty("ADSUNSN", serial64);
        connection.setRequestProperty("Cookie", cookie);
        connection.setRequestProperty("Content-Length", Integer.toString(file_size));
        connection.setRequestProperty("Content-Type", "application/x-binary");

        outputStream = new DataOutputStream(connection.getOutputStream());
        outputStream.write(buffer, 0, bufferSize);

        outputStream.flush();
        outputStream.close();
        outputStream = null;

        BufferedReader br = null;
        int serverResponseCode = connection.getResponseCode();
        if (100 <= serverResponseCode && serverResponseCode <= 399) {
            br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        }
        else {
            br = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
        }
        String body = br.readLine();

        String log = "Image Buffer: Code " + serverResponseCode
                + "..Body: " + body + "..." + camId + "..." + date;
        Log.i(TAG, "http_post_image_buffer: " + log);
        imageSendCallBack.onLogResult(log);

        boolean result = false;
        if ((serverResponseCode == RES_CODE) && (body.equals(RES_BODY))) {
            result = true;
        }
        return result;
    }

    public interface ImageSendCallBack {
        void onImageSendStorage(String path, boolean result);

        void onImageSendSuccess(int camId, Date date);

        void onImageSave(String path);

        void onImageCreateFail(int camId);

        void onLogResult(String log);
    }
}
