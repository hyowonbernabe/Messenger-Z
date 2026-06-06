package com.messengerz.core

/**
 * In-memory ring buffer of MailboxSDKJNI dispatch calls, used by the Debug Console
 * to discover which (method, cmdId) corresponds to an action (e.g. marking a message
 * read) on a given Messenger version. Recording only happens while the console overlay
 * is open (capturing == true) to keep overhead off the normal path.
 *
 * Hook callbacks fire on arbitrary binder threads, so all access is synchronized.
 */
object DebugLog {
    data class Entry(val time: Long, val method: String, val cmdId: Int?, val argCount: Int)

    private const val MAX = 1000
    private val buffer = ArrayDeque<Entry>()

    @Volatile
    var capturing = false

    @Synchronized
    fun add(method: String, cmdId: Int?, argCount: Int) {
        if (!capturing) return
        buffer.addLast(Entry(System.currentTimeMillis(), method, cmdId, argCount))
        while (buffer.size > MAX) buffer.removeFirst()
    }

    /** Free-text diagnostic line (argCount == -1 marks it as a note, not a dispatch call). */
    fun note(message: String) = add(message, null, -1)

    @Synchronized
    fun snapshot(): List<Entry> = buffer.toList()

    @Synchronized
    fun clear() {
        buffer.clear()
    }
}
