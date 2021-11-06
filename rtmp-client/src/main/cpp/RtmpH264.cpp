//
// Created by htha on 03/11/2021.
//

#include "RtmpH264.h"

#define RTMP_HEAD_SIZE (sizeof(RTMPPacket) + RTMP_MAX_HEADER_SIZE)
#define IDX_NAL_TYPE 4


int rtmpH264_open_for_write(RTMP *rtmp, char *url, uint32_t video_width, uint32_t video_height);

int rtmpH264_send_sps_pps(RTMP *rtmp, uint8_t *buffSPS, uint32_t lengthSPS, uint8_t *buffPPS,
						  uint32_t lengthPPS, int timeStamp);

int rtmpH264_send_IP_frame(RTMP *rtmp, uint8_t *bufferFrame_NAL, uint32_t n_NAL, int timeStamp);

int rtmpH264_send_video_frame(RTMP *rtmp, uint8_t *data, uint32_t total, long timeStamp);

RtmpH264::RtmpH264() {
	isStreaming = false;
	bCheckStream = false;
}

RtmpH264::~RtmpH264() {
}

void RtmpH264::thread_send_rtmpH264(void *vptr_args) {
	auto *rtmpH264 = reinterpret_cast<RtmpH264 *>(vptr_args);

	pthread_setname_np(pthread_self(), "rtmpThread");

	rtmpH264->isStreaming = true;
	rtmpH264->bCheckStream = true;
	while (rtmpH264->isStreaming) {
		if (!rtmpH264->rtmp_buffer_queue.empty()) {
			BufferH264 *bufferH264 = rtmpH264->rtmp_buffer_queue.front();
			rtmpH264->rtmp_buffer_queue.pop();
			rtmpH264_send_video_frame(rtmpH264->rtmp, bufferH264->getBuffer(),
									  bufferH264->getLength(), bufferH264->getTimestamp());

			delete bufferH264;
		}
		std::this_thread::sleep_for(std::chrono::milliseconds(5));
	}
	rtmpH264->bCheckStream = false;

}

int RtmpH264::openConnectionRtmpH264(const char *url, int width, int height) {
	this->rtmp = RTMP_Alloc();
	if (rtmp == NULL) {
		return 1;
	}
	int res = rtmpH264_open_for_write(this->rtmp, (char *) url, width, height);
	if (res == RTMP_SUCCESS) {
		this->threadSendFrame = std::thread(thread_send_rtmpH264, (void *) this);
		this->threadSendFrame.detach();
	}
	else {
		RTMP_Free(this->rtmp);
		this->rtmp = NULL;
	}
	return res;
}

void RtmpH264::addFrame(uint8_t *buffer, int length, long timestamp) {
	auto *frame = new BufferH264(buffer, length, timestamp);
	this->rtmp_buffer_queue.push(frame);
}

void RtmpH264::closeRtmpH264() {
	this->isStreaming = false;
	int count = 0;
	while (bCheckStream) {
		count++;
		if (count >= 200) {
			if (!rtmp_buffer_queue.empty()) {
				rtmp_buffer_queue.pop();
			}
			break;
		}
		std::this_thread::sleep_for(std::chrono::milliseconds(10));
	}

	while (!rtmp_buffer_queue.empty()) {
		BufferH264 *bufferH264 = rtmp_buffer_queue.front();
		rtmp_buffer_queue.pop();
		if (bufferH264 != nullptr) {
			delete bufferH264;
		}
	}
}

bool RtmpH264::isConnection() {
	if (this->rtmp != NULL) {
		return RTMP_IsConnected(this->rtmp);
	}
	return false;
}

bool RtmpH264::checkHanging() {
	if (rtmp_buffer_queue.size() > 50) {
		this->isStreaming = false;
		return true;
	}
	return false;
}


int rtmpH264_open_for_write(RTMP *rtmp, char *url, uint32_t video_width, uint32_t video_height) {
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
//
//		uint32_t offset = 0;
//		char buffer[512];
//		char *output = buffer;
//		char *outend = buffer + sizeof(buffer);
//		char send_buffer[512];
//
//		output = AMF_EncodeString(output, outend, &av_onMetaData);
//		*output++ = AMF_ECMA_ARRAY;
//
//		output = AMF_EncodeInt32(output, outend, 5);
//		output = AMF_EncodeNamedNumber(output, outend, &av_width, video_width);
//		output = AMF_EncodeNamedNumber(output, outend, &av_height, video_height);
//		output = AMF_EncodeNamedNumber(output, outend, &av_duration, 0.0);
//		output = AMF_EncodeNamedNumber(output, outend, &av_videocodecid, 7);
//		output = AMF_EncodeNamedNumber(output, outend, &av_audiocodecid, 10);
//		output = AMF_EncodeInt24(output, outend, AMF_OBJECT_END);
//
//		int body_len = output - buffer;
//		int output_len = body_len + FLV_TAG_HEAD_LEN + FLV_PRE_TAG_LEN;
//
//		send_buffer[offset++] = 0x12;                      //tagtype scripte
//		send_buffer[offset++] = (uint8_t) (body_len >> 16); //data len
//		send_buffer[offset++] = (uint8_t) (body_len >> 8);  //data len
//		send_buffer[offset++] = (uint8_t) (body_len);       //data len
//		send_buffer[offset++] = 0;                         //time stamp
//		send_buffer[offset++] = 0;                         //time stamp
//		send_buffer[offset++] = 0;                         //time stamp
//		send_buffer[offset++] = 0;                         //time stamp
//		send_buffer[offset++] = 0x00;                      //stream id 0
//		send_buffer[offset++] = 0x00;                      //stream id 0
//		send_buffer[offset++] = 0x00;                      //stream id 0
//
//		memcpy(send_buffer + offset, buffer, body_len);
//
//		int nwrite = RTMP_Write(rtmp, send_buffer, output_len);
//		if (nwrite > 0) {
//			return RTMP_SUCCESS;
//		}
		return RTMP_SUCCESS;
	}
	return RTMP_ERROR_CONNECTION_LOST;
}

int rtmpH264_send_sps_pps(RTMP *rtmp, uint8_t *buffSPS, uint32_t lengthSPS, uint8_t *buffPPS,
						  uint32_t lengthPPS, int timeStamp) {
	RTMPPacket *packet = NULL;
	unsigned char *body;
	size_t size = RTMP_HEAD_SIZE + 1024;
	packet = (RTMPPacket *) malloc(size);
	if (packet == NULL) {
//		LOGD("size malloc fail: size:%zu..SPS:%u..PPS:%u..%zu", size, lengthSPS, lengthPPS,
//			 RTMP_HEAD_SIZE);
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

int rtmpH264_send_IP_frame(RTMP *rtmp, uint8_t *bufferFrame_NAL, uint32_t n_NAL, int timeStamp) {

	if (n_NAL > RTMP_FRAME_SIZE_MAX) {
		return -1;
	}
	int type = bufferFrame_NAL[IDX_NAL_TYPE] & 0x1f;
	unsigned char *body;
	uint32_t len = n_NAL - IDX_NAL_TYPE;
	size_t size = (size_t) (RTMP_HEAD_SIZE + len + 9);
	RTMPPacket *packet = (RTMPPacket *) malloc(size);
	if (packet == NULL) {
//		LOGD("size malloc fail: size:%zu..len:%u..%u..%zu", size, len, n_NAL, RTMP_HEAD_SIZE);
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

int rtmpH264_send_video_frame(RTMP *rtmp, uint8_t *data, uint32_t total, long timeStamp) {
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
//			LOGD("No Nal after SPS\n");
			return -1;
		}

		result = rtmpH264_send_sps_pps(rtmp, nal, nal_len, nal_n, nal_len_n, timeStamp);
	}
	else {
		result = rtmpH264_send_IP_frame(rtmp, data, total, timeStamp);
	}

	return result;
}

