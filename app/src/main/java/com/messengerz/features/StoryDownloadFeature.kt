package com.messengerz.features

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.messengerz.core.Preferences
import com.zmods.shared.MediaSaver
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Story download: adds a ⬇ button to the story viewer that saves the story on screen
 * (video if it has one, otherwise the largest image) to Downloads/MessengerZ (see MediaSaver).
 *
 * The viewer (MontageViewerFragment, unobfuscated) has a no-arg method returning the current
 * MontageCard; the media URLs are found by walking the card's fields for Facebook CDN links.
 */
object StoryDownloadFeature {
    private const val TAG = "MessengerZ-Download"
    private const val FRAGMENT = "com.facebook.messaging.montage.viewer.MontageViewerFragment"
    private const val CARD = "com.facebook.messaging.montage.model.MontageCard"
    private const val BUTTON_TAG = "mz_story_download"

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val frag = XposedHelpers.findClassIfExists(FRAGMENT, lpparam.classLoader) ?: run {
                Log.e(TAG, "MontageViewerFragment not found"); return
            }
            val currentCard: Method = frag.declaredMethods.firstOrNull {
                it.parameterTypes.isEmpty() && it.returnType.name == CARD && !Modifier.isStatic(it.modifiers)
            }?.apply { isAccessible = true } ?: run {
                Log.e(TAG, "current-card getter not found"); return
            }
            XposedHelpers.findAndHookMethod(frag, "onViewCreated", View::class.java, android.os.Bundle::class.java, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!Preferences.isStoryDownloadEnabled) return
                    val view = param.args[0] as? View ?: return
                    addButton(view, param.thisObject, currentCard)
                }
            })
            Log.d(TAG, "Story download hooked (getter ${currentCard.name})")
        } catch (e: Throwable) {
            Log.e(TAG, "Error in StoryDownloadFeature", e)
        }
    }

    private fun addButton(fragmentView: View, fragment: Any, currentCard: Method) {
        val root = fragmentView as? ViewGroup ?: return
        if (root.findViewWithTag<View>(BUTTON_TAG) != null) return
        val ctx = root.context
        val button = TextView(ctx).apply {
            tag = BUTTON_TAG
            text = "⬇"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = "Download story"
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x66000000) }
            elevation = dp(ctx, 6).toFloat()
            setOnClickListener { download(ctx, fragment, currentCard) }
        }
        val size = dp(ctx, 44)
        // Below the story header, right side. Works for FrameLayout roots; otherwise overlay on the window.
        val target: ViewGroup = (root as? FrameLayout) ?: (root.rootView as? FrameLayout) ?: return
        target.addView(button, FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.END).apply {
            topMargin = dp(ctx, 140); rightMargin = dp(ctx, 14)
        })
        if (target !== root) root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) { target.removeView(button) }
        })
    }

    private fun download(ctx: Context, fragment: Any, currentCard: Method) {
        try {
            val card = currentCard.invoke(fragment) ?: return toast(ctx, "No story to download")
            if (!MediaSaver.save(ctx, "MessengerZ", "Story", card)) toast(ctx, "Couldn't find this story's media")
        } catch (e: Throwable) {
            Log.e(TAG, "Story download failed", e)
            toast(ctx, "Download failed")
        }
    }

    private fun toast(ctx: Context, msg: String) { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }

    private fun dp(ctx: Context, v: Int) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()
}
