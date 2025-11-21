package com.messengerz.core

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.messengerz.ui.SettingsDialog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import android.util.Log

object MenuInjector {
    private const val TAG = "MessengerZ-Menu"
    private var isHooked = false

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            Log.d(TAG, "Initializing MenuInjector...")

            // Hook the base Activity class
            XposedHelpers.findAndHookMethod(
                android.app.Activity::class.java,
                "onWindowFocusChanged",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val hasFocus = param.args[0] as Boolean
                        if (!hasFocus || isHooked) return

                        val activity = param.thisObject as Activity

                        // Only run for Messenger's Main Activity
                        val activityName = activity.javaClass.name
                        if (activityName != "com.facebook.messenger.neue.MainActivity") {
                            return
                        }

                        Log.d(TAG, "MainActivity Focused! Starting crawler...")

                        Preferences.init(activity)

                        val rootView = activity.window.decorView as ViewGroup

                        rootView.postDelayed({
                            try {
                                val found = traverseAndHook(activity, rootView)
                                if (found) {
                                    Log.d(TAG, "SUCCESS: Chats button hooked.")
                                    Toast.makeText(activity, "Messenger Z Active", Toast.LENGTH_SHORT).show()
                                    isHooked = true
                                } else {
                                    Log.e(TAG, "FAILURE: Could not find 'Chats' button.")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Crawler failed", e)
                            }
                        }, 3000)
                    }
                }
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Error in MenuInjector", e)
        }
    }

    private fun traverseAndHook(context: Context, view: View): Boolean {
        val desc = view.contentDescription?.toString()

        // TARGET THE HEADER TITLE
        if (desc != null && desc.equals("Messenger", ignoreCase = true)) {
            Log.w(TAG, ">>> FOUND HEADER: '$desc' <<<")

            view.setOnLongClickListener {
                Log.d(TAG, "HEADER Long Press Triggered!")
                try {
                    // SHOW DIALOG INSTEAD OF ACTIVITY
                    SettingsDialog.show(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open settings", e)
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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