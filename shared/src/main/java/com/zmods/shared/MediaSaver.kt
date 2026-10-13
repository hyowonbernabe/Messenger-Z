package com.zmods.shared

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import java.lang.reflect.Modifier
import java.util.IdentityHashMap

/** Finds Facebook-CDN media links inside an app's model objects and saves the best one to Downloads. */
object MediaSaver {

    /**
     * Saves the video in [roots] if there is one, otherwise the largest image, to Downloads/[folder].
     * Returns false when no media link was found.
     */
    fun save(ctx: Context, folder: String, label: String, vararg roots: Any?): Boolean {
        val urls = roots.flatMap { collectUrls(it) }.distinct()
        val video = urls.filter { isVideo(it) }.maxByOrNull { it.length }
        val url = video ?: urls.filterNot { isVideo(it) }.maxByOrNull { imageScore(it) } ?: return false
        val name = "$folder/${label.lowercase()}_${System.currentTimeMillis()}.${if (video != null) "mp4" else "jpg"}"
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(label)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        Toast.makeText(ctx, "Saving to Downloads/$folder", Toast.LENGTH_SHORT).show()
        return true
    }

    fun isVideo(u: String) = u.contains(".mp4") || u.contains("/v/t42.") || u.contains("video")

    /** Prefer full-size images: penalize thumbnail size hints like s100x100 / p50x50 / c-crops. */
    fun imageScore(u: String): Int {
        var score = u.length
        if (Regex("""[sp]\d{2,3}x\d{2,3}""").containsMatchIn(u)) score -= 1000
        if (u.contains("stp=c")) score -= 500
        return score
    }

    /** All Facebook-CDN https URLs reachable from [root] within a few levels of fields. */
    fun collectUrls(root: Any?): List<String> {
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
}
