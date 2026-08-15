#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_HOME:=$HOME/Android/Sdk}"
: "${NDK_HOME:=$ANDROID_HOME/ndk/28.2.13676358}"
# Override to build for an emulator: TARGET=x86_64-linux-android ./build.sh
: "${TARGET:=aarch64-linux-android}"
API=24
case "$TARGET" in
  aarch64-linux-android)     ABI=arm64-v8a ;;
  x86_64-linux-android)      ABI=x86_64 ;;
  armv7-linux-androideabi)   ABI=armeabi-v7a ;;
  i686-linux-android)        ABI=x86 ;;
  *) echo "unknown TARGET $TARGET" >&2; exit 1 ;;
esac
TOOLCHAIN="$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
# Cargo wants the linker env var name in SCREAMING_SNAKE of the target triple.
LINKER_VAR="CARGO_TARGET_$(echo "$TARGET" | tr 'a-z-' 'A-Z_')_LINKER"
CLANG="$TOOLCHAIN/${TARGET}${API}-clang"
[ -x "$CLANG" ] || CLANG="$TOOLCHAIN/${TARGET/armv7-linux-androideabi/armv7a-linux-androideabi}${API}-clang"
export "$LINKER_VAR=$CLANG"
export AR="$TOOLCHAIN/llvm-ar"
export CARGO_PROFILE_DEV_STRIP=debuginfo
cargo build --target "$TARGET"
JNI_DIR=android/app/src/main/jniLibs/$ABI
mkdir -p "$JNI_DIR"
cp "target/$TARGET/debug/libtaoraw.so" "$JNI_DIR/libtaoraw.so"
echo "staged $JNI_DIR/libtaoraw.so"
