package com.messengerz.features

import android.util.Log
import com.messengerz.core.Preferences
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

object NoTypingFeature {
    private const val TAG = "MessengerZ"

    private const val TYPING_ID_SDK = 88

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val sdkClass = XposedHelpers.findClassIfExists("com.facebook.sdk.mca.MailboxSDKJNI", lpparam.classLoader)
            if (sdkClass != null) {
                hookSdk(sdkClass)
            } else {
                Log.e(TAG, "MailboxSDKJNI not found for Typing Hook")
            }

        } catch (e: Throwable) {
            Log.e(TAG, "Error in NoTypingFeature", e)
        }
    }

    private fun hookSdk(clazz: Class<*>) {
        val method = clazz.declaredMethods.firstOrNull { it.name == "dispatchVOOOZ" } ?: return

        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val cmdId = param.args.getOrNull(0) as? Int ?: return

                if (cmdId == TYPING_ID_SDK) {

                    if (Preferences.isNoTypingEnabled) {
                        Log.d(TAG, ">>> BLOCKED TYPING INDICATOR (ID: $TYPING_ID_SDK) <<<")
                        param.result = null // Stop it
                    }
                }
            }
        })
    }
}