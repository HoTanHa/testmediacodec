//
// Created by hotanha on 03/07/2021.
//

#ifndef  _MYJNIDEFINE_H
#define _MYJNIDEFINE_H

#include <android/log.h>
#include <libgen.h>

#define LOG_TAG "MyLibNative"

#if 0
#define LOGV(FMT, ...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, "[%d..%s..%d..%s]:" FMT,    \
                            gettid(), basename(__FILE__), __LINE__, __FUNCTION__, ## __VA_ARGS__)
#define LOGD(FMT, ...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "[%d..%s..%d..%s]:" FMT,    \
                            gettid(), basename(__FILE__), __LINE__, __FUNCTION__, ## __VA_ARGS__)
#define LOGI(FMT, ...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "[%d..%s..%d..%s]:" FMT,    \
                            gettid(), basename(__FILE__), __LINE__, __FUNCTION__, ## __VA_ARGS__)
#define LOGW(FMT, ...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "[%d..%s..%d..%s]:" FMT,    \
                            gettid(), basename(__FILE__), __LINE__, __FUNCTION__, ## __VA_ARGS__)
#define LOGE(FMT, ...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "[%d..%s..%d..%s]:" FMT,    \
                            gettid(), basename(__FILE__), __LINE__, __FUNCTION__, ## __VA_ARGS__)
#else
#define logv(FMT, ...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, "[%s %d]:" FMT,    \
                            basename(__FILE__), __LINE__, ## __VA_ARGS__)
#define logd(FMT, ...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "[%s %d]:" FMT,    \
                            basename(__FILE__), __LINE__, ## __VA_ARGS__)
#define logi(FMT, ...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "[%s %d]:" FMT,    \
                            basename(__FILE__), __LINE__, ## __VA_ARGS__)
#define logw(FMT, ...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "[%s %d]:" FMT,    \
                            basename(__FILE__), __LINE__, ## __VA_ARGS__)
#define loge(FMT, ...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "[%s %d]:" FMT,    \
                            basename(__FILE__), __LINE__, ## __VA_ARGS__)

#endif

#endif // _MYJNIDEFINE_H
