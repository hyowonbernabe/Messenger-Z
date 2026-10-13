# Messenger Z

![Platform](https://img.shields.io/badge/Platform-Android-green) ![Root](https://img.shields.io/badge/Root-Not%20Required-blue) ![Language](https://img.shields.io/badge/Language-Kotlin-purple) ![Version](https://img.shields.io/badge/Version-1.9.0-orange)

**Messenger Z** is a non-root modification of Meta's Messenger (`com.facebook.orca`). It is
an Xposed module baked statically into the Messenger APK with **LSPatch**, so it needs no
root and no separate manager app. It adds privacy features (hide the seen and typing
indicators, watch stories anonymously, recover unsent messages) and **installs alongside the Facebook app**.

<p align="center">
  <img src="screenshots/features.png" alt="Messenger Z Interface" width="25%">
</p>

---

## ⚡ Features

*   **👻 No Seen** — read messages without sending the read receipt.
*   **⌨️ No Typing** — hide the "…" typing indicator while you write.
*   **🕶️ No Story Seen** — watch stories without appearing in the viewer list.
*   **⬇️ Story Download** — a button in the story viewer saves the story to Downloads/MessengerZ.
*   **⏸️ Stop Story Auto-Swipe** — stay on each story until you tap.
*   **🌙 Appear Offline** — stop showing "Active now" while still seeing who else is active (off by default).
*   **🔗 Remove Link Tracking** — links open without Facebook's redirect wrapper or `fbclid`/`utm_*` tags.
*   **🎯 Per-chat ghost mode** — apply seen/typing/story hiding to everyone, everyone except chosen chats, or only chosen chats.
*   **🗑️ Unsent Message Logger** — keep a copy of messages that were sent then removed.
*   **🧪 Spoof Version** — report a very high app version (handy when Meta blocks an old build).
*   **🛠️ Debug Console** — a tap-through, in-app overlay that shows Messenger's internal
    signals live. Used to re-find the hook ids after a Messenger update (see Maintenance).
*   **🎨 Native UI** — dark, programmatically-generated settings (no resource conflicts),
    opened by long-pressing the **Messenger** title.
*   **🤝 Coexists with Facebook** — installs while the Facebook app is present.
*   **🔒 Non-root** — static LSPatch patching, works on any device.

---

## 📥 Installation

1.  Grab the latest **`MessengerZ-*.apk`** from [Releases](../../releases), or add this repo
    to [Obtainium](https://github.com/ImranR98/Obtainium) for automatic updates.
2.  Uninstall the official **Messenger** app (same package name). You can keep the
    **Facebook** app installed.
3.  Install the APK.

> Pre-release builds are automated and unverified — a confirmed-working build is published
> as the latest (non-pre-release) version.

---

## ⚙️ Usage

1.  Open Messenger.
2.  **Long-press** the large **"Messenger"** title at the top-left.
3.  The **Messenger Z** menu appears — toggle features instantly.

---

## 🔄 Releases & automation

Builds are produced by GitHub Actions (`.github/workflows/build.yml`): every two weeks
(1st/15th) and on manual dispatch, it detects the latest stable Messenger, patches it, and
publishes a **pre-release**. A maintainer verifies the hooks (via the Debug Console) and
un-marks it as pre-release to ship. See [`docs/messenger/maintenance.md`](docs/messenger/maintenance.md).

---

## 🧠 How it works

Messenger Z is an Xposed module injected statically with LSPatch. It hooks Messenger's
native messaging bridge, `com.facebook.sdk.mca.MailboxSDKJNI`, which routes operations
through `dispatch…` methods like `dispatchVOOOO(int commandId, …)`.

*   **No Seen / No Typing** match a specific `(method, commandId)` and drop the call.
*   **No Story Seen** has no fixed id: it uses [DexKit](https://github.com/LuckyPray/DexKit) at
    startup to find Messenger's obfuscated "mark story read" handlers (anchored on the
    `MontageCard` type and a log string) and blocks them, so it survives updates without edits.
*   The `dispatch…` **method names are signature-encoded and stable** across versions; the
    **numeric command ids drift** when Meta reorders MSYS procedures. That is the usual cause
    of a feature breaking after an update.
*   UI is built in pure Kotlin and attached by hooking `View.setContentDescription`.
*   **Facebook coexistence**: the build strips the two signature-level permissions Messenger
    shares with `com.facebook.katana`, avoiding `INSTALL_FAILED_DUPLICATE_PERMISSION`.

Current command ids are tracked in [`docs/messenger/hook-ids.md`](docs/messenger/hook-ids.md).

---

## 🛠️ Building & maintaining

Full runbook (including how to re-resolve hook ids after a Messenger update): see
[`docs/messenger/maintenance.md`](docs/messenger/maintenance.md). In short:

```bash
# 1. Build the module
./gradlew :messenger:assembleDebug
# 2. Strip perms + sign + embed into the latest Messenger (downloads it via APKPure)
bash tools/fetch-messenger.sh download messenger.apk
bash tools/build-ci.sh messenger.apk app/build/outputs/apk/debug/messenger-debug.apk dist
```

**Prerequisites:** JDK 21 (Android Studio's JBR works — LSPatch needs 21), Android SDK,
and the pinned jars under `tools/` (`apkeditor`, `uber-apk-signer`, `lspatch`).

**When a feature breaks after an update:** install the build, open the **Debug Console**,
perform the action (read a message / type), read the new `id`, update the constant in the
matching `features/*.kt`, and log it in `docs/messenger/hook-ids.md`.

---

## 📘 Also in this repo: Facebook Z

**Facebook Z** applies the same approach to the Facebook app (`com.facebook.katana`): hide story
views, stop story auto-swipe, story download, skip story ads, hide sponsored posts in the feed, and
appear offline. Open its settings by long-pressing the **facebook** logo. It ships in the **same
release** as Messenger Z (separate `FacebookZ-*.apk` file), replaces the Facebook app, and signs in
through Messenger Z automatically. Full feature list of both apps: [`docs/release-notes.md`](docs/release-notes.md);
maintenance: [`docs/facebook/maintenance.md`](docs/facebook/maintenance.md).

Repo layout:

| Folder | What |
|---|---|
| `app/` | Messenger Z module (Gradle project `:messenger`) |
| `facebook/` | Facebook Z module |
| `shared/` | Code both use: settings UI kit, restart/cache tools |
| `tools/` | Patch pipeline used for both apps |
| `docs/messenger/`, `docs/facebook/` | Maintenance guides |

---

## 🤝 Credits

*   **Creator:** [Hyowon Bernabe](https://github.com/hyowonbernabe)
*   **Reference/Inspiration:** [MessengerPro by Mino260806](https://github.com/Mino260806/MessengerPro)
*   **Tools:** [LSPatch (JingMatrix)](https://github.com/JingMatrix/LSPatch), [DexKit](https://github.com/LuckyPray/DexKit), [APKEditor](https://github.com/REAndroid/APKEditor), [uber-apk-signer](https://github.com/patrickfav/uber-apk-signer)

---

## ⚠️ Disclaimer

For educational and research purposes only. Not affiliated with, endorsed by, or connected
to Meta Platforms, Inc. Modifying and re-signing the app may violate Meta's terms of
service. Use at your own risk.
