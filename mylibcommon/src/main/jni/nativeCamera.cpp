//
// Created by hotanha on 12/07/2021.
//
#include <jni.h>
#include <string>
#include "CameraDevice.h"

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
Java_com_example_mylibcommon_NativeCamera_nSetInfoLocation(JNIEnv *env, jobject thiz, jlong pointer,
														   jdouble lat, jdouble lon, jdouble speed
) {
	// TODO: implement nSetInfoLocation()
	auto *nCamera = reinterpret_cast<CameraDevice *>(pointer);
	nCamera->setInfoLocation(lat, lon, speed);
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
Java_com_example_mylibcommon_NativeCamera_nSetDriverInfo(JNIEnv *env, jobject thiz,
														 jlong pointer,
														 jstring bs,
														 jstring gplx) {
	// TODO: implement nSetDriverInfo()
	auto *nCamera = reinterpret_cast<CameraDevice *>(pointer);
	const char *sBienSo = env->GetStringUTFChars(bs, 0);
	const char *sGPLX = env->GetStringUTFChars(gplx, 0);

	nCamera->setDriverInfo((char *) sBienSo, (char *) sGPLX);

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