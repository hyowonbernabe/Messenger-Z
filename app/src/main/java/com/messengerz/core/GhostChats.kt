package com.messengerz.core

import android.app.AndroidAppHelper
import android.content.Context
import android.util.Log

/**
 * Per-chat exceptions for ghost mode (seen / typing / story seen).
 *
 * Seen and typing dispatches carry a per-chat token (e.g. "T_VEFNOnBrOjQxMDc3NTAwMQ==", base64
 * of "TAM:pk:<thread>"). They only fire inside an open chat, so the token is paired with the open
 * chat's name, read from the header's accessibility label "<name>, Thread details". Known chats
 * are kept so settings can list them. Stories have no token, so they match by owner name.
 */
object GhostChats {
    private const val TAG = "MessengerZ-Ghost"
    private const val HEADER_SUFFIX = ", Thread details"
    private const val PREFS = "MessengerZ_Prefs"
    private const val KEY_CHATS = "ghost_chats"   // lines: token \t name \t lastSeenMs
    private const val KEY_LIST = "ghost_list"     // tokens picked by the user
    private const val KEY_MODE = "ghost_mode"

    enum class Mode(val label: String) {
        EVERYONE("Everyone"),
        EXCEPT("Everyone except selected chats"),
        ONLY("Only selected chats"),
    }

    data class Chat(val token: String, val name: String, val lastSeen: Long)

    @Volatile private var openChatName: String? = null
    private var chats: MutableMap<String, Chat>? = null

    private fun prefs() = AndroidAppHelper.currentApplication()?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Called for every accessibility label Messenger sets; picks out the chat header. */
    fun onContentDescription(desc: String) {
        if (desc.endsWith(HEADER_SUFFIX) && desc.length > HEADER_SUFFIX.length) {
            openChatName = desc.removeSuffix(HEADER_SUFFIX)
        }
    }

    /** First non-numeric String argument of a dispatch = the chat token. */
    fun tokenOf(args: Array<Any?>): String? =
        args.firstOrNull { it is String && it.isNotEmpty() && !it.all(Char::isDigit) } as String?

    @Synchronized
    private fun all(): MutableMap<String, Chat> {
        chats?.let { return it }
        val m = LinkedHashMap<String, Chat>()
        prefs()?.getString(KEY_CHATS, null)?.lines()?.forEach { line ->
            val p = line.split('\t')
            if (p.size == 3) m[p[0]] = Chat(p[0], p[1], p[2].toLongOrNull() ?: 0)
        }
        chats = m
        return m
    }

    /** Remember which chat a token belongs to (only while a chat header is showing). */
    @Synchronized
    fun remember(token: String) {
        val name = openChatName ?: return
        val m = all()
        val now = System.currentTimeMillis()
        val old = m[token]
        if (old != null && old.name == name && now - old.lastSeen < 60_000) return
        m[token] = Chat(token, name, now)
        prefs()?.edit()?.putString(KEY_CHATS, m.values.joinToString("\n") { "${it.token}\t${it.name.replace('\t', ' ')}\t${it.lastSeen}" })?.apply()
        if (old == null) Log.d(TAG, "Learned chat '$name' -> $token")
    }

    fun knownChats(): List<Chat> = synchronized(this) { all().values.sortedByDescending { it.lastSeen } }

    var mode: Mode
        get() = try { Mode.valueOf(prefs()?.getString(KEY_MODE, null) ?: "EVERYONE") } catch (e: Exception) { Mode.EVERYONE }
        set(v) { prefs()?.edit()?.putString(KEY_MODE, v.name)?.apply() }

    fun selected(): Set<String> = prefs()?.getStringSet(KEY_LIST, emptySet())?.toSet() ?: emptySet()

    fun setSelected(tokens: Set<String>) { prefs()?.edit()?.putStringSet(KEY_LIST, tokens)?.apply() }

    /** Should ghost mode apply to the chat with this token? */
    fun appliesToToken(token: String?): Boolean = when (mode) {
        Mode.EVERYONE -> true
        Mode.EXCEPT -> token == null || token !in selected()   // unknown chat: stay hidden
        Mode.ONLY -> token != null && token in selected()
    }

    /** Stories: match the owner's name against the names of the selected chats. */
    fun appliesToPerson(name: String?): Boolean {
        val m = mode
        if (m == Mode.EVERYONE) return true
        val inList = name != null && name in synchronized(this) { selected().mapNotNull { all()[it]?.name }.toSet() }
        return if (m == Mode.EXCEPT) !inList else inList
    }
}
