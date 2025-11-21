package com.messengerz.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.messengerz.core.Preferences

object SettingsDialog {
    private const val COLOR_BG = 0xFF1E1E1E.toInt()
    private const val COLOR_ACCENT = 0xFFFF3B30.toInt()
    private const val COLOR_ACCENT_DIM = 0x4DFF3B30.toInt()
    private const val COLOR_TEXT_PRIMARY = 0xFFFFFFFF.toInt()
    private const val COLOR_TEXT_SECONDARY = 0xFFB0B3B8.toInt()
    private const val COLOR_DIVIDER = 0xFF3E4042.toInt()
    private const val COLOR_SWITCH_OFF = 0xFFB0B0B0.toInt()
    private const val COLOR_TRACK_OFF = 0xFF505050.toInt()

    fun show(context: Context) {
        val builder = AlertDialog.Builder(context)
        val dialog = builder.create()

        val scrollView = ScrollView(context)
        scrollView.isFillViewport = true

        // Main Card
        val mainLayout = LinearLayout(context)
        mainLayout.orientation = LinearLayout.VERTICAL
        val pad = dp(context, 24)
        mainLayout.setPadding(pad, pad, pad, pad)

        val bgShape = GradientDrawable()
        bgShape.color = ColorStateList.valueOf(COLOR_BG)
        bgShape.cornerRadius = dp(context, 20).toFloat()
        bgShape.setStroke(dp(context, 2), COLOR_ACCENT)
        mainLayout.background = bgShape

        // --- Header ---
        val title = TextView(context)
        title.text = "Messenger Z"
        title.textSize = 22f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(COLOR_TEXT_PRIMARY)
        title.gravity = Gravity.CENTER
        mainLayout.addView(title)

        val subtitle = TextView(context)
        subtitle.text = "v1.0.0"
        subtitle.textSize = 13f
        subtitle.setTextColor(COLOR_ACCENT)
        subtitle.gravity = Gravity.CENTER
        subtitle.setPadding(0, 0, 0, dp(context, 24))
        mainLayout.addView(subtitle)

        // --- Features ---

        // Seen
        addSwitchRow(context, mainLayout,
            "Disable Seen Indicator",
            "Read messages without alerting the sender.",
            Preferences.isNoSeenEnabled
        ) { isChecked -> Preferences.setNoSeenEnabled(isChecked) }

        addSpacer(context, mainLayout)

        // Typing
        addSwitchRow(context, mainLayout,
            "Disable Typing Indicator",
            "Hide the typing animation while you write.",
            Preferences.isNoTypingEnabled
        ) { isChecked -> Preferences.setNoTypingEnabled(isChecked) }

        // Divider
        val divider = View(context)
        divider.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 1))
        divider.setBackgroundColor(COLOR_DIVIDER)
        val divParams = divider.layoutParams as LinearLayout.LayoutParams
        divParams.setMargins(0, dp(context, 20), 0, dp(context, 20))
        divider.layoutParams = divParams
        mainLayout.addView(divider)

        // Credits
        val author = TextView(context)
        author.text = "Created by Hyowon Bernabe"
        author.textSize = 13f
        author.setTextColor(COLOR_TEXT_SECONDARY)
        author.gravity = Gravity.CENTER
        mainLayout.addView(author)

        val github = TextView(context)
        github.text = "github.com/hyowonbernabe/Messenger-Z"
        github.textSize = 13f
        github.setTextColor(COLOR_ACCENT)
        github.gravity = Gravity.CENTER
        github.setPadding(0, dp(context, 4), 0, dp(context, 20))
        github.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hyowonbernabe/Messenger-Z/"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {}
        }
        mainLayout.addView(github)

        // Done Button
        val btnClose = Button(context)
        btnClose.text = "Done"
        btnClose.setTextColor(COLOR_TEXT_PRIMARY)
        btnClose.textSize = 14f
        btnClose.setTypeface(null, Typeface.BOLD)

        val btnBg = GradientDrawable()
        btnBg.setColor(COLOR_ACCENT)
        btnBg.cornerRadius = dp(context, 50).toFloat()
        btnClose.background = btnBg

        btnClose.setOnClickListener { dialog.dismiss() }
        mainLayout.addView(btnClose)

        scrollView.addView(mainLayout)
        dialog.setView(scrollView)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun addSwitchRow(
        context: Context,
        parent: LinearLayout,
        titleText: String,
        descText: String,
        initialState: Boolean,
        onToggle: (Boolean) -> Unit
    ) {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(context, 8), 0, dp(context, 8))

        val textLayout = LinearLayout(context)
        textLayout.orientation = LinearLayout.VERTICAL
        textLayout.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val tvTitle = TextView(context)
        tvTitle.text = titleText
        tvTitle.textSize = 16f
        tvTitle.setTypeface(null, Typeface.BOLD)
        tvTitle.setTextColor(COLOR_TEXT_PRIMARY)

        val tvDesc = TextView(context)
        tvDesc.text = descText
        tvDesc.textSize = 12f
        tvDesc.setTextColor(COLOR_TEXT_SECONDARY)

        textLayout.addView(tvTitle)
        textLayout.addView(tvDesc)

        val switchView = Switch(context)
        switchView.isChecked = initialState

        val thumbStates = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                COLOR_ACCENT,
                COLOR_SWITCH_OFF
            )
        )
        switchView.thumbTintList = thumbStates

        val trackStates = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                COLOR_ACCENT_DIM,
                COLOR_TRACK_OFF
            )
        )
        switchView.trackTintList = trackStates

        switchView.setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }

        row.addView(textLayout)
        row.addView(switchView)
        parent.addView(row)
    }

    private fun addSpacer(context: Context, layout: LinearLayout) {
        val view = View(context)
        view.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 12))
        layout.addView(view)
    }

    private fun dp(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}