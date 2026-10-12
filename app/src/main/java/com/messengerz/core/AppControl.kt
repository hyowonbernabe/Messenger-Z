package com.messengerz.core

import android.content.Context
import android.content.Intent
import android.os.Process
import java.io.File

/** Settings actions: restart Messenger, clear its cache, reset Messenger Z's learned data. */
object AppControl {

    /** Relaunch Messenger from its launcher activity, then end the current process. */
    fun restart(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(Intent.makeRestartActivityTask(launch.component))
        Process.killProcess(Process.myPid())
    }

    /**
     * Deletes Messenger's cache folder, except `lspatch/` (the extracted original app LSPatch
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

    /** Forget learned message-table layouts and known chats (they are re-learned automatically). */
    fun resetLearnedData(context: Context) {
        val prefs = context.getSharedPreferences("MessengerZ_Prefs", Context.MODE_PRIVATE)
        val edit = prefs.edit()
        prefs.all.keys.filter { it.startsWith("logger_layouts_") || it == "ghost_chats" }.forEach { edit.remove(it) }
        edit.apply()
    }

    private fun sizeOf(f: File): Long = if (f.isDirectory) f.listFiles()?.sumOf { sizeOf(it) } ?: 0 else f.length()
}
