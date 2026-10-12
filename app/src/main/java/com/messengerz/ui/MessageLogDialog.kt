package com.messengerz.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
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
import android.widget.Toast
import com.messengerz.data.MessageDatabase
import com.messengerz.global.Global
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Unsent / edited message log, grouped by sender. Tap an entry to copy it. */
object MessageLogDialog {

    private const val COLOR_BG = 0xFF1E1E1E.toInt()
    private const val COLOR_ACCENT = 0xFFFF3B30.toInt()
    private const val COLOR_TEXT_PRIMARY = 0xFFFFFFFF.toInt()
    private const val COLOR_TEXT_SECONDARY = 0xFFB0B3B8.toInt()
    private const val COLOR_DIVIDER = 0xFF3E4042.toInt()
    private const val COLOR_CHIP = 0xFF2C2D2F.toInt()

    fun show(context: Context) {
        val dialog = AlertDialog.Builder(context).create()
        val db = MessageDatabase(context)
        val wrapper = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        var showEdited = false

        fun render() {
            wrapper.removeAllViews()
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val pad = dp(context, 20)
                setPadding(pad, pad, pad, pad)
                background = GradientDrawable().apply {
                    setColor(COLOR_BG)
                    cornerRadius = dp(context, 20).toFloat()
                    setStroke(dp(context, 2), COLOR_ACCENT)
                }
            }
            root.addView(TextView(context).apply {
                text = "Message Log"
                textSize = 20f
                setTypeface(null, Typeface.BOLD)
                setTextColor(COLOR_TEXT_PRIMARY)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(context, 12))
            })

            // Tabs
            val tabs = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            fun tab(label: String, active: Boolean, onClick: () -> Unit) = TextView(context).apply {
                text = label
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (active) COLOR_TEXT_PRIMARY else COLOR_TEXT_SECONDARY)
                setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8))
                background = GradientDrawable().apply {
                    setColor(if (active) COLOR_ACCENT else COLOR_CHIP)
                    cornerRadius = dp(context, 50).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(dp(context, 4), 0, dp(context, 4), dp(context, 12)) }
                setOnClickListener { onClick() }
            }
            tabs.addView(tab("Unsent", !showEdited) { showEdited = false; render() })
            tabs.addView(tab("Edited", showEdited) { showEdited = true; render() })
            root.addView(tabs)

            val items = if (showEdited) db.getEditedMessages() else db.getDeletedMessages()
            val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            if (items.isEmpty()) {
                list.addView(TextView(context).apply {
                    text = Global.MESSAGE_LOG_FEATURE_EMPTY
                    setTextColor(COLOR_TEXT_SECONDARY)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(context, 30), 0, dp(context, 30))
                })
            } else {
                // Group by sender, groups ordered by their newest entry.
                items.groupBy { it.sender.ifEmpty { "Unknown" } }.forEach { (sender, msgs) ->
                    list.addView(TextView(context).apply {
                        text = "$sender  ·  ${msgs.size}"
                        textSize = 13f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(COLOR_ACCENT)
                        setPadding(0, dp(context, 14), 0, dp(context, 4))
                    })
                    msgs.forEach { m ->
                        list.addView(item(context, m, showEdited) { db.deleteMessage(m.id); render() })
                    }
                }
            }
            root.addView(ScrollView(context).apply {
                addView(list)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            })

            if (!showEdited && items.isNotEmpty()) {
                root.addView(Button(context).apply {
                    text = Global.MESSAGE_LOG_FEATURE_CLEAR
                    isAllCaps = false
                    setTextColor(COLOR_TEXT_PRIMARY)
                    background = GradientDrawable().apply { setColor(COLOR_ACCENT); cornerRadius = dp(context, 50).toFloat() }
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        .apply { setMargins(0, dp(context, 16), 0, 0) }
                    setOnClickListener {
                        AlertDialog.Builder(context)
                            .setTitle("Clear unsent log?")
                            .setMessage("Saved unsent messages will be removed.")
                            .setPositiveButton("Clear") { _, _ -> db.clearDeleted(); render() }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                })
            }
            wrapper.addView(root, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 520)))
        }

        render()
        dialog.setView(wrapper)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun item(context: Context, m: MessageDatabase.LogItem, edited: Boolean, onDelete: () -> Unit): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 12), dp(context, 10), dp(context, 4), dp(context, 10))
            background = GradientDrawable().apply { setColor(COLOR_CHIP); cornerRadius = dp(context, 14).toFloat() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, dp(context, 4), 0, dp(context, 4)) }
        }
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        // History oldest → newest, current text last (struck-through lines are earlier versions).
        val history = m.edits + m.content
        history.forEachIndexed { i, t ->
            val current = i == history.lastIndex
            texts.addView(TextView(context).apply {
                text = if (history.size > 1 && !current) "↳ $t" else t
                textSize = if (current) 15f else 13f
                setTextColor(if (current) COLOR_TEXT_PRIMARY else COLOR_TEXT_SECONDARY)
                if (!current) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            })
        }
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        texts.addView(TextView(context).apply {
            text = if (!edited && m.deletedAt > 0) "sent ${sdf.format(Date(m.timestamp))}  ·  unsent ${sdf.format(Date(m.deletedAt))}"
                   else sdf.format(Date(m.timestamp))
            textSize = 11f
            setTextColor(COLOR_TEXT_SECONDARY)
            setPadding(0, dp(context, 4), 0, 0)
        })
        row.addView(texts)
        row.addView(TextView(context).apply {
            text = "✕"
            textSize = 16f
            setTextColor(COLOR_TEXT_SECONDARY)
            setPadding(dp(context, 12), dp(context, 8), dp(context, 8), dp(context, 8))
            setOnClickListener { onDelete() }
        })
        row.setOnClickListener {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("message", m.content))
            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
        }
        return row
    }

    private fun dp(context: Context, dp: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics).toInt()
}
