---
description: Rebase Messenger Z and Facebook Z onto the newest Messenger / Facebook, verify them on the emulator, and publish one joint release
---

Fully update Messenger Z and Facebook Z to the latest Messenger / Facebook and ship both in one release. Work autonomously; only stop to ask
if a step below says so. Talk to the user in plain language (see global CLAUDE.md).

Read `.claude/update.local.md` first (gitignored): it holds the friend chat URL, the chat title
as shown on the emulator, and the AVD name. If it is missing, ask the user for those values and
create it. Background: `docs/messenger/maintenance.md`, `docs/messenger/hook-ids.md`, `docs/facebook/maintenance.md`.

Paths used below: `ADB=$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`,
`EMU=$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe`, JDK = `/c/Program Files/Android/Android Studio/jbr`
(JDK 21 is required — lspatch.jar rejects newer). Use `export MSYS_NO_PATHCONV=1` for `adb shell` paths.

## 1. Sync and check for a new version
1. `git pull --ff-only` on `main`. Stop and tell the user if the tree is dirty or it can't fast-forward.
2. `V=$(bash tools/fetch-messenger.sh version)`, `FV=$(bash tools/fetch-facebook.sh version)`. If
   `gh release view v$V` exists, **is not a pre-release** and already has `FacebookZ-$FV.apk`, tell the
   user it's already up to date and stop (unless they asked to force).

## 2. Build
1. `bash tools/fetch-messenger.sh download base/messenger-$V.apk` (`*.apk` is gitignored).
2. `./gradlew :messenger:assembleDebug -PmsgrVer=$V` with JAVA_HOME = the JDK above.
3. `PYTHON=python bash tools/build-ci.sh base/messenger-$V.apk app/build/outputs/apk/debug/messenger-debug.apk dist`
   (JDK `bin` on PATH). Output: `dist/*-lspatched.apk`.
   `tools/lspatch/lspatch.jar` must stay the LSPatch v1.2 + PR #121 build (see maintenance.md §4.1).
4. Facebook: `bash tools/fetch-facebook.sh download base/facebook-$FV.apk`,
   `./gradlew :facebook:assembleDebug -PfbVer=$FV`, then
   `WORK=$PWD/tools/work-fb SIGBYPASS=1 PYTHON=python bash tools/build-ci.sh base/facebook-$FV.apk facebook/build/outputs/apk/debug/facebook-debug.apk dist-fb`.
   `SIGBYPASS=1` is required (level 2 makes Facebook crash/hang at startup).

## 3. Boot the emulator and install
1. Launch with the Bash tool's `run_in_background` (nohup/Start-Process die with the shell):
   `"$EMU" -avd <avd> -port 5554 -no-window -no-boot-anim -gpu host`.
   Wait until `getprop sys.boot_completed` is `1`.
2. `adb install -r dist/*-lspatched.apk` — `-r` keeps the logged-in account (same signing key).
   If install fails with a signature mismatch, stop and tell the user (re-login would be needed).
3. `adb shell setprop log.tag.MessengerZ-Probe VERBOSE`, `adb shell settings put global hide_error_dialogs 1`,
   then `adb logcat -c` and launch with `adb shell monkey -p com.facebook.orca -c android.intent.category.LAUNCHER 1`.
4. Wait for `Context injected` in logcat (can take 1–2 min; the first launch after install may ANR
   while unpacking — relaunch once). Confirm `NoSeenFeature: Hooked successfully` and
   `DispatchProbe hooked N dispatch methods in M classes`. Any `FATAL EXCEPTION` → diagnose
   (systematic-debugging) before going further.
5. If Messenger shows the login screen, the account was lost: ask the user to log in (start the
   emulator with a window for that), then continue.
6. Dismiss prompts (notification permission → "Don't allow", nux screens → "Not now"/Back).
   Find buttons with `uiautomator dump /sdcard/ui.xml` + `cat`, never guess coordinates.

## 3b. Facebook Z
1. `adb install -r dist-fb/*-lspatched.apk`, grant notifications
   (`adb shell pm grant com.facebook.katana android.permission.POST_NOTIFICATIONS`), launch with
   `monkey -p com.facebook.katana`. It signs in by itself through Messenger's shared login.
2. Startup log (`grep FacebookZ`) must show: `Story seen: hooked 1 sender(s)`, `Story progress hooked`,
   `AppearOffline hooked`, `Feed ads: hooked N insert check(s) + M vend(s)` (N, M ≥ 1), `Hooks ready`.
   A "not found" line means the anchor string moved — see `docs/facebook/maintenance.md` (hook map).
3. Stories: open **only the user's own story** (find "<user name>'s story" in the tray with
   `uiautomator`, scroll the tray with swipes, never tap other people's stories). Wait 20 s.
   **Pass** = the story does not advance by itself if "Stop auto-swipe" is on (set it in the settings,
   long-press the facebook logo), the ⬇ button saves a file to `/sdcard/Download/FacebookZ/`, and after
   closing the log shows `BLOCKED STORY SEEN`.
4. Feed: scroll ~30 times. **Pass** = `BLOCKED AD` lines in the log, posts keep loading (feed not stuck
   on grey placeholders) and no ad buttons ("Learn more", "Shop now", "Install now") in `uiautomator` dumps.

## 4. Test No Seen (friend = the user's browser account via claude-in-chrome)
Order matters — the log must contain only the one action:
1. Open a Chrome tab (claude-in-chrome) at the friend chat URL. Find the message box with `find`
   (window size changes, so never click by stale coordinates).
2. On the emulator open the chat (`uiautomator` → tap the row with the chat title) and wait until
   the composer (`text="Message"`) is visible.
3. `adb logcat -c`, then send `mz seen test <time>` from the browser, wait ~12 s, then read
   `adb logcat -d | grep MessengerZ`.
4. **Pass** = `BLOCKED SEEN INDICATOR` in the log **and** the browser shows the message as "Sent"
   with the reader's avatar still on an older message.
5. **Fail** = no BLOCKED line, browser shows the avatar under the new message. The probe lines from
   step 3 hold the new id. The seen call has been a `MailboxSDKJNI.dispatchVOOO…` call (unprefixed in
   the log) near the previous id; the method can gain arguments (v582: VOOOO→VOOOOO). Update
   `BLOCK_ID` / method name in `features/NoSeenFeature.kt`, rebuild (steps 2–3, install `-r`), retest.

## 5. Test No Typing
1. Chat still open on the emulator. Tap the composer, `adb logcat -c`, then type for ~15 s in the
   background: `for i in $(seq 1 15); do adb shell input text t; sleep 1; done`.
2. Meanwhile screenshot the browser at ~5 s and ~10 s (the bubble takes a few seconds to appear).
3. **Pass** = `BLOCKED TYPING INDICATOR` in the log and no "···" bubble in the browser.
4. **Fail** = a `dispatchVOOOZ id=<n>` line (unprefixed) and a visible bubble → set `TYPING_ID_SDK`
   in `features/NoTypingFeature.kt`, rebuild, retest.
5. Clear the composer afterwards (`KEYCODE_DEL`) — never send the junk text.

Also confirm the Message Logger line `CAPTURED: 'mz seen test …'` appeared in step 4.

## 5b. Test No Story Seen
1. Startup log must show `NoStorySeenFeature: hooked N handlers (…)` with N ≥ 1. `handler not found`
   means the anchor string moved: decompile the dex holding `MontageMarkSeenMutation` /
   `markStorySeen` (jadx, one dex at a time — see hook notes in maintenance.md §3) and update
   `ANCHOR` in `features/NoStorySeenFeature.kt`.
2. Open any unwatched story (blue ring) on the emulator after `adb logcat -c`. **Pass** =
   `BLOCKED STORY SEEN` in the log, no non-`Cql` (action) dispatch from `Stories.*`, and the ring
   stays blue after closing. Only the user can check a story's viewer list — ask them if in doubt.

## 6. Record
1. Add rows for `$V` (with versionCode from `aapt2 dump badging`) to both tables in `docs/messenger/hook-ids.md`,
   and update the §3 table in `docs/messenger/maintenance.md`. Update the comments in the feature files.
2. Shut the emulator down: `adb -s emulator-5554 emu kill`.

## 7. Publish (only if both tests passed)
1. Commit (conventional message, e.g. `fix: retarget hooks for Messenger $V`) and `git push`.
2. `cp dist/*-lspatched.apk MessengerZ-$V.apk`, `cp dist-fb/*-lspatched.apk FacebookZ-$FV.apk`.
   `MODVER` = `zVersion` in `gradle.properties` (one version for both apps).
3. Notes file: one line "Messenger Z + Facebook Z $MODVER (Messenger $V, Facebook $FV),
   verified on an emulator", then `docs/release-notes.md` (it lists **every** feature of both apps —
   keep it current; mark anything new with "(new)"). No pre-release warning.
4. If `v$V` exists (CI makes biweekly pre-releases): `gh release upload v$V MessengerZ-$V.apk FacebookZ-$FV.apk --clobber`,
   `gh release edit v$V --notes-file <notes> --prerelease=false --latest`. Otherwise
   `gh release create v$V MessengerZ-$V.apk FacebookZ-$FV.apk --latest --title "Messenger Z + Facebook Z $MODVER" --notes-file <notes>`.
   Both APKs always go in the same release.
5. Delete the local APK copies and tell the user the release link.

If any test can't be made to pass, do **not** publish. Commit nothing that's unverified; report
what was found (log lines, screenshots) and ask the user.
