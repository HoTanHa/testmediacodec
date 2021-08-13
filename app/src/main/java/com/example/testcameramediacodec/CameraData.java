package com.example.testcameramediacodec;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;

public class CameraData {
    private static final String TAG = "CameraData";
    private CameraDevice cameraDevice;
    private String sCameraId;
    private Context context;
    private ImageReader imageReader_Raw;
    private final int cWidth = 1280;
    private final int cHeight = 720;
    private CameraCaptureSession cameraCaptureSession;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private CameraDataCallback callback;
    public CameraData(@NonNull Context ctx, @NonNull final String sCamId, CameraDataCallback mCallback) {
        this.context = ctx;
        this.sCameraId = sCamId;
        this.callback = mCallback;
    }

    private final CameraDevice.StateCallback cameraDeviceCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "onOpened: Camera device on opened:.." + camera.getId());
            CameraData.this.cameraDevice = camera;
            createCameraCaptureSession();
            callback.cameraOpened(sCameraId);
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.d(TAG, "onDisconnected: Camera device on Disconnected:.." + camera.getId());
            camera.close();
            CameraData.this.cameraDevice = null;
            callback.cameraDisconnected(sCameraId);
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            CameraData.this.cameraDevice = null;
            callback.cameraError(sCameraId);
        }
    };

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            if (image!=null) {
                callback.onRawImage(sCameraId, image);
                image.close();
            }
        }
    };

    private synchronized void createCameraCaptureSession() {
        try {
            imageReader_Raw = ImageReader.newInstance(cWidth, cHeight, ImageFormat.YUV_420_888, 2);
            imageReader_Raw.setOnImageAvailableListener(mOnImageAvailableListener, backgroundHandler);
            CaptureRequest.Builder captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequest.addTarget(imageReader_Raw.getSurface());
            // Set new fsp to camera
            Range<Integer> fpsRange = new Range<>(10, 15);
            captureRequest.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
            // Create ArrayList Surface
            ArrayList<Surface> listSurface = new ArrayList<>();
            listSurface.add(imageReader_Raw.getSurface());
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
            cameraManager.openCamera(sCameraId, cameraDeviceCallback, backgroundHandler);
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
        if (imageReader_Raw != null) {
            imageReader_Raw.close();
            imageReader_Raw = null;
        }
    }


    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBgr-" + sCameraId);
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
            Log.e(TAG, "stopBackgroundThread: " + e.getMessage());
        }
    }

    public interface CameraDataCallback {
        void cameraOpened(String sCamId);

        void cameraError(String sCamId);

        void cameraDisconnected(String sCamId);

        void onRawImage(String sCamId, Image image);

    }
}
