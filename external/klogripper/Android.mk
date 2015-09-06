LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

# Enable PIE
LOCAL_CFLAGS += -fvisibility=default -fPIE
LOCAL_LDFLAGS += -rdynamic -fPIE -pie

LOCAL_MODULE := klogripper

LOCAL_SRC_FILES := main.c

include $(BUILD_EXECUTABLE)
