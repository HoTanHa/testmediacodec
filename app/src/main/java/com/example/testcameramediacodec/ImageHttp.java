package com.example.testcameramediacodec;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.ImageReader;
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
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Date;

public class ImageHttp {
    private static final String TAG = "ImageHttp";
    private static int serialNumber = 0;
    private static final boolean USE_ROUTER = false;
    private static final String URL_ROUTER_1 = "http://route1.adsun.vn/DeviceHttp/Camera?Serial=";
    private static final String URL_ROUTER_2 = "http://route2.adsun.pro.vn/DeviceHttp/Camera?Serial=";
    private static final String URL_ROUTER_3 = "http://route3.adsun.net.vn/DeviceHttp/Camera?Serial=";

    private static final String URL_DOMAIN_1 = "http://camera1.adsun.vn/DeviceHttp/Camera?Serial=";
    private static final String URL_DOMAIN_2 = "http://camera2.adsun.net.vn/DeviceHttp/Camera?Serial=";
    private static final String URL_DOMAIN_3 = "http://camera3.adsun.pro.vn/DeviceHttp/Camera?Serial=";
    private static final String HOST_PROXY = "livedev.adsun.vn";
    private static final int PORT_PROXY = 8090;
//    private static final String URL_DOMAIN_1 = "http://namaroute.adsun.vn/DeviceHttp/Camera?Serial=";
//    private static final String URL_DOMAIN_2 = "http://namaroute.adsun.vn/DeviceHttp/Camera?Serial=";
//    private static final String URL_DOMAIN_3 = "http://namaroute.adsun.vn/DeviceHttp/Camera?Serial=";
//    private static final String HOST_PROXY = "livedev.adsun.vn";
//    private static final int PORT_PROXY = 8091;

    private static final int RES_CODE = 200;
    private static final String RES_BODY = "OK!";
    private String pathStorage;
    private ImageSendCallBack imageSendCallBack;
    private static int countFail = 0;

    private static final int QUALITY_JPEG  = 70;


    public ImageHttp(int serialNumber, String path) {
        this.serialNumber = serialNumber;
        this.pathStorage = path;
    }

    public void setImageSendCallBack(ImageSendCallBack callBack) {
        this.imageSendCallBack = callBack;
    }

    private void setCountFail(boolean result) {
        if (result) {
            int tmp = countFail;
            countFail = tmp - (tmp % 10);
        }
        else {
            countFail++;
            if (countFail >= 30) {
                countFail = 0;
            }
        }
    }

    private String getUrlDomainToSend() {
        if (countFail < 10) {
            return USE_ROUTER ? URL_ROUTER_1 : URL_DOMAIN_1;
        }
        if (countFail < 20) {
            return USE_ROUTER ? URL_ROUTER_2 : URL_DOMAIN_2;
        }
        return USE_ROUTER ? URL_ROUTER_3 : URL_DOMAIN_3;

    }

    public void send(String path) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean result = false;
                try {
                    result = http_post_image(path);
                }
                catch (IOException e) {
//                    e.printStackTrace();
                }
                finally {
                    setCountFail(result);
                    imageSendCallBack.onImageSendStorage(path, result);
                }
            }
        }).start();
    }

    public void send(ByteBuffer frame, int pixelFormat, int camId, Date date, boolean send) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ByteBuffer readOnlyCopy = frame.duplicate();
                int camIdReal = camId + 1;
                ByteArrayOutputStream imageOS = null;
                if (pixelFormat == ImageFormat.YUV_422_888) {
                    imageOS = yuvToJpeg(camIdReal, readOnlyCopy, 1280, 720, QUALITY_JPEG);
                }
                else if (pixelFormat == ImageFormat.NV21) {
                    imageOS = yuvNV21ToJpeg(readOnlyCopy, 1280, 720, QUALITY_JPEG);
                }
//                else if (pixelFormat == UVCCamera.PIXEL_FORMAT_RGBX) {
//                    imageOS = rgb888ToJpeg(readOnlyCopy, 1280, 720, QUALITY_JPEG);
//                }
//                else if (pixelFormat == UVCCamera.PIXEL_FORMAT_RGB565) {
//                    imageOS = rgb565ToJpeg(readOnlyCopy, 1280, 720, QUALITY_JPEG);
//                }

                if ((imageOS == null)) {// || (imageOS.size() > (150 * 1024))) {
                    Log.d(TAG, "run: Create Image Fail..!!!!.." + camId);
                    imageSendCallBack.onImageCreateFail(camId);
                    return;
                }
                boolean result = false;
                if (send) {
                    try {
                        result = http_post_image(imageOS, camIdReal, date);
                    }
                    catch (IOException e) {
//                        e.printStackTrace();
                    }
                    finally {
                        setCountFail(result);
                        if (result) {
                            imageSendCallBack.onImageSendSuccess(camIdReal, date);
                        }
                        else {
                            saveImage(imageOS, camIdReal, date);
                        }
                    }
                }
                else {
                    saveImage(imageOS, camIdReal, date);
                }
            }
        }).start();
    }

    private ByteArrayOutputStream rgb565ToJpeg(ByteBuffer frame, int mWidth, int mHeight, int quality) {
        Bitmap bitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.RGB_565);
        bitmap.copyPixelsFromBuffer(frame);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
            return null;
        }
        return out;
    }

    private ByteArrayOutputStream rgb888ToJpeg(ByteBuffer frame, int mWidth, int mHeight, int quality) {
        IntBuffer intBuff = frame.asIntBuffer();//.order(ByteOrder.BIG_ENDIAN).asIntBuffer();
//        IntBuffer intBuff = frame.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        int[] array = new int[intBuff.remaining()];
        intBuff.get(array);
        int tem = 0;
        for (int i = 0; i < array.length; i++) {
            tem = array[i];
            array[i] = ((tem & 0xFFFFFF00) >> 8) + ((tem & 0x000000FF) << 24);
        }
        Bitmap bitmapImage = Bitmap.createBitmap(array, mWidth, mHeight, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!bitmapImage.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
            Log.d("output", "problem converting yuv to jpg");
            return null;
        }
        return out;
    }

    private void saveImage(ByteArrayOutputStream outputStream, int camId, Date date) {
        if (pathStorage == null) {
            imageSendCallBack.onImageCreateFail(camId - 1);
            return;
        }
        String folder = "image_" + serialNumber;
        final File dir = new File(pathStorage, folder);
        Log.d(TAG, "path=" + dir.toString());
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.d(TAG, "saveImage: Cannot create folder image!!");
                imageSendCallBack.onImageCreateFail(camId - 1);
                return;
            }
        }
        String nameImage = "img_" + camId + "_" + date.getTime() + ".jpg";
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
                Log.i(TAG, "saveImage: " + path);
                imageSendCallBack.onImageSave(path);
            }
            else {
                imageSendCallBack.onImageCreateFail(camId - 1);
            }
        }
    }

    private ByteArrayOutputStream yuvToJpeg(int camIdReal, ByteBuffer frame, int width, int height, int quality) {
        byte[] data = new byte[frame.remaining()];

        frame.get(data);
        int pixel_black = 0;
        int step1row = 1280 * 2;
        int step2row = step1row * 2; //step 2 row
        for (int ii = 0; ii < 24; ii++) {
            pixel_black += step1row;
            if (data[pixel_black - 2] != 0x00) {
                return null;
            }
        }
        byte color = (byte) (0xFF - (byte) camIdReal);
        int pix_check = 24 * step1row;
        for (int iii = 24; iii < 720; iii++) {
            if (data[pix_check] != color) {
                return null;
            }
            pix_check += step1row;
        }

        YuvImage yuvimage = new YuvImage(data, ImageFormat.YUY2, width, height, null);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        boolean res = yuvimage.compressToJpeg(new Rect(0, 0, width, height), quality, outputStream);
        if (res) {
//            ByteArrayOutputStream outputStream1 = new ByteArrayOutputStream();
//            YuvImage yuvImage1 = new YuvImage(data, ImageFormat.YUY2, width, height, null);
//            boolean res2 = yuvImage1.compressToJpeg(new Rect(0, 0, width, height), quality, outputStream1);
//            if (res2) {
//                if (outputStream.size() > outputStream1.size()) {
//                    return outputStream1;
//                } else {
//                    return outputStream;
//                }
//            } else {
//                return null; //outputStream;
//            }
            return outputStream;
        }
        else {
            return null;
        }
    }

    private ByteArrayOutputStream yuvNV21ToJpeg(ByteBuffer frame, int width, int height, int quality) {
        byte[] data = new byte[frame.remaining()];

        frame.get(data);
        int pixel_black = 0;
        for (int ii = 0; ii < 24; ii++) {
            pixel_black += 1280;
            if (data[pixel_black - 1] != 0x00) {
                return null;
            }
        }
        YuvImage yuvimage = new YuvImage(data, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream outputStream = null;
        boolean res = false;
        try {
            outputStream = new ByteArrayOutputStream();
            res = yuvimage.compressToJpeg(new Rect(0, 0, width, height), quality, outputStream);
        }
        finally {
            if (res && outputStream.size() > 0) {
                return outputStream;
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
        String serial64 = ham_10to64(serialNumber);
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
        String urlServer = getUrlDomainToSend() + serialNumber;
        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        int maxBufferSize = 1024 * 1024;

        FileInputStream fileInputStream = new FileInputStream(new File(pathToOurFile));
        bufferSize = fileInputStream.available();
        buffer = new byte[bufferSize];
        bytesRead = fileInputStream.read(buffer, 0, bufferSize);
        /******************************************************/
        if (USE_ROUTER) {
            URL url = new URL(urlServer);
            connection = (HttpURLConnection) url.openConnection();
        }
        else {
            /*****************************************************/
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(HOST_PROXY, PORT_PROXY));
            connection = (HttpURLConnection) new URL(urlServer).openConnection(proxy);
        }
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
        Log.i(TAG, "http_post_image: Response code " + serverResponseCode
                + "..Body: " + body
                + "..." + camId + "..." + date);
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

        String urlServer = getUrlDomainToSend() + serialNumber;
        int bytesRead, bytesAvailable, bufferSize;

        int maxBufferSize = 1024 * 1024;

        byte[] buffer = stream.toByteArray();
        bufferSize = stream.size();
        /******************************************************/
        if (USE_ROUTER) {
            URL url = new URL(urlServer);
            connection = (HttpURLConnection) url.openConnection();
        }
        else {
            /*****************************************************/
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(HOST_PROXY, PORT_PROXY));
            connection = (HttpURLConnection) new URL(urlServer).openConnection(proxy);
        }
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
        Log.i(TAG, "http_post_image_buffer: Response code " + serverResponseCode
                + "..Body: " + body
                + "..." + camId + "..." + date);
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
    }
}
