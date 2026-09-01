#!/usr/bin/env bash
#
# Fetch the latest stable Messenger (com.facebook.orca).
#
# Source history: APKMirror is Cloudflare-blocked for headless clients. Uptodown worked
# until it put its download links behind Cloudflare Turnstile + JS. APKPure exposes a plain
# redirect endpoint that serves the APK directly, and is the primary source now; the
# uptodown JSON API is kept as a version-only fallback.
#
#   fetch-messenger.sh version            -> prints the latest version string, exits
#   fetch-messenger.sh download <out.apk> -> downloads the latest APK to <out.apk>, prints version
#
set -euo pipefail

MODE="${1:-download}"
OUT="${2:-}"

UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
PKG="com.facebook.orca"
APKPURE_URL="https://d.apkpure.com/b/APK/${PKG}?version=latest"

# --- version -----------------------------------------------------------------
# APKPure puts it in the Content-Disposition filename: Messenger_576.0.0.47.92_APKPure.apk
APKPURE_HEADERS="$(curl -s -A "$UA" -I -L --max-time 120 "$APKPURE_URL" || true)"
VERSION="$(grep -i '^content-disposition:' <<<"$APKPURE_HEADERS" | grep -oE '[0-9]+(\.[0-9]+){2,}' | head -1 || true)"
SOURCE="apkpure"

if [ -z "$VERSION" ]; then
    echo "apkpure gave no version; response headers were:" >&2
    printf '%s\n' "$APKPURE_HEADERS" | head -20 >&2
    # Fallback: uptodown's versions JSON (data[0] = newest). Version only — its download
    # links are Turnstile-gated, so this cannot serve the APK itself.
    api="$(curl -s -A "$UA" --max-time 60 "https://facebook-messenger.en.uptodown.com/android/apps/18495/versions/1" || true)"
    VERSION="$(grep -oE '"version":"[0-9][0-9.]*"' <<<"$api" | head -1 | grep -oE '[0-9][0-9.]*' || true)"
    SOURCE="uptodown"
fi

[ -n "$VERSION" ] || { echo "failed to determine latest Messenger version from any source" >&2; exit 1; }

if [ "$MODE" = "version" ]; then
    echo "$VERSION"
    exit 0
fi

# --- download ----------------------------------------------------------------
[ -n "$OUT" ] || { echo "usage: fetch-messenger.sh download <out.apk>" >&2; exit 1; }

echo "Downloading Messenger ${VERSION} (version via ${SOURCE}) ..." >&2
code="$(curl -s -A "$UA" --max-time 900 -L -o "$OUT" -w '%{http_code}' "$APKPURE_URL" || true)"

# Sanity check: must be a ZIP/APK (PK magic).
if [ "$(head -c2 "$OUT" 2>/dev/null)" != "PK" ]; then
    echo "apkpure download failed (http $code, $(wc -c <"$OUT" 2>/dev/null || echo 0) bytes, no PK magic)" >&2
    head -c 400 "$OUT" 2>/dev/null >&2 || true
    exit 1
fi
echo "$VERSION"
