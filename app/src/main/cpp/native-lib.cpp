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

uint8_t *buffer360[640 * 360 * 2];

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
	if (ANativeWindow_lock(preview_window, &buffer, NULL) == 0) {
		if (count == 200) {
			count = 0;
			LOGI("%d --- %d ---- %d ---- %d ...aRect: %d..%d..%d..%d", buffer.format, buffer.stride,
				 buffer.width,
				 buffer.height, aRect.left, aRect.right, aRect.top, aRect.bottom);
		}
		uint8_t *src = (uint8_t *) data;
//		auto *dest = (uint8_t *) buffer.bits;
//		memcpy(dest, src, SIZE_IMAGE);
//		auto *sUV = src + SIZE_IMAGE;
//		auto *dCrCb = dest + (SIZE_IMAGE + 1280 * 16);
//		memcpy(dCrCb, sUV, SIZE1 * 2);
//		ANativeWindow_unlockAndPost(preview_window);

		auto *sY0 = (uint8_t *) data;
		auto *sY1 = (uint8_t *) (data + 1280);
		auto *sUV0 = data + SIZE_IMAGE;
		auto *sUV1 = data + SIZE_IMAGE + 1280;
		auto *d360 = (uint8_t *) buffer360;
		auto *dUV = (uint8_t *) (buffer360 + 360 * 640);
		for (int i = 0; i < 12; ++i) {
			for (int j = 0; j < 640 / 2; ++j) {
				*d360 = (sY0[0] + sY0[1] + sY1[0] + sY1[0]) / 4;
				d360++;
				*d360 = (sY0[2] + sY0[3] + sY1[2] + sY1[3]) / 4;
				d360++;
				sY0 += 4;
				sY1 += 4;
				*(dUV++) = (sUV0[0]);// + sUV0[2] + sUV1[0] + sUV1[2]) / 4;
				*(dUV++) = (sUV0[1]);// + sUV0[3] + sUV1[1] + sUV1[3]) / 4;
				sUV0 += 4;
				sUV1 += 4;
			}
			sY0 += 1280;
			sY1 += 1280;
			sUV0 += 1280;
			sUV1 += 1280;
		}
		for (int i = 12; i < 360; ++i) {
			for (int j = 0; j < 640 / 2; ++j) {
				*d360 = *sY0;
				d360++;
				sY0 += 2;
				*d360 = *sY0;
				d360++;
				sY0 += 2;
				*(dUV++) = (sUV0[0]); //+ sUV0[2] + sUV1[0] + sUV1[2]) / 4;
				*(dUV++) = (sUV0[1]);// + sUV0[3] + sUV1[1] + sUV1[3]) / 4;
				sUV0 += 4;
				sUV1 += 4;
			}
			sY0 += 1280;
			sUV0 += 1280;
			sUV1 += 1280;
		}

		auto *dest = (uint8_t *) buffer.bits;
		memcpy(dest, buffer360, 640 * 360);
		auto *sUV360 = buffer360 + 360 * 640;
		auto *dCrCb = dest + (640 * (360 + 24));
		memcpy(dCrCb, sUV360, 640 * 360 / 2);
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
//	int32_t res = ANativeWindow_setBuffersGeometry(preview_window,
//												   1280, 720,
//												   AHARDWAREBUFFER_FORMAT_Y8Cb8Cr8_420);
	int32_t res = ANativeWindow_setBuffersGeometry(preview_window,
												   640, 360,
												   AHARDWAREBUFFER_FORMAT_Y8Cb8Cr8_420);
	LOGI("result set Buffer Geometry: %d..", res);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_testcameramediacodec_MainActivity_changeByteArray(JNIEnv *env, jobject thiz,
																   jbyteArray array) {
	// TODO: implement changeByteArray()
	jbyte *byteArray = env->GetByteArrayElements(array, NULL);
	for (int i = 0; i < 10; i++) {
		byteArray[i] = i;
	}
	env->ReleaseByteArrayElements(array, byteArray, 0);
}

#include <camera/NdkCameraManager.h>
extern "C"
JNIEXPORT void JNICALL
Java_com_example_testcameramediacodec_MainActivity_nCheckCamera(JNIEnv *env, jobject thiz) {
	// TODO: implement nCheckCamera()
	ACameraManager *camManager = ACameraManager_create();
	ACameraIdList *cameraIds = nullptr;
	ACameraManager_getCameraIdList(camManager, &cameraIds);

	LOGI("Num of camera get by nkd: %d", cameraIds->numCameras);
//	for (int i = 0; i < cameraIds->numCameras; ++i)
//	{
//		const char* id = cameraIds->cameraIds[i];
//		ACameraMetadata* metadataObj;
//		ACameraManager_getCameraCharacteristics(camManager, id, &metadataObj);
//
////		LOGI()
//		ACameraMetadata_free(metadataObj);
//
//	}
	ACameraManager_deleteCameraIdList(cameraIds);

	ACameraManager_delete(camManager);
}