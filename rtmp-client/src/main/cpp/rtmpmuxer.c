#include <jni.h>
#include <malloc.h>
#include "librtmp/rtmp.h"
#include <pthread.h>
#include <string.h>
#include <unistd.h>
#include "flvmuxer/xiecc_rtmp.h"
#include <sys/prctl.h>
#include <android/log.h>
#include <stdlib.h>
#include <signal.h>

#define LOG_TAG_a "rtmp"

#define LOG(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG_a, __VA_ARGS__)


#define SIG_CANCEL_SIGNAL1 SIGUSR1
#define PTHREAD_CANCEL_ENABLE1 1
#define PTHREAD_CANCEL_DISABLE1 0


static int pthread_setcancelstate1(int state, int *oldstate) {
	sigset_t   new, old;
	int ret;
	sigemptyset (&new);
	sigaddset (&new, SIG_CANCEL_SIGNAL1);

	ret = pthread_sigmask(state == PTHREAD_CANCEL_ENABLE1 ? SIG_BLOCK : SIG_UNBLOCK, &new , &old);
	if(oldstate != NULL)
	{
		*oldstate =sigismember(&old,SIG_CANCEL_SIGNAL1) == 0 ? PTHREAD_CANCEL_DISABLE1 : PTHREAD_CANCEL_ENABLE1;
	}
	return ret;
}

static inline int pthread_cancel1(pthread_t thread) {
	return pthread_kill(thread, SIG_CANCEL_SIGNAL1);
}

struct rtmp_buffer_queue {
	uint8_t *buffer;
	uint32_t length;
	int timestamp;
	struct rtmp_buffer_queue *next;
};

struct rtmpmuxer_t {
	RTMP *rtmp;
	struct rtmp_buffer_queue *head_buff;
	pthread_mutex_t rtmp_mutex;
	pthread_t tid;
	int quit;
};

void add_buffer_to_queue(struct rtmpmuxer_t *muxer, uint8_t *buffer, int length, long timestamp) {
	pthread_mutex_lock(&(muxer->rtmp_mutex));
	if (muxer->head_buff == NULL) {
		muxer->head_buff = (struct rtmp_buffer_queue *) malloc(sizeof(struct rtmp_buffer_queue));
		muxer->head_buff->buffer = (uint8_t *) malloc(length);
		if (muxer->head_buff->buffer != NULL) {
			memcpy(muxer->head_buff->buffer, buffer, length);
			muxer->head_buff->length = (uint32_t) length;
			muxer->head_buff->timestamp = (int) timestamp;
			muxer->head_buff->next = NULL;
		} else {
			free(muxer->head_buff);
			muxer->head_buff = NULL;
		}
	} else {
		struct rtmp_buffer_queue *current = muxer->head_buff;
		while (current->next != NULL) {
			current = current->next;
		}
		current->next = (struct rtmp_buffer_queue *) malloc(sizeof(struct rtmp_buffer_queue));
		current->next->buffer = (uint8_t *) malloc(length);
		if (current->next->buffer != NULL) {
			memcpy(current->next->buffer, buffer, length);
			current->next->length = (uint32_t) length;
			current->next->timestamp = (int) timestamp;
			current->next->next = NULL;
		} else {
			free(current->next);
			current->next = NULL;
		}
	}
	pthread_mutex_unlock(&(muxer->rtmp_mutex));
}

void free_head_queue(struct rtmpmuxer_t *muxer) {
	if (muxer->head_buff == NULL) {
		return;
	}
	pthread_mutex_lock(&(muxer->rtmp_mutex));
	struct rtmp_buffer_queue *current = muxer->head_buff;
	muxer->head_buff = current->next;
	free(current->buffer);
	free(current);
	pthread_mutex_unlock(&(muxer->rtmp_mutex));
}

void free_queue(struct rtmpmuxer_t *muxer) {

	pthread_mutex_lock(&(muxer->rtmp_mutex));
	struct rtmp_buffer_queue *current = muxer->head_buff;
	struct rtmp_buffer_queue *temp;
	while (current != NULL) {
		temp = current;
		current = current->next;
		free(temp->buffer);
		free(temp);
	}
	muxer->head_buff = NULL;
	pthread_mutex_unlock(&(muxer->rtmp_mutex));
}

void thread_send_rtmp(void *arg) {
	pthread_setname_np(pthread_self(), "rtmpThread");

	int s32Ret = pthread_setcancelstate1(PTHREAD_CANCEL_ENABLE1, NULL);
	if (s32Ret != 0)
	{
		exit(EXIT_FAILURE);
	}
//	s32Ret = pthread_setcanceltype(PTHREAD_CANCEL_DEFERRED, NULL);
//	if (s32Ret != 0)
//	{
//		exit(EXIT_FAILURE);
//	}
	struct rtmpmuxer_t *muxer = (struct rtmpmuxer_t *) arg;
	struct rtmp_buffer_queue *queue;
	int isNull;
	time_t time1, time2, timeSend;
	while (1) {
		if (muxer->quit) {
			break;
		}
		pthread_mutex_lock(&(muxer->rtmp_mutex));
		if (muxer->head_buff == NULL) {
			isNull = 1;
		} else {
			isNull = 0;
		}
		pthread_mutex_unlock(&(muxer->rtmp_mutex));
		if (isNull == 0) {
			time1 = time(NULL);
			int res = rtmp_sender_write_video_frame(muxer->rtmp, muxer->head_buff->buffer,
										  muxer->head_buff->length, muxer->head_buff->timestamp, 0,
										  0);
			time2 = time(NULL);
//			LOG("size Frame: ...%ld..%ld...%d...%d", time1, time2, res, muxer->head_buff->length);
			timeSend = time2 - time1;
			if (timeSend > 2) {
				free_queue(muxer);
			} else {
				free_head_queue(muxer);
			}
		}
		usleep(2000);
	}
	RTMP_Close(muxer->rtmp);
	pthread_exit(0);
}

/*************************************************
 *
 * ***********************************************/

jboolean
Java_com_example_rtmpClient_RTMPMuxer_nIsConnected(JNIEnv *env, jobject thiz,
													 jlong rtmp_pointer) {
	// TODO: implement nIsConnected()
	// RTMP *rtmp = (RTMP *)rtmp_pointer;
	// return rtmp_is_connected(rtmp) ? true : false;

	struct rtmpmuxer_t *muxer = (struct rtmpmuxer_t *) rtmp_pointer;
	return rtmp_is_connected(muxer->rtmp) ? true : false;
};

jint
Java_com_example_rtmpClient_RTMPMuxer_nClose(JNIEnv *env, jobject thiz, jlong rtmp_pointer) {
	// TODO: implement nClose()
	// RTMP *rtmp = (RTMP *)rtmp_pointer;
	// rtmp_close(rtmp);
	// return 0;

	struct rtmpmuxer_t *muxer = (struct rtmpmuxer_t *) rtmp_pointer;
	muxer->quit = 1;
	usleep(10000);
	pthread_cancel1(muxer->tid);
	usleep(1000);
	pthread_join(muxer->tid, NULL);
//	rtmp_close(muxer->rtmp);
	if (muxer->rtmp) {
		RTMP_Free(muxer->rtmp);
		muxer->rtmp = NULL;
	}
	free_queue(muxer);
	pthread_mutex_destroy(&(muxer->rtmp_mutex));
	free(muxer);
	muxer = NULL;
	return 0;
}

jint
Java_com_example_rtmpClient_RTMPMuxer_nWriteAudio(JNIEnv *env, jobject thiz, jlong rtmp_pointer,
													jbyteArray data_, jint offset, jint length,
													jlong timestamp) {
	// TODO: implement nWriteAudio()
//	RTMP *rtmp = (RTMP *)rtmp_pointer;
//	jbyte *data = (*env)->GetByteArrayElements(env, data_, NULL);
//
//	jint result = 0; // rtmp_sender_write_audio_frame(rtmp, &data[offset], length, timestamp, 0);
//
//	(*env)->ReleaseByteArrayElements(env, data_, data, JNI_ABORT);
//	return result;

	struct rtmpmuxer_t *muxer = (struct rtmpmuxer_t *) rtmp_pointer;
	return 0;
};

jint
Java_com_example_rtmpClient_RTMPMuxer_nWriteVideo(JNIEnv *env, jobject thiz, jlong rtmp_pointer,
													jbyteArray data_, jint offset, jint length,
													jlong timestamp) {
	// TODO: implement nWriteVideo()

	// RTMP *rtmp = (RTMP *)rtmp_pointer;
	// jbyte *data = (*env)->GetByteArrayElements(env, data_, NULL);

	// jint result = rtmp_sender_write_video_frame(rtmp, &data[offset], length, timestamp, 0, 0);

	// (*env)->ReleaseByteArrayElements(env, data_, data, JNI_ABORT);

	// return result;

	if (length > RTMP_FRAME_SIZE_MAX) {
		return 0;
	}
	struct rtmpmuxer_t *muxer = (struct rtmpmuxer_t *) rtmp_pointer;
	jbyte *data = (*env)->GetByteArrayElements(env, data_, NULL);

	jint result = 0;
	if (data != NULL) {
		add_buffer_to_queue(muxer, (uint8_t *) &data[offset], length, timestamp);
		(*env)->ReleaseByteArrayElements(env, data_, data, JNI_ABORT);
	}

	return result;
}

jint
Java_com_example_rtmpClient_RTMPMuxer_nOpen(JNIEnv *env, jobject thiz, jlong rtmp_pointer,
											  jstring url_, jint video_width, jint video_height) {
	// TODO: implement nOpen()
	// RTMP *rtmp = (RTMP *)rtmp_pointer;
	// const char *url = (*env)->GetStringUTFChars(env, url_, NULL);
	// int result = rtmp_open_for_write(rtmp, url, video_width, video_height);

	// (*env)->ReleaseStringUTFChars(env, url_, url);
	// return result;


	struct rtmpmuxer_t *muxer = (struct rtmpmuxer_t *) rtmp_pointer;
	const char *url = (*env)->GetStringUTFChars(env, url_, NULL);
	int result = rtmp_open_for_write(muxer->rtmp, (char *) url, video_width, video_height);
	pthread_create(&(muxer->tid), NULL, (void *) thread_send_rtmp, muxer);
	(*env)->ReleaseStringUTFChars(env, url_, url);
	return result;
}

jlong
Java_com_example_rtmpClient_RTMPMuxer_nativeAlloc(JNIEnv *env, jobject thiz) {
	// TODO: implement nativeAlloc()

	// RTMP *rtmp = RTMP_Alloc();
	// return (jlong)rtmp;

	struct rtmpmuxer_t *muxer = (struct rtmpmuxer_t *) malloc(sizeof(struct rtmpmuxer_t));
	muxer->rtmp = RTMP_Alloc();
	muxer->head_buff = NULL;
	pthread_mutex_init(&(muxer->rtmp_mutex), NULL);
	muxer->quit = 0;

	return (jlong) muxer;
}
