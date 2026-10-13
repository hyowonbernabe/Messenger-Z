package com.facebookz

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

/** Facebook Z entry point (LSPatch-embedded Xposed module for com.facebook.katana). */
class FacebookZ : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.facebook.katana" || lpparam.processName != "com.facebook.katana") return
        Log.d(TAG, "Facebook Z v${BuildConfig.VERSION_NAME} loaded (Facebook ${BuildConfig.FACEBOOK_VER})")
    }

    companion object { const val TAG = "FacebookZ" }
}
