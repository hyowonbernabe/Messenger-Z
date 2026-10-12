package com.messengerz.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * Local store for the message logger. Every message text Messenger renders is kept by its
 * real message id, so an unsend (or edit) can be matched back to the original.
 *
 * v3 adds sender_name / deleted_at and an `edits` table; v2 rows are kept (migrated in place).
 */
class MessageDatabase(context: Context) : SQLiteOpenHelper(context, "MessengerZ_Logs.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS messages (msg_id TEXT PRIMARY KEY, thread_id TEXT, sender_id TEXT, content TEXT, timestamp LONG, is_deleted INTEGER DEFAULT 0, sender_name TEXT, deleted_at LONG)")
        db.execSQL("CREATE TABLE IF NOT EXISTS edits (msg_id TEXT, content TEXT, replaced_at LONG)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_msg_deleted ON messages(is_deleted, deleted_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("DROP TABLE IF EXISTS messages")
        if (oldVersion == 2) {
            db.execSQL("ALTER TABLE messages ADD COLUMN sender_name TEXT")
            db.execSQL("ALTER TABLE messages ADD COLUMN deleted_at LONG")
        }
        onCreate(db)
    }

    data class LogItem(
        val id: String, val chat: String, val sender: String, val content: String,
        val timestamp: Long, val deletedAt: Long, val edits: List<String> = emptyList(),
    )

    /** Insert or refresh a message. Returns the previous content if it changed (= an edit). */
    fun upsert(msgId: String, chat: String?, senderName: String?, senderId: String?, content: String, timestamp: Long): String? {
        try {
            val db = writableDatabase
            var previous: String? = null
            db.rawQuery("SELECT content FROM messages WHERE msg_id = ?", arrayOf(msgId)).use {
                if (it.moveToFirst()) previous = it.getString(0)
            }
            if (previous == content) {
                // Same text: only backfill a sender name learned after the first save.
                if (senderName != null) db.execSQL("UPDATE messages SET sender_name = ? WHERE msg_id = ? AND sender_name IS NULL", arrayOf(senderName, msgId))
                return null
            }
            val values = ContentValues().apply {
                put("msg_id", msgId)
                put("content", content)
                chat?.let { put("thread_id", it) }
                senderName?.let { put("sender_name", it) }
                senderId?.let { put("sender_id", it) }
                if (previous == null) { put("timestamp", timestamp); put("is_deleted", 0) }
            }
            if (previous == null) {
                db.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_IGNORE)
            } else {
                db.update("messages", values, "msg_id = ?", arrayOf(msgId))
                db.insert("edits", null, ContentValues().apply {
                    put("msg_id", msgId); put("content", previous); put("replaced_at", System.currentTimeMillis())
                })
            }
            return previous
        } catch (e: Exception) {
            Log.e("MessengerZ-DB", "Upsert failed", e)
            return null
        }
    }

    fun getContent(msgId: String): String? = try {
        readableDatabase.rawQuery("SELECT content FROM messages WHERE msg_id = ?", arrayOf(msgId)).use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (e: Exception) { null }

    /** Marks a message deleted; returns true the first time (so callers can notify once). */
    fun markAsDeleted(msgId: String): Boolean = try {
        writableDatabase.update("messages", ContentValues().apply {
            put("is_deleted", 1); put("deleted_at", System.currentTimeMillis())
        }, "msg_id = ? AND is_deleted = 0", arrayOf(msgId)) > 0
    } catch (e: Exception) {
        Log.e("MessengerZ-DB", "Mark deleted failed", e); false
    }

    fun deleteMessage(msgId: String) {
        try {
            writableDatabase.delete("messages", "msg_id = ?", arrayOf(msgId))
            writableDatabase.delete("edits", "msg_id = ?", arrayOf(msgId))
        } catch (e: Exception) {}
    }

    fun clearDeleted() {
        try {
            writableDatabase.execSQL("DELETE FROM edits WHERE msg_id IN (SELECT msg_id FROM messages WHERE is_deleted = 1)")
            writableDatabase.delete("messages", "is_deleted = 1", null)
        } catch (e: Exception) {}
    }

    /** Drops non-deleted messages older than [maxAgeMs] so the cache can't grow forever. */
    fun prune(maxAgeMs: Long) {
        try {
            val cutoff = System.currentTimeMillis() - maxAgeMs
            writableDatabase.delete("messages", "is_deleted = 0 AND timestamp < ?", arrayOf(cutoff.toString()))
            writableDatabase.execSQL("DELETE FROM edits WHERE msg_id NOT IN (SELECT msg_id FROM messages)")
        } catch (e: Exception) {}
    }

    /** Deleted messages, newest first, with their edit history attached. */
    fun getDeletedMessages(): List<LogItem> = query("WHERE is_deleted = 1 ORDER BY COALESCE(deleted_at, timestamp) DESC")

    /** Messages that were edited at least once (not deleted), newest edit first. */
    fun getEditedMessages(): List<LogItem> =
        query("WHERE is_deleted = 0 AND msg_id IN (SELECT msg_id FROM edits) ORDER BY timestamp DESC").filter { it.edits.isNotEmpty() }

    private fun query(tail: String): List<LogItem> {
        val list = ArrayList<LogItem>()
        try {
            val db = readableDatabase
            db.rawQuery("SELECT msg_id, COALESCE(thread_id,''), COALESCE(sender_name, sender_id, ''), content, COALESCE(timestamp,0), COALESCE(deleted_at,0) FROM messages $tail", null).use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val edits = ArrayList<String>()
                    db.rawQuery("SELECT content FROM edits WHERE msg_id = ? ORDER BY replaced_at", arrayOf(id)).use { e ->
                        while (e.moveToNext()) edits.add(e.getString(0))
                    }
                    list.add(LogItem(id, c.getString(1), c.getString(2), c.getString(3) ?: "", c.getLong(4), c.getLong(5), edits))
                }
            }
        } catch (e: Exception) {
            Log.e("MessengerZ-DB", "Query failed", e)
        }
        return list
    }
}
