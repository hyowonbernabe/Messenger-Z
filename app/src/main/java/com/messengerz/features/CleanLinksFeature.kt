package com.messengerz.features

import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import com.messengerz.core.LinkCleaner
import com.messengerz.core.Preferences
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Cleans links at the moment they are opened: in Messenger's in-app browser (WebView.loadUrl)
 * and when handed to another app (startActivity with a VIEW intent). See [LinkCleaner].
 */
object CleanLinksFeature {
    private const val TAG = "MessengerZ-Links"

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val urlHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val url = param.args[0] as? String ?: return
                    clean(url)?.let { param.args[0] = it }
                }
            }
            XposedHelpers.findAndHookMethod(WebView::class.java, "loadUrl", String::class.java, urlHook)
            XposedHelpers.findAndHookMethod(WebView::class.java, "loadUrl", String::class.java, Map::class.java, urlHook)

            val intentHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val intent = param.args[0] as? Intent ?: return
                    val data = intent.dataString ?: return
                    val cleaned = clean(data) ?: return
                    intent.setDataAndType(Uri.parse(cleaned), intent.type)
                }
            }
            // Every Activity.startActivity variant funnels into startActivityForResult(Intent, int, Bundle).
            XposedHelpers.findAndHookMethod(Activity::class.java, "startActivityForResult",
                Intent::class.java, Int::class.javaPrimitiveType, Bundle::class.java, intentHook)
            XposedHelpers.findAndHookMethod(ContextWrapper::class.java, "startActivity", Intent::class.java, intentHook)
            Log.d(TAG, "Link cleaning hooks installed")
        } catch (e: Throwable) {
            Log.e(TAG, "Error installing link cleaning", e)
        }
    }

    /** Cleaned URL, or null if unchanged / disabled. */
    private fun clean(url: String): String? {
        if (!Preferences.isCleanLinksEnabled) return null
        val out = LinkCleaner.clean(url)
        if (out == url) return null
        Log.d(TAG, "Cleaned link: $url -> $out")
        return out
    }
}
