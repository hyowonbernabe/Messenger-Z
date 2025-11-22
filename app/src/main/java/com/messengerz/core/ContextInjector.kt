package com.messengerz.core

import android.app.Application
import android.content.Context
import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

object ContextInjector {
    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "attach",
                Context::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = param.args[0] as Context

                        Preferences.init(context)
                        Log.d("MessengerZ", "Preferences initialized via ContextInjector.")
                    }
                }
            )
        } catch (e: Throwable) {
            Log.e("MessengerZ", "Failed to hook Application context", e)
        }
    }
}