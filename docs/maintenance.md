# Messenger Z — Maintenance Guide

This document explains how Messenger Z works internally and, most importantly, **how to
fix it when a new Messenger release breaks the hooks**. If you forked this repo or you are
future-me staring at a broken build six months from now, start here.

---

## 1. What Messenger Z is

A **non-root, static modification** of Meta's Messenger (`com.facebook.orca`). It is an
Xposed module that is baked into the Messenger APK with **LSPatch** (no Xposed framework
or root required on the device). At runtime the embedded LSPatch loader loads the module
and the module installs its hooks.

- Module code: `app/src/main/java/com/messengerz/`
- Entry point: `core/MessengerZ.kt` (`IXposedHookLoadPackage`) → guards on the Messenger
  package, then calls `ContextInjector`, `FeatureManager`, `MenuInjector`.
- UI is generated programmatically (no XML resources) to avoid `Resources$NotFoundException`
  inside the host app.

Settings are opened by **long-pressing the "Messenger" title** at the top-left of the app
(see `core/MenuInjector.kt`).

---

## 2. Why it breaks on Messenger updates

Messenger talks to its native messaging engine (MSYS) through a class
`com.facebook.sdk.mca.MailboxSDKJNI`, which exposes ~60 `dispatch…` methods. Each call
looks like:

```
dispatchVOOOO(int commandId, Object, Object, Object, Object)
```

Two facts matter:

1. **The method names are stable.** `dispatchVOOOO` is *signature-encoded*, not obfuscated:
   `V` = void return, then one letter per argument **after** the leading `int commandId`
   (`O` = object, `Z` = boolean, `I` = int, `J` = long). So `dispatchVOOOO` = "void return,
   four object args after the command id" and `dispatchVOOOZ` = "…three objects + one
   boolean". These names survive across versions.

2. **The numeric command ids drift.** The `commandId` that means "mark thread read / send
   the seen receipt" is a generated MSYS procedure number. When Meta adds or reorders
   procedures, that number changes. Example observed in this repo:

   | Messenger version | "seen" command id |
   |---|---|
   | v82  | 81 |
   | v562 | 83 |

**So the usual breakage is: a feature's hardcoded command id is stale.** The hook still
installs (the method exists), it just never matches, and the feature silently does nothing.

A second, separate fragility: `MessageLoggerFeature` identifies fields by class-name
substrings and a field name (`A1X`). Those *can* be re-obfuscated and may need re-checking
independently of the command ids.

---

## 3. The hook map

> Per-version id history lives in [`hook-ids.md`](hook-ids.md) — log new versions there.

| Feature | File | Hooked target | Version-specific constant | Current value |
|---|---|---|---|---|
| No Seen | `features/NoSeenFeature.kt` | `MailboxSDKJNI.dispatchVOOOO`, arg[0] == id | `BLOCK_ID` | **83** (v562) |
| No Typing | `features/NoTypingFeature.kt` | `MailboxSDKJNI.dispatchVOOOZ`, arg[0] == id | `TYPING_ID_SDK` | **90** (v562, pending confirm) |
| Spoof Version | `features/SpoofVersionFeature.kt` | `ApplicationPackageManager.getPackageInfo` | none (stable) | n/a |
| Message Logger | `features/MessageLoggerFeature.kt` | constructors of `com.facebook.messaging.notify.type.NewMessageNotification` + field-name reflection | class/field name substrings | see file |
| Settings menu | `core/MenuInjector.kt` | `View.setContentDescription == "Messenger"` | the "Messenger" label | n/a |
| Debug Console (tool) | `features/DispatchProbe.kt` + `ui/DebugConsoleOverlay.kt` | all `MailboxSDKJNI.dispatch*` | none | n/a |

---

## 4. Runbook — fixing hooks after a Messenger update

This is the loop you will repeat. The **Debug Console is the tool that finds the new ids**,
so you do not have to decompile by hand.

### 4.1 Build a fresh patched APK

1. Download the target Messenger APK (`com.facebook.orca`) from APKMirror and drop it at
   the repo root. (Single-arch, e.g. `arm64-v8a`, is fine.)
2. Build the module:
   ```
   ./gradlew :app:assembleDebug
   ```
3. Run the full patch pipeline (strips the Facebook-conflicting permissions, re-signs,
   embeds the module via LSPatch — all headless, no phone):
   ```
   pwsh tools/repack-messenger.ps1 -Clean
   ```
   Output: `dist/…-lspatched.apk`.

> **Note:** the LSPatch *CLI* (`tools/lspatch/lspatch.jar`) works headless on desktop and
> in CI. (An older `tools/STATUS.md` claims it failed — that is outdated; the bundled jar
> works.)

### 4.2 Install and capture the signal

1. Uninstall any previous Messenger / Messenger Z. Keep the Facebook app if you want to
   confirm coexistence. Install the `dist` APK.
2. Open Messenger, log in. Long-press the **"Messenger"** title → **Debug Console**.
   A panel slides up showing every `MailboxSDKJNI.dispatch*` call live. `O`/`0` characters
   are colored alternately so long runs are countable.
3. Tap **Clear**, then perform the action you want to block:
   - **Seen:** open an *unread* chat and read the message.
   - **Typing:** type into the message box (do not send).
4. Tap **Copy**. The lines that appear at that moment are your candidates. The relevant one
   matches the feature's method (e.g. `dispatchVOOOO` for seen) with `n` = arg count
   (seen = `n=5`).

### 4.3 Update the constant and verify

1. Set the new id in the feature file (e.g. `NoSeenFeature.BLOCK_ID = <new id>`). Update
   the table in §3 and the comment in the file.
2. Rebuild (§4.1) and reinstall.
3. Confirm the feature now works (for seen: read a friend's message, confirm they do **not**
   see "Seen").

If the right method itself changed (rare — only if Meta changed an argument signature),
find the new `dispatch…` name in the console and update the `firstOrNull { it.name == … }`
line in the feature file too.

---

## 5. Facebook coexistence (install alongside the Facebook app)

Messenger declares two signature-level permissions that the Facebook app
(`com.facebook.katana`) also owns:

```
com.facebook.permission.prod.FB_APP_COMMUNICATION
com.facebook.receiver.permission.ACCESS
```

After LSPatch re-signs Messenger with a non-Meta cert, Android refuses to install it while
Facebook is present (`INSTALL_FAILED_DUPLICATE_PERMISSION`). `tools/repack-messenger.ps1`
strips those two `<permission>` declarations from the manifest before patching. The
`<uses-permission>` references are left intact so Messenger keeps working.

If a future Messenger version adds more shared permissions, the install error names the
offending permission — add it to `$ConflictingPermissions` in the script.

---

## 6. Build pipeline reference

| Stage | Tool | Why |
|---|---|---|
| Build module | Gradle (`:app:assembleDebug`) | produces `app/build/outputs/apk/debug/app-debug.apk` |
| Decode Messenger | APKEditor (`-t xml -dex`) | ARSCLib, avoids aapt2 (Meta APKs use resource names aapt2 rejects); `-dex` keeps dex bytes byte-identical so native integrity checks don't trip |
| Strip permissions | regex on `AndroidManifest.xml` | fixes the coexistence collision (§5) |
| Rebuild | APKEditor `b` | unsigned APK |
| Sign | uber-apk-signer | debug cert |
| Embed module | LSPatch (`-m … -l 2`) | bakes the Xposed module + loader into the APK, sigbypass level 2 |

`tools/repack-messenger.ps1` chains all of these. Tool jars live under `tools/` and are
gitignored; see `tools/repack-messenger.ps1` header for exact invocations.

---

## 7. Adding a new feature

1. Create `features/MyFeature.kt` with an `init(lpparam)` that installs its hook(s).
2. Register it in `core/FeatureManager.kt`.
3. Add a preference in `core/Preferences.kt` and a switch row in `ui/SettingsDialog.kt`.
4. If the feature targets a MSYS command, use the **Debug Console** to find its
   `(method, commandId)` the same way as §4, and gate the block on `param.args[0]`.

Prefer matching on the **command id + signature-encoded method name** rather than anything
that looks obfuscated, and record the id (with the Messenger version it was found on) in
§3 so the next maintainer can re-verify it.

---

## 8. Environment / prerequisites

- JDK 17+ (the Android Studio JBR at `…/Android Studio/jbr` works).
- Android SDK (`local.properties` → `sdk.dir`).
- `tools/` jars: `apkeditor.jar`, `uber-apk-signer.jar`, `lspatch/lspatch.jar` (gitignored;
  re-download if missing).
- PowerShell for `repack-messenger.ps1` (the same steps port to bash/CI).

---

## 9. Troubleshooting

| Symptom | Likely cause | Action |
|---|---|---|
| Feature silently does nothing | stale command id | §4 — recapture with Debug Console |
| `INSTALL_FAILED_DUPLICATE_PERMISSION` | new shared permission with Facebook | add it to `$ConflictingPermissions` (§5) |
| App crashes on first launches then opens | LSPatch loader warm-up / deprecated base | use a current Messenger base; usually transient |
| "This app version is deprecated" on calls | patched base APK too old | rebuild on the latest Messenger version |
| No `MessengerZ` logs at all | LSPatch not loading the module | re-check the embed step / sigbypass level |
| Settings menu won't open | header content-description changed | re-check the label in `MenuInjector.kt` |
