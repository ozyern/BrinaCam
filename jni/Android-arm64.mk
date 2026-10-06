# 64-bit (arm64-v8a) native build.
#
# Built with a current NDK (clang + libc++), separately from the 32-bit build in
# Android.mk. Only the open-source libraries are included: the Almalence
# processing core (almalib) and the libraries linking it only exist as 32-bit
# prebuilts, so the modes that need them are hidden on 64-bit-only devices.
#
# libjpeg-turbo is built by CI into prebuilt/arm64-v8a (see build-apk.yml).

LOCAL_PATH := $(call my-dir)

ARM64_JPEG_DIR := $(LOCAL_PATH)/prebuilt/$(TARGET_ARCH_ABI)/libjpeg-turbo

ARM64_CFLAGS := -I$(LOCAL_PATH)/include -fno-math-errno -ftree-vectorize -D__STDC_CONSTANT_MACROS \
	-include stdlib.h -include string.h -Wno-everything
ARM64_LDFLAGS := -Wl,-z,max-page-size=16384

include $(CLEAR_VARS)
LOCAL_MODULE := jpeg
LOCAL_SRC_FILES := prebuilt/$(TARGET_ARCH_ABI)/libjpeg-turbo/libjpeg.a
LOCAL_EXPORT_C_INCLUDES := $(ARM64_JPEG_DIR)/include
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := swapheap
LOCAL_SRC_FILES := swapheap/swapheap.cpp swapheap/ptrhandle.cpp
LOCAL_CFLAGS := $(ARM64_CFLAGS)
LOCAL_LDFLAGS := $(ARM64_LDFLAGS)
LOCAL_LDLIBS := -llog
LOCAL_DISABLE_FORMAT_STRING_CHECKS := true
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := utils-image
LOCAL_SRC_FILES := utils/ImageConversionUtils.cpp
LOCAL_CFLAGS := $(ARM64_CFLAGS)
LOCAL_LDFLAGS := $(ARM64_LDFLAGS)
LOCAL_STATIC_LIBRARIES := jpeg
LOCAL_SHARED_LIBRARIES := swapheap
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/utils
LOCAL_LDLIBS := -ldl -llog
LOCAL_DISABLE_FORMAT_STRING_CHECKS := true
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := utils-jni
LOCAL_SRC_FILES := utils-jni/ImageConversion.cpp
LOCAL_CFLAGS := $(ARM64_CFLAGS)
LOCAL_LDFLAGS := $(ARM64_LDFLAGS)
LOCAL_C_INCLUDES := $(LOCAL_PATH)/utils
LOCAL_SHARED_LIBRARIES := utils-image swapheap
LOCAL_STATIC_LIBRARIES := jpeg
LOCAL_LDLIBS := -ldl -llog
LOCAL_DISABLE_FORMAT_STRING_CHECKS := true
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := yuvimage
LOCAL_SRC_FILES := yuvimage/yuvimage.cpp yuvimage/YuvToJpegEncoderMT.cpp
LOCAL_CFLAGS := $(ARM64_CFLAGS)
LOCAL_LDFLAGS := $(ARM64_LDFLAGS)
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include/almashot
LOCAL_STATIC_LIBRARIES := jpeg
LOCAL_SHARED_LIBRARIES := swapheap
LOCAL_LDLIBS := -llog
LOCAL_DISABLE_FORMAT_STRING_CHECKS := true
include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_MODULE := histogram
LOCAL_SRC_FILES := histogram/histogram.cpp
LOCAL_CFLAGS := $(ARM64_CFLAGS)
LOCAL_LDFLAGS := $(ARM64_LDFLAGS)
LOCAL_LDLIBS := -ldl -llog
LOCAL_DISABLE_FORMAT_STRING_CHECKS := true
include $(BUILD_SHARED_LIBRARY)
