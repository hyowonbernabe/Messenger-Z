package com.messengerz.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.messengerz.ui.SettingsDialog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import android.util.Log
import java.util.WeakHashMap

object MenuInjector {
    private const val TAG = "MessengerZ-Menu"

    private val hookedActivities = WeakHashMap<Activity, Boolean>()

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            Log.d(TAG, "Initializing MenuInjector...")

            XposedHelpers.findAndHookMethod(
                android.app.Activity::class.java,
                "onWindowFocusChanged",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val hasFocus = param.args[0] as Boolean
                        val activity = param.thisObject as Activity

                        if (activity.javaClass.name != "com.facebook.messenger.neue.MainActivity") {
                            return
                        }

                        if (hasFocus && !hookedActivities.containsKey(activity)) {
                            Log.d(TAG, "New MainActivity instance detected. Starting crawler...")

                            hookedActivities[activity] = true

                            Preferences.init(activity)
                            startCrawler(activity)
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Error in MenuInjector", e)
        }
    }

    private fun startCrawler(activity: Activity) {
        val rootView = activity.window.decorView as ViewGroup

        rootView.postDelayed({
            try {
                val found = traverseAndHook(activity, rootView)
                if (found) {
                    Log.d(TAG, "SUCCESS: Header hooked.")
                    Toast.makeText(activity, "Messenger Z Active", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e(TAG, "FAILURE: Header not found. Retrying in 2s...")
                    rootView.postDelayed({
                        if (traverseAndHook(activity, rootView)) {
                            Log.d(TAG, "SUCCESS: Header hooked on retry.")
                        }
                    }, 2000)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Crawler error", e)
            }
        }, 3000)
    }

    private fun traverseAndHook(context: Context, view: View): Boolean {
        val desc = view.contentDescription?.toString()

        if (desc != null && desc.equals("Messenger", ignoreCase = true)) {
            Log.w(TAG, ">>> FOUND HEADER: '$desc' <<<")

            view.setOnLongClickListener(null)

            view.setOnLongClickListener {
                Log.d(TAG, "HEADER Long Press Triggered!")
                try {
                    SettingsDialog.show(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open dialog", e)
                    Toast.makeText(context, "Error", Toast.LENGTH_SHORT).show()
                }
                true
            }
            return true
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (traverseAndHook(context, child)) {
                    return true
                }
            }
        }
        return false
    }
}