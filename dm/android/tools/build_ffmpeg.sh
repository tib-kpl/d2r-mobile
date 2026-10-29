#!/bin/bash
# Minimal, dynamically linked LGPL FFmpeg for Android video playback. No game data.
set -euo pipefail
root=${1:?Usage: build_ffmpeg.sh output-root android-ndk}
ndk=${2:?Supply the Linux Android NDK directory}
mkdir -p "$root"
root=$(cd "$root" && pwd)
ndk=$(cd "$ndk" && pwd)
version=9.0.2
archive="ffmpeg-$version.tar.xz"
sha=8c3850283eb25fa026482078a04051e0be17347b09ef81a0849bec15a96e002e
toolchain="$ndk/toolchains/llvm/prebuilt/linux-x86_64"
test -x "$toolchain/bin/aarch64-linux-android31-clang"
recipe=$(cat "$0" "$ndk/source.properties" | sha256sum | cut -d' ' -f1)
out="$root/out"
if [ -f "$out/recipe.sha256" ] && [ "$(cat "$out/recipe.sha256")" = "$recipe" ]; then
  echo "FFmpeg Android already built: $out"; exit 0
fi
mkdir -p "$root/source" "$root/build" "$out"
if [ ! -f "$root/source/$archive" ]; then
  curl -fL --retry 3 "https://ffmpeg.org/releases/$archive" -o "$root/source/$archive"
fi
printf '%s  %s\n' "$sha" "$root/source/$archive" | sha256sum -c -
if [ ! -f "$root/source/ffmpeg-$version/configure" ]; then
  tar -xf "$root/source/$archive" -C "$root/source"
fi
cd "$root/build"
"$root/source/ffmpeg-$version/configure" \
  --prefix="$out" --target-os=android --arch=aarch64 --enable-cross-compile \
  --cc="$toolchain/bin/aarch64-linux-android31-clang" \
  --ar="$toolchain/bin/llvm-ar" --ranlib="$toolchain/bin/llvm-ranlib" \
  --nm="$toolchain/bin/llvm-nm" --strip="$toolchain/bin/llvm-strip" \
  --extra-ldflags='-Wl,-z,max-page-size=16384' \
  --disable-everything --disable-programs --disable-doc --disable-debug \
  --disable-avdevice --disable-avfilter --disable-swresample \
  --disable-network --disable-autodetect --disable-zlib --disable-iconv \
  --disable-gpl --disable-nonfree --disable-static --disable-symver \
  --enable-shared --enable-pic --enable-pthreads \
  --enable-avformat --enable-avcodec --enable-avutil --enable-swscale \
  --enable-demuxer=matroska --enable-decoder=vp8 --enable-parser=vp8
make -j"${J:-$(nproc)}"
make install
for library in avformat avcodec swscale avutil; do test -f "$out/lib/lib$library.so"; done
cp config.h "$out/config.h"
cp ffbuild/config.mak "$out/config.mak"
cp "$root/source/ffmpeg-$version/COPYING.LGPLv2.1" "$out/COPYING.LGPLv2.1"
printf '%s\n' "$recipe" > "$out/recipe.sha256"
echo "FFmpeg Android built: $out"
