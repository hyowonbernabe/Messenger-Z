package com.zmods.shared

import android.content.Context
import android.content.Intent
import android.os.Process
import java.io.File

/** Settings tools shared by Messenger Z and Facebook Z: restart the host app, clear its cache, reset learned data. */
object AppControl {

    /** Relaunch the host app from its launcher activity, then end the current process. */
    fun restart(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(Intent.makeRestartActivityTask(launch.component))
        Process.killProcess(Process.myPid())
    }

    /**
     * Deletes the host app's cache folder, except `lspatch/` (the extracted original app LSPatch
     * needs at startup; wiping it only forces a slow re-extract). Returns bytes freed.
     */
    fun clearCache(context: Context): Long {
        var freed = 0L
        context.cacheDir.listFiles()?.forEach { f ->
            if (f.name == "lspatch") return@forEach
            freed += sizeOf(f)
            f.deleteRecursively()
        }
        return freed
    }

    /** Remove preference keys matching [isLearned] from [prefsName] (data the mod re-learns automatically). */
    fun resetLearnedData(context: Context, prefsName: String, isLearned: (String) -> Boolean) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        prefs.all.keys.filter(isLearned).forEach { edit.remove(it) }
        edit.apply()
    }

    private fun sizeOf(f: File): Long = if (f.isDirectory) f.listFiles()?.sumOf { sizeOf(it) } ?: 0 else f.length()
}
