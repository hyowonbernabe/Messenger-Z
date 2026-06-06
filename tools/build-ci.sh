#!/usr/bin/env bash
#
# Messenger Z build pipeline (bash port of tools/repack-messenger.ps1), for CI and desktop.
#
#   stock Messenger APK
#     -> APKEditor decode (-t xml -dex : raw dex so Meta integrity checks don't trip)
#     -> strip the two Facebook-conflicting <permission> declarations
#     -> APKEditor build
#     -> uber-apk-signer (debug cert)
#     -> LSPatch embed the Messenger Z module (sigbypass lv2)
#
# Usage: build-ci.sh <messenger-stock.apk> <module.apk> <out-dir>
# Prints the final lspatched APK path on the last line.
#
# Env overrides: PYTHON (default python3). `java` must be on PATH.
set -euo pipefail

MESSENGER_APK="${1:?usage: build-ci.sh <messenger.apk> <module.apk> <outdir>}"
MODULE_APK="${2:?module apk required}"
OUT_DIR="${3:?out dir required}"
PYTHON="${PYTHON:-python3}"

TOOLS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="${TOOLS_DIR}/work"
APKEDITOR="${TOOLS_DIR}/apkeditor.jar"
SIGNER="${TOOLS_DIR}/uber-apk-signer.jar"
LSPATCH="${TOOLS_DIR}/lspatch/lspatch.jar"

for j in "$APKEDITOR" "$SIGNER" "$LSPATCH"; do
    [ -f "$j" ] || { echo "missing tool jar: $j" >&2; exit 1; }
done

# Signature-level permissions Messenger shares with com.facebook.katana (the Facebook app).
CONFLICTING_PERMS=(
    "com.facebook.permission.prod.FB_APP_COMMUNICATION"
    "com.facebook.receiver.permission.ACCESS"
)

DECODE="${WORK}/decoded"
REBUILT="${WORK}/messenger-rebuilt.apk"
SIGNED_DIR="${WORK}/signed"

rm -rf "$WORK"
mkdir -p "$WORK" "$OUT_DIR"

echo "[1/5] APKEditor decode (raw dex)"
java -jar "$APKEDITOR" d -t xml -dex -i "$MESSENGER_APK" -o "$DECODE" -f

echo "[2/5] strip conflicting <permission> declarations"
"$PYTHON" "${TOOLS_DIR}/strip-perms.py" "${DECODE}/AndroidManifest.xml" "${CONFLICTING_PERMS[@]}"

echo "[3/5] APKEditor build"
java -jar "$APKEDITOR" b -i "$DECODE" -o "$REBUILT" -f

echo "[4/5] uber-apk-signer"
rm -rf "$SIGNED_DIR"; mkdir -p "$SIGNED_DIR"
java -jar "$SIGNER" --apks "$REBUILT" --out "$SIGNED_DIR"
SIGNED_APK="$(ls "$SIGNED_DIR"/*.apk | head -1)"
[ -f "$SIGNED_APK" ] || { echo "no signed apk produced" >&2; exit 1; }

echo "[5/5] LSPatch embed module"
java -jar "$LSPATCH" "$SIGNED_APK" -m "$MODULE_APK" -f -l 2 -o "$OUT_DIR" -v

FINAL="$(ls -t "$OUT_DIR"/*lspatched.apk 2>/dev/null | head -1)"
[ -f "$FINAL" ] || { echo "lspatch produced no output" >&2; exit 1; }
echo "DONE: $FINAL"
echo "$FINAL"
