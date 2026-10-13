# Facebook Z — Maintenance Guide

Facebook Z is the Facebook-app (`com.facebook.katana`) sibling of Messenger Z. Same approach:
an Xposed module (`facebook/`) baked into the Facebook APK with LSPatch, no root. Code shared
with Messenger Z (settings UI kit, restart/cache tools) lives in `shared/`. The general
technique, LSPatch notes and debugging workflow are in [`../messenger/maintenance.md`](../messenger/maintenance.md);
this file only covers what is Facebook-specific.

## Getting the base APK

APKPure serves Facebook as an **XAPK** (base APK + a density split), not a single APK. Merge it
into one APK with APKEditor before patching:

```
bash tools/fetch-facebook.sh download base/facebook-<ver>.apk   # download + merge
```

The base is arm64-v8a only (like Messenger), so the Android 16 x86_64 emulator (berberis) is
used for testing.

## Building

```
./gradlew :facebook:assembleDebug -PfbVer=<ver>
WORK=$PWD/tools/work-fb SIGBYPASS=1 PYTHON=python bash tools/build-ci.sh base/facebook-<ver>.apk \
    facebook/build/outputs/apk/debug/facebook-debug.apk dist-fb
```

`WORK` keeps the Facebook build from wiping Messenger's intermediate files (`build-ci.sh`
deletes its work folder at the start).

`SIGBYPASS=1` is required. LSPatch's default level 2 adds native file-redirect hooks that make
Facebook's startup threads crash at random (SIGSEGV in `HybridData.<init>`, then Facebook's own
crash reporter deadlocks and the app hangs on the splash screen) — even with zero module hooks.
Level 1 (Java-only signature spoof) is stable and shared login still works.

## Coexisting with other Meta apps

Same rule as Messenger Z: Android lets only one signer own a permission name. Every Meta app
declares two shared signature permissions:

- `com.facebook.permission.prod.FB_APP_COMMUNICATION`
- `com.facebook.receiver.permission.ACCESS`

Once LSPatch re-signs Facebook with a non-Meta key, keeping those declarations would make
Facebook Z fail to install next to stock Messenger / Instagram / Messenger Z
(`INSTALL_FAILED_DUPLICATE_PERMISSION`). `tools/build-ci.sh` strips the `<permission>`
declarations (the `<uses-permission>` lines stay). Facebook's other declared permissions are
all `com.facebook.katana.*`, so they can't collide.

Facebook Z uses the stock package name, so it **replaces** the stock Facebook app (same as
Messenger Z replacing stock Messenger). Updates install over the top as long as they are
signed with the same LSPatch key.

## Login

On a device that already has (Messenger Z) logged in, Facebook Z picks up the account
automatically through Meta's shared login.

## Hook map

Facebook's code is obfuscated (class/method names change every release), so every target is found
at startup with DexKit through a stable anchor — a log or query string inside it — in one scan
(`FacebookZ.kt`, ~0.5 s). Nothing is version-specific.

| Feature | File | Anchor → hooked target |
|---|---|---|
| Entry point | `FacebookZ.kt` | `IXposedHookLoadPackage`, main process only |
| Settings | `Settings.kt` | long-press on the view with content description "Facebook logo" |
| Hide story views | `Stories.kt` | class whose method builds the seen request with `story_ids_list` + `is_story_peek_view` → its void method taking a `Set` (sends the GraphQL mutation) returns early |
| Stop auto-swipe | `Stories.kt` | method using "Trying to set negative progress in StoryViewerProgressBarController." (setProgress 0..1000) → capped at 999 |
| Skip story ads | `Stories.kt` | same method; card is `com.facebook.audience.snacks.model.AdStory` → progress 1000 at once |
| Story download | `StoryDownload.kt` | ⬇ button added in `Activity.onResume` of `com.facebook.stories.viewer.activity.*`; media = current `StoryCard` (tracked by the progress hook) `.getMedia()` CDN URLs |
| Appear offline | `AppearOffline.kt` | `com.facebook.mqtt.service.XplatNativeClientWrapper` family `setForeground(true)` → false |
| Hide sponsored posts | `FeedAds.kt` | enum with `SPONSORED_CANNOT_INSERT`; in classes mentioning "Sponsored", collection-interface methods returning it → `SPONSORED_CANNOT_INSERT`, returning (story, status) → (null, that) |

Do not widen `FeedAds` to non-interface methods: the main feed loader also mentions "Sponsored"
and returns the same types, and hooking it leaves the feed empty.

To re-find a target after an update: extract the dex files (`unzip base/facebook-<ver>.apk 'classes*.dex'`),
`grep -l -a "<anchor>" *.dex`, then decompile only that dex with jadx (`-Xmx3g`, `--no-res`).
