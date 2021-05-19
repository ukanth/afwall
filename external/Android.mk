LOCAL_PATH := $(call my-dir)
# Clear variables here.
include $(CLEAR_VARS)

ZPATH := $(LOCAL_PATH)

include $(ZPATH)/nflog/Android.mk

# run pie is no longer supported
# include $(ZPATH)/run_pie/Android.mk
