package com.messengerz.core

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

object Preferences {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(context)
        }
    }

    val isNoSeenEnabled: Boolean
        get() {
            if (prefs == null) return true
            return prefs?.getBoolean("pref_no_seen", true) ?: true
        }

    fun setNoSeenEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean("pref_no_seen", enabled)?.apply()
    }
}