package com.messengerz.core

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MessengerZ : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.facebook.orca") return
        if (lpparam.processName != "com.facebook.orca") return

        Log.d("MessengerZ", "Initializing Messenger Z...")

        Preferences.init()
        FeatureManager.init(lpparam)
    }
}