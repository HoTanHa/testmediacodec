//
// Created by htha on 03/11/2021.
//

#ifndef TESTCAMERAMEDIACODEC_RTMPH264_H
#define TESTCAMERAMEDIACODEC_RTMPH264_H

#include <mutex>
#include <thread>
#include <list>
#include <queue>
#include <cstring>
#include "librtmp/rtmp.h"


#define RTMP_FRAME_SIZE_MAX 262144*2

class BufferH264 {
private:
	uint8_t *buffer;
	uint32_t length;
	long timestamp;
public:
	BufferH264(uint8_t *buff, uint32_t length, long timestamp_t) {
		this->buffer = new uint8_t[length];
		if (this->buffer != nullptr) {
			memcpy(this->buffer, buff, length);
			this->length = length;
			this->timestamp = timestamp_t;
		}
	}

	~BufferH264() {
		if (buffer != nullptr) {
			delete[] buffer;
			buffer = nullptr;
		}
	}

	uint8_t *getBuffer() {
		return this->buffer;
	}

	uint32_t getLength() {
		return this->length;
	}

	int getTimestamp() {
		return this->timestamp;
	}
};

class RtmpH264 {

private:
	RTMP *rtmp;
	std::queue<BufferH264 *> rtmp_buffer_queue;
	std::thread threadSendFrame;
	volatile bool isStreaming;
	volatile bool bCheckStream;

	static void thread_send_rtmpH264(void *vptr_args);

public:
	RtmpH264();

	~RtmpH264();

	int openConnectionRtmpH264(const char *url, int width, int height);

	void addFrame(uint8_t *buffer, int length, long timestamp);

	void closeRtmpH264();

	bool isConnection();

	bool checkHanging();
};


#endif //TESTCAMERAMEDIACODEC_RTMPH264_H
