#include <jni.h>

#include <android/log.h>

#define LOG_TAG_a "rtmp"

#define LOG(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG_a, __VA_ARGS__)

#include "RtmpH264.h"

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_htha_rtmpClient_RTMPMuxer_nIsConnected(JNIEnv *env, jobject thiz,
												jlong rtmp_pointer) {
	// TODO: implement nIsConnected()

	auto *rtmpH264 = reinterpret_cast<RtmpH264 *>(rtmp_pointer);
	bool res = rtmpH264->isConnection();
	return res;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_htha_rtmpClient_RTMPMuxer_nClose(JNIEnv *env, jobject thiz, jlong rtmp_pointer) {
	// TODO: implement nClose()

	auto *rtmpH264 = reinterpret_cast<RtmpH264 *>(rtmp_pointer);
	rtmpH264->closeRtmpH264();
	delete rtmpH264;
	return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_htha_rtmpClient_RTMPMuxer_nWriteAudio(JNIEnv *env, jobject thiz, jlong rtmp_pointer,
											   jbyteArray data_, jint offset, jint length,
											   jlong timestamp) {
	// TODO: implement nWriteAudio()
	return 0;
};

extern "C"
JNIEXPORT jint JNICALL
Java_com_htha_rtmpClient_RTMPMuxer_nWriteVideo(JNIEnv *env, jobject thiz, jlong rtmp_pointer,
											   jbyteArray data_, jint offset, jint length,
											   jlong timestamp) {
	// TODO: implement nWriteVideo()

	auto *rtmpH264 = reinterpret_cast<RtmpH264 *>(rtmp_pointer);
	if (length > RTMP_FRAME_SIZE_MAX) {
		return 0;
	}
	jbyte *data = env->GetByteArrayElements(data_, NULL);

	if (data != NULL) {
		rtmpH264->addFrame(reinterpret_cast<uint8_t *>(data), length, timestamp);
		env->ReleaseByteArrayElements(data_, data, 0);
	}

	return 0;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_htha_rtmpClient_RTMPMuxer_nCheckHanging(JNIEnv *env, jobject thiz, jlong rtmp_pointer) {
	// TODO: implement nCheckHanging()
	auto *rtmpH264 = reinterpret_cast<RtmpH264 *>(rtmp_pointer);
	return rtmpH264->checkHanging();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_htha_rtmpClient_RTMPMuxer_nOpen(JNIEnv *env, jobject thiz, jlong rtmp_pointer,
										 jstring url_, jint video_width, jint video_height) {
	// TODO: implement nOpen()

	auto *rtmpH264 = reinterpret_cast<RtmpH264 *>(rtmp_pointer);
	const char *url = env->GetStringUTFChars(url_, NULL);
	int result = rtmpH264->openConnectionRtmpH264(url, video_width, video_height);
	env->ReleaseStringUTFChars(url_, url);
	return result;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_htha_rtmpClient_RTMPMuxer_nativeAlloc(JNIEnv *env, jobject thiz) {
	// TODO: implement nativeAlloc()

	auto *rtmpH264 = new RtmpH264();
	return (jlong) rtmpH264;
}
