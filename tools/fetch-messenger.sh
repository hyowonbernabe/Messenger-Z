#!/usr/bin/env bash
#
# Fetch the latest stable Messenger (com.facebook.orca) from uptodown.
# APKMirror is Cloudflare-blocked for headless clients; uptodown serves a plain APK.
#
#   fetch-messenger.sh version            -> prints the latest version string, exits
#   fetch-messenger.sh download <out.apk> -> downloads the latest APK to <out.apk>, prints version
#
# Env overrides: PYTHON (default python3).
set -euo pipefail

MODE="${1:-download}"
OUT="${2:-}"
PYTHON="${PYTHON:-python3}"

UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
DATA_CODE="18495" # uptodown app id for Facebook Messenger
BASE="https://facebook-messenger.en.uptodown.com/android"

# 1. Latest version + its fileID from the versions JSON API (data[0] = newest).
api="$(curl -s -A "$UA" --max-time 60 "${BASE}/apps/${DATA_CODE}/versions/1")"
info="$("$PYTHON" - "$api" <<'PY'
import json, sys
d = json.loads(sys.argv[1])
e = d["data"][0]
print(e["version"], e["fileID"])
PY
)"
VERSION="$(echo "$info" | cut -d' ' -f1)"
FILEID="$(echo "$info" | cut -d' ' -f2)"
[ -n "$VERSION" ] && [ -n "$FILEID" ] || { echo "failed to read latest version from uptodown" >&2; exit 1; }

if [ "$MODE" = "version" ]; then
    echo "$VERSION"
    exit 0
fi

[ -n "$OUT" ] || { echo "usage: fetch-messenger.sh download <out.apk>" >&2; exit 1; }

# 2. Download page -> the real token is the longest data-url attribute on the page.
page="$(curl -s -A "$UA" --max-time 60 "${BASE}/download/${FILEID}")"
TOKEN="$(grep -oE 'data-url="[^"]*"' <<<"$page" \
    | sed 's/data-url="//; s/"$//' \
    | awk '{ print length, $0 }' | sort -rn | head -1 | cut -d' ' -f2-)"
[ -n "$TOKEN" ] || { echo "failed to extract download token" >&2; exit 1; }

# 3. Download from the CDN.
echo "Downloading Messenger ${VERSION} ..." >&2
curl -s -A "$UA" --max-time 900 -L -o "$OUT" "https://dw.uptodown.com/dwn/${TOKEN}"

# 4. Sanity check: must be a ZIP/APK (PK magic).
if [ "$(head -c2 "$OUT")" != "PK" ]; then
    echo "downloaded file is not an APK (no PK magic)" >&2
    exit 1
fi
echo "$VERSION"
