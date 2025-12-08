#!/bin/bash
#
# AFWall+ Binary Build Script
# Extracts build logic from .github/workflows/build-binaries.yml for local/F-Droid builds
#
# This script builds cross-compiled binaries for AFWall+:
# - busybox (arm, arm64, x86)
# - iptables/ip6tables (arm, arm64, x86)
# - nflog (arm, arm64, x86)
#
# Requirements:
# - Linux build environment
# - build-essential, wget, unzip, file, pkg-config, autoconf, automake, libtool
# - gcc-arm-linux-gnueabihf, gcc-aarch64-linux-gnu, gcc-i686-linux-gnu
#
# Usage:
#   ./build-binaries.sh [arch]
#
# Arguments:
#   arch - Optional: arm, arm64, x86, or 'all' (default: all)
#
# Environment variables:
#   BUSYBOX_VERSION  - Busybox version to build (default: 1.36.1)
#   IPTABLES_VERSION - iptables version to build (default: 1.8.10)
#   NDK_VERSION      - Android NDK version (default: r26d)
#   OUTPUT_DIR       - Output directory for binaries (default: binaries/)
#   SKIP_NDK_DOWNLOAD - Set to '1' to skip NDK download if already present
#

set -e  # Exit on error
set -u  # Exit on undefined variable

#####################################################################
# Configuration
#####################################################################

BUSYBOX_VERSION="${BUSYBOX_VERSION:-1.36.1}"
IPTABLES_VERSION="${IPTABLES_VERSION:-1.8.10}"
NDK_VERSION="${NDK_VERSION:-r26d}"
OUTPUT_DIR="${OUTPUT_DIR:-binaries}"
SKIP_NDK_DOWNLOAD="${SKIP_NDK_DOWNLOAD:-0}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="${SCRIPT_DIR}/build-temp"
NDK_DIR="${BUILD_DIR}/android-ndk-${NDK_VERSION}"

# Architecture to build (default: all)
BUILD_ARCH="${1:-all}"

# Validate architecture
if [[ ! "$BUILD_ARCH" =~ ^(arm|arm64|x86|all)$ ]]; then
    echo "Error: Invalid architecture '$BUILD_ARCH'"
    echo "Usage: $0 [arm|arm64|x86|all]"
    exit 1
fi

#####################################################################
# Colors and output
#####################################################################

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}[INFO]${NC} $*"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $*"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $*"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $*"
}

#####################################################################
# Environment setup
#####################################################################

check_dependencies() {
    log_info "Checking dependencies..."

    local missing_deps=()

    # Check basic tools
    for tool in wget unzip file make gcc; do
        if ! command -v "$tool" &> /dev/null; then
            missing_deps+=("$tool")
        fi
    done

    # Check cross-compilers (for nflog)
    for compiler in arm-linux-gnueabihf-gcc aarch64-linux-gnu-gcc i686-linux-gnu-gcc; do
        if ! command -v "$compiler" &> /dev/null; then
            log_warn "Cross-compiler not found: $compiler (needed for nflog)"
        fi
    done

    if [ ${#missing_deps[@]} -gt 0 ]; then
        log_error "Missing dependencies: ${missing_deps[*]}"
        log_info "Install with: sudo apt-get install build-essential wget unzip file pkg-config autoconf automake libtool gcc-arm-linux-gnueabihf gcc-aarch64-linux-gnu gcc-i686-linux-gnu"
        exit 1
    fi

    log_success "All required dependencies found"
}

setup_ndk() {
    if [ "$SKIP_NDK_DOWNLOAD" = "1" ] && [ -d "$NDK_DIR" ]; then
        log_info "Skipping NDK download (SKIP_NDK_DOWNLOAD=1 and NDK exists)"
        export ANDROID_NDK_ROOT="$NDK_DIR"
        return 0
    fi

    if [ -d "$NDK_DIR" ]; then
        log_info "Android NDK ${NDK_VERSION} already exists at ${NDK_DIR}"
        export ANDROID_NDK_ROOT="$NDK_DIR"
        return 0
    fi

    log_info "Downloading Android NDK ${NDK_VERSION}..."

    mkdir -p "$BUILD_DIR"
    cd "$BUILD_DIR"

    local ndk_zip="android-ndk-${NDK_VERSION}-linux.zip"

    # Download with retries
    for i in {1..3}; do
        if wget -q "https://dl.google.com/android/repository/${ndk_zip}"; then
            log_success "Downloaded ${ndk_zip} (attempt $i)"
            break
        fi
        log_warn "Download attempt $i failed, retrying..."
        sleep 5
    done

    # Verify download
    if [ ! -f "${ndk_zip}" ]; then
        log_error "Failed to download NDK after 3 attempts"
        exit 1
    fi

    log_info "Extracting NDK..."
    unzip -q "${ndk_zip}"

    # Verify extraction
    if [ ! -d "android-ndk-${NDK_VERSION}" ]; then
        log_error "NDK extraction failed - directory not found"
        ls -la
        exit 1
    fi

    rm "${ndk_zip}"

    export ANDROID_NDK_ROOT="$NDK_DIR"
    log_success "NDK setup complete: ${ANDROID_NDK_ROOT}"
}

get_ndk_toolchain() {
    local arch="$1"

    case "$arch" in
        arm64)
            echo "NDK_CC=${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android28-clang"
            echo "NDK_STRIP=${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
            echo "NDK_AR=${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-ar"
            echo "NDK_TARGET=aarch64-linux-android"
            ;;
        arm)
            echo "NDK_CC=${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin/armv7a-linux-androideabi28-clang"
            echo "NDK_STRIP=${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
            echo "NDK_AR=${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-ar"
            echo "NDK_TARGET=armv7a-linux-androideabi"
            ;;
        x86)
            echo "NDK_CC=${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin/i686-linux-android28-clang"
            echo "NDK_STRIP=${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
            echo "NDK_AR=${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-ar"
            echo "NDK_TARGET=i686-linux-android"
            ;;
        *)
            log_error "Unknown architecture: $arch"
            exit 1
            ;;
    esac
}

#####################################################################
# Busybox build
#####################################################################

build_busybox() {
    local arch="$1"

    log_info "Building busybox for ${arch}..."

    # Set up NDK toolchain
    eval "$(get_ndk_toolchain "$arch")"

    local busybox_src="${BUILD_DIR}/busybox-${BUSYBOX_VERSION}"

    # Download source if needed
    if [ ! -d "$busybox_src" ]; then
        log_info "Downloading busybox ${BUSYBOX_VERSION}..."
        cd "$BUILD_DIR"

        local busybox_tar="busybox-${BUSYBOX_VERSION}.tar.bz2"

        for i in {1..3}; do
            if wget -q "https://busybox.net/downloads/${busybox_tar}"; then
                log_success "Downloaded ${busybox_tar} (attempt $i)"
                break
            fi
            log_warn "Download attempt $i failed, retrying..."
            sleep 5
        done

        tar -xf "${busybox_tar}"
        rm "${busybox_tar}"
    fi

    # Create architecture-specific build directory
    local build_dir="${BUILD_DIR}/busybox-build-${arch}"
    rm -rf "$build_dir"
    cp -r "$busybox_src" "$build_dir"
    cd "$build_dir"

    log_info "Configuring busybox for Android..."

    # Use Android default config
    cp configs/android_defconfig .config

    # Fix cross-compiler prefix for NDK
    sed -i 's/CONFIG_CROSS_COMPILER_PREFIX=.*/CONFIG_CROSS_COMPILER_PREFIX=""/' .config

    # Process initial config
    yes '' | make ARCH="$arch" oldconfig > /dev/null 2>&1 || true

    # Disable problematic applets for Android bionic libc
    log_info "Disabling bionic-incompatible features..."

    # Swap features
    sed -i 's/CONFIG_SWAPON=y/# CONFIG_SWAPON is not set/g' .config
    sed -i 's/CONFIG_SWAPOFF=y/# CONFIG_SWAPOFF is not set/g' .config
    sed -i 's/CONFIG_FEATURE_SWAPON_DISCARD=y/# CONFIG_FEATURE_SWAPON_DISCARD is not set/g' .config
    sed -i 's/CONFIG_FEATURE_SWAPON_PRI=y/# CONFIG_FEATURE_SWAPON_PRI is not set/g' .config

    # Archival features
    sed -i 's/CONFIG_TAR=y/# CONFIG_TAR is not set/g' .config
    sed -i 's/CONFIG_FEATURE_TAR_TO_COMMAND=y/# CONFIG_FEATURE_TAR_TO_COMMAND is not set/g' .config
    sed -i 's/CONFIG_FEATURE_TAR_FROM=y/# CONFIG_FEATURE_TAR_FROM is not set/g' .config
    sed -i 's/CONFIG_AR=y/# CONFIG_AR is not set/g' .config
    sed -i 's/CONFIG_DPKG=y/# CONFIG_DPKG is not set/g' .config
    sed -i 's/CONFIG_DPKG_DEB=y/# CONFIG_DPKG_DEB is not set/g' .config
    sed -i 's/CONFIG_RPM=y/# CONFIG_RPM is not set/g' .config
    sed -i 's/CONFIG_RPM2CPIO=y/# CONFIG_RPM2CPIO is not set/g' .config

    # crypt.h features
    sed -i 's/CONFIG_LOGIN=y/# CONFIG_LOGIN is not set/g' .config
    sed -i 's/CONFIG_PASSWD=y/# CONFIG_PASSWD is not set/g' .config
    sed -i 's/CONFIG_SU=y/# CONFIG_SU is not set/g' .config
    sed -i 's/CONFIG_SULOGIN=y/# CONFIG_SULOGIN is not set/g' .config
    sed -i 's/CONFIG_VLOCK=y/# CONFIG_VLOCK is not set/g' .config
    sed -i 's/CONFIG_CRYPTPW=y/# CONFIG_CRYPTPW is not set/g' .config
    sed -i 's/CONFIG_CHPASSWD=y/# CONFIG_CHPASSWD is not set/g' .config
    sed -i 's/CONFIG_FEATURE_SHADOWPASSWDS=y/# CONFIG_FEATURE_SHADOWPASSWDS is not set/g' .config

    # utmp/wtmp features
    sed -i 's/CONFIG_FEATURE_UTMP=y/# CONFIG_FEATURE_UTMP is not set/g' .config
    sed -i 's/CONFIG_FEATURE_WTMP=y/# CONFIG_FEATURE_WTMP is not set/g' .config
    sed -i 's/CONFIG_LAST=y/# CONFIG_LAST is not set/g' .config
    sed -i 's/CONFIG_USERS=y/# CONFIG_USERS is not set/g' .config
    sed -i 's/CONFIG_WHO=y/# CONFIG_WHO is not set/g' .config
    sed -i 's/CONFIG_W=y/# CONFIG_W is not set/g' .config

    # Networking crypt features
    sed -i 's/CONFIG_FTPD=y/# CONFIG_FTPD is not set/g' .config
    sed -i 's/CONFIG_FEATURE_FTP_AUTHENTICATION=y/# CONFIG_FEATURE_FTP_AUTHENTICATION is not set/g' .config
    sed -i 's/CONFIG_FEATURE_HTTPD_AUTH_MD5=y/# CONFIG_FEATURE_HTTPD_AUTH_MD5 is not set/g' .config
    sed -i 's/CONFIG_FEATURE_HTTPD_BASIC_AUTH=y/# CONFIG_FEATURE_HTTPD_BASIC_AUTH is not set/g' .config
    sed -i 's/CONFIG_MKPASSWD=y/# CONFIG_MKPASSWD is not set/g' .config

    # Reprocess config
    yes '' | make ARCH="$arch" oldconfig > /dev/null 2>&1 || true

    # x86-specific TLS fixes
    if [ "$arch" = "x86" ]; then
        log_info "Applying x86-specific TLS fixes..."

        sed -i 's/CONFIG_TLS=y/# CONFIG_TLS is not set/g' .config
        sed -i 's/CONFIG_FEATURE_TLS_SHA1=y/# CONFIG_FEATURE_TLS_SHA1 is not set/g' .config
        sed -i 's/CONFIG_FEATURE_WGET_HTTPS=y/# CONFIG_FEATURE_WGET_HTTPS is not set/g' .config
        sed -i 's/CONFIG_FTPS=y/# CONFIG_FTPS is not set/g' .config
        sed -i 's/CONFIG_SHA1SUM=y/# CONFIG_SHA1SUM is not set/g' .config
        sed -i 's/CONFIG_SHA256SUM=y/# CONFIG_SHA256SUM is not set/g' .config
        sed -i 's/CONFIG_SHA512SUM=y/# CONFIG_SHA512SUM is not set/g' .config
        sed -i 's/CONFIG_MD5SUM=y/# CONFIG_MD5SUM is not set/g' .config
        sed -i 's/CONFIG_SSL_CLIENT=y/# CONFIG_SSL_CLIENT is not set/g' .config
        sed -i 's/CONFIG_FEATURE_SSL_CLIENT=y/# CONFIG_FEATURE_SSL_CLIENT is not set/g' .config
        sed -i 's/CONFIG_WGET=y/# CONFIG_WGET is not set/g' .config
        sed -i 's/CONFIG_FEATURE_WGET_STATUSBAR=y/# CONFIG_FEATURE_WGET_STATUSBAR is not set/g' .config
        sed -i 's/CONFIG_FEATURE_WGET_AUTHENTICATION=y/# CONFIG_FEATURE_WGET_AUTHENTICATION is not set/g' .config
        sed -i 's/CONFIG_FEATURE_WGET_LONG_OPTIONS=y/# CONFIG_FEATURE_WGET_LONG_OPTIONS is not set/g' .config
        sed -i 's/CONFIG_FEATURE_WGET_TIMEOUT=y/# CONFIG_FEATURE_WGET_TIMEOUT is not set/g' .config
        sed -i 's/CONFIG_BASE64=y/# CONFIG_BASE64 is not set/g' .config
        sed -i 's/CONFIG_UUENCODE=y/# CONFIG_UUENCODE is not set/g' .config
        sed -i 's/CONFIG_UUDECODE=y/# CONFIG_UUDECODE is not set/g' .config

        yes '' | make ARCH="$arch" oldconfig > /dev/null 2>&1 || true
        yes '' | make ARCH="$arch" oldconfig > /dev/null 2>&1 || true

        # Remove TLS source files
        log_info "Removing TLS source files for x86..."
        find networking -name "tls*.c" -type f -exec rm {} \; 2>/dev/null || true
        find networking -name "ssl_client.c" -type f -exec rm {} \; 2>/dev/null || true
        find networking -name "tls*.c" -type f -exec touch {} \; 2>/dev/null || true
        find networking -name "ssl_client.c" -type f -exec touch {} \; 2>/dev/null || true
    fi

    log_info "Applying Android compatibility source patches..."

    # Fix off_t size check
    if grep -q "struct BUG_off_t_size_is_misdetected" include/libbb.h 2>/dev/null; then
        sed -i '/struct BUG_off_t_size_is_misdetected/,/};/c\
/* Cross-compilation fix: off_t size check disabled */' include/libbb.h
    fi

    # Fix FAST_FUNC calling convention
    sed -i 's/__attribute__((regparm(3),stdcall))/__attribute__((regparm(3)))/g' include/platform.h 2>/dev/null || true

    # Remove strchrnul conflicts
    sed -i '/extern char \*strchrnul.*FAST_FUNC;/d' include/platform.h 2>/dev/null || true
    sed -i '/char\* FAST_FUNC strchrnul/,/^}/d' libbb/platform.c 2>/dev/null || true

    # Create stub for data_extract_to_command
    if [ ! -f "archival/libarchive/data_extract_to_command.c.bak" ]; then
        cp archival/libarchive/data_extract_to_command.c archival/libarchive/data_extract_to_command.c.bak 2>/dev/null || true
        cat > archival/libarchive/data_extract_to_command.c << 'EOF'
/* Stub for data_extract_to_command when FEATURE_TAR_TO_COMMAND is disabled */
#include "libbb.h"
#include "bb_archive.h"

void FAST_FUNC data_extract_to_command(archive_handle_t *archive_handle)
{
    bb_simple_error_msg_and_die("--to-command feature not compiled");
}
EOF
        sed -i 's/lib-\$(CONFIG_FEATURE_TAR_TO_COMMAND)/lib-y/' archival/libarchive/Kbuild 2>/dev/null || true
    fi

    # Create stub for pw_encrypt
    if [ ! -f "libbb/pw_encrypt.c.bak" ]; then
        cp libbb/pw_encrypt.c libbb/pw_encrypt.c.bak 2>/dev/null || true
        cat > libbb/pw_encrypt.c << 'EOF'
/* Stub for pw_encrypt when crypt.h features are disabled */
#include "libbb.h"

char* FAST_FUNC pw_encrypt(const char *clear, const char *salt, int cleanup)
{
    bb_simple_error_msg_and_die("password encryption not supported");
    return NULL;
}
EOF
    fi

    # Fix duplicate syscall symbols
    if [ -f "libbb/missing_syscalls.c" ]; then
        sed -i '/pid_t.*getsid/,/^}/d' libbb/missing_syscalls.c 2>/dev/null || true
        sed -i '/int.*adjtimex/,/^}/d' libbb/missing_syscalls.c 2>/dev/null || true
        sed -i '/int.*sethostname/,/^}/d' libbb/missing_syscalls.c 2>/dev/null || true
        sed -i '/getsid.*(/d' libbb/missing_syscalls.c 2>/dev/null || true
        sed -i '/adjtimex.*(/d' libbb/missing_syscalls.c 2>/dev/null || true
        sed -i '/sethostname.*(/d' libbb/missing_syscalls.c 2>/dev/null || true
    fi

    if [ -f "include/libbb.h" ]; then
        sed -i '/extern.*getsid.*(/d' include/libbb.h 2>/dev/null || true
        sed -i '/extern.*adjtimex.*(/d' include/libbb.h 2>/dev/null || true
        sed -i '/extern.*sethostname.*(/d' include/libbb.h 2>/dev/null || true
    fi

    log_info "Building busybox binary..."

    # Build
    make ARCH="$arch" \
         CC="${NDK_CC}" \
         AR="${NDK_AR}" \
         HOSTCC=gcc \
         HOSTCXX=g++ \
         STRIP="${NDK_STRIP}" \
         CFLAGS="-static -DHAVE_STRCHRNUL" \
         EXTRA_CFLAGS="-DHAVE_STRCHRNUL" \
         LDFLAGS="-static" \
         -j"$(nproc)" > /dev/null 2>&1

    # Verify binary
    file busybox

    # Strip
    ${NDK_STRIP} busybox

    # Copy to output
    mkdir -p "${SCRIPT_DIR}/${OUTPUT_DIR}"
    cp busybox "${SCRIPT_DIR}/${OUTPUT_DIR}/busybox_${arch}"

    log_success "Built busybox_${arch}"
}

#####################################################################
# iptables build
#####################################################################

build_iptables() {
    local arch="$1"

    log_info "Building iptables for ${arch}..."

    # Set up NDK toolchain
    eval "$(get_ndk_toolchain "$arch")"

    local iptables_src="${BUILD_DIR}/iptables-${IPTABLES_VERSION}"

    # Download source if needed
    if [ ! -d "$iptables_src" ]; then
        log_info "Downloading iptables ${IPTABLES_VERSION}..."
        cd "$BUILD_DIR"

        local iptables_tar="iptables-${IPTABLES_VERSION}.tar.xz"

        for i in {1..3}; do
            if wget -q "https://netfilter.org/projects/iptables/files/${iptables_tar}"; then
                log_success "Downloaded ${iptables_tar} (attempt $i)"
                break
            fi
            log_warn "Download attempt $i failed, retrying..."
            sleep 5
        done

        tar -xf "${iptables_tar}"
        rm "${iptables_tar}"
    fi

    # Create architecture-specific build directory
    local build_dir="${BUILD_DIR}/iptables-build-${arch}"
    rm -rf "$build_dir"
    cp -r "$iptables_src" "$build_dir"
    cd "$build_dir"

    log_info "Configuring iptables for Android..."

    # Set environment for cross-compilation
    export CC="${NDK_CC}"
    export STRIP="${NDK_STRIP}"

    # Set CFLAGS with lock path fix
    if [ "$arch" = "arm64" ]; then
        export CFLAGS="-static -Os -DANDROID -D_GNU_SOURCE -Wno-logical-op -Wno-unknown-warning-option -fno-emulated-tls -DXTABLES_LOCK_DIR='\"/data/local/tmp\"'"
    else
        export CFLAGS="-static -Os -DANDROID -D_GNU_SOURCE -Wno-logical-op -Wno-unknown-warning-option -DXTABLES_LOCK_DIR='\"/data/local/tmp\"'"
    fi

    export LDFLAGS="-static"
    export PKG_CONFIG_PATH=""

    # Configure
    ./configure \
        --host="${NDK_TARGET}" \
        --enable-static \
        --disable-shared \
        --disable-nftables \
        --disable-bpf-compiler \
        --disable-connlabel \
        --disable-devel \
        --with-kernel=/usr/include \
        --disable-libipq \
        --with-xtlockdir=/data/local/tmp > /dev/null 2>&1

    log_info "Building iptables binary..."

    # Remove problematic extensions
    rm -f extensions/libxt_cgroup.c

    # Fix prioritynames in LOG extension
    sed -i 's/prioritynames\[i\]\.c_name/0/g; s/prioritynames\[i\]\.c_val/0/g' extensions/libxt_LOG.c 2>/dev/null || true

    # Fix hardcoded lock path
    find . -name "*.c" -o -name "*.h" | xargs grep -l "/run/xtables.lock" 2>/dev/null | while read file; do
        sed -i 's|/run/xtables\.lock|/data/local/tmp/xtables.lock|g' "$file"
    done

    # Build
    make -j"$(nproc)" V=1 > /dev/null 2>&1

    # Copy binaries
    if [ -f "iptables/xtables-legacy-multi" ]; then
        # Prefer legacy multi-tool
        cp "iptables/xtables-legacy-multi" "${SCRIPT_DIR}/${OUTPUT_DIR}/iptables_${arch}"
        cp "iptables/xtables-legacy-multi" "${SCRIPT_DIR}/${OUTPUT_DIR}/ip6tables_${arch}"
    else
        # Fallback
        cp "iptables/iptables" "${SCRIPT_DIR}/${OUTPUT_DIR}/iptables_${arch}"
        cp "iptables/ip6tables" "${SCRIPT_DIR}/${OUTPUT_DIR}/ip6tables_${arch}"
    fi

    # Verify and strip
    file "${SCRIPT_DIR}/${OUTPUT_DIR}/iptables_${arch}"
    file "${SCRIPT_DIR}/${OUTPUT_DIR}/ip6tables_${arch}"

    ${NDK_STRIP} "${SCRIPT_DIR}/${OUTPUT_DIR}/iptables_${arch}"
    ${NDK_STRIP} "${SCRIPT_DIR}/${OUTPUT_DIR}/ip6tables_${arch}"

    log_success "Built iptables_${arch} and ip6tables_${arch}"
}

#####################################################################
# nflog build
#####################################################################

build_nflog() {
    local arch="$1"

    log_info "Building nflog for ${arch}..."

    local nflog_src="${SCRIPT_DIR}/external/nflog"

    if [ ! -d "$nflog_src" ]; then
        log_error "nflog source not found at $nflog_src"
        exit 1
    fi

    cd "$nflog_src"

    # Determine cross-compiler
    local cc
    local strip_tool
    case "$arch" in
        arm)
            cc="arm-linux-gnueabihf-gcc"
            strip_tool="arm-linux-gnueabihf-strip"
            ;;
        arm64)
            cc="aarch64-linux-gnu-gcc"
            strip_tool="aarch64-linux-gnu-strip"
            ;;
        x86)
            cc="i686-linux-gnu-gcc"
            strip_tool="i686-linux-gnu-strip"
            ;;
    esac

    # Build
    $cc -static \
        -I. -Ilibmnl \
        -D_GNU_SOURCE \
        -o "nflog_${arch}" \
        nflog.c attr.c callback.c nlmsg.c socket.c

    # Verify and strip
    file "nflog_${arch}"
    $strip_tool "nflog_${arch}"

    # Copy to output
    mkdir -p "${SCRIPT_DIR}/${OUTPUT_DIR}"
    cp "nflog_${arch}" "${SCRIPT_DIR}/${OUTPUT_DIR}/nflog_${arch}"
    rm "nflog_${arch}"

    log_success "Built nflog_${arch}"
}

#####################################################################
# Main execution
#####################################################################

main() {
    log_info "AFWall+ Binary Build Script"
    log_info "Building binaries for: ${BUILD_ARCH}"
    log_info "Output directory: ${OUTPUT_DIR}"
    echo ""

    # Check dependencies
    check_dependencies

    # Create build directory
    mkdir -p "$BUILD_DIR"

    # Setup NDK
    setup_ndk

    # Determine architectures to build
    local archs
    if [ "$BUILD_ARCH" = "all" ]; then
        archs=("arm" "arm64" "x86")
    else
        archs=("$BUILD_ARCH")
    fi

    # Build for each architecture
    for arch in "${archs[@]}"; do
        echo ""
        log_info "========================================="
        log_info "Building for architecture: ${arch}"
        log_info "========================================="

        build_busybox "$arch"
        build_iptables "$arch"
        build_nflog "$arch"
    done

    echo ""
    log_info "========================================="
    log_info "Build Summary"
    log_info "========================================="

    # List built binaries
    if [ -d "${SCRIPT_DIR}/${OUTPUT_DIR}" ]; then
        ls -lh "${SCRIPT_DIR}/${OUTPUT_DIR}/"
    fi

    echo ""
    log_success "All binaries built successfully!"
    log_info "Binaries are in: ${SCRIPT_DIR}/${OUTPUT_DIR}/"
    echo ""
    log_info "To use these binaries in AFWall+, copy them to:"
    log_info "  app/src/main/res/raw/"
}

# Run main function
main
