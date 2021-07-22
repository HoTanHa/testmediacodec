//
// Created by hotanha on 11/05/2021.
//

#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <unistd.h>
#include <time.h>
#include <stdbool.h>
#include <string.h>
#include "myFunctionC.h"
#include "font_12x24.h"

#define BUFFER_INFO_SIZE (1280*24)

void testFunction1() {
	__android_log_write(ANDROID_LOG_ERROR, "Tag", "Error here");
	printf("testFunction");
}

void create_info_in_image(char* buffer_info, int camId_i, double latitude_i, double longitude_i,
						  double speed_i, char *xeInfo) {
	time_t time_unix = 0;
//	time_t time_compare = 0;
	char strInfo[110];
	struct tm tm;
	uint16_t value;
	int ii, jj, c_idx;
//	int idx = 0;
	int idx_arr = 0;
	int length = 0;
//	int step_row = 1280 * 2;

	time_unix = time(NULL);
	localtime_r(&time_unix, &tm);
	memset(strInfo, 0, 110);
	snprintf(strInfo, 100, "Cam%d %04d/%02d/%02d %02d:%02d:%02d %9.6lf %10.6lf %5.1lfKm/h %s",
			 camId_i, tm.tm_year + 1900, tm.tm_mon + 1, tm.tm_mday, tm.tm_hour, tm.tm_min,
			 tm.tm_sec,
			 latitude_i, longitude_i, speed_i, xeInfo);
	length = strlen(strInfo);
	memset(buffer_info, 128, BUFFER_INFO_SIZE);
	int pixCrCb_tmp = 0;
	int pixCrCb_tmp11 = 0;
//	int pixel_black = 0;
	for (ii = 0; ii < 24; ii++) {
		for (c_idx = 0; c_idx < length; c_idx++) {
			idx_arr = (char) strInfo[c_idx] * 24 * 2 + 2 * ii;
			pixCrCb_tmp11 = pixCrCb_tmp + c_idx * 12 ;
			value = (console_font_12x24[idx_arr]) * 0x100 + console_font_12x24[idx_arr + 1];
			for (jj = 0; jj < 12; jj++) {
				if (value & (0x8000 >> jj)) {
					buffer_info[pixCrCb_tmp11 + jj] = 0xff;
					//	buffer_info[idx + 1] = 0x80; //0xfc;
				}
			}
		}
		pixCrCb_tmp += 1280;

	}
}
