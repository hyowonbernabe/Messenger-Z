package com.messengerz.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.net.toUri
import com.messengerz.core.AppControl
import com.messengerz.core.GhostChats
import com.messengerz.core.Preferences
import com.messengerz.global.Global

/**
 * Messenger Z settings. Opened by long-pressing the "Messenger" title (see MenuInjector).
 * Grouped cards; tapping anywhere on a row toggles it.
 */
object SettingsDialog {
    private const val COLOR_BG = 0xFF121316.toInt()
    private const val COLOR_CARD = 0xFF1E1F23.toInt()
    private const val COLOR_ACCENT = 0xFFFF3B30.toInt()
    private const val COLOR_ACCENT_DIM = 0x55FF3B30
    private const val COLOR_TEXT_PRIMARY = 0xFFF2F3F5.toInt()
    private const val COLOR_TEXT_SECONDARY = 0xFF9EA3AB.toInt()
    private const val COLOR_DIVIDER = 0xFF2B2D32.toInt()
    private const val COLOR_SWITCH_OFF = 0xFFB8BCC4.toInt()
    private const val COLOR_TRACK_OFF = 0xFF3A3C42.toInt()

    fun show(context: Context) {
        val dialog = AlertDialog.Builder(context).create()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 20), dp(context, 22), dp(context, 20), dp(context, 18))
            background = GradientDrawable().apply { setColor(COLOR_BG); cornerRadius = dp(context, 26).toFloat() }
        }

        // --- Header ---
        root.addView(TextView(context).apply {
            text = Global.MESSENGER_Z_TITLE
            textSize = 24f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(COLOR_TEXT_PRIMARY)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(context).apply {
            text = "${Global.VERSION}  ·  Messenger ${com.messengerz.BuildConfig.MESSENGER_VER}"
            textSize = 12f
            setTextColor(COLOR_ACCENT)
            gravity = Gravity.CENTER
            setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 4))
            background = GradientDrawable().apply { setColor(0x1FFF3B30); cornerRadius = dp(context, 50).toFloat() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { gravity = Gravity.CENTER_HORIZONTAL; setMargins(0, dp(context, 8), 0, dp(context, 6)) }
        })

        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        // --- Ghost mode ---
        section(context, content, "Ghost mode").apply {
            switchRow(context, this, Global.NO_SEEN_FEATURE_TITLE, Global.NO_SEEN_FEATURE_DESC, Preferences.isNoSeenEnabled) { Preferences.setNoSeenEnabled(it) }
            switchRow(context, this, Global.NO_TYPING_FEATURE_TITLE, Global.NO_TYPING_FEATURE_DESC, Preferences.isNoTypingEnabled) { Preferences.setNoTypingEnabled(it) }
            switchRow(context, this, Global.NO_STORY_SEEN_FEATURE_TITLE, Global.NO_STORY_SEEN_FEATURE_DESC, Preferences.isNoStorySeenEnabled) { Preferences.setNoStorySeenEnabled(it) }
            lateinit var applies: TextView
            applies = actionRow(context, this, "Applies to", GhostChats.mode.label) {
                GhostChatsDialog.show(context) { applies.text = GhostChats.mode.label }
            }
        }

        // --- Messages ---
        section(context, content, "Messages").apply {
            switchRow(context, this, Global.MESSAGE_LOG_FEATURE_TITLE, Global.MESSAGE_LOG_FEATURE_DESC, Preferences.isMessageLoggerEnabled) { Preferences.setMessageLoggerEnabled(it) }
            switchRow(context, this, Global.LOGGER_INLINE_TITLE, Global.LOGGER_INLINE_DESC, Preferences.isLoggerInlineEnabled) { Preferences.setLoggerInlineEnabled(it) }
            actionRow(context, this, "Unsent & edited messages", "Open the log") { MessageLogDialog.show(context) }
        }

        // --- Links & presence ---
        section(context, content, "Links & presence").apply {
            switchRow(context, this, Global.CLEAN_LINKS_TITLE, Global.CLEAN_LINKS_DESC, Preferences.isCleanLinksEnabled) { Preferences.setCleanLinksEnabled(it) }
            switchRow(context, this, Global.APPEAR_OFFLINE_TITLE, Global.APPEAR_OFFLINE_DESC, Preferences.isAppearOfflineEnabled) { Preferences.setAppearOfflineEnabled(it) }
            switchRow(context, this, Global.SPOOF_VERSION_FEATURE_TITLE, Global.SPOOF_VERSION_FEATURE_DESC, Preferences.isSpoofVersionEnabled) { Preferences.setSpoofVersionEnabled(it) }
        }

        // --- Tools ---
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
                    AppControl.resetLearnedData(context)
                    AppControl.restart(context)
                }
            }
            actionRow(context, this, "Debug Console", "Live list of Messenger's internal calls") {
                dialog.dismiss()
                DebugConsoleOverlay.show(context)
            }
        }

        // --- Footer ---
        content.addView(TextView(context).apply {
            text = Global.MESSENGER_Z_AUTHOR
            textSize = 12f
            setTextColor(COLOR_TEXT_SECONDARY)
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 18), 0, 0)
        })
        content.addView(TextView(context).apply {
            text = Global.MESSENGER_Z_GITHUB.removePrefix("https://")
            textSize = 12f
            setTextColor(COLOR_ACCENT)
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 2), 0, dp(context, 6))
            setOnClickListener {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Global.MESSENGER_Z_GITHUB.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {}
            }
        })

        root.addView(ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(content)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        })

        root.addView(TextView(context).apply {
            text = "Done"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 14), 0, dp(context, 14))
            background = GradientDrawable().apply { setColor(COLOR_ACCENT); cornerRadius = dp(context, 50).toFloat() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, dp(context, 12), 0, 0) }
            setOnClickListener { dialog.dismiss() }
        })

        val height = (context.resources.displayMetrics.heightPixels * 0.85).toInt()
        val frame = LinearLayout(context).apply { addView(root, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)) }
        dialog.setView(frame)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    /** Section label + rounded card; returns the card to add rows to. */
    private fun section(context: Context, parent: LinearLayout, title: String): LinearLayout {
        parent.addView(TextView(context).apply {
            text = title.uppercase()
            textSize = 11f
            letterSpacing = 0.08f
            setTypeface(null, Typeface.BOLD)
            setTextColor(COLOR_TEXT_SECONDARY)
            setPadding(dp(context, 6), dp(context, 18), 0, dp(context, 8))
        })
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(COLOR_CARD); cornerRadius = dp(context, 18).toFloat() }
            clipToOutline = true
        }
        parent.addView(card)
        return card
    }

    private fun addDividerIfNeeded(context: Context, card: LinearLayout) {
        if (card.childCount == 0) return
        card.addView(View(context).apply {
            setBackgroundColor(COLOR_DIVIDER)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 1))
                .apply { setMargins(dp(context, 16), 0, dp(context, 16), 0) }
        })
    }

    private fun textBlock(context: Context, title: String, desc: String?): Pair<LinearLayout, TextView?> {
        var descView: TextView? = null
        val block = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = title
                textSize = 15f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
                setTextColor(COLOR_TEXT_PRIMARY)
            })
            if (desc != null) addView(TextView(context).apply {
                text = desc
                textSize = 12.5f
                setTextColor(COLOR_TEXT_SECONDARY)
                setPadding(0, dp(context, 2), 0, 0)
                descView = this
            })
        }
        return block to descView
    }

    private fun switchRow(context: Context, card: LinearLayout, title: String, desc: String, initial: Boolean, onToggle: (Boolean) -> Unit) {
        addDividerIfNeeded(context, card)
        val sw = Switch(context).apply {
            isChecked = initial
            thumbTintList = states(COLOR_ACCENT, COLOR_SWITCH_OFF)
            trackTintList = states(COLOR_ACCENT_DIM, COLOR_TRACK_OFF)
            setOnCheckedChangeListener { _, on -> onToggle(on) }
        }
        card.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 16), dp(context, 13), dp(context, 12), dp(context, 13))
            addView(textBlock(context, title, desc).first)
            addView(sw)
            setOnClickListener { sw.toggle() }
            isClickable = true
        })
    }

    /** Row that opens something; returns the subtitle view so callers can update it. */
    private fun actionRow(context: Context, card: LinearLayout, title: String, subtitle: String, onClick: () -> Unit): TextView {
        addDividerIfNeeded(context, card)
        val (block, descView) = textBlock(context, title, subtitle)
        card.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14))
            addView(block)
            addView(TextView(context).apply { text = "›"; textSize = 22f; setTextColor(COLOR_TEXT_SECONDARY) })
            setOnClickListener { onClick() }
        })
        return descView!!
    }

    private fun confirm(context: Context, title: String, message: String, onYes: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> onYes() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun states(on: Int, off: Int) = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
        intArrayOf(on, off)
    )

    private fun dp(context: Context, dp: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics).toInt()
}
