#!/usr/bin/env bash
#
# Fetch the latest stable Messenger (com.facebook.orca) from APKPure.
# APKMirror is Cloudflare-blocked for headless clients. Uptodown used to work but now
# gates its download links behind Cloudflare Turnstile + JS, so the old scrape is dead.
# APKPure exposes a plain redirect endpoint that serves the APK directly.
#
#   fetch-messenger.sh version            -> prints the latest version string, exits
#   fetch-messenger.sh download <out.apk> -> downloads the latest APK to <out.apk>, prints version
#
set -euo pipefail

MODE="${1:-download}"
OUT="${2:-}"

UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
URL="https://d.apkpure.com/b/APK/com.facebook.orca?version=latest"

# Version comes from the Content-Disposition filename: Messenger_576.0.0.47.92_APKPure.apk
VERSION="$(curl -s -A "$UA" -I -L --max-time 120 "$URL" \
    | grep -i '^content-disposition:' \
    | grep -oE '[0-9]+(\.[0-9]+)+' | head -1)"
[ -n "$VERSION" ] || { echo "failed to read latest version from apkpure" >&2; exit 1; }

if [ "$MODE" = "version" ]; then
    echo "$VERSION"
    exit 0
fi

[ -n "$OUT" ] || { echo "usage: fetch-messenger.sh download <out.apk>" >&2; exit 1; }

echo "Downloading Messenger ${VERSION} ..." >&2
curl -s -A "$UA" --max-time 900 -L -o "$OUT" "$URL"

# Sanity check: must be a ZIP/APK (PK magic).
if [ "$(head -c2 "$OUT")" != "PK" ]; then
    echo "downloaded file is not an APK (no PK magic)" >&2
    exit 1
fi
echo "$VERSION"
