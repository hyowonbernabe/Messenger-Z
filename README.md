# Messenger Z

![Badge](https://img.shields.io/badge/Platform-Android-green) ![Badge](https://img.shields.io/badge/Root-Not%20Required-blue) ![Badge](https://img.shields.io/badge/Language-Kotlin-purple) ![Badge](https://img.shields.io/badge/Encryption-E2EE%20Supported-red)

**Messenger Z** is a modern, non-root, static modification for Meta's Messenger application. It injects a lightweight engine into the APK to provide privacy features like disabling read receipts and typing indicators without requiring a rooted device or external manager apps.

Unlike older mods, Messenger Z supports the new **End-to-End Encrypted (E2EE)** architecture (ACT/TAM) used by modern Messenger versions.

---

## ⚡ Features

*   **👻 Ghost Mode (No Seen):** Read messages without the sender knowing.
    *   Supports Standard chats (`Core`).
    *   Supports Secret/Encrypted chats (`ACT` / `Shim`).
*   **⌨️ No Typing:** Disable the "..." typing animation while you write.
*   **🛠️ Version Spoofing:** Fixes the *"Your version of Messenger doesn't support this"* error by spoofing the internal version code to bypass server-side checks.
*   **🎨 Native UI:**
    *   Integrated Settings Menu (Long-press the "Messenger" header).
    *   Dark Mode compatible with a modern Red Accent design.
    *   Pure Kotlin UI generation (No resource conflicts).
*   **🔒 Non-Root:** Works on any Android device using static patching.

---

## 📥 Installation

There are two ways to install Messenger Z.

### Method 1: The "Clean" Install (Easiest)
1.  Uninstall the official Messenger app.
2.  Download the **Patched Messenger Z APK** (from Releases).
3.  Install it.

---

## ⚙️ Usage

1.  Open Messenger.
2.  **Long Press** on the large **"Messenger"** title text at the top-left of the screen.
3.  The **Messenger Z Settings** menu will appear.
4.  Toggle features on/off instantly.

---

## 🛠️ Building from Source

### Prerequisites
*   Android Studio (Koala+ recommended)
*   JDK 17+
*   `lspatch.jar` (for patching)

### Structure
*   **`com.messengerz.core`**: Dependency injection and lifecycle management.
*   **`com.messengerz.features`**: The JNI hooking logic.
*   **`com.messengerz.ui`**: Programmatic UI generation (avoids `Resources$NotFoundException`).

### Build Steps
1.  Clone the repository.
2.  Open in Android Studio.
3.  Build the Project (`Build` -> `Build APK`).
4.  The output is a **Module APK**. You must embed this into the Messenger APK using **LSPatch**.

```bash
java -jar lspatch.jar -m messenger-z.apk -f -l 2 -v messenger.apk
```

---

## 🧠 Technical Architecture

Messenger Z uses **Xposed** API logic injected statically via **LSPatch**. It bypasses Meta's heavy obfuscation by targeting the **JNI (Java Native Interface)** layer, which cannot be easily renamed by ProGuard.

### Hooking Strategy
*   **Standard Chats:** Hooks `MailboxSDKJNI.dispatchVOOOO` (ID 81) to block read receipts.
*   **E2EE (Encryption):** Hooks `MailboxAdvancedCryptoTransportJNI.dispatchCqlOJO` (ID 10) and `dispatchVOZ` (ID 20) to block secure read signals.
*   **UI Injection:** Hooks `View.setContentDescription` to instantly detect when the Header View is created, attaching a listener before the view is even drawn to ensure zero lag.

---

## 🤝 Credits

*   **Creator:** [Hyowon Bernabe](https://github.com/hyowonbernabe)
*   **Reference/Inspiration:** [MessengerPro by Mino260806](https://github.com/Mino260806/MessengerPro)
*   **Tools:** [LSPatch](https://github.com/LSPosed/LSPatch) by LSPosed Team

---

## ⚠️ Disclaimer

This project is for educational and research purposes only. It is not affiliated with, endorsed by, or connected to Meta Platforms, Inc. or Facebook. Use at your own risk.
