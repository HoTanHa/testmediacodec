#include <jni.h>
#include <string>
#include "aaaa.h"

#include <android/log.h>
#include <libgen.h>

#define LOG_TAG "MyLibNative"

#define LOGI(FMT, ...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "[%s %d]:" FMT,    \
                            basename(__FILE__), __LINE__, ## __VA_ARGS__)


extern "C" JNIEXPORT jstring JNICALL
Java_com_example_testcameramediacodec_MainActivity_stringFromJNI(
	JNIEnv *env,
	jobject /* this */) {
	std::string hello = "Hello from C++";
	return env->NewStringUTF(hello.c_str());
}


extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_testcameramediacodec_MainActivity_getbByteInfo(JNIEnv *env, jclass clazz) {
	// TODO: implement getbByteInfo()
	jbyteArray retVal = env->NewByteArray(1280 * 24);
//	char* buf = new char[1280*24];
//	env->GetByteArrayRegion (retVal, 0, 1280*24, reinterpret_cast<jbyte*>(buf));
	jbyte *buf = env->GetByteArrayElements(retVal, NULL);
//	strcpy(buf, src);

	std::string info = "adsun";
	create_info_in_image((char *) buf, 0x00, 10.012145f, 101.125478f, 12.0f, (char *) info.c_str());
	env->ReleaseByteArrayElements(retVal, buf, 0);
	return retVal;

}

#include <android/native_activity.h>
#include <android/native_window_jni.h>
#include <android/surface_control.h>
#include <android/native_window.h>
#include <android/hardware_buffer_jni.h>
#include <android/hardware_buffer.h>
#include <android/rect.h>
#include <cstring>
#include <cstdlib>

#define  PREVIEW_PIXEL_BYTES 2;

#define        LIKELY(x)                    ((__builtin_expect(!!(x), 1)))    // x is likely true
#define        UNLIKELY(x)                    ((__builtin_expect(!!(x), 0)))    // x is likely false


extern "C"
JNIEXPORT void JNICALL
Java_com_example_testcameramediacodec_MainActivity_drawDataToSurface(JNIEnv *env, jobject thiz,
																	 jbyteArray dataImage,
																	 jobject surface) {
	// TODO: implement drawDataToSurface()
	ANativeWindow *preview_window = surface ? ANativeWindow_fromSurface(env, surface) : NULL;
	jboolean isCopy = false;
	jbyte *data = env->GetByteArrayElements(dataImage, &isCopy);
	static int SIZE_IMAGE = 1280 * 720;
	static int SIZE1 = SIZE_IMAGE / 4;
	static int SIZE_BUFFER = SIZE_IMAGE * 3 / 2;
	static int count = 0;
	count++;
	ANativeWindow_Buffer buffer;
	ARect aRect;
//	if (ANativeWindow_lock(preview_window, &buffer, NULL) == 0) {
		if (ANativeWindow_lock(preview_window, &buffer, &aRect) == 0) {
		if (count == 200) {
			count = 0;
			LOGI("%d --- %d ---- %d ---- %d ...aRect: %d..%d..%d..%d", buffer.format, buffer.stride, buffer.width,
				 buffer.height, aRect.left, aRect.right, aRect.top, aRect.bottom);
		}
		uint8_t *src = (uint8_t *) data;
		auto *dest = (uint8_t *) buffer.bits;
		memcpy(dest, src, SIZE_IMAGE);
		auto *sUV = src + SIZE_IMAGE;
		auto *sU = src + SIZE_IMAGE;
		auto *sV = src + SIZE_IMAGE + SIZE1;
		auto *dCrCb = dest + (SIZE_IMAGE+ 1280*16);
		memcpy(dCrCb, sUV, SIZE1*2);
//		for (int i = 0; i < SIZE1; ++i) {
//			*dCrCb = *sU;
//			dCrCb++;
//			sU++;
//			*dCrCb = *sV;
//			dCrCb++;
//			sV++;
//		}
		ANativeWindow_unlockAndPost(preview_window);
	} else {

	}
	env->ReleaseByteArrayElements(dataImage, data, 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_testcameramediacodec_MainActivity_setSurfaceFormat(JNIEnv *env, jclass clazz,
																	jobject surface) {
	// TODO: implement setSurfaceFormat()

	ANativeWindow *preview_window = surface ? ANativeWindow_fromSurface(env, surface) : NULL;
//	ANativeWindow_setBuffersDataSpace()
	int32_t res = ANativeWindow_setBuffersGeometry(preview_window,
												   1280, 720,
												   AHARDWAREBUFFER_FORMAT_Y8Cb8Cr8_420);
	LOGI("result set Buffer Geometry: %d..", res);
}