#
# Based somewhat on android/Makefile in OpenConnect (David Woodhouse)
#

NDK     := /opt/android-ndk-r9
ARCH    := arm
GCCVER  := 4.8

# You should be able to just 'make ARCH=x86' and it should DTRT.
ARCH_LIST := arm x86 mips
ifeq ($(ARCH),arm)
TRIPLET := arm-linux-androideabi
TOOLCHAIN := $(TRIPLET)-$(GCCVER)
APIVER  := 8
NDK_ABINAME := armeabi
endif
ifeq ($(ARCH),x86)
TRIPLET := i686-linux-android
TOOLCHAIN := x86-$(GCCVER)
APIVER  := 9
NDK_ABINAME := x86
endif
ifeq ($(ARCH),mips)
TRIPLET := mipsel-linux-android
TOOLCHAIN := $(TRIPLET)-$(GCCVER)
APIVER  := 9
NDK_ABINAME := mips
endif

NDK_SYSROOT := $(NDK)/platforms/android-$(APIVER)/arch-$(ARCH)

BINDIR := $(firstword $(wildcard $(NDK)/toolchains/$(TOOLCHAIN)/prebuilt/*/bin))
PATH := $(BINDIR):$(NDK):$(PATH)

RESDIR := $(shell pwd)/../res/raw
DESTDIR := $(shell pwd)/$(TRIPLET)/out

CONFIGURE_ARGS := --host=$(TRIPLET) --disable-shared --enable-static \
		  CFLAGS="--sysroot=$(NDK_SYSROOT) -O2"

PER_ARCH_TARGETS := iptables busybox nflog klogripper

.PHONY: all unpack clean
all: $(addsuffix -binaries,$(ARCH_LIST))
clean: $(addsuffix -clean,$(ARCH_LIST))
	rm -rf sources
unpack: $(addsuffix -unpack,$(PER_ARCH_TARGETS))

#####################################################################
#
# Build iptables
#
IPTABLES_VER := 1.4.20
IPTABLES_SRC := sources/iptables-$(IPTABLES_VER)
IPTABLES_BUILD := $(TRIPLET)/iptables

dist/iptables-$(IPTABLES_VER).tar.bz2:
	mkdir -p dist
	curl http://ftp.netfilter.org/pub/iptables/iptables-$(IPTABLES_VER).tar.bz2 -o $@.tmp && mv $@.tmp $@

$(IPTABLES_SRC)/configure: dist/iptables-$(IPTABLES_VER).tar.bz2
	rm -rf $(IPTABLES_SRC)
	mkdir -p sources
	tar -jxf $< -C sources
	cd $(IPTABLES_SRC) && \
		for x in ../../dist/iptables-patches/*; do \
			patch -p1 < $$x || exit 1; \
		done
	cd $(IPTABLES_SRC) && ./autogen.sh

$(IPTABLES_BUILD)/Makefile: $(IPTABLES_SRC)/configure
	mkdir -p $(IPTABLES_BUILD)
	cd $(IPTABLES_BUILD) && ../../$(IPTABLES_SRC)/configure \
		$(CONFIGURE_ARGS) --prefix=/

$(DESTDIR)/sbin/iptables: $(IPTABLES_BUILD)/Makefile
	$(MAKE) -C $(IPTABLES_BUILD)
	$(MAKE) -C $(IPTABLES_BUILD) install DESTDIR=$(DESTDIR)

.PHONY: iptables iptables-unpack
iptables: $(DESTDIR)/sbin/iptables
	cp -L $(DESTDIR)/sbin/iptables $(RESDIR)/iptables_$(ARCH)
	cp -L $(DESTDIR)/sbin/ip6tables $(RESDIR)/ip6tables_$(ARCH)
iptables-unpack: $(IPTABLES_SRC)/configure

#####################################################################
#
# Build busybox
#
BUSYBOX_VER := 1.21.1
BUSYBOX_BUILD := $(TRIPLET)/busybox

dist/busybox-$(BUSYBOX_VER).tar.bz2:
	mkdir -p dist
	curl http://busybox.net/downloads/busybox-$(BUSYBOX_VER).tar.bz2 -o $@.tmp && mv $@.tmp $@

$(BUSYBOX_BUILD)/.unpacked: dist/busybox-$(BUSYBOX_VER).tar.bz2
	rm -rf $(BUSYBOX_BUILD)
	mkdir -p $(BUSYBOX_BUILD)
	tar --strip 1 -jxf $< -C $(BUSYBOX_BUILD)
	cd $(BUSYBOX_BUILD) && \
		for x in ../../dist/busybox-patches/*; do \
			patch -p1 < $$x || exit 1; \
		done
	touch $@

$(BUSYBOX_BUILD)/.configured: $(BUSYBOX_BUILD)/.unpacked
	cp -f dist/busybox-defconfig $(BUSYBOX_BUILD)/.config
	echo "CONFIG_CROSS_COMPILER_PREFIX=\"$(TRIPLET)-\"" >> $(BUSYBOX_BUILD)/.config
	echo "CONFIG_SYSROOT=\"$(NDK_SYSROOT)\"" >> $(BUSYBOX_BUILD)/.config
	touch $@

$(BUSYBOX_BUILD)/busybox: $(BUSYBOX_BUILD)/.configured
	$(MAKE) -C $(BUSYBOX_BUILD) KCONFIG_NOTIMESTAMP=1

.PHONY: busybox busybox-unpack
busybox: $(BUSYBOX_BUILD)/busybox
	cp $< $(RESDIR)/busybox_$(ARCH)
busybox-unpack: $(BUSYBOX_BUILD)/.configured

#####################################################################
#
# Build nflog
#
NDK_OUTDIR := ../libs/$(NDK_ABINAME)

$(NDK_OUTDIR)/nflog:
	ndk-build APP_ABI=$(NDK_ABINAME)

.PHONY: nflog nflog-unpack
nflog: $(NDK_OUTDIR)/nflog
	cp $< $(RESDIR)/nflog_$(ARCH)
nflog-unpack:

#####################################################################
#
# Build klogripper
#
NDK_OUTDIR := ../libs/$(NDK_ABINAME)

$(NDK_OUTDIR)/klogripper:
	ndk-build APP_ABI=$(NDK_ABINAME)

.PHONY: klogripper klogripper-unpack
klogripper: $(NDK_OUTDIR)/klogripper
	cp $< $(RESDIR)/klogripper_$(ARCH)
klogripper-unpack:


#####################################################################
#
# Common targets
#
.PHONY: arch-clean %-clean %-binaries
arch-clean:
	rm -rf $(TRIPLET) $(NDK_OUTDIR)
%-clean:
	$(MAKE) ARCH=$* arch-clean
	ndk-build APP_ABI=$(NDK_ABINAME) clean
%-binaries: unpack
	$(MAKE) ARCH=$* $(PER_ARCH_TARGETS)
