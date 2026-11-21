#!/bin/sh
# Prepares packaging/flatpak/stage/ for flatpak-builder: the app image from the portable tarball, the libraries from bundle-libs.sh, the metadata.
# Usage: stage.sh <version> <Vela-<version>-linux-x64.tar.gz> <libs dir from bundle-libs.sh>
set -eu
version=$1; tarball=$2; libs=$3
here=$(cd "$(dirname "$0")" && pwd)
stage="$here/stage"
rm -rf "$stage"; mkdir -p "$stage/app" "$stage/libs" "$stage/bin"
tar xzf "$tarball" -C "$stage/app" --strip-components=1
[ -x "$stage/app/bin/vela" ] || { echo "no bin/vela in $tarball" >&2; exit 1; }
cp -a "$libs/lib/." "$stage/libs/"
cp -a "$libs/bin/." "$stage/bin/"
cp "$here/vela-flatpak" "$here/io.github.greenaltambe.Vela.desktop" "$stage/"
cp "$here/../../docs/vela-icon-512.png" "$stage/"
sed -e "s/@VERSION@/$version/" -e "s/@DATE@/$(date -u +%Y-%m-%d)/" "$here/io.github.greenaltambe.Vela.metainfo.xml.in" > "$stage/io.github.greenaltambe.Vela.metainfo.xml"
echo "staged for $version"
