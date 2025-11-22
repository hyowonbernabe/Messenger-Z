package com.messengerz.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.messengerz.data.MessageDatabase
import com.messengerz.global.Global
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MessageLogDialog {

    private const val COLOR_BG = 0xFF1E1E1E.toInt()
    private const val COLOR_ACCENT = 0xFFFF3B30.toInt()
    private const val COLOR_TEXT_PRIMARY = 0xFFFFFFFF.toInt()
    private const val COLOR_TEXT_SECONDARY = 0xFFB0B3B8.toInt()
    private const val COLOR_DIVIDER = 0xFF3E4042.toInt()

    fun show(context: Context) {
        val builder = AlertDialog.Builder(context)
        val dialog = builder.create()

        val db = MessageDatabase(context)

        val mainWrapper = LinearLayout(context)
        mainWrapper.orientation = LinearLayout.VERTICAL

        fun refreshList() {
            mainWrapper.removeAllViews()

            val scrollView = ScrollView(context)
            scrollView.isFillViewport = true

            val mainLayout = LinearLayout(context)
            mainLayout.orientation = LinearLayout.VERTICAL
            val pad = dp(context, 20)
            mainLayout.setPadding(pad, pad, pad, pad)

            // Background
            val bgShape = GradientDrawable()
            bgShape.color = android.content.res.ColorStateList.valueOf(COLOR_BG)
            bgShape.cornerRadius = dp(context, 20).toFloat()
            bgShape.setStroke(dp(context, 2), COLOR_ACCENT)
            mainLayout.background = bgShape

            // Title
            val title = TextView(context)
            title.text = Global.MESSAGE_LOG_FEATURE_TITLE
            title.textSize = 20f
            title.setTypeface(null, Typeface.BOLD)
            title.setTextColor(COLOR_TEXT_PRIMARY)
            title.gravity = Gravity.CENTER
            title.setPadding(0, 0, 0, dp(context, 20))
            mainLayout.addView(title)

            val messages = db.getAllMessagesDebug()

            if (messages.isEmpty()) {
                val emptyView = TextView(context)
                emptyView.text = Global.MESSAGE_LOG_FEATURE_EMPTY
                emptyView.setTextColor(COLOR_TEXT_SECONDARY)
                emptyView.gravity = Gravity.CENTER
                emptyView.setPadding(0, 50, 0, 50)
                mainLayout.addView(emptyView)
            } else {
                for (msg in messages) {
                    mainLayout.addView(createLogItem(context, msg) {
                        db.deleteMessage(msg.id)
                        refreshList()
                    })

                    val div = View(context)
                    div.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 1))
                    div.setBackgroundColor(COLOR_DIVIDER)
                    mainLayout.addView(div)
                }

                // Clear All Button
                val btnClear = Button(context)
                btnClear.text = Global.MESSAGE_LOG_FEATURE_CLEAR
                btnClear.setTextColor(COLOR_TEXT_PRIMARY)
                btnClear.textSize = 14f

                val btnBg = GradientDrawable()
                btnBg.setColor(COLOR_ACCENT)
                btnBg.cornerRadius = dp(context, 50).toFloat()
                btnClear.background = btnBg

                val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                params.setMargins(0, dp(context, 20), 0, 0)
                btnClear.layoutParams = params

                btnClear.setOnClickListener {
                    AlertDialog.Builder(context)
                        .setTitle("Clear All?")
                        .setMessage("This will delete all saved messages.")
                        .setPositiveButton("Yes") { _, _ ->
                            db.clearAllMessages()
                            refreshList()
                        }
                        .setNegativeButton("No", null)
                        .show()
                }
                mainLayout.addView(btnClear)
            }

            scrollView.addView(mainLayout)
            mainWrapper.addView(scrollView)
        }

        refreshList()

        dialog.setView(mainWrapper)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun createLogItem(context: Context, item: MessageDatabase.LogItem, onDelete: () -> Unit): LinearLayout {
        val container = LinearLayout(context)
        container.orientation = LinearLayout.HORIZONTAL
        container.gravity = Gravity.CENTER_VERTICAL
        container.setPadding(0, dp(context, 12), 0, dp(context, 12))

        // Text Layout
        val textLayout = LinearLayout(context)
        textLayout.orientation = LinearLayout.VERTICAL
        textLayout.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val senderView = TextView(context)
        senderView.text = item.senderId
        senderView.setTextColor(COLOR_ACCENT)
        senderView.textSize = 12f
        senderView.setTypeface(null, Typeface.BOLD)

        val contentView = TextView(context)
        contentView.text = item.content
        contentView.setTextColor(COLOR_TEXT_PRIMARY)
        contentView.textSize = 15f
        contentView.setPadding(0, dp(context, 2), 0, dp(context, 4))

        val timeView = TextView(context)
        val sdf = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
        timeView.text = sdf.format(Date(item.timestamp))
        timeView.setTextColor(COLOR_TEXT_SECONDARY)
        timeView.textSize = 11f

        textLayout.addView(senderView)
        textLayout.addView(contentView)
        textLayout.addView(timeView)

        // Delete Button
        val deleteBtn = TextView(context)
        deleteBtn.text = "✕"
        deleteBtn.textSize = 18f
        deleteBtn.setTextColor(COLOR_TEXT_SECONDARY)
        deleteBtn.setPadding(dp(context, 15), dp(context, 10), dp(context, 5), dp(context, 10))
        deleteBtn.setOnClickListener { onDelete() }

        container.addView(textLayout)
        container.addView(deleteBtn)

        return container
    }

    private fun dp(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}