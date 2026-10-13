package com.facebookz

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.zmods.shared.MediaSaver
import com.zmods.shared.ui.SettingsKit.dp
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

/**
 * ⬇ button on Facebook's story viewer (StoryViewer*Activity, unobfuscated). Saves the story that
 * is playing ([Stories.current], tracked from the progress bar) via MediaSaver: the card's
 * getMedia() model holds plain CDN URLs.
 */
object StoryDownload {
    private const val TAG = "FacebookZ-Download"
    private const val BUTTON_TAG = "fz_story_download"

    fun init() {
        XposedHelpers.findAndHookMethod(Activity::class.java, "onResume", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as Activity
                if (!activity.javaClass.name.startsWith("com.facebook.stories.viewer.activity.")) return
                if (Prefs.STORY_DOWNLOAD.on) addButton(activity)
            }
        })
    }

    private fun addButton(activity: Activity) {
        val decor = activity.window.decorView as? FrameLayout ?: return
        if (decor.findViewWithTag<View>(BUTTON_TAG) != null) return
        val button = TextView(activity).apply {
            tag = BUTTON_TAG
            text = "⬇"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = "Download story"
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x66000000) }
            elevation = dp(activity, 6).toFloat()
            setOnClickListener { download(activity) }
        }
        val size = dp(activity, 44)
        decor.addView(button, FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.END).apply {
            topMargin = dp(activity, 140); rightMargin = dp(activity, 14)
        })
    }

    private fun download(activity: Activity) {
        try {
            val card = Stories.current?.get()
                ?: return Toast.makeText(activity, "No story to download", Toast.LENGTH_SHORT).show()
            val media = card.javaClass.methods.firstOrNull { it.name == "getMedia" && it.parameterTypes.isEmpty() }?.invoke(card)
            // The media model alone first: walking the whole card can reach neighbouring stories.
            if (!MediaSaver.save(activity, "FacebookZ", "Story", media) && !MediaSaver.save(activity, "FacebookZ", "Story", card))
                Toast.makeText(activity, "Couldn't find this story's media", Toast.LENGTH_SHORT).show()
        } catch (e: Throwable) {
            Log.e(TAG, "Story download failed", e)
            Toast.makeText(activity, "Download failed", Toast.LENGTH_SHORT).show()
        }
    }
}
