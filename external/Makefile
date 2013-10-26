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
endif
ifeq ($(ARCH),x86)
TRIPLET := i686-linux-android
TOOLCHAIN := x86-$(GCCVER)
APIVER  := 9
endif
ifeq ($(ARCH),mips)
TRIPLET := mipsel-linux-android
TOOLCHAIN := $(TRIPLET)-$(GCCVER)
APIVER  := 9
endif

NDK_SYSROOT := $(NDK)/platforms/android-$(APIVER)/arch-$(ARCH)

BINDIR := $(firstword $(wildcard $(NDK)/toolchains/$(TOOLCHAIN)/prebuilt/*/bin))
PATH := $(BINDIR):$(PATH)

RESDIR := $(shell pwd)/../res/raw
DESTDIR := $(shell pwd)/$(TRIPLET)/out

CONFIGURE_ARGS := --host=$(TRIPLET) --disable-shared --enable-static \
		  CFLAGS="--sysroot=$(NDK_SYSROOT) -O2"

PER_ARCH_TARGETS := iptables

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
# Common targets
#
.PHONY: arch-clean %-clean %-binaries
arch-clean:
	rm -rf $(TRIPLET)
%-clean:
	$(MAKE) ARCH=$* arch-clean
%-binaries: unpack
	$(MAKE) ARCH=$* $(PER_ARCH_TARGETS)
