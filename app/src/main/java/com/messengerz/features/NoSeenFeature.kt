package com.messengerz.features

import android.util.Log
import com.messengerz.core.Preferences
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

object NoSeenFeature {
    private const val TAG = "MessengerZ"

    private const val BLOCK_ID = 81

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val sdkClass = XposedHelpers.findClassIfExists("com.facebook.sdk.mca.MailboxSDKJNI", lpparam.classLoader)

            if (sdkClass == null) {
                Log.e(TAG, "Error: MailboxSDKJNI class not found.")
                return
            }

            val targetMethod = sdkClass.declaredMethods.firstOrNull { it.name == "dispatchVOOOO" }

            if (targetMethod != null) {
                XposedBridge.hookMethod(targetMethod, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val cmdId = param.args.getOrNull(0) as? Int ?: return

                        if (cmdId == BLOCK_ID) {
                            if (Preferences.isNoSeenEnabled) {
                                Log.d(TAG, ">>> BLOCKED SEEN INDICATOR (ID: $BLOCK_ID) <<<")
                                param.result = null
                            }
                        }
                    }
                })
                Log.d(TAG, "NoSeenFeature: Hooked successfully.")
            } else {
                Log.e(TAG, "Error: dispatchVOOOO method not found.")
            }

        } catch (e: Throwable) {
            Log.e(TAG, "Error initializing NoSeenFeature", e)
        }
    }
}