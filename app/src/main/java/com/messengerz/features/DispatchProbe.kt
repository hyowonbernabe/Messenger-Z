package com.messengerz.features

import android.util.Log
import com.messengerz.core.DebugLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Diagnostic-only: hooks EVERY dispatch* method on MailboxSDKJNI and records the
 * first int argument (the command id) into DebugLog whenever the Debug Console is
 * open. Lets us see, live, which (method, cmdId) fires when an action happens —
 * e.g. reading a message — so the No-Seen hook can be re-pointed after Meta shuffles
 * the ids on a new Messenger version.
 *
 * Recording is gated by DebugLog.capturing, so when the console is closed this adds
 * only a volatile read per dispatch call.
 */
object DispatchProbe {
    private const val TAG = "MessengerZ-Probe"

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Headless capture for automated testing: `adb shell setprop log.tag.MessengerZ-Probe VERBOSE`
            // before launch makes every dispatch go to logcat. Read once at startup (it's a sysprop read).
            val toLogcat = Log.isLoggable(TAG, Log.VERBOSE)

            // Since ~v576 Messenger splits dispatch across ~240 per-feature Mailbox*JNI classes
            // (MailboxTypingIndicatorJNI, MailboxThreadJNI, ...). In logcat mode hook all of them;
            // the dex scan costs startup time, so the in-app console keeps watching MailboxSDKJNI only.
            val classNames = if (toLogcat) mailboxJniClasses(lpparam.appInfo.sourceDir)
                             else listOf("com.facebook.sdk.mca.MailboxSDKJNI")

            var hooked = 0
            for (cn in classNames) {
                val cls = XposedHelpers.findClassIfExists(cn, lpparam.classLoader) ?: continue
                val short = cls.simpleName.removePrefix("Mailbox").removeSuffix("JNI")
                for (m in cls.declaredMethods) {
                    if (!m.name.startsWith("dispatch")) continue
                    val mname = if (cn.endsWith(".MailboxSDKJNI")) m.name else "$short.${m.name}"
                    try {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val id = param.args.getOrNull(0) as? Int
                                if (toLogcat) Log.v(TAG, "dispatch $mname id=$id n=${param.args.size}")
                                if (!DebugLog.capturing) return
                                DebugLog.add(mname, id, param.args.size)
                            }
                        })
                        hooked++
                    } catch (e: Throwable) {
                        // some overloads may be unhookable; skip
                    }
                }
            }
            Log.d(TAG, "DispatchProbe hooked $hooked dispatch methods in ${classNames.size} classes")
        } catch (e: Throwable) {
            Log.e(TAG, "Error in DispatchProbe", e)
        }
    }

    private val MAILBOX_JNI = Regex("""com\.facebook\.[\w.]+\.mca\.Mailbox\w+JNI""")

    @Suppress("DEPRECATION") // DexFile is deprecated but still the simplest way to list an apk's classes
    private fun mailboxJniClasses(apkPath: String): List<String> =
        dalvik.system.DexFile(apkPath).entries().asSequence().filter { MAILBOX_JNI.matches(it) }.toList()
}
