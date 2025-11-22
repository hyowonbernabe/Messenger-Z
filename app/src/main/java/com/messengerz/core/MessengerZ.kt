package com.messengerz.core

import android.util.Log
import com.messengerz.global.Global
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MessengerZ : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.facebook.orca") return
        if (lpparam.processName != "com.facebook.orca") return

        Log.d("MessengerZ", Global.VERSION)

        ContextInjector.init(lpparam)
        FeatureManager.init(lpparam)
        MenuInjector.init(lpparam)
    }
}