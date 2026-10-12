package com.messengerz.features

import android.app.AndroidAppHelper
import android.content.Context
import android.util.Log
import com.messengerz.core.DebugLog
import com.messengerz.core.MsysRows
import com.messengerz.core.Preferences
import com.messengerz.data.MessageDatabase
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.Executors

/**
 * Unsent-message logger.
 *
 * Every chat bubble's text is read by the UI through `JQLResultSet.getString(row, col)`.
 * We watch those reads: message texts are stored by their real message id, and when a row's
 * text turns into Messenger's "X unsent/deleted a message" placeholder we look the original
 * up, mark it deleted, and (optionally) return "🗑 <original>" so it shows inline in the chat.
 *
 * Column positions differ between Messenger versions, so they are *learned* per result-set
 * layout: the first placeholder (or a text we already saw in a notification) tells us which
 * column holds message text; the id/name/time columns are then found by their value shapes.
 * Learned layouts are saved per Messenger build, so after one sighting it works from launch.
 */
object MessageLoggerFeature {
    private const val TAG = "MessengerZ-Logger"
    private const val INLINE_PREFIX = "🗑 "
    private const val KEEP_MS = 30L * 24 * 60 * 60 * 1000 // non-deleted texts kept 30 days

    // "Hyowon unsent a message", "You deleted a message", "Ana removed an attachment"
    private val PLACEHOLDER = Regex("""^(.{1,80}?) (unsent|deleted|removed) (a|an) (message|attachment|photo|video|voice message)$""")
    // 19-digit E2EE ids / "mid.$…" ids. 15–17 digit numbers are user ids / thread keys, not messages.
    private val MSG_ID = Regex("""^(\d{18,22}|mid\.\S{10,})$""")

    private class Layout(val text: Int, val id: Int, val name: Int, val time: Int) {
        fun encode() = "$text,$id,$name,$time"
        companion object {
            fun decode(s: String) = s.split(',').map { it.toInt() }.let { Layout(it[0], it[1], it[2], it[3]) }
        }
    }

    private var db: MessageDatabase? = null
    private val io = Executors.newSingleThreadExecutor()
    private var appVersion = "0"

    /** signature -> learned layout (null = looked at, not a message table). */
    private val layouts = HashMap<String, Layout?>()
    // Keyed by result-set identity (JQLResultSet has no equals override, so WeakHashMap is identity-like).
    private val rsLayout = Collections.synchronizedMap(WeakHashMap<Any, Any>())
    private val NONE = Any()

    /** msg id -> last seen text, so a placeholder can be resolved without touching disk. */
    private val recent = object : LinkedHashMap<String, String>(512, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, String>?) = size > 3000
    }
    /** Texts recently seen in notifications; a matching bubble read teaches the text column. */
    private val notifTexts = Collections.synchronizedSet(LinkedHashSet<String>())
    /** Recently read tables with no known layout, searched when a notification text arrives. */
    private val recentTables = ArrayDeque<java.lang.ref.WeakReference<Any>>()

    fun setContext(context: Context) {
        if (db != null) return
        db = MessageDatabase(context)
        appVersion = try { context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toString() } catch (e: Throwable) { "0" }
        loadLayouts(context)
        Log.d(TAG, "Message logger ready (${synchronized(layouts) { layouts.size }} learned layouts for build $appVersion)")
        io.execute { db?.prune(KEEP_MS) }
    }

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            AndroidAppHelper.currentApplication()?.let { setContext(it) }
            val rsCls = XposedHelpers.findClassIfExists("com.facebook.msys.mci.JQLResultSet", lpparam.classLoader)
            if (rsCls == null) {
                Log.e(TAG, "JQLResultSet not found; logger disabled")
                return
            }
            XposedHelpers.findAndHookMethod(rsCls, "getString", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val v = param.result as? String ?: return
                        if (!Preferences.isMessageLoggerEnabled) return
                        try {
                            onRead(param.thisObject, param.args[0] as Int, param.args[1] as Int, v)?.let { param.result = it }
                        } catch (e: Throwable) {
                            Log.e(TAG, "onRead failed", e)
                        }
                    }
                })
            hookNotifications(lpparam)
            Log.d(TAG, "Message logger hooked")
        } catch (e: Throwable) {
            Log.e(TAG, "Error in Logger init", e)
        }
    }

    /** Returns a replacement string for the UI, or null to leave the value alone. */
    private fun onRead(rs: Any, row: Int, col: Int, value: String): String? {
        val known = rsLayout[rs]
        val layout: Layout? = when {
            known === NONE -> null
            known != null -> known as Layout
            else -> resolveLayout(rs).also {
                rsLayout[rs] = it ?: NONE
                // Keep tables we may still learn from (unknown, or known but without the name column).
                if (it == null || it.name < 0) synchronized(recentTables) {
                    recentTables.addLast(java.lang.ref.WeakReference(rs))
                    if (recentTables.size > 20) recentTables.removeFirst()
                }
            }
        }
        val placeholder = if (looksLikePlaceholder(value)) PLACEHOLDER.matchEntire(value) else null
        if (layout == null) {
            // Unknown table: learn from a placeholder or a notification text appearing in it.
            if (placeholder == null && !notifTexts.contains(value)) return null
            val learned = learn(rs, row, col, placeholder) ?: return null
            rsLayout[rs] = learned
            return onRead(rs, row, col, value)
        }
        if (col != layout.text) return null

        // Unsent rows lose their message id but keep their timestamp, so the timestamp (ms) is
        // the key that survives an unsend. Fall back to the id if no time column was learned.
        val ts = if (layout.time >= 0) MsysRows.long(rs, row, layout.time) ?: 0L else 0L
        val id = if (ts > 0) "t$ts" else MsysRows.str(rs, row, layout.id) ?: return null
        if (placeholder != null && layout.name < 0) learn(rs, row, col, placeholder, known = layout)?.let { rsLayout[rs] = it }
        if (placeholder != null) {
            val original = synchronized(recent) { recent[id] } ?: db?.getContent(id) ?: return null
            if (original.startsWith(INLINE_PREFIX)) return null
            io.execute {
                if (db?.markAsDeleted(id) == true) Log.w(TAG, ">>> UNSENT by ${placeholder.groupValues[1]}: '$original' (id $id)")
            }
            return if (Preferences.isLoggerInlineEnabled) INLINE_PREFIX + original else null
        }

        if (value.isBlank()) return null // media-only rows have no text worth keeping
        val prev = synchronized(recent) { recent.put(id, value) }
        if (prev == value) return null
        val name = if (layout.name >= 0) MsysRows.str(rs, row, layout.name) else null
        val sentAt = if (ts > 0) ts else System.currentTimeMillis()
        io.execute {
            val old = db?.upsert(id, name, name, null, value, sentAt)
            if (old != null) Log.w(TAG, ">>> EDITED by $name: '$old' -> '$value'")
        }
        if (DebugLog.capturing) DebugLog.note("Logger saw ${name ?: "?"}: ${value.take(40)}")
        return null
    }

    private fun looksLikePlaceholder(v: String) = v.length < 120 &&
        (v.endsWith(" message") || v.endsWith(" attachment") || v.endsWith(" photo") || v.endsWith(" video"))

    private fun resolveLayout(rs: Any): Layout? = synchronized(layouts) { layouts[MsysRows.signature(MsysRows.types(rs))] }

    /** Work out the message layout from a row whose [textCol] holds a placeholder or a known text. */
    private fun learn(rs: Any, row: Int, textCol: Int, placeholder: MatchResult?, sender: String? = null, known: Layout? = null): Layout? {
        val types = MsysRows.types(rs)
        var id = -1; var name = -1; var time = -1
        val subject = (placeholder?.groupValues?.get(1) ?: sender)?.takeIf { it != "You" && it.isNotBlank() }
        for (c in types.indices) {
            if (c == textCol) continue
            when (MsysRows.type(types, c)) {
                MsysRows.T_STRING -> {
                    val s = MsysRows.str(rs, row, c) ?: continue
                    if (id < 0 && MSG_ID.matches(s)) id = c
                    else if (name < 0 && subject != null && s.startsWith(subject) && s.length <= 80) name = c
                }
                MsysRows.T_LONG -> {
                    val l = MsysRows.long(rs, row, c) ?: continue
                    if (time < 0 && l in 1_400_000_000_000L..2_600_000_000_000L) time = c
                }
            }
        }
        // Unsent rows have an empty id column, so an upgrade of a known layout keeps its id/time.
        if (known != null) { if (id < 0) id = known.id; if (time < 0) time = known.time }
        if (id < 0) return null // no message id: inbox snippet or other table, not a bubble
        val layout = Layout(textCol, id, name, time)
        val sig = MsysRows.signature(types)
        synchronized(layouts) { layouts[sig] = layout }
        saveLayouts()
        Log.w(TAG, "Learned message layout $sig -> text=$textCol id=$id name=$name time=$time")
        return layout
    }

    private fun learnFromRecentTables(text: String, sender: String?) {
        val tables = synchronized(recentTables) { recentTables.mapNotNull { it.get() } }
        for (rs in tables.asReversed()) {
            val types = MsysRows.types(rs)
            for (row in 0 until MsysRows.rowCount(rs)) for (c in types.indices) {
                if (MsysRows.type(types, c) != MsysRows.T_STRING || MsysRows.str(rs, row, c) != text) continue
                val l = learn(rs, row, c, null, sender) ?: continue
                rsLayout[rs] = l
                return
            }
        }
    }

    private fun prefsKey() = "logger_layouts_$appVersion"

    private fun loadLayouts(context: Context) {
        try {
            val raw = context.getSharedPreferences("MessengerZ_Prefs", Context.MODE_PRIVATE).getString(prefsKey(), null) ?: return
            synchronized(layouts) {
                raw.split(';').filter { it.contains('=') }.forEach {
                    val (sig, l) = it.split('=', limit = 2)
                    layouts[sig] = Layout.decode(l)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "loadLayouts failed", e)
        }
    }

    private fun saveLayouts() {
        val ctx = AndroidAppHelper.currentApplication() ?: return
        val raw = synchronized(layouts) { layouts.entries.filter { it.value != null }.joinToString(";") { "${it.key}=${it.value!!.encode()}" } }
        ctx.getSharedPreferences("MessengerZ_Prefs", Context.MODE_PRIVATE).edit().putString(prefsKey(), raw).apply()
    }

    /**
     * Notifications give us message texts even before a chat is opened; we only use them to
     * teach the text column (bubbles are matched by id, which notifications don't carry).
     */
    private fun hookNotifications(lpparam: XC_LoadPackage.LoadPackageParam) {
        val cls = XposedHelpers.findClassIfExists("com.facebook.messaging.notify.type.NewMessageNotification", lpparam.classLoader) ?: return
        XposedBridge.hookAllConstructors(cls, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    val text = notificationText(param.thisObject) ?: return
                    val sender = notificationSender(param.thisObject)
                    synchronized(notifTexts) {
                        notifTexts.add(text)
                        if (notifTexts.size > 50) notifTexts.remove(notifTexts.first())
                    }
                    // The bubble is usually drawn before the notification exists: look back.
                    if (synchronized(layouts) { layouts.values.none { it != null && it.name >= 0 } }) learnFromRecentTables(text, sender)
                } catch (e: Throwable) {}
            }
        })
    }

    /** Sender display name from the notification's ThreadSummary (first readable name field). */
    private fun notificationSender(n: Any): String? {
        for (f in n.javaClass.declaredFields) {
            f.isAccessible = true
            val summary = f.get(n) ?: continue
            if (!summary.javaClass.name.contains("model.threads.ThreadSummary")) continue
            for (sf in summary.javaClass.declaredFields) {
                sf.isAccessible = true
                val v = sf.get(summary)
                if (v is String && v.length in 2..80 && v.any { it.isLetter() } && !v.contains("FACEBOOK") && !v.contains(':')) return v
            }
        }
        return null
    }

    private fun notificationText(n: Any): String? {
        for (f in n.javaClass.declaredFields) {
            f.isAccessible = true
            val msg = f.get(n) ?: continue
            if (!msg.javaClass.name.contains("model.messages.Message")) continue
            for (mf in msg.javaClass.declaredFields) {
                mf.isAccessible = true
                val v = mf.get(msg) ?: continue
                if (!v.javaClass.name.contains("SecretString")) continue
                for (sf in v.javaClass.declaredFields) {
                    sf.isAccessible = true
                    val s = sf.get(v) as? String ?: continue
                    if (s.isNotEmpty() && s != "null" && !s.all { it == '*' }) return s
                }
            }
        }
        return null
    }
}
