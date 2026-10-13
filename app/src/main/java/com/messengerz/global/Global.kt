package com.messengerz.global

object Global {
    val VERSION = "v" + com.messengerz.BuildConfig.VERSION_NAME
    const val MESSENGER_Z_TITLE = "Messenger Z"
    const val MESSENGER_Z_AUTHOR = "Created by Hyowon Bernabe"
    const val MESSENGER_Z_GITHUB = "https://github.com/hyowonbernabe/Messenger-Z"

    const val NO_SEEN_FEATURE_TITLE = "Disable Seen Indicator"
    const val NO_SEEN_FEATURE_DESC = "Read messages without alerting the sender."

    const val NO_TYPING_FEATURE_TITLE = "Disable Typing Indicator"
    const val NO_TYPING_FEATURE_DESC = "Hide the typing animation while you write."
    const val STORY_AUTOSWIPE_TITLE = "Stop Story Auto-Swipe"
    const val STORY_AUTOSWIPE_DESC = "Stay on each story until you tap to move on."
    const val APPEAR_OFFLINE_TITLE = "Appear Offline"
    const val APPEAR_OFFLINE_DESC = "Hide your \"Active now\" while still seeing others. Restart Messenger to apply."
    const val CLEAN_LINKS_TITLE = "Remove Link Tracking"
    const val CLEAN_LINKS_DESC = "Open links without Facebook's redirect and tracking tags."
    const val NO_STORY_SEEN_FEATURE_TITLE = "Disable Story Seen"
    const val NO_STORY_SEEN_FEATURE_DESC = "Watch stories without showing up in the viewer list."

    const val SPOOF_VERSION_FEATURE_TITLE = "Spoof Latest Version"
    const val SPOOF_VERSION_FEATURE_DESC = "Sets Messenger version to 999.0.0.99.999 (Debug purposes)."
    const val MESSAGE_LOG_FEATURE_TITLE = "Logs Unsent Messages"
    const val MESSAGE_LOG_FEATURE_DESC = "Keep a copy of messages people unsend or edit."
    const val LOGGER_INLINE_TITLE = "Show Unsent Text In Chat"
    const val LOGGER_INLINE_DESC = "Replace \"unsent a message\" with the original text."
    const val MESSAGE_LOG_FEATURE_EMPTY = "Nothing captured yet."
    const val MESSAGE_LOG_FEATURE_CLEAR = "Clear Unsent Log"
}