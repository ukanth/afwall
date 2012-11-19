LOCAL_PATH:= $(call my-dir)

#
# libxtables
#

include $(CLEAR_VARS)

LOCAL_C_INCLUDES:= \
	$(LOCAL_PATH)/include/ \
	$(KERNEL_HEADERS) 

LOCAL_CFLAGS:=-DNO_SHARED_LIBS
LOCAL_CFLAGS+=-DXTABLES_INTERNAL
LOCAL_CFLAGS+=-DXTABLES_LIBDIR

LOCAL_SRC_FILES:= \
	xtables.c

LOCAL_MODULE_TAGS:=
LOCAL_MODULE:=libxtables

include $(BUILD_STATIC_LIBRARY)


#
# libip4tc
#

include $(CLEAR_VARS)

LOCAL_C_INCLUDES:= \
	$(KERNEL_HEADERS) \
	$(LOCAL_PATH)/include/

LOCAL_CFLAGS:=-DNO_SHARED_LIBS
LOCAL_CFLAGS+=-DXTABLES_INTERNAL

LOCAL_SRC_FILES:= \
	libiptc/libip4tc.c

LOCAL_MODULE_TAGS:=
LOCAL_MODULE:=libip4tc

include $(BUILD_STATIC_LIBRARY)

# libext4

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS:=
LOCAL_MODULE:=libext4

# LOCAL_MODULE_CLASS must be defined before calling $(local-intermediates-dir)
LOCAL_MODULE_CLASS := STATIC_LIBRARIES
intermediates := $(call local-intermediates-dir)

LOCAL_C_INCLUDES:= \
	$(LOCAL_PATH)/include/ \
	$(KERNEL_HEADERS) \
	$(intermediates)/extensions/

LOCAL_CFLAGS:=-DNO_SHARED_LIBS
LOCAL_CFLAGS+=-DXTABLES_INTERNAL

# List of "libipt_*" extensions to compile
PF_EXT_SLIB := addrtype ah CLUSTERIP DNAT ecn ECN icmp LOG MASQUERADE MIRROR NETMAP realm REDIRECT REJECT SAME
PF_EXT_SLIB += SNAT ttl TTL ULOG unclean
#PF_EXT_SLIB += set SET

EXT_FUNC+=$(foreach T,$(PF_EXT_SLIB),ipt_$(T))

# List of "libxt_*" extensions to compile
NEW_PF_EXT_SLIB := CLASSIFY cluster comment connbytes connlimit connmark CONNMARK CONNSECMARK conntrack dccp dscp
NEW_PF_EXT_SLIB += DSCP esp hashlimit helper iprange length limit mac mark MARK multiport NFLOG NFQUEUE NOTRACK
NEW_PF_EXT_SLIB += osf owner physdev pkttype policy quota rateest RATEEST recent sctp SECMARK socket standard
NEW_PF_EXT_SLIB += state statistic string tcp tcpmss TCPMSS time tos TOS TPROXY TRACE u32 udp

EXT_FUNC+=$(foreach N,$(NEW_PF_EXT_SLIB),xt_$(N))

# Generated source: gen_initext4.c
FORCE:
	echo FORCE
GEN_INITEXT:= extensions/gen_initext4.c
LOCAL_GEN_INITEXT:= $(LOCAL_PATH)/$(GEN_INITEXT)
$(LOCAL_GEN_INITEXT): FORCE
	$(LOCAL_PATH)/extensions/create_initext4 "$(EXT_FUNC)" > $@
$(intermediates)/extensions/gen_initext4.o: $(GEN_INITEXT)


LOCAL_SRC_FILES:= \
	$(foreach T,$(PF_EXT_SLIB),extensions/libipt_$(T).c) \
	$(foreach N,$(NEW_PF_EXT_SLIB),extensions/libxt_$(N).c) \
	$(GEN_INITEXT)


LOCAL_STATIC_LIBRARIES := \
	libc

include $(BUILD_STATIC_LIBRARY)

#
# Build iptables
#

include $(CLEAR_VARS)

LOCAL_C_INCLUDES:= \
	$(LOCAL_PATH)/include/ \
	$(KERNEL_HEADERS)

LOCAL_CFLAGS:=-DNO_SHARED_LIBS
LOCAL_CFLAGS+=-DXTABLES_INTERNAL

LOCAL_SRC_FILES:= \
	iptables.c \
	iptables-standalone.c \
        xshared.c

LOCAL_MODULE_TAGS:=
LOCAL_MODULE:=iptables

LOCAL_STATIC_LIBRARIES := \
	libip4tc \
	libext4  \
        libxtables 

include $(BUILD_EXECUTABLE)

