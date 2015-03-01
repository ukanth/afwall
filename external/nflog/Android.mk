LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

# Enable PIE
LOCAL_CFLAGS += -fvisibility=default -fPIE
LOCAL_LDFLAGS += -rdynamic -fPIE -pie

LOCAL_MODULE := nflog

LOCAL_SRC_FILES := attr.c callback.c nflog.c nlmsg.c socket.c

include $(BUILD_EXECUTABLE)
