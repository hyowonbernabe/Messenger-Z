package com.facebookz

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.luckypray.dexkit.DexKitBridge

/** Facebook Z entry point (LSPatch-embedded Xposed module for com.facebook.katana). */
class FacebookZ : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.facebook.katana" || lpparam.processName != "com.facebook.katana") return
        Log.d(TAG, "Facebook Z v${BuildConfig.VERSION_NAME} loaded (Facebook ${BuildConfig.FACEBOOK_VER})")

        XposedHelpers.findAndHookMethod(Application::class.java, "attach", Context::class.java, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) { Prefs.init(param.args[0] as Context) }
        })
        Settings.init()
        StoryDownload.init()

        // One DexKit scan for every obfuscated target (Facebook has ~180 MB of dex).
        // ponytail: full scan each launch; cache found names per versionCode if startup cost matters
        try {
            val t0 = SystemClock.elapsedRealtime()
            System.loadLibrary("dexkit")
            DexKitBridge.create(lpparam.appInfo.sourceDir).use { bridge ->
                Stories.init(bridge, lpparam.classLoader)
                AppearOffline.init(bridge, lpparam.classLoader)
                FeedAds.init(bridge, lpparam.classLoader)
            }
            Log.d(TAG, "Hooks ready in ${SystemClock.elapsedRealtime() - t0} ms")
        } catch (e: Throwable) {
            Log.e(TAG, "DexKit hooks failed", e)
        }
    }

    companion object { const val TAG = "FacebookZ" }
}
