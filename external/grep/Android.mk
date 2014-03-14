LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := grep

LOCAL_SRC_FILES := getopt32.c grep.c llist.c get_line_from_file.c libbb.c \
                   recursive_action.c xregcomp.c

include $(BUILD_EXECUTABLE)
