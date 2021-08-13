//
// Created by hotanha on 12/07/2021.
//

#ifndef TESTCAMERAMEDIACODEC_CAMERADEVICE_H
#define TESTCAMERAMEDIACODEC_CAMERADEVICE_H

#include <android/native_window.h>
#include <android/native_activity.h>
#include <android/native_window_jni.h>
#include <media/NdkImage.h>
#include <media/NdkImageReader.h>
#include <mutex>
#include <thread>
#include <list>

const int32_t BUFFER_INFO_SIZE = 1280 * 24;
const int32_t WIDTH_IMG = 1280;
const int32_t HEIGHT_IMG = 720;
const int32_t WIDTH_STREAM = 640;
const int32_t HEIGHT_STREAM = 360;

const int32_t SIZE_MAIN_IMAGE = WIDTH_IMG * HEIGHT_IMG;
const int32_t SIZE_BUFFER_MAIN = SIZE_MAIN_IMAGE * 3 / 2;
const int32_t SIZE_Y_MAIN = SIZE_MAIN_IMAGE;
const int32_t SIZE_UV_MAIN = SIZE_MAIN_IMAGE / 4;
const int32_t SIZE_STREAM_IMAGE = WIDTH_STREAM * HEIGHT_STREAM;
const int32_t SIZE_BUFFER_STREAM = SIZE_STREAM_IMAGE * 3 / 2;
const int32_t SIZE_Y_STREAM = SIZE_STREAM_IMAGE;
const int32_t SIZE_UV_STREAM = SIZE_STREAM_IMAGE / 4;
const int32_t FORMAT_SURFACE = AHARDWAREBUFFER_FORMAT_Y8Cb8Cr8_420;

class CameraDevice {
private:
	static double sLatitude;
	static double sLongitude;
	static double sSpeeds;
	static char sBsXe[20];
	static char sDriverInfo[40];
	static std::mutex sInfo_mutex;
	static int numCam;
	int camId;
	bool isInfoChange;
	char *strInfo;
	uint8_t *bufferInfoY;
	uint8_t *bufferMain;
	uint8_t *bufferStream;
	std::thread drawStream_thread;
	std::mutex draw_mutex;
	std::thread info_thread;
	std::mutex info_mutex;

	volatile bool isRunning;
	volatile bool isStreaming;

	ANativeWindow *mMainWindow;
	ANativeWindow *mStreamWindow;


	static void create_info_in_image(void *vptr_args);

	static void draw_frame_to_surface_stream(void *vptr_args);

public:
	CameraDevice();

	~CameraDevice();

	void setCamId(int camId);

	void setMainWindow(ANativeWindow *mainWindow);

	void setStreamWindow(ANativeWindow *streamWindow);

	void stopStreamWindow();

	void drawBufferToMainWindow(uint8_t *rawImage);

	static void setInfoLocation(double sLat, double sLon, double sSpeed);

	static void setDriverInfo(char *sBsXe, char *sGPLX);

};


#endif //TESTCAMERAMEDIACODEC_CAMERADEVICE_H
