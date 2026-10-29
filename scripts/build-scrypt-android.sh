#!/usr/bin/env bash
set -euo pipefail

# Experimental cross-build of cpuminer-opt for Android ARM64. cpuminer-opt
# upstream documents Linux ARM64 support, but does not officially support Android.
# This script is pinned for reproducibility and is run only in GitHub Actions.

NDK_VERSION="27.2.12479018"
API="26"
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$ANDROID_HOME" ]]; then echo "ANDROID_HOME is not set" >&2; exit 1; fi
NDK="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/$NDK_VERSION}"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
if [[ ! -x "$TOOLCHAIN/bin/aarch64-linux-android${API}-clang" ]]; then
  echo "Android NDK $NDK_VERSION not found at $NDK" >&2; exit 1
fi

ROOT="$(git rev-parse --show-toplevel)"
BUILD="${RUNNER_TEMP:-/tmp}/x1-scrypt-android-build"
PREFIX="$BUILD/prefix"
HOST="aarch64-linux-android"
BUILD_TRIPLET="$(gcc -dumpmachine)"
CC="$TOOLCHAIN/bin/aarch64-linux-android${API}-clang"
CXX="$TOOLCHAIN/bin/aarch64-linux-android${API}-clang++"
AR="$TOOLCHAIN/bin/llvm-ar"
RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
STRIP="$TOOLCHAIN/bin/llvm-strip"
export CC CXX AR RANLIB
mkdir -p "$BUILD" "$PREFIX"

# Build GMP statically for the Android ABI, so the app does not need a system GMP library.
cd "$BUILD"
curl --fail --location --retry 3 --retry-all-errors \
  "https://ftp.gnu.org/gnu/gmp/gmp-6.3.0.tar.xz" -o gmp-6.3.0.tar.xz
tar -xf gmp-6.3.0.tar.xz
cd "$BUILD/gmp-6.3.0"
CC="$CC" AR="$AR" RANLIB="$RANLIB" CFLAGS="-O2 -fPIC" \
  ./configure --build="$BUILD_TRIPLET" --host="$HOST" --prefix="$PREFIX" \
    --disable-shared --enable-static --disable-assembly --disable-cxx
make -j2
make install

# cpuminer-opt includes curl/curl.h; build a static, non-TLS curl for HTTP RPC support.
# This Scrypt integration deliberately accepts only stratum+tcp URLs (no pool TLS yet).
cd "$BUILD"
curl --fail --location --retry 3 --retry-all-errors \
  "https://curl.se/download/curl-8.16.0.tar.xz" -o curl-8.16.0.tar.xz
tar -xf curl-8.16.0.tar.xz
cd "$BUILD/curl-8.16.0"
CC="$CC" AR="$AR" RANLIB="$RANLIB" CFLAGS="-O2 -fPIC" \
  ./configure --build="$BUILD_TRIPLET" --host="$HOST" --prefix="$PREFIX" \
    --disable-shared --enable-static --without-ssl --without-zlib \
    --disable-ftp --disable-file --disable-ldap --disable-ldaps --disable-rtsp \
    --disable-dict --disable-telnet --disable-tftp --disable-pop3 --disable-imap \
    --disable-smtp --disable-gopher --disable-mqtt --disable-manual \
    --without-libpsl --without-brotli --without-zstd
make -j2
make install

# Build a known cpuminer-opt source revision with Scrypt/NEON support.
cd "$BUILD"
git init cpuminer-opt
cd cpuminer-opt
git remote add origin https://github.com/JayDDee/cpuminer-opt.git
git fetch --depth 1 origin b34565bfaca5da9ed894a326e3ba0dfe459550a4
git checkout --detach FETCH_HEAD
test "$(git rev-parse HEAD)" = "b34565bfaca5da9ed894a326e3ba0dfe459550a4"
./autogen.sh
CPPFLAGS="-I$PREFIX/include" \
LDFLAGS="-L$PREFIX/lib" \
CFLAGS="-O3 -fPIC -flax-vector-conversions -D__ANDROID_API__=$API" \
CXXFLAGS="-O2 -fPIC -D__ANDROID_API__=$API" \
  ./configure --build="$BUILD_TRIPLET" --host="$HOST" --with-curl="$PREFIX" --disable-assembly
make -j2

test -x ./cpuminer
echo "=== Diagnóstico do binário cpuminer ==="
test -x ./cpuminer
file ./cpuminer
"$TOOLCHAIN/bin/llvm-readelf" -l ./cpuminer
mkdir -p "$ROOT/app/src/main/jniLibs/arm64-v8a"
"$STRIP" --strip-unneeded ./cpuminer
cp ./cpuminer "$ROOT/app/src/main/jniLibs/arm64-v8a/libcpuminer.so"
chmod 755 "$ROOT/app/src/main/jniLibs/arm64-v8a/libcpuminer.so"
# Package the Android NDK C++ runtime required by cpuminer.
CXX_RUNTIME="$TOOLCHAIN/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so"

if [[ ! -f "$CXX_RUNTIME" ]]; then
  echo "Android NDK libc++_shared.so not found: $CXX_RUNTIME" >&2
  exit 1
fi

cp "$CXX_RUNTIME" "$ROOT/app/src/main/jniLibs/arm64-v8a/libc++_shared.so"
chmod 644 "$ROOT/app/src/main/jniLibs/arm64-v8a/libc++_shared.so"

echo "Packaged Android C++ runtime for ARM64."
file "$ROOT/app/src/main/jniLibs/arm64-v8a/libcpuminer.so"
echo "Built experimental Android ARM64 Scrypt engine: app/src/main/jniLibs/arm64-v8a/libcpuminer.so"
