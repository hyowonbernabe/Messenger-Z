package com.messengerz.core

import android.content.Context
import android.content.SharedPreferences

object Preferences {
    private const val PREF_NAME = "MessengerZ_Prefs"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    // --- SEEN ---
    val isNoSeenEnabled: Boolean
        get() = prefs?.getBoolean("pref_no_seen", true) ?: true

    fun setNoSeenEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean("pref_no_seen", enabled)?.apply()
    }

    // --- TYPING ---
    val isNoTypingEnabled: Boolean
        get() = prefs?.getBoolean("pref_no_typing", true) ?: true

    fun setNoTypingEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean("pref_no_typing", enabled)?.apply()
    }

    // --- STORY SEEN ---
    val isNoStorySeenEnabled: Boolean
        get() = prefs?.getBoolean("pref_no_story_seen", true) ?: true

    fun setNoStorySeenEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean("pref_no_story_seen", enabled)?.apply()
    }

    // --- LINK CLEANING ---
    val isCleanLinksEnabled: Boolean
        get() = prefs?.getBoolean("pref_clean_links", true) ?: true

    fun setCleanLinksEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean("pref_clean_links", enabled)?.apply()
    }

    // --- SPOOF VERSION ---
    val isSpoofVersionEnabled: Boolean
        get() = prefs?.getBoolean("pref_spoof_version", false) ?: false

    fun setSpoofVersionEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean("pref_spoof_version", enabled)?.apply()
    }

    // --- MESSAGE LOGGER ---
    val isMessageLoggerEnabled: Boolean
        get() = prefs?.getBoolean("pref_msg_logger", true) ?: true

    fun setMessageLoggerEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean("pref_msg_logger", enabled)?.apply()
    }

    /** Show the original text in place of "X unsent a message" inside the chat. */
    val isLoggerInlineEnabled: Boolean
        get() = prefs?.getBoolean("pref_logger_inline", true) ?: true

    fun setLoggerInlineEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean("pref_logger_inline", enabled)?.apply()
    }
}