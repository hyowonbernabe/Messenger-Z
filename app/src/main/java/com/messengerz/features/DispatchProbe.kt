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
            val cls = XposedHelpers.findClassIfExists(
                "com.facebook.sdk.mca.MailboxSDKJNI", lpparam.classLoader
            )
            if (cls == null) {
                Log.e(TAG, "MailboxSDKJNI not found")
                return
            }

            var hooked = 0
            for (m in cls.declaredMethods) {
                if (!m.name.startsWith("dispatch")) continue
                val mname = m.name
                try {
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!DebugLog.capturing) return
                            val id = param.args.getOrNull(0) as? Int
                            DebugLog.add(mname, id, param.args.size)
                        }
                    })
                    hooked++
                } catch (e: Throwable) {
                    // some overloads may be unhookable; skip
                }
            }
            Log.d(TAG, "DispatchProbe hooked $hooked dispatch methods")
        } catch (e: Throwable) {
            Log.e(TAG, "Error in DispatchProbe", e)
        }
    }
}
