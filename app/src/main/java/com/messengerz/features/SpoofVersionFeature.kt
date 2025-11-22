package com.messengerz.features

import android.content.pm.PackageInfo
import android.os.Build
import android.util.Log
import com.messengerz.core.Preferences
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

object SpoofVersionFeature {
    private const val TAG = "MessengerZ-Spoof"

    private const val SPOOF_CODE = 999999999L
    private const val SPOOF_NAME = "999.0.0.99.999"

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val pmClass = XposedHelpers.findClass("android.app.ApplicationPackageManager", lpparam.classLoader)

            XposedBridge.hookAllMethods(pmClass, "getPackageInfo", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val packageName = param.args[0] as? String ?: return

                    // Filter only for Messenger
                    if (packageName == "com.facebook.orca") {

                        if (!Preferences.isSpoofVersionEnabled) return

                        val info = param.result as? PackageInfo ?: return

                        val oldVer = info.versionName

                        // Apply Spoof
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            info.longVersionCode = SPOOF_CODE
                        } else {
                            @Suppress("DEPRECATION")
                            info.versionCode = SPOOF_CODE.toInt()
                        }
                        info.versionName = SPOOF_NAME

                        try {
                            XposedHelpers.setIntField(info, "versionCodeMajor", (SPOOF_CODE shr 32).toInt())
                        } catch (e: Throwable) {}

                        Log.d(TAG, "Spoofed! $oldVer -> $SPOOF_NAME")

                        param.result = info
                    }
                }
            })
            Log.d(TAG, "Spoof Version Hook initialized.")

        } catch (e: Throwable) {
            Log.e(TAG, "Error in SpoofVersionFeature", e)
        }
    }
}