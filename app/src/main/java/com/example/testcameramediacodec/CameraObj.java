package com.example.testcameramediacodec;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Camera;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.view.Surface;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Size;
import androidx.core.app.ActivityCompat;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Semaphore;

public class CameraObj {
    private static final String TAG = "CameraObj";

    private CameraDevice cameraDevice;
    private String cameraId;
    private Context context;
    private Surface mediaCodecSurface;
    private ImageReader imageReader_t;
    private ImageReader imageReader_Raw;

    private final int cWidth = 1280;
    private final int cHeight = 720;
    private CameraCaptureSession cameraCaptureSession;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private String imageFolder;
    private CameraObjCallback callback;
    byte[] byteImage = new byte[1280 * 720 * 3 / 2];

    public void setCameraObjCallback(CameraObjCallback callback) {
        this.callback = callback;
    }

    public CameraObj(@NonNull Context ctx,
                     @NonNull final String cameraId,
                     @NonNull Surface mediaCodecSurface,
                     @NonNull String folder) {
        this.context = ctx;
        this.cameraId = cameraId;
        this.mediaCodecSurface = mediaCodecSurface;
        this.imageFolder = folder;
    }

    private CameraDevice.StateCallback cameraDeviceCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "onOpened: Camera device on opened ");
            CameraObj.this.cameraDevice = camera;
            createCameraCaptureSession();
            callback.cameraOpened();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            CameraObj.this.cameraDevice = null;
            callback.cameraDisconnected();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            CameraObj.this.cameraDevice = null;
            callback.cameraError();
        }
    };
    private int count = 0;
    ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            //                    Image img = null;
//                    img = reader.acquireLatestImage();
//                    Result rawResult = null;
//                    try {
//                        if (img == null) throw new NullPointerException("cannot be null");
//                        ByteBuffer buffer = img.getPlanes()[0].getBuffer();
//                        byte[] data = new byte[buffer.remaining()];
//                        buffer.get(data);
//
//                        Bitmap b = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
//
//                    } catch (ReaderException ignored) {
//                    } catch (NullPointerException ex) {
//                    } finally {
//                        mQrReader.reset();
//                        if (img != null)
//                            img.close();
//                    }
            Image image = reader.acquireNextImage();
//            byte[] iBuffer = ImageUtil.getBytesFromImageAsType(image, ImageUtil.YUV420P);
            byte[] iBuffer = ImageUtil.imageGetByteYUV_420_888(image, byteImage);
            byte[] infoBuffer = MainActivity.getbByteInfo();
            System.arraycopy(infoBuffer, 0, iBuffer, 0, infoBuffer.length);

            callback.onDataCamera(iBuffer);

            if (count % 500 == 100) {
                Log.d(TAG, "onImageAvailable: " + iBuffer.length + "...." + infoBuffer.length);
                new Thread(() -> {
//                        byte[] iNV21Buffer = new byte[1280*720*3/2];
//                        iNV21Buffer = ImageUtil.YUV420PtoNV21(iBuffer, iNV21Buffer, 1280,720);
                    byte[] iNV21Buffer = new byte[iBuffer.length];
                    iNV21Buffer = ImageUtil.YUV420SPtoNV21(iBuffer, iNV21Buffer, 1280, 720);
                    YuvImage yuvimage = new YuvImage(iNV21Buffer, ImageFormat.NV21, 1280, 720, null);
                    ByteArrayOutputStream outputStream = null;
                    boolean res = false;
                    try {
                        outputStream = new ByteArrayOutputStream();
                        res = yuvimage.compressToJpeg(new Rect(0, 0, 1280, 720), 70, outputStream);
                        if (res) {
                            res = http_post_image(outputStream, 0, new Date());
                        }
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
            }
            count++;
            image.close();
        }
    };

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

    private boolean http_post_image(ByteArrayOutputStream stream, int camId, Date date) throws IOException {
        int file_size = stream.size();
        int serialNumber = 990909097;
        String serial64 = ham_10to64(serialNumber);
        String sDate = getDateTime64(date);
        String cookie = "ID=" + serial64 + ";" + sDate + ";" + camId + ";" + file_size + ";1;" + serial64;

        HttpURLConnection connection = null;
        DataOutputStream outputStream = null;
        DataInputStream inputStream = null;

        String urlServer = "http://camera1.adsun.vn/DeviceHttp/Camera?Serial=" + serialNumber;
        int bytesRead, bytesAvailable, bufferSize;

        int maxBufferSize = 1024 * 1024;

        byte[] buffer = stream.toByteArray();
        bufferSize = stream.size();
        /******************************************************/
//        if (USE_ROUTER) {
//            URL url = new URL(urlServer);
//            connection = (HttpURLConnection) url.openConnection();
//        }
//        else {
        /*****************************************************/
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("livedev.adsun.vn", 8090));
        connection = (HttpURLConnection) new URL(urlServer).openConnection(proxy);
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
        Log.i(TAG, "http_post_image_buffer: Response code " + serverResponseCode
                + "..Body: " + body
                + "..." + camId + "..." + date + "..." + bufferSize);
        boolean result = false;
        if ((serverResponseCode == 200) && (body.equals("OK!"))) {
            result = true;
        }
        return result;
    }

    ImageHttp.ImageSendCallBack imageSendCallBack = new ImageHttp.ImageSendCallBack() {
        @Override
        public void onImageSendStorage(String path, boolean result) {

        }

        @Override
        public void onImageSendSuccess(int camId, Date date) {

        }

        @Override
        public void onImageSave(String path) {

        }

        @Override
        public void onImageCreateFail(int camId) {

        }
    };

    private synchronized void createCameraCaptureSession() {
        try {
            imageReader_t = ImageReader.newInstance(cWidth, cHeight, ImageFormat.JPEG, 2);
            imageReader_Raw = ImageReader.newInstance(cWidth, cHeight, ImageFormat.YUV_420_888, 2);
            imageReader_Raw.setOnImageAvailableListener(mOnImageAvailableListener, backgroundHandler);

            CaptureRequest.Builder captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
//            captureRequest.addTarget(mediaCodecSurface);
            captureRequest.addTarget(imageReader_Raw.getSurface());
            // Set new fsp to camera
            Range<Integer> fpsRange = new Range<>(10, 15);
            captureRequest.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
            // Create ArrayList Surface
            ArrayList<Surface> listSurface = new ArrayList<>();
            listSurface.add(imageReader_Raw.getSurface());
//            listSurface.add(mediaCodecSurface);
            cameraDevice.createCaptureSession(listSurface, new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            if (cameraDevice == null) {
                                return;
                            }
                            try {
                                captureRequest.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                cameraCaptureSession = session;
                                cameraCaptureSession.setRepeatingRequest(captureRequest.build(), null, backgroundHandler);
                                cameraCaptureSession.capture(captureRequest.build(), null, backgroundHandler);
                            }
                            catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.e(TAG, "onConfigureFailed: Create camera session fail");
                        }
                    },
                    backgroundHandler);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void openCamera() {
        if (cameraDevice != null) return;

        CameraManager cameraManager = (CameraManager) this.context.getSystemService(Context.CAMERA_SERVICE);
        if (ActivityCompat.checkSelfPermission(this.context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            startBackgroundThread();
            cameraManager.openCamera(cameraId, cameraDeviceCallback, backgroundHandler);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void closeCamera() {
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        stopBackgroundThread();
    }


    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground" + cameraId);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void takePhoto() {
        if (cameraDevice == null) {
            return;
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
//        File file = new File(context.getExternalFilesDir(null), "IMG_" + timeStamp + ".jpg");
        File file = new File(imageFolder, "IMG_" + cameraId + "_" + timeStamp + ".jpg");

        try {

            CaptureRequest.Builder captureBuilder = cameraCaptureSession.getDevice().createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader_t.getSurface());
            cameraCaptureSession.capture(captureBuilder.build(), null, backgroundHandler);

            imageReader_t.setOnImageAvailableListener(reader -> {
                Log.d(TAG, "ImageAvailable " + file.getAbsolutePath());
                backgroundHandler.post(new ImageSaver(reader.acquireNextImage(), file, callback));
            }, backgroundHandler);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private static class ImageSaver implements Runnable {
        private final Image mImage;
        private final File mFile;
        private final CameraObjCallback callback;

        public ImageSaver(Image image, File file, CameraObjCallback cb) {
            mImage = image;
            mFile = file;
            callback = cb;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                        callback.onPhotoComplete(mFile.getAbsolutePath());
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public interface CameraObjCallback {
        void cameraOpened();

        void cameraError();

        void cameraDisconnected();

        void onPhotoComplete(String path);

        void onDataCamera(byte[] data);
    }
}
