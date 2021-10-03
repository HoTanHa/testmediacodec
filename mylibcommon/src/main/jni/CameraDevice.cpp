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
#include <media/NdkImage.h>
#include <media/NdkImageReader.h>
#include <search.h>
#include "myJniDefine.h"

CameraDevice::CameraDevice()
		: camId(1),
		  isInfoChange(false),
		  isRunning(true),
		  isStreaming(false),
		  mMainWindow(nullptr),
		  mStreamWindow(nullptr) {

	timeS = time(NULL);
	strInfo = (char *) malloc(120);
	memset(strInfo, 0, 120);

//	bufferInfoY = (uint8_t *) malloc(BUFFER_INFO_SIZE);
//	bufferMain = (uint8_t *) malloc(SIZE_BUFFER_MAIN);
//	bufferStream = (uint8_t *) malloc(SIZE_BUFFER_STREAM);
	bufferInfoY = new uint8_t[BUFFER_INFO_SIZE];
	bufferMain = new uint8_t[SIZE_BUFFER_MAIN];
	bufferStream = new uint8_t[SIZE_BUFFER_STREAM];
	CameraDevice::numCam++;
}

CameraDevice::~CameraDevice() {
	stopStreamWindow();
	this->isRunning = false;
	info_thread.join();

	free(strInfo);
//	free(bufferInfoY);
//	free(bufferMain);
//	free(bufferStream);
	delete[] bufferInfoY;
	delete[] bufferMain;
	delete[] bufferStream;
}

#include <chrono>

void CameraDevice::create_info_in_image(void *vptr_args) {
	auto *mCamera = reinterpret_cast<CameraDevice *>(vptr_args);
	time_t time_unix = 0;
	time_t time_compare = 0;
	struct tm tm;
	uint16_t value;
	int ii, jj, c_idx;
	int idx_arr = 0;
	int length = 0;
	int count = 0;
	int camIdTitle = mCamera->camId + 1;

	pthread_setname_np(pthread_self(), "setInfoThread");
	while (mCamera->isRunning) {
		usleep(50000);
		count++;
		time_unix = time(NULL);
		// TODO: set lai CameraDevice::sIsInfoChange
		if ((time_compare != time_unix) || (count == 10)) {
			time_compare = time_unix;
			count = 0;
			mCamera->isInfoChange = false;
			localtime_r(&time_unix, &tm);
			CameraDevice::sInfo_mutex.try_lock();
			memset(mCamera->strInfo, 0, 110);
			snprintf(mCamera->strInfo, 100,
					 "Cam%d %04d/%02d/%02d %02d:%02d:%02d %s %9.6lf %10.6lf %5.1lfKm/h %s",
					 camIdTitle , tm.tm_year + 1900, tm.tm_mon + 1, tm.tm_mday, tm.tm_hour,
					 tm.tm_min, tm.tm_sec, CameraDevice::sBsXe, CameraDevice::sLatitude,
					 CameraDevice::sLongitude, CameraDevice::sSpeeds, CameraDevice::sDriverInfo);
			length = strlen(mCamera->strInfo);
			CameraDevice::sInfo_mutex.unlock();

			mCamera->info_mutex.try_lock();
			mCamera->timeS = time_unix;
			memset(mCamera->bufferInfoY, 128, BUFFER_INFO_SIZE);
			int pixCrCb_tmp = 0;
			int pixCrCb_tmp11;
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
				pixCrCb_tmp += WIDTH_IMG;
			}
			mCamera->info_mutex.unlock();
		}
	}
	loge("final final...%d", mCamera->camId);
}

void CameraDevice::draw_frame_to_surface_stream(void *vptr_args) {
	auto *mCamera = reinterpret_cast<CameraDevice *>(vptr_args);
//	mCamera->isStreaming = true;
	while ((mCamera->isStreaming && mCamera->isRunning)) {
		usleep(100000);


		mCamera->draw_mutex.lock();
//		auto *src = (uint8_t *) mCamera->bufferMain;
		auto *sY0 = (uint8_t *) mCamera->bufferMain;
		auto *sY1 = (uint8_t *) (mCamera->bufferMain + WIDTH_IMG);
		auto *sUV0 = (uint8_t *) (mCamera->bufferMain + SIZE_MAIN_IMAGE);
		auto *d360 = (uint8_t *) mCamera->bufferStream;
		auto *dUV = (uint8_t *) (mCamera->bufferStream + SIZE_STREAM_IMAGE);

		for (int i = 0; i < 12; ++i) {
			for (int j = 0; j < (WIDTH_STREAM / 2); ++j) {
				*(d360++) = (sY0[0] + sY0[1] + sY1[0] + sY1[0]) / 4;
				*(d360++) = (sY0[2] + sY0[3] + sY1[2] + sY1[3]) / 4;
				sY0 += 4;
				sY1 += 4;
				*(dUV++) = (sUV0[0]);// + sUV0[2] + sUV1[0] + sUV1[2]) / 4;
				*(dUV++) = (sUV0[1]);// + sUV0[3] + sUV1[1] + sUV1[3]) / 4;
				sUV0 += 4;
			}
			sY0 += WIDTH_IMG;
			sY1 += WIDTH_IMG;
			sUV0 += WIDTH_IMG;
		}
		for (int i = 12; i < 180; ++i) {
			for (int j = 0; j < (WIDTH_STREAM / 4); ++j) {
				*(d360++) = sY0[0];
				*(d360++) = sY0[2];
//				sY0 += 4;
				*(d360++) = sY0[4];
				*(d360++) = sY0[6];
				sY0 += 8;

				*(dUV++) = (sUV0[0]); //+ sUV0[2] + sUV1[0] + sUV1[2]) / 4;
				*(dUV++) = (sUV0[1]);// + sUV0[3] + sUV1[1] + sUV1[3]) / 4;
//				sUV0 += 4;
				*(dUV++) = (sUV0[4]); //+ sUV0[2] + sUV1[0] + sUV1[2]) / 4;
				*(dUV++) = (sUV0[5]);// + sUV0[3] + sUV1[1] + sUV1[3]) / 4;
				sUV0 += 8;
			}
			sY0 += WIDTH_IMG;
			sUV0 += WIDTH_IMG;
		}
		for (int i = 180; i < 360; ++i) {
			for (int j = 0; j < (WIDTH_STREAM / 4); ++j) {
				*(d360++) = sY0[0];
				*(d360++) = sY0[2];
//				sY0 += 4;
				*(d360++) = sY0[4];
				*(d360++) = sY0[6];
				sY0 += 8;
			}
			sY0 += WIDTH_IMG;
		}
		mCamera->draw_mutex.unlock();
		ANativeWindow_Buffer buffer;
		if (ANativeWindow_lock(mCamera->mStreamWindow, &buffer, NULL) == 0) {
			auto *dest = (uint8_t *) buffer.bits;
			memcpy(dest, mCamera->bufferStream, SIZE_Y_STREAM);
			auto *sUV360 = mCamera->bufferStream + SIZE_Y_STREAM;
			auto *destCrCb = dest + (WIDTH_STREAM * (HEIGHT_STREAM + 24));
			memcpy(destCrCb, sUV360, SIZE_UV_STREAM * 2);
			ANativeWindow_unlockAndPost(mCamera->mStreamWindow);
		}
	}
}

void CameraDevice::setCamId(int camIdSet) {
	this->camId = camIdSet;
}

void CameraDevice::setMainWindow(ANativeWindow *mainWindow) {
	this->mMainWindow = mainWindow;
	int res = ANativeWindow_setBuffersGeometry(mMainWindow, WIDTH_IMG, HEIGHT_IMG, FORMAT_SURFACE);
	if (res){
		loge("Set native window error...%d....%ld", camId, time(NULL));
	}
	info_thread = std::thread(create_info_in_image, (void *) this);
}

void CameraDevice::setStreamWindow(ANativeWindow *streamWindow) {
	this->mStreamWindow = streamWindow;
	this->isStreaming = true;
	ANativeWindow_setBuffersGeometry(mStreamWindow, WIDTH_STREAM, HEIGHT_STREAM, FORMAT_SURFACE);
	this->drawStream_thread = std::thread(draw_frame_to_surface_stream, (void *) this);
}

void CameraDevice::stopStreamWindow() {
	if (isStreaming) {
		isStreaming = false;
		drawStream_thread.join();
	}
	if (mStreamWindow) {
		ANativeWindow_release(mStreamWindow);
		mStreamWindow = NULL;
	}
}

void CameraDevice::drawBufferToMainWindow(uint8_t *rawImage) {
	info_mutex.lock();
	memcpy(rawImage, bufferInfoY, WIDTH_IMG * 24);
	info_mutex.unlock();

	draw_mutex.lock();
	memcpy(bufferMain, rawImage, SIZE_BUFFER_MAIN);
	draw_mutex.unlock();
	ANativeWindow_Buffer buffer;
	if (ANativeWindow_lock(mMainWindow, &buffer, NULL) == 0) {
		auto *src = (uint8_t *) rawImage;
		auto *dest = (uint8_t *) buffer.bits;
		memcpy(dest, src, SIZE_Y_MAIN);
		auto *sUV = src + SIZE_Y_MAIN;
		auto *dCrCb = dest + (SIZE_Y_MAIN + WIDTH_IMG * 16);
		memcpy(dCrCb, sUV, SIZE_UV_MAIN * 2);
		ANativeWindow_unlockAndPost(mMainWindow);
	}
}

void CameraDevice::setInfoLocation(double sLat, double sLon, double sSpeed) {
	CameraDevice::sInfo_mutex.lock();
	CameraDevice::sLatitude = sLat;
	CameraDevice::sLongitude = sLon;
	CameraDevice::sSpeeds = sSpeed;
	CameraDevice::sInfo_mutex.lock();
}

void CameraDevice::setDriverInfo(char *ssBsXe, char *sGPLX) {
	CameraDevice::sInfo_mutex.lock();
	memset(CameraDevice::sBsXe, 0, 20);
	snprintf(CameraDevice::sBsXe, 15, "%s", ssBsXe);
	memset(CameraDevice::sDriverInfo, 0, 40);
	snprintf(CameraDevice::sDriverInfo, 30, "%s", sGPLX);
	CameraDevice::sInfo_mutex.unlock();
}

long CameraDevice::getTimeS(){
	this->info_mutex.try_lock();
	long time = this->timeS;
	this->info_mutex.unlock();
	return time;
}

double CameraDevice::sLatitude = 0.0f;
double CameraDevice::sLongitude = 0.0f;
double CameraDevice::sSpeeds = 0.0f;
char CameraDevice::sBsXe[20] = {0};
char CameraDevice::sDriverInfo[40] = {0};
std::mutex CameraDevice::sInfo_mutex;
int CameraDevice::numCam = 0;

