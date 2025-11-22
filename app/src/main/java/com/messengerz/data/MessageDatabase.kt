package com.messengerz.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class MessageDatabase(context: Context) : SQLiteOpenHelper(context, "MessengerZ_Logs.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS messages (msg_id TEXT PRIMARY KEY, thread_id TEXT, sender_id TEXT, content TEXT, timestamp LONG, is_deleted INTEGER DEFAULT 0)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS messages")
        onCreate(db)
    }

    fun saveMessage(msgId: String?, threadId: String?, senderId: String?, content: String?, timestamp: Long) {
        if (msgId == null || content.isNullOrEmpty()) return
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("msg_id", msgId)
                put("thread_id", threadId ?: "unknown")
                put("sender_id", senderId ?: "unknown")
                put("content", content)
                put("timestamp", timestamp)
                put("is_deleted", 0) // Always save as NOT deleted initially
            }
            db.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
            Log.e("MessengerZ-DB", "Save error", e)
        }
    }

    fun clearAllMessages() {
        try {
            writableDatabase.delete("messages", null, null)
        } catch (e: Exception) {}
    }

    fun deleteMessage(msgId: String) {
        try {
            writableDatabase.delete("messages", "msg_id = ?", arrayOf(msgId))
        } catch (e: Exception) {}
    }

    data class LogItem(val id: String, val senderId: String, val content: String, val timestamp: Long)

    fun getAllMessagesDebug(): List<LogItem> {
        val list = ArrayList<LogItem>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT msg_id, sender_id, content, timestamp FROM messages ORDER BY timestamp DESC", null)
        if (cursor.moveToFirst()) {
            do {
                list.add(LogItem(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3)))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }
}