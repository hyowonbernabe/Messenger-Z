package com.messengerz.features

import android.util.Log
import com.messengerz.core.Preferences
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

object NoSeenFeature {
    private const val TAG = "MessengerZ"

    private val BLOCK_IDS = listOf(81)

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            Log.d(TAG, "Initializing SDK Blocker (Target: ID 81)...")

            val sdkClass = XposedHelpers.findClassIfExists("com.facebook.sdk.mca.MailboxSDKJNI", lpparam.classLoader)
            if (sdkClass != null) {
                hookMethod(sdkClass, "SDK", "dispatchVOOOO")

                hookMethod(sdkClass, "SDK", "dispatchVOOOOOZ")
            } else {
                Log.e(TAG, "MailboxSDKJNI not found")
            }

        } catch (e: Throwable) {
            Log.e(TAG, "Error", e)
        }
    }

    private fun hookMethod(clazz: Class<*>, type: String, methodName: String) {
        val method = clazz.declaredMethods.firstOrNull { it.name == methodName } ?: return

        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val cmdId = param.args.getOrNull(0) as? Int ?: return

                if (cmdId in BLOCK_IDS) {
                    Log.w(TAG, ">>> BLOCKED SDK READ RECEIPT (ID: $cmdId) <<<")

                    if (Preferences.isNoSeenEnabled) {
                        param.result = null
                    }
                }
            }
        })
    }
}