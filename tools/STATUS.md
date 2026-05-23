# Session Status — v1.8.0 (Messenger v562 target)

## Goal

Re-patch Messenger Z on top of Messenger `com.facebook.orca` v562.0.0.53.83 to fix
the **"This app version is deprecated"** error Meta now returns when initiating a
call from the previously-patched Messenger v82 base.

## Workflow (correct)

Patch **on the phone** via LSPatch Manager (the working setup):
- LSPatch Manager: API 93, v0.7 build 430, Framework 1.10.1 (7137)
- Settings used: Integrated mode, Sigbypass lv2, Override version code OFF,
  Inject loader dex OFF

The two APKs needed are already pushed to the phone:

```
/sdcard/Download/MessengerZ-1.8.0-module.apk    (6.6 MB)  — built from this repo
/sdcard/Download/Messenger-562-stripped.apk     (92  MB)  — Messenger v562 with
                                                            conflicting signature
                                                            permissions stripped
```

Open LSPatch Manager on the phone, pick `Messenger-562-stripped.apk`, embed
`MessengerZ-1.8.0-module.apk` as the module, patch. Uninstall any prior Messenger /
Messenger Z, install the patched APK.

## What changed in the repo

1. [app/build.gradle.kts](../app/build.gradle.kts) — `versionCode 1 → 10800`,
   `versionName "1.0" → "1.8.0"` (was a mismatch with `Global.VERSION`).
2. [app/src/main/java/com/messengerz/global/Global.kt](../app/src/main/java/com/messengerz/global/Global.kt) — `VERSION = "v1.7.7" → "v1.8.0"`.
3. [tools/repack-messenger.ps1](repack-messenger.ps1) — one-command pipeline that
   strips conflicting signature permissions from the Messenger APK, rebuilds it,
   and re-signs it. Output: `tools/work/signed/messenger-rebuilt-aligned-debugSigned.apk`.
   See script header for the why.
4. [tools/jadx-mcp/](jadx-mcp/) — JADX-MCP-Server local install + `.mcp.json` at
   repo root, for driving JADX-GUI from Claude Code.

## Why the strip step exists

Messenger v562's manifest declares two signature-level permissions that the
Facebook (`com.facebook.katana`) app also declares under Meta's signing cert:

```
com.facebook.permission.prod.FB_APP_COMMUNICATION
com.facebook.receiver.permission.ACCESS
```

When LSPatch Manager re-signs the patched APK with a debug cert, Android refuses
install because two apps now claim the same `<permission>` name under different
certs (`INSTALL_FAILED_DUPLICATE_PERMISSION`). The script strips both `<permission>`
declarations from the manifest before patching. `<uses-permission>` references stay
intact so Messenger components keep working.

## Re-running the strip step for a new Messenger version

```powershell
# 1. Drop the new Messenger APK at the repo root
# 2. Rebuild the module APK if needed
./gradlew :app:assembleDebug
# 3. Run the strip pipeline
pwsh tools/repack-messenger.ps1 -Clean
# 4. Push results to phone
adb push tools/work/signed/messenger-rebuilt-aligned-debugSigned.apk /sdcard/Download/
adb push app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/
# 5. Patch on phone via LSPatch Manager
```

## Open items for next session

- Confirm install succeeds on phone with the stripped APK + LSPatch Manager patch.
- Once installed, test calls — Meta server should no longer return "deprecated"
  since the base APK is now current (v562).
- Test the Messenger Z hooks (No Seen, No Typing, settings menu via long-press).
  Hook constants in [features/NoSeenFeature.kt](../app/src/main/java/com/messengerz/features/NoSeenFeature.kt) (`BLOCK_ID = 81`)
  may need re-resolution against v562's `MailboxSDKJNI` dispatch IDs. JADX-MCP is set up
  for this — `tools/jadx-mcp/README.md` explains how to drive it.

## Things tried but rolled back (do not redo)

- LSPatch CLI (`tools/lspatch/lspatch.jar`) — both LSPosed v0.6 and JingMatrix v0.8
  forks. Failed on this device for unrelated reasons. The user's working workflow is
  the on-device LSPatch Manager (v0.7 build 430), not the CLI. The CLI jar is kept
  in `tools/lspatch/` only as a fallback — do not use it as the primary path.
- LSPatch source patch attempt — `tools/lspatch-src/` is the cloned JingMatrix repo
  with `meta-loader/.../LSPAppComponentFactoryStub.java` modified. Build output is
  preserved as `tools/lspatch/lspatch-android16-patched.jar`. Abandoned — wrong tool
  for the user's actual workflow.
