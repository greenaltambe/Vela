#!/bin/sh
# Collects libmpv, ffmpeg and notify-send from this (Ubuntu 24.04) machine with the libraries they need, except the ones the Freedesktop runtime provides
# (the C library, GPU/X11/Wayland, audio, fonts). Usage: bundle-libs.sh <output dir>. Needs: libmpv2 ffmpeg libnotify-bin installed.
set -eu
out=$1
mkdir -p "$out/lib" "$out/bin"
exclude='^(ld-linux.*|libc|libm|libdl|libpthread|librt|libresolv|libutil|libnsl|libcrypt|libanl|libBrokenLocale|libgcc_s|libstdc\+\+|libz|libGL|libGLX|libGLdispatch|libEGL|libGLESv2|libOpenGL|libgbm|libdrm.*|libva.*|libvdpau|libvulkan|libX11|libX11-xcb|libXext|libXrender|libXfixes|libXi|libXcursor|libXrandr|libXinerama|libXxf86vm|libXss|libXv|libxcb.*|libXau|libXdmcp|libwayland.*|libxkbcommon|libasound|libpulse.*|libfontconfig|libfreetype|libexpat|libdbus-1|libsystemd)\.so'
mpv=$(ldconfig -p | awk '/libmpv\.so\.2 /{print $NF; exit}')
[ -n "$mpv" ] || { echo "libmpv.so.2 not found: install libmpv2" >&2; exit 1; }
cp -L "$mpv" "$out/lib/libmpv.so.2"
cp -L "$(command -v ffmpeg)" "$out/bin/ffmpeg"
cp -L "$(command -v notify-send)" "$out/bin/notify-send"
for root in "$mpv" "$(command -v ffmpeg)" "$(command -v notify-send)"; do
  ldd "$root" | awk '/=> \//{print $3}' | while read -r lib; do
    name=$(basename "$lib")
    echo "$name" | grep -Eq "$exclude" && continue
    [ -e "$out/lib/$name" ] || cp -L "$lib" "$out/lib/$name"
  done
done
# Libraries of libraries (the first pass only saw direct dependencies of the three roots): once more over what was collected.
for pass in 1 2; do
  for f in "$out"/lib/*.so*; do
    ldd "$f" 2>/dev/null | awk '/=> \//{print $3}' | while read -r lib; do
      name=$(basename "$lib")
      echo "$name" | grep -Eq "$exclude" && continue
      [ -e "$out/lib/$name" ] || cp -L "$lib" "$out/lib/$name"
    done
  done
done
ls "$out/lib" | wc -l | sed 's/$/ libraries bundled/'
du -sh "$out"
