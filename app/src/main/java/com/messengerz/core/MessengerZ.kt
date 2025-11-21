package com.messengerz.core

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MessengerZ : IXposedHookLoadPackage {

    // This tag helps us filter logs in Logcat
    private val TAG = "MessengerZ"

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 1. Filter: Ensure we only hook the main Messenger app, not system stuff
        if (lpparam.packageName != "com.facebook.orca") return

        // 2. Filter: Messenger has many processes (mqtt, sandboxed, etc).
        // We usually only want the main UI process for now.
        // The main process usually has the same name as the package.
        if (lpparam.processName != "com.facebook.orca") return

        Log.d(TAG, "============================================")
        Log.d(TAG, "Messenger Z: Loaded successfully into Messenger!")
        Log.d(TAG, "Package: ${lpparam.packageName}")
        Log.d(TAG, "Process: ${lpparam.processName}")
        Log.d(TAG, "============================================")

        // This is where we will load our features later
        // FeatureManager.init(lpparam)
    }
}