package com.messengerz.features

import android.app.DownloadManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.messengerz.core.Preferences
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.IdentityHashMap

/**
 * Story download: adds a ⬇ button to the story viewer that saves the story on screen
 * (video if it has one, otherwise the largest image) to Downloads/MessengerZ via DownloadManager.
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
            val urls = collectUrls(card)
            val video = urls.filter { isVideo(it) }.maxByOrNull { it.length }
            val image = urls.filterNot { isVideo(it) }.maxByOrNull { imageScore(it) }
            val url = video ?: image ?: return toast(ctx, "Couldn't find this story's media")
            val ext = if (video != null) "mp4" else "jpg"
            val name = "MessengerZ/story_${System.currentTimeMillis()}.$ext"
            val req = DownloadManager.Request(Uri.parse(url))
                .setTitle("Story")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            Log.d(TAG, "Downloading story ${if (video != null) "video" else "image"} -> Downloads/$name")
            toast(ctx, "Saving to Downloads/MessengerZ")
        } catch (e: Throwable) {
            Log.e(TAG, "Story download failed", e)
            toast(ctx, "Download failed")
        }
    }

    private fun isVideo(u: String) = u.contains(".mp4") || u.contains("/v/t42.") || u.contains("video")

    /** Prefer full-size images: penalize thumbnail size hints like s100x100 / p50x50 / c-crops. */
    private fun imageScore(u: String): Int {
        var score = u.length
        if (Regex("""[sp]\d{2,3}x\d{2,3}""").containsMatchIn(u)) score -= 1000
        if (u.contains("stp=c")) score -= 500
        return score
    }

    /** All Facebook-CDN https URLs reachable from [root] within a few levels of fields. */
    private fun collectUrls(root: Any): List<String> {
        val out = LinkedHashSet<String>()
        val seen = IdentityHashMap<Any, Boolean>()
        fun walk(o: Any?, depth: Int) {
            if (o == null || depth > 5 || seen.put(o, true) != null) return
            when (o) {
                is String -> if (o.startsWith("https://") && (o.contains("fbcdn") || o.contains("fbsbx"))) out.add(o)
                is Number, is Boolean, is Char, is Enum<*> -> {}
                is Iterable<*> -> o.take(20).forEach { walk(it, depth + 1) }
                is Map<*, *> -> o.values.take(20).forEach { walk(it, depth + 1) }
                is Array<*> -> o.take(20).forEach { walk(it, depth + 1) }
                is Uri -> walk(o.toString(), depth)
                else -> {
                    val n = o.javaClass.name
                    if (n.startsWith("android.") || n.startsWith("java.") || n.startsWith("kotlin.")) return
                    var c: Class<*>? = o.javaClass
                    while (c != null && c != Any::class.java) {
                        for (f in c.declaredFields) {
                            if (Modifier.isStatic(f.modifiers) || f.type.isPrimitive) continue
                            try { f.isAccessible = true; walk(f.get(o), depth + 1) } catch (e: Throwable) {}
                        }
                        c = c.superclass
                    }
                }
            }
        }
        walk(root, 0)
        return out.toList()
    }

    private fun toast(ctx: Context, msg: String) { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }

    private fun dp(ctx: Context, v: Int) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()
}
