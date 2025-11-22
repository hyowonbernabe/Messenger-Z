package com.messengerz.core

import android.view.View
import android.widget.Toast
import com.messengerz.ui.SettingsDialog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import android.util.Log

object MenuInjector {
    private const val TAG = "MessengerZ-Menu"

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            Log.d(TAG, "Initializing Instant Hook...")

            XposedHelpers.findAndHookMethod(
                android.view.View::class.java,
                "setContentDescription",
                CharSequence::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val desc = param.args[0]?.toString() ?: return
                        val view = param.thisObject as View

                        if (desc.equals("Messenger", ignoreCase = true)) {
                            attachListener(view, "Header")
                        }
                    }
                }
            )

        } catch (e: Throwable) {
            Log.e(TAG, "Error in MenuInjector", e)
        }
    }

    private fun attachListener(view: View, type: String) {
        if (view.hasOnClickListeners()) {}

        view.setOnLongClickListener { v ->
            Log.d(TAG, "$type Long Press Triggered")
            try {
                Preferences.init(v.context)

                // Show Dialog
                SettingsDialog.show(v.context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open settings", e)
                Toast.makeText(v.context, "Error opening menu", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }
}