package com.messengerz.core

import de.robv.android.xposed.XSharedPreferences

object Preferences {
    // We use the package name of OUR module, not Messenger
    private const val MODULE_PACKAGE = "com.messengerz"
    private var prefs: XSharedPreferences? = null

    fun init() {
        if (prefs == null) {
            prefs = XSharedPreferences(MODULE_PACKAGE, "MessengerZ_Prefs")
            prefs?.makeWorldReadable()
        } else {
            prefs?.reload()
        }
    }

    val isNoSeenEnabled: Boolean
        get() {
            // prefs?.reload()
            // return prefs?.getBoolean("pref_no_seen", false) ?: false
            return true // FORCE ON FOR TESTING
        }
}