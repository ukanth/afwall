LOCAL_PATH := $(call my-dir)
# Clear variables here.
include $(CLEAR_VARS)

ZPATH := $(LOCAL_PATH)

include $(ZPATH)/grep/Android.mk
include $(ZPATH)/nflog/Android.mk
#include $(ZPATH)/iptables/Android.mk
