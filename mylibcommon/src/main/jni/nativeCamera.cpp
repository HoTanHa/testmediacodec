//
// Created by hotanha on 12/07/2021.
//
#include <jni.h>
#include <string>
#include "CameraDevice.h"
#include <camera/NdkCameraManager.h>
#include "myJniDefine.h"

extern "C"
JNIEXPORT void JNICALL
Java_com_example_mylibcommon_NativeCamera_nCheckCameraList(JNIEnv *env, jclass clazz) {
	// TODO: implement nCheckCameraList()
	ACameraManager *camManager = ACameraManager_create();
	ACameraIdList *cameraIds = nullptr;
	ACameraManager_getCameraIdList(camManager, &cameraIds);

	logi("Num of camera get by nkd: %d", cameraIds->numCameras);
//	for (int i = 0; i < cameraIds->numCameras; ++i)
//	{
//		const char* id = cameraIds->cameraIds[i];
//		ACameraMetadata* metadataObj;
//		ACameraManager_getCameraCharacteristics(camManager, id, &metadataObj);
//
//		ACameraMetadata_free(metadataObj);
//
//	}
	ACameraManager_deleteCameraIdList(cameraIds);

	ACameraManager_delete(camManager);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_mylibcommon_NativeCamera_nativeAllocAndInit(JNIEnv *env, jobject thiz) {
	// TODO: implement nativeAllocAndInit()
	auto *nCamera = new CameraDevice();
	return (jlong) nCamera;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_mylibcommon_NativeCamera_nClose(JNIEnv *env, jobject thiz, jlong pointer) {
	// TODO: implement nClose()
	auto *nCamera = reinterpret_cast<CameraDevice *>(pointer);
	delete nCamera;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_mylibcommon_NativeCamera_nSetCamId(JNIEnv *env, jobject thiz, jlong pointer,
													jint cam_id) {
	// TODO: implement nSetCamId()
	auto *nCamera = reinterpret_cast<CameraDevice *>(pointer);
	nCamera->setCamId(cam_id);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_mylibcommon_NativeCamera_nSetInfoLocation(JNIEnv *env, jclass clazz,
														   jdouble lat, jdouble lon, jdouble speed
) {
	// TODO: implement nSetInfoLocation()
	CameraDevice::setInfoLocation(lat, lon, speed);
}

extern "C"

JNIEXPORT void JNICALL
Java_com_example_mylibcommon_NativeCamera_nDrawBufferInfoToImage(JNIEnv *env, jobject thiz,
																 jlong pointer,
																 jbyteArray image_buffer) {
	// TODO: implement nDrawBufferInfoToImage()
	auto *nCamera = reinterpret_cast<CameraDevice *>(pointer);
	jbyte *rawImage = env->GetByteArrayElements(image_buffer, NULL);

	nCamera->drawBufferToMainWindow((uint8_t *) rawImage);

	env->ReleaseByteArrayElements(image_buffer, rawImage, 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_mylibcommon_NativeCamera_nSetDriverInfo(JNIEnv *env, jclass clazz,
														 jstring bs,
														 jstring gplx) {
	// TODO: implement nSetDriverInfo()
	const char *sBienSo = env->GetStringUTFChars(bs, 0);
	const char *sGPLX = env->GetStringUTFChars(gplx, 0);

	CameraDevice::setDriverInfo((char *) sBienSo, (char *) sGPLX);

	env->ReleaseStringUTFChars(bs, sBienSo);
	env->ReleaseStringUTFChars(gplx, sGPLX);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_mylibcommon_NativeCamera_nSetMainSurface(JNIEnv *env, jobject thiz, jlong pointer,
														  jobject surface) {
	// TODO: implement nSetMainSurface()
	auto *nCamera = reinterpret_cast<CameraDevice *>(pointer);
	ANativeWindow *window = surface ? ANativeWindow_fromSurface(env, surface) : NULL;
	nCamera->setMainWindow(window);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_mylibcommon_NativeCamera_nSetStreamSurface(JNIEnv *env, jobject thiz,
															jlong pointer, jobject surface) {
	// TODO: implement nSetStreamSurface()
	auto *nCamera = reinterpret_cast<CameraDevice *>(pointer);
	ANativeWindow *window = surface ? ANativeWindow_fromSurface(env, surface) : NULL;
	nCamera->setStreamWindow(window);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_mylibcommon_NativeCamera_nCloseStream(JNIEnv *env, jobject thiz, jlong pointer) {
	// TODO: implement nCloseStream()
	auto *nCamera = reinterpret_cast<CameraDevice *>(pointer);
	nCamera->stopStreamWindow();
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_mylibcommon_NativeCamera_getTimeS(JNIEnv *env, jobject thiz, jlong pointer) {
	// TODO: implement getTimeS()

	auto *nCamera = reinterpret_cast<CameraDevice *>(pointer);
	return nCamera->getTimeS();
}