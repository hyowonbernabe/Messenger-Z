#!/usr/bin/env bash
#
# Fetch the latest stable Facebook (com.facebook.katana) as one installable APK.
#
# APKPure serves Facebook as an XAPK (base APK + density/language splits); the base and its
# config splits are merged into a single APK with APKEditor.
#
#   fetch-facebook.sh version            -> prints the latest version string, exits
#   fetch-facebook.sh download <out.apk> -> downloads + merges the latest APK to <out.apk>, prints version
#
set -euo pipefail

MODE="${1:-download}"
OUT="${2:-}"

UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
PKG="com.facebook.katana"
URL="https://d.apkpure.com/b/APK/${PKG}?version=latest"
TOOLS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Version is in the Content-Disposition filename: Facebook_582.0.0.50.54_APKPure.xapk
HEADERS="$(curl -s -A "$UA" -I -L --max-time 120 "$URL" || true)"
VERSION="$(grep -i '^content-disposition:' <<<"$HEADERS" | grep -oE '[0-9]+(\.[0-9]+){2,}' | head -1 || true)"
[ -n "$VERSION" ] || { echo "apkpure gave no Facebook version; headers were:" >&2; printf '%s\n' "$HEADERS" | head -20 >&2; exit 1; }

if [ "$MODE" = "version" ]; then
    echo "$VERSION"
    exit 0
fi

[ -n "$OUT" ] || { echo "usage: fetch-facebook.sh download <out.apk>" >&2; exit 1; }
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "Downloading Facebook ${VERSION} ..." >&2
code="$(curl -s -A "$UA" --max-time 900 -L -o "$TMP/fb.xapk" -w '%{http_code}' "$URL" || true)"
[ "$(head -c2 "$TMP/fb.xapk" 2>/dev/null)" = "PK" ] || { echo "apkpure download failed (http $code)" >&2; exit 1; }

mkdir -p "$TMP/parts"
unzip -q -o "$TMP/fb.xapk" "${PKG}.apk" 'config.*.apk' -d "$TMP/parts"
java -jar "$TOOLS_DIR/apkeditor.jar" m -i "$TMP/parts" -o "$OUT" -f >&2
echo "$VERSION"
