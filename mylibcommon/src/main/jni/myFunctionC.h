//
// Created by hotanha on 11/05/2021.
//

#ifndef TESTCAMERAMEDIACODEC_MYFUNCTIONC_H
#define TESTCAMERAMEDIACODEC_MYFUNCTIONC_H
#ifdef __cplusplus
extern "C"{
#endif

void testFunction1();

void create_info_in_image(char* buffer_info, int camId_i, double latitude_i, double longitude_i,
						  double speed_i, char *xeInfo);

#ifdef __cplusplus
}
#endif
#endif //TESTCAMERAMEDIACODEC_MYFUNCTIONC_H
