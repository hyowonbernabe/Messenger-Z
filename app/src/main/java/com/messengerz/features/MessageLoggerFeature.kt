package com.messengerz.features

import android.app.AndroidAppHelper
import android.content.Context
import android.util.Log
import com.messengerz.data.MessageDatabase
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

object MessageLoggerFeature {
    private const val TAG = "MessengerZ-Logger"
    private var db: MessageDatabase? = null

    // Deduplication Cache
    private var lastMessageHash = ""
    private var lastMessageTime = 0L

    fun setContext(context: Context) {
        if (db == null) {
            db = MessageDatabase(context)
        }
    }

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            Log.d(TAG, "Initializing Message Logger...")
            val context = AndroidAppHelper.currentApplication()
            if (context != null) setContext(context)

            val notificationClass = XposedHelpers.findClassIfExists(
                "com.facebook.messaging.notify.type.NewMessageNotification",
                lpparam.classLoader
            )

            if (notificationClass != null) {
                XposedBridge.hookAllConstructors(notificationClass, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val notification = param.thisObject
                            findMessageFields(notification)
                        } catch (e: Throwable) {
                            Log.e(TAG, "Error processing notification", e)
                        }
                    }
                })
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in Logger init", e)
        }
    }

    private fun findMessageFields(notification: Any) {
        val fields = notification.javaClass.declaredFields
        var messageObj: Any? = null
        var threadSummaryObj: Any? = null

        for (field in fields) {
            field.isAccessible = true
            val value = field.get(notification) ?: continue
            val className = value.javaClass.name

            if (className.contains("model.messages.Message")) {
                messageObj = value
            }
            if (className.contains("model.threads.ThreadSummary")) {
                threadSummaryObj = value
            }
        }

        if (messageObj != null) {
            processMessage(messageObj, threadSummaryObj)
        }
    }

    private fun processMessage(message: Any, threadSummary: Any?) {
        try {
            val fields = message.javaClass.declaredFields

            var content: String? = null
            var senderName: String? = null
            var senderId: String? = null
            var threadKey: String = "unknown"
            var realMsgId: String? = null

            for (field in fields) {
                field.isAccessible = true
                val value = field.get(message)

                if (value != null) {
                    val className = value.javaClass.name

                    // 1. Content
                    if (className.contains("SecretString")) {
                        val extracted = extractSecret(value)
                        if (content == null && extracted != "[Secret]") {
                            content = extracted
                        }
                    }

                    // 2. Sender ID
                    if (className.contains("ParticipantInfo")) {
                        val participantStr = value.toString()
                        val match = Regex("FACEBOOK:(\\d+)").find(participantStr)
                        senderId = match?.groupValues?.get(1) ?: participantStr
                    }

                    // 3. Thread Key
                    if (className.contains("ThreadKey")) {
                        threadKey = value.toString()
                    }

                    // 4. Real Message ID (A1X)
                    if (field.name == "A1X" && value is String) {
                        realMsgId = value
                    } else if (value is String && value.length > 20 && !value.contains("FACEBOOK")) {
                        if (realMsgId == null) realMsgId = value
                    }
                }
            }

            // 5. Name from ThreadSummary
            if (threadSummary != null) {
                senderName = extractNameFromSummary(threadSummary)
            }

            val finalSender = if (!senderName.isNullOrEmpty()) senderName else senderId

            if (content != null && finalSender != null) {

                // --- UNSEND DETECTION LOGIC ---
                if (content.contains("sent a message") || content.contains("removed a message")) {
                    if (realMsgId != null) {
                        Log.w(TAG, ">>> DETECTED UNSEND via Notification! ID: $realMsgId")
                        db?.markAsDeleted(realMsgId)
                    } else {
                        Log.e(TAG, "Unsend detected but ID was null.")
                    }
                    return // Stop here. Do not save "sent a message" to DB.
                }

                // --- DEDUPLICATION ---
                val currentHash = "$finalSender$content"
                val now = System.currentTimeMillis()
                if (currentHash == lastMessageHash && (now - lastMessageTime) < 2000) return
                lastMessageHash = currentHash
                lastMessageTime = now

                Log.w(TAG, ">>> CAPTURED: '$content' (ID: $realMsgId)")

                val dbId = realMsgId ?: now.toString()

                db?.saveMessage(dbId, threadKey, finalSender, content, now)
            }

        } catch (e: Throwable) {
            Log.e(TAG, "Error parsing message details", e)
        }
    }

    private fun extractSecret(secretStringObj: Any): String {
        try {
            val fields = secretStringObj.javaClass.declaredFields
            for (f in fields) {
                f.isAccessible = true
                val v = f.get(secretStringObj) as? String ?: continue
                if (v.isNotEmpty() && v != "null" && !v.all { it == '*' }) return v
            }
        } catch (e: Throwable) {}
        return "[Secret]"
    }

    private fun extractNameFromSummary(summary: Any): String? {
        try {
            val fields = summary.javaClass.declaredFields
            for (f in fields) {
                f.isAccessible = true
                val v = f.get(summary)
                if (v is String && v.length > 1 && !v.all { it == '*' } && !v.contains("FACEBOOK")) {
                    if (v.any { it.isLetter() }) return v
                }
                if (v != null && v.javaClass.name.contains("SecretString")) {
                    val secret = extractSecret(v)
                    if (secret != "[Secret]") return secret
                }
            }
        } catch (e: Throwable) {}
        return null
    }
}