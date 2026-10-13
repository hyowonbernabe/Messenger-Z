package com.zmods.shared.ui

import android.app.AlertDialog
import android.content.Context
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

/**
 * Building blocks for the Messenger Z / Facebook Z settings screens: a dark rounded sheet with
 * a header, grouped cards, switch rows (whole row toggles) and action rows. Pure Views, no
 * resources, so it works inside the host app.
 */
object SettingsKit {
    const val COLOR_BG = 0xFF121316.toInt()
    const val COLOR_CARD = 0xFF1E1F23.toInt()
    const val COLOR_ACCENT = 0xFFFF3B30.toInt()
    private const val COLOR_ACCENT_DIM = 0x55FF3B30
    const val COLOR_TEXT_PRIMARY = 0xFFF2F3F5.toInt()
    const val COLOR_TEXT_SECONDARY = 0xFF9EA3AB.toInt()
    private const val COLOR_DIVIDER = 0xFF2B2D32.toInt()
    private const val COLOR_SWITCH_OFF = 0xFFB8BCC4.toInt()
    private const val COLOR_TRACK_OFF = 0xFF3A3C42.toInt()

    /**
     * Shows the settings sheet. [build] fills the scrolling content (use [section] etc.);
     * it receives the dialog so rows can dismiss it.
     */
    fun show(context: Context, title: String, subtitle: String, footer: List<Pair<String, (() -> Unit)?>>,
             build: (content: LinearLayout, dialog: AlertDialog) -> Unit) {
        val dialog = AlertDialog.Builder(context).create()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 20), dp(context, 22), dp(context, 20), dp(context, 18))
            background = GradientDrawable().apply { setColor(COLOR_BG); cornerRadius = dp(context, 26).toFloat() }
        }
        root.addView(TextView(context).apply {
            text = title
            textSize = 24f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(COLOR_TEXT_PRIMARY)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(context).apply {
            text = subtitle
            textSize = 12f
            setTextColor(COLOR_ACCENT)
            gravity = Gravity.CENTER
            setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 4))
            background = GradientDrawable().apply { setColor(0x1FFF3B30); cornerRadius = dp(context, 50).toFloat() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { gravity = Gravity.CENTER_HORIZONTAL; setMargins(0, dp(context, 8), 0, dp(context, 6)) }
        })

        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        build(content, dialog)
        footer.forEachIndexed { i, (text, onClick) ->
            content.addView(TextView(context).apply {
                this.text = text
                textSize = 12f
                setTextColor(if (onClick != null) COLOR_ACCENT else COLOR_TEXT_SECONDARY)
                gravity = Gravity.CENTER
                setPadding(0, if (i == 0) dp(context, 18) else dp(context, 2), 0, dp(context, 2))
                onClick?.let { c -> setOnClickListener { c() } }
            })
        }

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
        dialog.setView(LinearLayout(context).apply { addView(root, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)) })
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    /** Section label + rounded card; returns the card to add rows to. */
    fun section(context: Context, parent: LinearLayout, title: String): LinearLayout {
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

    fun switchRow(context: Context, card: LinearLayout, title: String, desc: String, initial: Boolean, onToggle: (Boolean) -> Unit) {
        divider(context, card)
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
        })
    }

    /** Row that opens something; returns the subtitle view so callers can update it. */
    fun actionRow(context: Context, card: LinearLayout, title: String, subtitle: String, onClick: () -> Unit): TextView {
        divider(context, card)
        val (block, descView) = textBlock(context, title, subtitle)
        card.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14))
            addView(block)
            addView(TextView(context).apply { text = "›"; textSize = 22f; setTextColor(COLOR_TEXT_SECONDARY) })
            setOnClickListener { onClick() }
        })
        return descView
    }

    fun confirm(context: Context, title: String, message: String, onYes: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> onYes() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun divider(context: Context, card: LinearLayout) {
        if (card.childCount == 0) return
        card.addView(View(context).apply {
            setBackgroundColor(COLOR_DIVIDER)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 1))
                .apply { setMargins(dp(context, 16), 0, dp(context, 16), 0) }
        })
    }

    private fun textBlock(context: Context, title: String, desc: String): Pair<LinearLayout, TextView> {
        val descView = TextView(context).apply {
            text = desc
            textSize = 12.5f
            setTextColor(COLOR_TEXT_SECONDARY)
            setPadding(0, dp(context, 2), 0, 0)
        }
        val block = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = title
                textSize = 15f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
                setTextColor(COLOR_TEXT_PRIMARY)
            })
            addView(descView)
        }
        return block to descView
    }

    private fun states(on: Int, off: Int) = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
        intArrayOf(on, off)
    )

    fun dp(context: Context, dp: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics).toInt()
}
