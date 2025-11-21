package com.messengerz.core

import android.util.Log
import android.widget.TextView
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MessengerZ : IXposedHookLoadPackage {

    private val TAG = "MessengerZ"

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 1. Filter for main process only
        if (lpparam.packageName != "com.facebook.orca") return
        if (lpparam.processName != "com.facebook.orca") return

        Log.d(TAG, "Messenger Z: Injecting into Main Process...")

        // 2. The Test Hook
        // We hook the 'setText' method of the standard Android TextView.
        // Since Messenger uses TextViews for almost everything, this will affect the whole app.
        try {
            XposedHelpers.findAndHookMethod(
                android.widget.TextView::class.java, // The class to hook
                "setText",                           // The method name
                CharSequence::class.java,            // Arg 1 type
                android.widget.TextView.BufferType::class.java, // Arg 2 type
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // This runs BEFORE the original setText method.
                        val originalText = param.args[0] as? CharSequence ?: return

                        // Avoid infinite loops or messing up empty text
                        if (originalText.isEmpty() || originalText.toString().endsWith(" [Z]")) {
                            return
                        }

                        // Modify the argument to include our signature
                        param.args[0] = "$originalText [Z]"
                    }
                }
            )
            Log.d(TAG, "Messenger Z: Test Hook applied successfully!")

        } catch (e: Throwable) {
            Log.e(TAG, "Messenger Z: Failed to hook TextView", e)
        }
    }
}