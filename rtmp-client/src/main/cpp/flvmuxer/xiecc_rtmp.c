#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include "../librtmp/rtmp.h"
#include "../librtmp/log.h"
#include "xiecc_rtmp.h"
#include <android/log.h>
#include <string.h>

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

static FILE *g_file_handle = NULL;
static uint64_t g_time_begin;

bool audio_config_ok = false;

void flv_file_open(const char *filename) {
	if (NULL == filename) {
		return;
	}

	g_file_handle = fopen(filename, "wb");

	return;
}

void flv_file_close() {
	if (g_file_handle) {
		fclose(g_file_handle);
	}
}

void write_flv_header(bool is_have_audio, bool is_have_video) {
	char flv_file_header[] = "FLV\x1\x5\0\0\0\x9\0\0\0\0"; // have audio and have video

	if (is_have_audio && is_have_video) {
		flv_file_header[4] = 0x05;
	} else if (is_have_audio && !is_have_video) {
		flv_file_header[4] = 0x04;
	} else if (!is_have_audio && is_have_video) {
		flv_file_header[4] = 0x01;
	} else {
		flv_file_header[4] = 0x00;
	}

	fwrite(flv_file_header, 13, 1, g_file_handle);

	return;
}

static uint8_t gen_audio_tag_header() {
	/*

	UB [4] Format of SoundData. The following values are defined:
	0 = Linear PCM, platform endian
	1 = ADPCM
	2 = MP3
	3 = Linear PCM, little endian
	4 = Nellymoser 16 kHz mono
	5 = Nellymoser 8 kHz mono
	6 = Nellymoser
	7 = G.711 A-law logarithmic PCM
	8 = G.711 mu-law logarithmic PCM
	9 = reserved
	10 = AAC *****************
	11 = Speex
	14 = MP3 8 kHz
	15 = Device-specific sound

   SoundRate UB [2] Sampling rate. The following values are defined:
	0 = 5.5 kHz
	1 = 11 kHz
	2 = 22 kHz
	3 = 44 kHz  ************* specification says mark it always 44khz

	SoundSize UB [1]

	to 16 bits internally.
	0 = 8-bit samples
	1 = 16-bit samples *************

	SoundType UB [1] Mono or stereo sound
	0 = Mono sound
	1 = Stereo sound ***********  specification says: even if sound is not stereo, mark as stereo


	*/
	uint8_t soundType = 1; // should be always 1 - stereo --- config.channel_configuration - 1; //0 mono, 1 stero
	uint8_t soundRate = 3; //44Khz it should be always 44Khx
	uint8_t val = 0;

	/*
	switch (config.sample_frequency_index) {
		case 10: { //11.025k
			soundRate = 1;
			break;
		}
		case 7: { //22k
			soundRate = 2;
			break;
		}
		case 4: { //44k
			soundRate = 3;
			break;
		}
		default:
		{
			return val;
		}
	}
	*/
	// 0xA0 means this is AAC
	//soundrate << 2  44khz
	// 0x02 means there are 16 bit samples
	val = 0xA0 | (soundRate << 2) | 0x02 | soundType;
	return val;
}

int rtmp_open_for_write(RTMP *rtmp, char *url, uint32_t video_width, uint32_t video_height) {
	//    rtmp = RTMP_Alloc();
	if (rtmp == NULL) {
		return RTMP_ERROR_OPEN_ALLOC;
	}

	RTMP_Init(rtmp);
	RTMPResult ret = RTMP_SetupURL(rtmp, url);

	if (ret != RTMP_SUCCESS) {
		RTMP_Free(rtmp);
		return ret;
	}

	RTMP_EnableWrite(rtmp);

	ret = RTMP_Connect(rtmp, NULL);
	if (ret != RTMP_SUCCESS) {
		RTMP_Free(rtmp);
		return ret;
	}
	ret = RTMP_ConnectStream(rtmp, 0);

	if (ret != RTMP_SUCCESS) {
		return RTMP_ERROR_OPEN_CONNECT_STREAM;
	}

	audio_config_ok = false;

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
		send_buffer[offset++] = (uint8_t) (body_len >> 16); //data len
		send_buffer[offset++] = (uint8_t) (body_len >> 8);  //data len
		send_buffer[offset++] = (uint8_t) (body_len);       //data len
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

int rtmp_close(RTMP *rtmp) {
	if (rtmp) {
		RTMP_Close(rtmp);
		RTMP_Free(rtmp);
		rtmp = NULL;
	}
	return 0;
}

int rtmp_is_connected(RTMP *rtmp) {
	if (rtmp) {
		if (RTMP_IsConnected(rtmp)) {
			return 1;
		}
	}
	return 0;
}

/*****************************************************
* @brief send audio frame
* @param [in] data       : AACAUDIODATA
* @param [in] size       : AACAUDIODATA size
* @param [in] dts_us     : decode timestamp of frame
* @param [in] abs_ts     : indicate whether you'd like to use absolute time stamp
*************************************************/
int rtmp_sender_write_audio_frame(RTMP *rtmp, uint8_t *data,
								  int size,
								  uint64_t dts_us,
								  uint32_t abs_ts) {

	int val = RTMP_SUCCESS;
	uint32_t audio_ts = (uint32_t) dts_us;
	uint32_t offset;
	uint32_t body_len;
	uint32_t output_len;
	char *output;

	//Audio OUTPUT
	offset = 0;

	if (audio_config_ok == false) {
		// first packet is two bytes AudioSpecificConfig

		//rtmp_xiecc->config = gen_config(audio_frame);
		body_len = 2 + 2; //AudioTagHeader + AudioSpecificConfig
		output_len = body_len + FLV_TAG_HEAD_LEN + FLV_PRE_TAG_LEN;
		output = malloc(output_len);
		// flv tag header
		output[offset++] = 0x08;                      //tagtype audio
		output[offset++] = (uint8_t) (body_len >> 16); //data len
		output[offset++] = (uint8_t) (body_len >> 8);  //data len
		output[offset++] = (uint8_t) (body_len);       //data len
		output[offset++] = (uint8_t) (audio_ts >> 16); //time stamp
		output[offset++] = (uint8_t) (audio_ts >> 8);  //time stamp
		output[offset++] = (uint8_t) (audio_ts);       //time stamp
		output[offset++] = (uint8_t) (audio_ts >> 24); //time stamp
		output[offset++] = abs_ts;                    //stream id 0
		output[offset++] = 0x00;                      //stream id 0
		output[offset++] = 0x00;                      //stream id 0

		//flv AudioTagHeader
		output[offset++] = gen_audio_tag_header(); // sound format aac
		output[offset++] = 0x00;                   //aac sequence header

		//flv VideoTagBody --AudioSpecificConfig
		//    uint8_t audio_object_type = rtmp_xiecc->config.audio_object_type;
		output[offset++] = data[0]; //(audio_object_type << 3)|(rtmp_xiecc->config.sample_frequency_index >> 1);
		output[offset++] = data[1]; //((rtmp_xiecc->config.sample_frequency_index & 0x01) << 7) \
                           //| (rtmp_xiecc->config.channel_configuration << 3) ;
		//no need to set pre_tag_size

		uint32_t fff = body_len + FLV_TAG_HEAD_LEN;
		output[offset++] = (uint8_t) (fff >> 24); //data len
		output[offset++] = (uint8_t) (fff >> 16); //data len
		output[offset++] = (uint8_t) (fff >> 8);  //data len
		output[offset++] = (uint8_t) (fff);       //data len

		if (g_file_handle) {
			fwrite(output, output_len, 1, g_file_handle);
		}
		val = RTMP_Write(rtmp, output, output_len);
		free(output);
		//rtmp_xiecc->audio_config_ok = 1;
		audio_config_ok = true;
	} else {

		body_len = 2 +
				   size; //aac header + raw data size // adts_len - AAC_ADTS_HEADER_SIZE; // audito tag header + adts_len - remove adts header + AudioTagHeader
		output_len = body_len + FLV_TAG_HEAD_LEN + FLV_PRE_TAG_LEN;
		output = malloc(output_len);
		// flv tag header
		output[offset++] = 0x08;                      //tagtype audio
		output[offset++] = (uint8_t) (body_len >> 16); //data len
		output[offset++] = (uint8_t) (body_len >> 8);  //data len
		output[offset++] = (uint8_t) (body_len);       //data len
		output[offset++] = (uint8_t) (audio_ts >> 16); //time stamp
		output[offset++] = (uint8_t) (audio_ts >> 8);  //time stamp
		output[offset++] = (uint8_t) (audio_ts);       //time stamp
		output[offset++] = (uint8_t) (audio_ts >> 24); //time stamp
		output[offset++] = abs_ts;                    //stream id 0
		output[offset++] = 0x00;                      //stream id 0
		output[offset++] = 0x00;                      //stream id 0

		//flv AudioTagHeader
		output[offset++] = gen_audio_tag_header(); // sound format aac
		output[offset++] = 0x01;                   //aac raw data

		//flv VideoTagBody --raw aac data
		memcpy(output + offset, data, size); // data + AAC_ADTS_HEADER_SIZE -> data,
		// (adts_len - AAC_ADTS_HEADER_SIZE) -> size

		//previous tag size
		uint32_t fff = body_len + FLV_TAG_HEAD_LEN;
		offset += size;                          // (adts_len - AAC_ADTS_HEADER_SIZE);
		output[offset++] = (uint8_t) (fff >> 24); //data len
		output[offset++] = (uint8_t) (fff >> 16); //data len
		output[offset++] = (uint8_t) (fff >> 8);  //data len
		output[offset++] = (uint8_t) (fff);       //data len

		if (g_file_handle) {
			fwrite(output, output_len, 1, g_file_handle);
		}
		val = RTMP_Write(rtmp, output, output_len);
		free(output);
	}
	return val;
}

static uint32_t find_start_code(uint8_t *buf, uint32_t zeros_in_startcode) {
	uint32_t info;
	uint32_t i;

	info = 1;
	if ((info = (buf[zeros_in_startcode] != 1) ? 0 : 1) == 0)
		return 0;

	for (i = 0; i < zeros_in_startcode; i++) {
		if (buf[i] != 0) {
			info = 0;
			break;
		}
	}

	return info;
}

/*********************************************************
 * len parameter will be filled the length of the nal unit
 * total: total size of the packet
 * return nal unit start byte or NULL if there is no nal unit
 **********************************************************/
static uint8_t *get_nal(uint32_t *len, uint8_t **offset, uint8_t *start, uint32_t total) {
	uint32_t info;
	uint8_t *q;
	uint8_t *p = *offset;
	*len = 0;

	while (1) {
		//p=offset
		// p - start >= total means reach of the end of the packet
		// HINT "-3": Do not access not allowed memory
		if ((p - start) >= total - 3)
			return NULL;

		info = find_start_code(p, 3);
		//if info equals to 1, it means it find the start code
		if (info == 1)
			break;
		p++;
	}
	q = p + 4; // add 4 for first bytes 0 0 0 1
	p = q;
	// find a second start code in the data, there may be second code in data or there may not
	while (1) {
		// HINT "-3": Do not access not allowed memory
		if ((p - start) >= total - 3) {
			p = start + total;
			break;
		}

		info = find_start_code(p, 3);

		if (info == 1)
			break;
		p++;
	}

	// length of the nal unit
	*len = (p - q);
	//offset is the second nal unit start or the end of the data
	*offset = p;
	//return the first nal unit pointer
	return q;
}

int send_key_frame(RTMP *rtmp, int nal_len, uint32_t ts, uint32_t abs_ts, uint8_t *nal) {
	int offset = 0;
	int body_len = nal_len + 5 + 4; //flv VideoTagHeader +  NALU length
	int output_len = body_len + FLV_TAG_HEAD_LEN + FLV_PRE_TAG_LEN;
	char *output = malloc(output_len);
	if (!output) {
		LOGD("Memory is not allocated...");
		return 0;
	}
	// flv tag header
	output[offset++] = 0x09;                      //tagtype video
	output[offset++] = (uint8_t) (body_len >> 16); //data len
	output[offset++] = (uint8_t) (body_len >> 8);  //data len
	output[offset++] = (uint8_t) (body_len);       //data len
	output[offset++] = (uint8_t) (ts >> 16);       //time stamp
	output[offset++] = (uint8_t) (ts >> 8);        //time stamp
	output[offset++] = (uint8_t) (ts);             //time stamp
	output[offset++] = (uint8_t) (ts >> 24);       //time stamp
	output[offset++] = abs_ts;                    //stream id 0
	output[offset++] = 0x00;                      //stream id 0
	output[offset++] = 0x00;                      //stream id 0

	//flv VideoTagHeader
	output[offset++] = 0x17; //key frame, AVC
	output[offset++] = 0x01; //avc NALU unit
	output[offset++] = 0x00; //composit time ??????????
	output[offset++] = 0x00; // composit time
	output[offset++] = 0x00; //composit time

	output[offset++] = (uint8_t) (nal_len >> 24); //nal length
	output[offset++] = (uint8_t) (nal_len >> 16); //nal length
	output[offset++] = (uint8_t) (nal_len >> 8);  //nal length
	output[offset++] = (uint8_t) (nal_len);       //nal length
	memcpy(output + offset, nal, nal_len);

	//no need set pre_tag_size ,RTMP NO NEED

	offset += nal_len;
	uint32_t fff = body_len + FLV_TAG_HEAD_LEN;
	output[offset++] = (uint8_t) (fff >> 24); //data len
	output[offset++] = (uint8_t) (fff >> 16); //data len
	output[offset++] = (uint8_t) (fff >> 8);  //data len
	output[offset++] = (uint8_t) (fff);       //data len

	if (g_file_handle) {
		fwrite(output, output_len, 1, g_file_handle);
	}
	int val = RTMP_Write(rtmp, output, output_len);
	//RTMP Send out
	free(output);
	return val;
}

/*******************************************************************
* @brief send video frame, now only H264 supported
* @param [in] rtmp_sender handler
* @param [in] size       : video data size
* @param [in] dts_us     : decode timestamp of frame
* @param [in] key        : key frame indicate, [0: non key] [1: key]
* @param [in] abs_ts     : indicate whether you'd like to use absolute time stamp
*****************************************************************/
int rtmp_sender_write_video_frame_a(RTMP *rtmp,
									uint8_t *data,
									int total,
									uint64_t dts_us,
									int key,
									uint32_t abs_ts) {
	uint8_t *buf;
	uint8_t *buf_offset;
	int val = 0;
	//int total;
	uint32_t ts;
	uint32_t nal_len;
	uint32_t nal_len_n;
	uint8_t *nal;
	uint8_t *nal_n;
	char *output;
	uint32_t offset = 0;
	uint32_t body_len;
	uint32_t output_len;

	buf = data;
	buf_offset = data;
	//total = size;
	ts = (uint32_t) dts_us;

	//ts = RTMP_GetTime() - start_time;
	offset = 0;

	nal = get_nal(&nal_len, &buf_offset, buf, total);

	if (nal == NULL) {
		return -1;
	}
	while (nal != NULL) {

		if (nal[0] == 0x67) {
			nal_n = get_nal(&nal_len_n, &buf_offset, buf, total); //get pps
			if (nal_n == NULL) {
				LOGD("No Nal after SPS\n");
				return -1;
			}

			body_len = nal_len + nal_len_n + 16;
			output_len = body_len + FLV_TAG_HEAD_LEN + FLV_PRE_TAG_LEN;
			output = malloc(output_len);
			if (!output) {
				LOGD("Memory is not allocated...");
				return 0;
			}

			// flv tag header
			output[offset++] = 0x09;                      //tagtype video
			output[offset++] = (uint8_t) (body_len >> 16); //data len
			output[offset++] = (uint8_t) (body_len >> 8);  //data len
			output[offset++] = (uint8_t) (body_len);       //data len
			output[offset++] = (uint8_t) (ts >> 16);       //time stamp
			output[offset++] = (uint8_t) (ts >> 8);        //time stamp
			output[offset++] = (uint8_t) (ts);             //time stamp
			output[offset++] = (uint8_t) (ts >> 24);       //time stamp
			output[offset++] = abs_ts;                    //stream id 0
			output[offset++] = 0x00;                      //stream id 0
			output[offset++] = 0x00;                      //stream id 0

			//flv VideoTagHeader
			output[offset++] = 0x17; //key frame, AVC
			output[offset++] = 0x00; //avc sequence header
			output[offset++] = 0x00; //composit time ??????????
			output[offset++] = 0x00; // composit time
			output[offset++] = 0x00; //composit time

			//flv VideoTagBody --AVCDecoderCOnfigurationRecord
			output[offset++] = 0x01;                    //configurationversion
			output[offset++] = nal[1];                  //avcprofileindication
			output[offset++] = nal[2];                  //profilecompatibilty
			output[offset++] = nal[3];                  //avclevelindication
			output[offset++] = 0xff;                    //reserved + lengthsizeminusone
			output[offset++] = 0xe1;                    //numofsequenceset
			output[offset++] = (uint8_t) (nal_len >> 8); //sequence parameter set length high 8 bits
			output[offset++] = (uint8_t) (nal_len);      //sequence parameter set  length low 8 bits
			memcpy(output + offset, nal, nal_len);      //H264 sequence parameter set
			offset += nal_len;
			output[offset++] = 0x01;                      //numofpictureset
			output[offset++] = (uint8_t) (nal_len_n
				>> 8); //picture parameter set length high 8 bits
			output[offset++] = (uint8_t) (nal_len_n);      //picture parameter set length low 8 bits
			memcpy(output + offset, nal_n, nal_len_n);    //H264 picture parameter set

			//no need set pre_tag_size ,RTMP NO NEED
			// flv test

			offset += nal_len_n;
			uint32_t fff = body_len + FLV_TAG_HEAD_LEN;
			output[offset++] = (uint8_t) (fff >> 24); //data len
			output[offset++] = (uint8_t) (fff >> 16); //data len
			output[offset++] = (uint8_t) (fff >> 8);  //data len
			output[offset++] = (uint8_t) (fff);       //data len

			if (g_file_handle) {
				fwrite(output, output_len, 1, g_file_handle);
			}
			val = RTMP_Write(rtmp, output, output_len);
			//RTMP Send out
			free(output);
			if (val < RTMP_SUCCESS) {
				return val;
			}
		} else if ((nal[0] & 0x1f) == 0x05) // it can be 25,45,65
		{
			int result = send_key_frame(rtmp, nal_len, ts, abs_ts, nal);
			if (result < RTMP_SUCCESS) {
				return result;
			} else {
				val += result;
			}
		} else if ((nal[0] & 0x1f) == 0x01) // itcan be 21,41,61
		{
			body_len = nal_len + 5 + 4; //flv VideoTagHeader +  NALU length
			output_len = body_len + FLV_TAG_HEAD_LEN + FLV_PRE_TAG_LEN;
			output = malloc(output_len);
			if (!output) {
				LOGD("Memory is not allocated...");
				return 0;
			}
			// flv tag header
			output[offset++] = 0x09;                      //tagtype video
			output[offset++] = (uint8_t) (body_len >> 16); //data len
			output[offset++] = (uint8_t) (body_len >> 8);  //data len
			output[offset++] = (uint8_t) (body_len);       //data len
			output[offset++] = (uint8_t) (ts >> 16);       //time stamp
			output[offset++] = (uint8_t) (ts >> 8);        //time stamp
			output[offset++] = (uint8_t) (ts);             //time stamp
			output[offset++] = (uint8_t) (ts >> 24);       //time stamp
			output[offset++] = abs_ts;                    //stream id 0
			output[offset++] = 0x00;                      //stream id 0
			output[offset++] = 0x00;                      //stream id 0

			//flv VideoTagHeader
			output[offset++] = 0x27; //not key frame, AVC
			output[offset++] = 0x01; //avc NALU unit
			output[offset++] = 0x00; //composit time ??????????
			output[offset++] = 0x00; // composit time
			output[offset++] = 0x00; //composit time

			output[offset++] = (uint8_t) (nal_len >> 24); //nal length
			output[offset++] = (uint8_t) (nal_len >> 16); //nal length
			output[offset++] = (uint8_t) (nal_len >> 8);  //nal length
			output[offset++] = (uint8_t) (nal_len);       //nal length
			memcpy(output + offset, nal, nal_len);

			//no need set pre_tag_size ,RTMP NO NEED

			offset += nal_len;
			uint32_t fff = body_len + FLV_TAG_HEAD_LEN;
			output[offset++] = (uint8_t) (fff >> 24); //data len
			output[offset++] = (uint8_t) (fff >> 16); //data len
			output[offset++] = (uint8_t) (fff >> 8);  //data len
			output[offset++] = (uint8_t) (fff);       //data len

			if (g_file_handle) {
				fwrite(output, output_len, 1, g_file_handle);
			}
			int result = RTMP_Write(rtmp, output, output_len);

			//RTMP Send out
			free(output);

			if (result < RTMP_SUCCESS) {
				return result;
			}
			val += result;
		}

		nal = get_nal(&nal_len, &buf_offset, buf, total);
	}

	return val;
}

#define RTMP_HEAD_SIZE (sizeof(RTMPPacket) + RTMP_MAX_HEADER_SIZE)
#define IDX_NAL_TYPE 4

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
	size_t size = (size_t) (RTMP_HEAD_SIZE + len + 9);
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
	} else {
		result = rtmp_send_stream_h264(rtmp, data, total, timeStamp);
	}

	return result;
}
