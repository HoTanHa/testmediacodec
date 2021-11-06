#include <jni.h>
#include <malloc.h>
#include "librtmp/rtmp.h"
#include <pthread.h>
#include <string.h>
#include <unistd.h>
//#include "flvmuxer/xiecc_rtmp.h"
#include <sys/prctl.h>
#include <android/log.h>
#include <stdlib.h>
#include <signal.h>
#include <stdbool.h>
#include <jni.h>

#define LOG_TAG_a "rtmp"

#define LOG(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG_a, __VA_ARGS__)


#define SIG_CANCEL_SIGNAL1 SIGUSR1
#define PTHREAD_CANCEL_ENABLE1 1
#define PTHREAD_CANCEL_DISABLE1 0


static int pthread_setcancelstate1(int state, int *oldstate) {
	sigset_t new, old;
	int ret;
	sigemptyset(&new);
	sigaddset(&new, SIG_CANCEL_SIGNAL1);

	ret = pthread_sigmask(state == PTHREAD_CANCEL_ENABLE1 ? SIG_BLOCK : SIG_UNBLOCK, &new, &old);
	if (oldstate != NULL) {
		*oldstate = sigismember(&old, SIG_CANCEL_SIGNAL1) == 0 ? PTHREAD_CANCEL_DISABLE1
															   : PTHREAD_CANCEL_ENABLE1;
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
	int frameCount;
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
			muxer->frameCount = 1;
		}
		else {
			free(muxer->head_buff);
			muxer->head_buff = NULL;
			muxer->frameCount = 0;
		}
	}
	else {
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
			muxer->frameCount++;
		}
		else {
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
	muxer->frameCount--;
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
	muxer->frameCount = 0;
	pthread_mutex_unlock(&(muxer->rtmp_mutex));
}

#define AAC_ADTS_HEADER_SIZE 7
#define FLV_TAG_HEAD_LEN 11
#define FLV_PRE_TAG_LEN 4

#define LOG_TAG "rtmp-muxer"

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

static const AVal av_onMetaData = AVC("onMetaData");
static const AVal av_duration = AVC("duration");
static const AVal av_width = AVC("width");
static const AVal av_height = AVC("height");
static const AVal av_videocodecid = AVC("videocodecid");
static const AVal av_avcprofile = AVC("avcprofile");
static const AVal av_avclevel = AVC("avclevel");
static const AVal av_videoframerate = AVC("videoframerate");
static const AVal av_audiocodecid = AVC("audiocodecid");
static const AVal av_audiosamplerate = AVC("audiosamplerate");
static const AVal av_audiochannels = AVC("audiochannels");
static const AVal av_avc1 = AVC("avc1");
static const AVal av_mp4a = AVC("mp4a");
static const AVal av_onPrivateData = AVC("onPrivateData");
static const AVal av_record = AVC("record");

#define RTMP_HEAD_SIZE (sizeof(RTMPPacket) + RTMP_MAX_HEADER_SIZE)
#define IDX_NAL_TYPE 4
#define RTMP_FRAME_SIZE_MAX 262144


int rtmp_open_for_write(RTMP *rtmp, char *url, uint32_t video_width, uint32_t video_height) {
	//    rtmp = RTMP_Alloc();
	if (rtmp == NULL) {
		return RTMP_ERROR_OPEN_ALLOC;
	}

	RTMP_Init(rtmp);
	RTMPResult ret = RTMP_SetupURL(rtmp, url);

	if (ret != RTMP_SUCCESS) {
		return ret;
	}

	RTMP_EnableWrite(rtmp);

	ret = RTMP_Connect(rtmp, NULL);
	if (ret != RTMP_SUCCESS) {
		return ret;
	}
	ret = RTMP_ConnectStream(rtmp, 0);

	if (ret != RTMP_SUCCESS) {
		return RTMP_ERROR_OPEN_CONNECT_STREAM;
	}


	if (RTMP_IsConnected(rtmp)) {

		uint32_t offset = 0;
		char buffer[512];
		char *output = buffer;
		char *outend = buffer + sizeof(buffer);
		char send_buffer[512];

		output = AMF_EncodeString(output, outend, &av_onMetaData);
		*output++ = AMF_ECMA_ARRAY;

		output = AMF_EncodeInt32(output, outend, 5);
		output = AMF_EncodeNamedNumber(output, outend, &av_width, video_width);
		output = AMF_EncodeNamedNumber(output, outend, &av_height, video_height);
		output = AMF_EncodeNamedNumber(output, outend, &av_duration, 0.0);
		output = AMF_EncodeNamedNumber(output, outend, &av_videocodecid, 7);
		output = AMF_EncodeNamedNumber(output, outend, &av_audiocodecid, 10);
		output = AMF_EncodeInt24(output, outend, AMF_OBJECT_END);

		int body_len = output - buffer;
		int output_len = body_len + FLV_TAG_HEAD_LEN + FLV_PRE_TAG_LEN;

		send_buffer[offset++] = 0x12;                      //tagtype scripte
		send_buffer[offset++] = (uint8_t)(body_len >> 16); //data len
		send_buffer[offset++] = (uint8_t)(body_len >> 8);  //data len
		send_buffer[offset++] = (uint8_t)(body_len);       //data len
		send_buffer[offset++] = 0;                         //time stamp
		send_buffer[offset++] = 0;                         //time stamp
		send_buffer[offset++] = 0;                         //time stamp
		send_buffer[offset++] = 0;                         //time stamp
		send_buffer[offset++] = 0x00;                      //stream id 0
		send_buffer[offset++] = 0x00;                      //stream id 0
		send_buffer[offset++] = 0x00;                      //stream id 0

		memcpy(send_buffer + offset, buffer, body_len);

		int nwrite = RTMP_Write(rtmp, send_buffer, output_len);
		if (nwrite > 0) {
			return RTMP_SUCCESS;
		}
	}
	return RTMP_ERROR_CONNECTION_LOST;
}

int rtmp_send_sps_pps(RTMP *rtmp, uint8_t *buffSPS, uint32_t lengthSPS, uint8_t *buffPPS,
					  uint32_t lengthPPS, int timeStamp) {
	RTMPPacket *packet = NULL;
	unsigned char *body;
	size_t size = RTMP_HEAD_SIZE + 1024;
	packet = (RTMPPacket *) malloc(size);
	if (packet == NULL) {
		LOGD("size malloc fail: size:%zu..SPS:%u..PPS:%u..%zu", size, lengthSPS, lengthPPS,
			 RTMP_HEAD_SIZE);
		return -1;
	}
	memset(packet, 0, RTMP_HEAD_SIZE);

	packet->m_body = (char *) packet + RTMP_HEAD_SIZE;
	body = (unsigned char *) packet->m_body;

	int i = 0;
	body[i++] = 0x17;
	body[i++] = 0x00;

	body[i++] = 0x00;
	body[i++] = 0x00;
	body[i++] = 0x00;

	/*AVCDecoderConfigurationRecord*/
	body[i++] = 0x01;
	body[i++] = buffSPS[1];
	body[i++] = buffSPS[2];
	body[i++] = buffSPS[3];
	body[i++] = 0xff;

	/*sps*/
	body[i++] = 0xe1;
	body[i++] = (lengthSPS >> 8) & 0xff;
	body[i++] = (lengthSPS) & 0xff;
	memcpy(&body[i], buffSPS, lengthSPS);
	i += lengthSPS;

	/*pps*/
	body[i++] = 0x01;
	body[i++] = (lengthPPS >> 8) & 0xff;
	body[i++] = (lengthPPS) & 0xff;
	memcpy(&body[i], buffPPS, lengthPPS);
	i += lengthPPS;

	packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
	packet->m_nBodySize = i;
	packet->m_nChannel = 0x04;
	packet->m_nTimeStamp = timeStamp;
	packet->m_hasAbsTimestamp = 0;
	packet->m_headerType = RTMP_PACKET_SIZE_MEDIUM;
	packet->m_nInfoField2 = rtmp->m_stream_id;

	/*Call Send Interface*/
	int res = RTMP_ERROR_SEND_PACKET_FAIL;
	res = RTMP_SendPacket(rtmp, packet, TRUE);
	free(packet);
	packet = NULL;
	return res;
}

int rtmp_send_stream_h264(RTMP *rtmp, uint8_t *bufferFrame_NAL, uint32_t n_NAL, int timeStamp) {

	if (n_NAL > RTMP_FRAME_SIZE_MAX) {
		return -1;
	}
	int type = bufferFrame_NAL[IDX_NAL_TYPE] & 0x1f;
	unsigned char *body;
	uint32_t len = n_NAL - IDX_NAL_TYPE;
	size_t size = (size_t)(RTMP_HEAD_SIZE + len + 9);
	RTMPPacket *packet = (RTMPPacket *) malloc(size);
	if (packet == NULL) {
		LOGD("size malloc fail: size:%zu..len:%u..%u..%zu", size, len, n_NAL, RTMP_HEAD_SIZE);
		return -1;
	}
	memset(packet, 0, RTMP_HEAD_SIZE);

	packet->m_body = (char *) packet + RTMP_HEAD_SIZE;
	packet->m_nBodySize = len + 9;

	/*send video packet*/
	body = (unsigned char *) packet->m_body;
	memset(body, 0, len + 9);

	/*key frame*/
	body[0] = 0x27;

	if (type == 0x05) { //NAL_SLICE_IDR
		body[0] = 0x17;
	}

	body[1] = 0x01; /*nal unit*/
	body[2] = 0x00;
	body[3] = 0x00;
	body[4] = 0x00;

	body[5] = (len >> 24) & 0xff;
	body[6] = (len >> 16) & 0xff;
	body[7] = (len >> 8) & 0xff;
	body[8] = (len) & 0xff;

	/*copy data*/
	memcpy(&body[9], &bufferFrame_NAL[IDX_NAL_TYPE], len);
	if (type == 0x05) {
		body[9] = 0x65;
	}
	else {
		body[9] = 0x61;
	}
	packet->m_hasAbsTimestamp = 0;
	packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
	packet->m_nInfoField2 = rtmp->m_stream_id;
	packet->m_nChannel = 0x04;
	packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
	packet->m_nTimeStamp = timeStamp;

	/*Call Send Interface*/
	int res = RTMP_ERROR_SEND_PACKET_FAIL;
	res = RTMP_SendPacket(rtmp, packet, TRUE);
	free(packet);
	packet = NULL;
	return res;
}

int rtmp_sender_write_video_frame(RTMP *rtmp, uint8_t *data, uint32_t total, int timeStamp, int key,
								  uint32_t abs_ts) {
	uint8_t *buf;
	uint8_t *buf_offset;
	uint32_t nal_len;
	uint32_t nal_len_n;
	uint8_t *nal;
	uint8_t *nal_n;
	int result = 0;

	buf = data;
	buf_offset = data;

	if (data[IDX_NAL_TYPE] == 0x67) {
		nal = data + 4;
		int i = 0;
		for (i = 4; i < (total - 5); i++) {
			if ((data[i] == 0x00) && (data[i + 1] == 0x00) && (data[i + 2] == 0x00) &&
				(data[i + 3] == 0x01)) {
				nal_len = i - 4;
				nal_n = data + i + 4;
				nal_len_n = total - i - 4;
				break;
			}
		}
		if (i >= (total - 5)) {
			LOGD("No Nal after SPS\n");
			return -1;
		}

		result = rtmp_send_sps_pps(rtmp, nal, nal_len, nal_n, nal_len_n, timeStamp);
	}
	else {
		result = rtmp_send_stream_h264(rtmp, data, total, timeStamp);
	}

	return result;
}


void thread_send_rtmp(void *arg) {
	pthread_setname_np(pthread_self(), "rtmpThread");

	int s32Ret = pthread_setcancelstate1(PTHREAD_CANCEL_ENABLE1, NULL);
	if (s32Ret != 0) {
		exit(EXIT_FAILURE);
	}
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
		}
		else {
			isNull = 0;
		}
		pthread_mutex_unlock(&(muxer->rtmp_mutex));
		if (isNull == 0) {
			time1 = time(NULL);
			if (muxer->rtmp != NULL) {
				int res = rtmp_sender_write_video_frame(muxer->rtmp, muxer->head_buff->buffer,
														muxer->head_buff->length,
														muxer->head_buff->timestamp, 0,
														0);
				time2 = time(NULL);
			}
//			LOG("size Frame: ...%ld..%ld...%d...%d", time1, time2, res, muxer->head_buff->length);
//			timeSend = time2 - time1;
//			if (timeSend > 2) {
//				free_queue(muxer);
//			}
//			else {
//				free_head_queue(muxer);
//			}

			free_head_queue(muxer);
		}
		usleep(2000);
	}
	if (muxer->rtmp != NULL) {
		RTMP_Close(muxer->rtmp);
	}
	pthread_exit(0);
}

/*************************************************
 *
 * ***********************************************/

jboolean
Java_com_htha_rtmpClient_RTMPMuxer_nIsConnected(JNIEnv *env, jobject thiz,
												jlong rtmp_pointer) {
	// TODO: implement nIsConnected()
	// RTMP *rtmp = (RTMP *)rtmp_pointer;
	// return rtmp_is_connected(rtmp) ? true : false;

	struct rtmpmuxer_t *muxer = (struct rtmpmuxer_t *) rtmp_pointer;
	if (muxer->rtmp != NULL) {
		return RTMP_IsConnected(muxer->rtmp);
	}
	return 0;
};

jint
Java_com_htha_rtmpClient_RTMPMuxer_nClose(JNIEnv *env, jobject thiz, jlong rtmp_pointer) {
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
	if (muxer != NULL && muxer->rtmp != NULL) {
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
Java_com_htha_rtmpClient_RTMPMuxer_nWriteAudio(JNIEnv *env, jobject thiz, jlong rtmp_pointer,
											   jbyteArray data_, jint offset, jint length,
											   jlong timestamp) {
	// TODO: implement nWriteAudio()
	struct rtmpmuxer_t *muxer = (struct rtmpmuxer_t *) rtmp_pointer;
	return 0;
};

jint
Java_com_htha_rtmpClient_RTMPMuxer_nWriteVideo(JNIEnv *env, jobject thiz, jlong rtmp_pointer,
											   jbyteArray data_, jint offset, jint length,
											   jlong timestamp) {
	// TODO: implement nWriteVideo()
	if (length > RTMP_FRAME_SIZE_MAX) {
		return 0;
	}
	struct rtmpmuxer_t *muxer = (struct rtmpmuxer_t *) rtmp_pointer;
	jbyte *data = (*env)->GetByteArrayElements(env, data_, NULL);

	jint result = 0;
	if (data != NULL) {
		add_buffer_to_queue(muxer, (uint8_t * ) & data[offset], length, timestamp);
		(*env)->ReleaseByteArrayElements(env, data_, data, 0);
	}

	return result;
}

jint
Java_com_htha_rtmpClient_RTMPMuxer_nOpen(JNIEnv *env, jobject thiz, jlong rtmp_pointer,
										 jstring url_, jint video_width, jint video_height) {
	// TODO: implement nOpen()
	struct rtmpmuxer_t *muxer = (struct rtmpmuxer_t *) rtmp_pointer;
	const char *url = (*env)->GetStringUTFChars(env, url_, NULL);
	int result = rtmp_open_for_write(muxer->rtmp, (char *) url, video_width, video_height);
	if (result != RTMP_SUCCESS) {
		if (muxer->rtmp != NULL) {
			RTMP_Free(muxer->rtmp);
			muxer->rtmp = NULL;
		}
	}
	pthread_create(&(muxer->tid), NULL, (void *) thread_send_rtmp, muxer);
	(*env)->ReleaseStringUTFChars(env, url_, url);
	return result;
}

jlong
Java_com_htha_rtmpClient_RTMPMuxer_nativeAlloc(JNIEnv *env, jobject thiz) {
	// TODO: implement nativeAlloc()
	struct rtmpmuxer_t *muxer = (struct rtmpmuxer_t *) malloc(sizeof(struct rtmpmuxer_t));
	muxer->rtmp = RTMP_Alloc();
	muxer->head_buff = NULL;
	pthread_mutex_init(&(muxer->rtmp_mutex), NULL);
	muxer->quit = 0;

	return (jlong) muxer;
}

jboolean
Java_com_htha_rtmpClient_RTMPMuxer_nCheckHanging(JNIEnv *env, jobject thiz, jlong rtmp_pointer) {
	// TODO: implement nCheckHanging()
	struct rtmpmuxer_t *muxer = (struct rtmpmuxer_t *) rtmp_pointer;
	if (muxer->frameCount > 50) {
		return true;
	}
	return false;
}