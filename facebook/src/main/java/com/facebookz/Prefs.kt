package com.facebookz

import android.content.Context
import android.content.SharedPreferences

/** Facebook Z switches. Each [Flag] is one on/off setting stored in the host app's prefs. */
object Prefs {
    const val NAME = "FacebookZ_Prefs"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    }

    class Flag(private val key: String, private val default: Boolean, val title: String, val desc: String) {
        val on: Boolean get() = prefs?.getBoolean(key, default) ?: default
        fun set(value: Boolean) { prefs?.edit()?.putBoolean(key, value)?.apply() }
    }

    val NO_STORY_SEEN = Flag("no_story_seen", true, "Hide Story Views", "Watch stories without showing up in the viewer list.")
    val NO_AUTOSWIPE = Flag("no_autoswipe", false, "Stop Story Auto-Swipe", "Stay on each story until you tap to move on.")
    val STORY_DOWNLOAD = Flag("story_download", true, "Story Download Button", "Show a ⬇ button in stories to save them to Downloads.")
    val APPEAR_OFFLINE = Flag("appear_offline", false, "Appear Offline", "Hide your \"Active now\" while still seeing others. Restart Facebook to apply.")
    val NO_FEED_ADS = Flag("no_feed_ads", true, "Hide Sponsored Posts", "Remove ads from your feed.")
    val NO_STORY_ADS = Flag("no_story_ads", true, "Hide Story Ads", "Skip ads between stories.")
    val CLEAR_SEARCH = Flag("clear_search", false, "Auto-Clear Search History", "Forget your searches every time Facebook starts.")
}
