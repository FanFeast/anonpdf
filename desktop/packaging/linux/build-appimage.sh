#!/usr/bin/env bash
# Builds the portable Linux download: an AppImage inside a .tar.gz.
#
#   desktop/packaging/linux/build-appimage.sh <version> <output-dir>
#
# Expects `./gradlew :desktop:createReleaseDistributable` to have run.
#
# Why a .tar.gz around the AppImage: browsers drop the executable bit on
# download, which is why AppImages usually need "make executable" first. tar
# keeps it, so the user extracts and double-clicks.
#
# The AppImage runtime is the static one (no libfuse2 needed, which current
# Ubuntu no longer ships), and both it and appimagetool are pinned by checksum:
# nothing unverified ends up inside the download.
set -euo pipefail

VERSION="$1"
OUT="$(realpath -m "$2")"
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../../.." && pwd)"
DIST="$ROOT/desktop/build/compose/binaries/main-release/app/AnonPDF"
WORK="$ROOT/desktop/build/appimage"
TOOLS="$WORK/tools"

APPIMAGETOOL_URL="https://github.com/AppImage/appimagetool/releases/download/1.9.1/appimagetool-x86_64.AppImage"
APPIMAGETOOL_SHA256="ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0"
RUNTIME_URL="https://github.com/AppImage/type2-runtime/releases/download/20251108/runtime-x86_64"
RUNTIME_SHA256="2fca8b443c92510f1483a883f60061ad09b46b978b2631c807cd873a47ec260d"

fetch() { # url sha256 destination
  if [ ! -f "$3" ] || ! echo "$2  $3" | sha256sum -c --status; then
    curl -fsSL -o "$3" "$1"
  fi
  echo "$2  $3" | sha256sum -c --quiet
}

test -x "$DIST/bin/AnonPDF" || { echo "No release distributable at $DIST" >&2; exit 1; }
rm -rf "$WORK/AppDir" && mkdir -p "$TOOLS" "$WORK/AppDir" "$OUT"
fetch "$APPIMAGETOOL_URL" "$APPIMAGETOOL_SHA256" "$TOOLS/appimagetool"
fetch "$RUNTIME_URL" "$RUNTIME_SHA256" "$TOOLS/runtime-x86_64"
chmod +x "$TOOLS/appimagetool"

APPDIR="$WORK/AppDir"
cp -a "$DIST" "$APPDIR/AnonPDF"
cp "$HERE/AppRun" "$APPDIR/AppRun"
cp "$HERE/anonpdf.desktop" "$APPDIR/anonpdf.desktop"
cp "$ROOT/desktop/packaging/anonpdf.png" "$APPDIR/anonpdf.png"

IMAGE="$WORK/AnonPDF-x86_64.AppImage"
# appimagetool is itself an AppImage; extract-and-run means it needs no FUSE either.
APPIMAGE_EXTRACT_AND_RUN=1 ARCH=x86_64 "$TOOLS/appimagetool" --no-appstream \
  --runtime-file "$TOOLS/runtime-x86_64" "$APPDIR" "$IMAGE" >/dev/null

STAGE="$WORK/AnonPDF-$VERSION"
rm -rf "$STAGE" && mkdir -p "$STAGE"
cp "$IMAGE" "$STAGE/AnonPDF.AppImage"
chmod 755 "$STAGE/AnonPDF.AppImage"
cat > "$STAGE/README.txt" <<TXT
AnonPDF $VERSION for Linux

Double-click AnonPDF.AppImage to start. Nothing to install: delete this folder
and it is gone. Your documents never leave this computer.
TXT
TARBALL="$OUT/AnonPDF-$VERSION-linux-x86_64.tar.gz"
tar -C "$WORK" -czf "$TARBALL" "AnonPDF-$VERSION"
echo "$TARBALL"
