//
// Created by hotanha on 06/07/2021.
//

#include <cstdlib>
#include <unistd.h>
#include <fcntl.h>
#include <cerrno>
#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>
#include <libgen.h>
#include <string>
#include <iostream>
#include <jni.h>
#include <android/log.h>
#include <android/native_activity.h>
#include <android/native_window_jni.h>
#include <android/surface_control.h>
#include <android/native_window.h>
#include <android/hardware_buffer_jni.h>
#include <android/hardware_buffer.h>
#include <android/rect.h>
#include <cstring>
#include <cstdlib>

extern "C"
JNIEXPORT jint

JNICALL
Java_com_example_mylibcommon_NativeFunction_nResetUsb(JNIEnv *env, jclass clazz, jstring name) {
	// TODO: implement nResetUsb()
	const char *nativeString = env->GetStringUTFChars(name, 0);
	int fd = open(nativeString, O_WRONLY);
	int res = 0;
	if (fd < 0) {
		res = 1;
	} else {
		int rc = ioctl(fd, USBDEVFS_RESET, 0);
		if (rc < 0) {
			res = 2;
		}
		close(fd);
	}
	env->ReleaseStringUTFChars(name, nativeString);
	return res;
}

