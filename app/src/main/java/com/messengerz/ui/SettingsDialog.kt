package com.messengerz.ui

import android.content.Context
import android.content.Intent
import android.widget.TextView
import android.widget.Toast
import androidx.core.net.toUri
import com.messengerz.core.GhostChats
import com.messengerz.core.Preferences
import com.messengerz.global.Global
import com.zmods.shared.AppControl
import com.zmods.shared.ui.SettingsKit
import com.zmods.shared.ui.SettingsKit.actionRow
import com.zmods.shared.ui.SettingsKit.confirm
import com.zmods.shared.ui.SettingsKit.section
import com.zmods.shared.ui.SettingsKit.switchRow

/** Messenger Z settings. Opened by long-pressing the "Messenger" title (see MenuInjector). */
object SettingsDialog {

    fun show(context: Context) {
        val footer = listOf<Pair<String, (() -> Unit)?>>(
            Global.MESSENGER_Z_AUTHOR to null,
            Global.MESSENGER_Z_GITHUB.removePrefix("https://") to {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Global.MESSENGER_Z_GITHUB.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {}
            },
        )
        SettingsKit.show(context, Global.MESSENGER_Z_TITLE,
            "${Global.VERSION}  ·  Messenger ${com.messengerz.BuildConfig.MESSENGER_VER}", footer) { content, dialog ->

            section(context, content, "Ghost mode").apply {
                switchRow(context, this, Global.NO_SEEN_FEATURE_TITLE, Global.NO_SEEN_FEATURE_DESC, Preferences.isNoSeenEnabled) { Preferences.setNoSeenEnabled(it) }
                switchRow(context, this, Global.NO_TYPING_FEATURE_TITLE, Global.NO_TYPING_FEATURE_DESC, Preferences.isNoTypingEnabled) { Preferences.setNoTypingEnabled(it) }
                switchRow(context, this, Global.NO_STORY_SEEN_FEATURE_TITLE, Global.NO_STORY_SEEN_FEATURE_DESC, Preferences.isNoStorySeenEnabled) { Preferences.setNoStorySeenEnabled(it) }
                lateinit var applies: TextView
                applies = actionRow(context, this, "Applies to", GhostChats.mode.label) {
                    GhostChatsDialog.show(context) { applies.text = GhostChats.mode.label }
                }
            }

            section(context, content, "Messages").apply {
                switchRow(context, this, Global.MESSAGE_LOG_FEATURE_TITLE, Global.MESSAGE_LOG_FEATURE_DESC, Preferences.isMessageLoggerEnabled) { Preferences.setMessageLoggerEnabled(it) }
                switchRow(context, this, Global.LOGGER_INLINE_TITLE, Global.LOGGER_INLINE_DESC, Preferences.isLoggerInlineEnabled) { Preferences.setLoggerInlineEnabled(it) }
                actionRow(context, this, "Unsent & edited messages", "Open the log") { MessageLogDialog.show(context) }
            }

            section(context, content, "Stories").apply {
                switchRow(context, this, Global.STORY_AUTOSWIPE_TITLE, Global.STORY_AUTOSWIPE_DESC, Preferences.isStoryAutoSwipeDisabled) { Preferences.setStoryAutoSwipeDisabled(it) }
                switchRow(context, this, Global.STORY_DOWNLOAD_TITLE, Global.STORY_DOWNLOAD_DESC, Preferences.isStoryDownloadEnabled) { Preferences.setStoryDownloadEnabled(it) }
            }

            section(context, content, "Links & presence").apply {
                switchRow(context, this, Global.CLEAN_LINKS_TITLE, Global.CLEAN_LINKS_DESC, Preferences.isCleanLinksEnabled) { Preferences.setCleanLinksEnabled(it) }
                switchRow(context, this, Global.APPEAR_OFFLINE_TITLE, Global.APPEAR_OFFLINE_DESC, Preferences.isAppearOfflineEnabled) { Preferences.setAppearOfflineEnabled(it) }
                switchRow(context, this, Global.SPOOF_VERSION_FEATURE_TITLE, Global.SPOOF_VERSION_FEATURE_DESC, Preferences.isSpoofVersionEnabled) { Preferences.setSpoofVersionEnabled(it) }
            }

            section(context, content, "Tools").apply {
                actionRow(context, this, "Restart Messenger", "Apply changes that need a restart") { AppControl.restart(context) }
                actionRow(context, this, "Clear Messenger cache", "Free space used by cached media") {
                    confirm(context, "Clear cache?", "Cached images and videos will be downloaded again when needed.") {
                        val mb = AppControl.clearCache(context) / (1024 * 1024)
                        Toast.makeText(context, "Cleared $mb MB", Toast.LENGTH_SHORT).show()
                    }
                }
                actionRow(context, this, "Reset learned data", "Re-detect message layout and chats, then restart") {
                    confirm(context, "Reset learned data?", "Messenger Z will re-learn automatically. Your logs are kept.") {
                        AppControl.resetLearnedData(context, "MessengerZ_Prefs") { it.startsWith("logger_layouts_") || it == "ghost_chats" }
                        AppControl.restart(context)
                    }
                }
                actionRow(context, this, "Debug Console", "Live list of Messenger's internal calls") {
                    dialog.dismiss()
                    DebugConsoleOverlay.show(context)
                }
            }
        }
    }
}
