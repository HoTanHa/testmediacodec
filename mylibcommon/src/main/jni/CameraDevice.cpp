//
// Created by hotanha on 12/07/2021.
//

#include "CameraDevice.h"
#include "font_12x24.h"
#include <cstdlib>
#include <ctime>
#include <cstring>
#include <unistd.h>
#include <chrono>
#include <thread>

CameraDevice::CameraDevice()
	: camId(1),
	  latitude(0.0f),
	  longitude(0.0f),
	  speed(0.0f),
	  isInfoChange(false),
	  isRunning(false),
	  isStreaming(false),
	  mMainWindow(nullptr),
	  mStreamWindow(nullptr) {

	strInfo = (char *) malloc(120);
	bsXe = (char *) malloc(20);
	driveInfo = (char *) malloc(40);
	bufferInfoY = (uint8_t *) malloc(BUFFER_INFO_SIZE);
	bufferMain = (uint8_t *) malloc(SIZE_BUFFER_MAIN);
	bufferStream = (uint8_t *) malloc(SIZE_BUFFER_STREAM);

	pthread_mutex_init(&info_mutex, NULL);
	pthread_mutex_init(&draw_mutex, NULL);
}

CameraDevice::~CameraDevice() {
	stopStreamWindow();

	free(strInfo);
	free(bsXe);
	free(driveInfo);
	free(bufferInfoY);
	free(bufferMain);
	free(bufferStream);
	pthread_mutex_destroy(&info_mutex);
	pthread_mutex_destroy(&draw_mutex);
}

void *CameraDevice::create_info_in_image(void *vptr_args) {
	auto *mCamera = reinterpret_cast<CameraDevice *>(vptr_args);
	time_t time_unix = 0;
	time_t time_compare = 0;
	struct tm tm;
	uint16_t value;
	int ii, jj, c_idx;
	int idx_arr = 0;
	int length = 0;

	while (mCamera->isRunning) {
		time_unix = time(NULL);
		if (time_compare == time_unix && mCamera->isInfoChange) {
			usleep(100000);
			continue;
		}

		time_compare = time_unix;
		mCamera->isInfoChange = false;
		localtime_r(&time_unix, &tm);

		memset(mCamera->strInfo, 0, 110);
		snprintf(mCamera->strInfo, 100,
				 "Cam%d %04d/%02d/%02d %02d:%02d:%02d %9.6lf %10.6lf %5.1lfKm/h %s",
				 mCamera->camId, tm.tm_year + 1900, tm.tm_mon + 1, tm.tm_mday, tm.tm_hour,
				 tm.tm_min, tm.tm_sec, mCamera->latitude, mCamera->longitude, mCamera->speed,
				 mCamera->driveInfo);
		length = strlen(mCamera->strInfo);
		memset(mCamera->bufferInfoY, 128, BUFFER_INFO_SIZE);
		int pixCrCb_tmp = 0;
		int pixCrCb_tmp11 = 0;
		for (ii = 0; ii < 24; ii++) {
			for (c_idx = 0; c_idx < length; c_idx++) {
				idx_arr = mCamera->strInfo[c_idx] * 24 * 2 + 2 * ii;
				pixCrCb_tmp11 = pixCrCb_tmp + c_idx * 12;
				value = (console_font_12x24[idx_arr]) * 0x100 + console_font_12x24[idx_arr + 1];
				for (jj = 0; jj < 12; jj++) {
					if (value & (0x8000 >> jj)) {
						mCamera->bufferInfoY[pixCrCb_tmp11 + jj] = 0xff;
					}
				}
			}
			pixCrCb_tmp += 1280;
		}
	}
	return nullptr;
}

void *CameraDevice::drawFrameToSurfaceStream(void *vptr_args) {
	auto *mCamera = reinterpret_cast<CameraDevice *>(vptr_args);

	ANativeWindow_Buffer buffer;
	int count = 0;
	while ((mCamera->isStreaming && mCamera->isRunning)) {
		usleep(10000);
		if (count < 10) {
			count++;
			continue;
		}
		else {
			count = 0;
		}

		pthread_mutex_lock(&(mCamera->draw_mutex));
		auto *src = (uint8_t *) mCamera->bufferMain;
		auto *sY0 = (uint8_t *) mCamera->bufferMain;
		auto *sY1 = (uint8_t *) (mCamera->bufferMain + WIDTH_IMG);
		auto *sUV0 = (uint8_t *) (mCamera->bufferMain + SIZE_MAIN_IMAGE);
		auto *sUV1 = (uint8_t *) (mCamera->bufferMain + SIZE_MAIN_IMAGE + WIDTH_IMG);;
		auto *d360 = (uint8_t *) mCamera->bufferStream;
		auto *dUV = (uint8_t *) (mCamera->bufferStream + SIZE_STREAM_IMAGE);

		for (int i = 0; i < 12; ++i) {
			for (int j = 0; j < WIDTH_STREAM / 2; ++j) {
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
			sY0 += WIDTH_IMG;
			sY1 += WIDTH_IMG;
			sUV0 += WIDTH_IMG;
			sUV1 += WIDTH_IMG;
		}
		for (int i = 12; i < 360; ++i) {
			for (int j = 0; j < WIDTH_STREAM / 2; ++j) {
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
			sY0 += WIDTH_IMG;
			sUV0 += WIDTH_IMG;
			sUV1 += WIDTH_IMG;
		}
		pthread_mutex_unlock(&(mCamera->draw_mutex));

		if (ANativeWindow_lock(mCamera->mStreamWindow, &buffer, NULL) == 0) {
			auto *dest = (uint8_t *) buffer.bits;
			memcpy(dest, mCamera->bufferStream, SIZE_Y_STREAM);
			auto *sUV360 = mCamera->bufferStream + SIZE_Y_STREAM;
			auto *destCrCb = dest + (HEIGHT_STREAM * (WIDTH_STREAM + 24));
			memcpy(destCrCb, sUV360, SIZE_UV_STREAM * 2);
			ANativeWindow_unlockAndPost(mCamera->mStreamWindow);
		}
	}

	return nullptr;
}

void CameraDevice::setMainWindow(ANativeWindow *mainWindow) {
	this->mMainWindow = mainWindow;
	ANativeWindow_setBuffersGeometry(mMainWindow, WIDTH_IMG, HEIGHT_IMG, FORMAT_SURFACE);
}

void CameraDevice::setStreamWindow(ANativeWindow *streamWindow) {
	this->mStreamWindow = streamWindow;
	ANativeWindow_setBuffersGeometry(mStreamWindow, WIDTH_STREAM, HEIGHT_STREAM, FORMAT_SURFACE);
	pthread_create(&(drawStream_thread), NULL, drawFrameToSurfaceStream, (void *) this);
}

void CameraDevice::stopStreamWindow() {
	isStreaming = false;
	pthread_join(drawStream_thread, NULL);
	if (mStreamWindow) {
		ANativeWindow_release(mStreamWindow);
		mStreamWindow = NULL;
	}
}

void CameraDevice::drawBufferToMainWindow(uint8_t *rawImage) {
	pthread_mutex_lock(&info_mutex);
	memcpy(rawImage, bufferInfoY, WIDTH_IMG * 24);
	pthread_mutex_unlock(&info_mutex);

	pthread_mutex_lock(&draw_mutex);
	memcpy(bufferMain, rawImage, SIZE_BUFFER_MAIN);

	ANativeWindow_Buffer buffer;
	if (ANativeWindow_lock(mMainWindow, &buffer, NULL) == 0) {

		uint8_t *src = (uint8_t *) bufferMain;
		auto *dest = (uint8_t *) buffer.bits;
		memcpy(dest, src, SIZE_Y_MAIN);
		auto *sUV = src + SIZE_Y_MAIN;
		auto *dCrCb = dest + (SIZE_Y_MAIN + 1280 * 16);
		memcpy(dCrCb, sUV, SIZE_UV_MAIN * 2);
		ANativeWindow_unlockAndPost(mMainWindow);
	}
	pthread_mutex_unlock(&draw_mutex);
}

void CameraDevice::setInfoLocation(double sLat, double sLon, double sSpeed) {
	pthread_mutex_lock(&info_mutex);
	this->latitude = sLat;
	this->longitude = sLon;
	this->speed = sSpeed;
	this->isInfoChange = true;
	pthread_mutex_unlock(&info_mutex);

}

void CameraDevice::setDriverInfo(char *sBsXe, char *sGPLX) {
	pthread_mutex_lock(&info_mutex);
	memset(bsXe, 0, 20);
	snprintf(bsXe, 15, "%s", sBsXe);

	memset(driveInfo, 0, 40);
	snprintf(driveInfo, 30, "%s", sGPLX);

	pthread_mutex_unlock(&info_mutex);
}



