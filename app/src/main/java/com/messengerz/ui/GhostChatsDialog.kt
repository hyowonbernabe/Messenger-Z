package com.messengerz.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.messengerz.core.GhostChats

/** Pick who ghost mode (seen / typing / story seen) applies to. */
object GhostChatsDialog {
    private const val COLOR_BG = 0xFF121316.toInt()
    private const val COLOR_ACCENT = 0xFFFF3B30.toInt()
    private const val COLOR_TEXT_PRIMARY = 0xFFFFFFFF.toInt()
    private const val COLOR_TEXT_SECONDARY = 0xFF9EA3AB.toInt()
    private const val COLOR_CHIP = 0xFF1E1F23.toInt()

    fun show(context: Context, onChanged: () -> Unit) {
        val dialog = AlertDialog.Builder(context).create()
        val wrapper = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        fun render() {
            wrapper.removeAllViews()
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val pad = dp(context, 20)
                setPadding(pad, pad, pad, pad)
                background = GradientDrawable().apply {
                    setColor(COLOR_BG); cornerRadius = dp(context, 26).toFloat()
                }
            }
            root.addView(TextView(context).apply {
                text = "Ghost Mode Applies To"
                textSize = 20f
                setTypeface(null, Typeface.BOLD)
                setTextColor(COLOR_TEXT_PRIMARY)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(context, 12))
            })

            val current = GhostChats.mode
            GhostChats.Mode.values().forEach { m ->
                root.addView(TextView(context).apply {
                    text = (if (m == current) "●  " else "○  ") + m.label
                    textSize = 15f
                    setTextColor(if (m == current) COLOR_TEXT_PRIMARY else COLOR_TEXT_SECONDARY)
                    setPadding(dp(context, 8), dp(context, 10), dp(context, 8), dp(context, 10))
                    setOnClickListener { GhostChats.mode = m; onChanged(); render() }
                })
            }

            val chats = GhostChats.knownChats()
            val selected = GhostChats.selected().toMutableSet()
            val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            if (current != GhostChats.Mode.EVERYONE) {
                list.addView(TextView(context).apply {
                    text = if (current == GhostChats.Mode.EXCEPT) "Seen, typing and stories stay visible to:" else "Ghost mode only for:"
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(COLOR_ACCENT)
                    setPadding(0, dp(context, 12), 0, dp(context, 6))
                })
                if (chats.isEmpty()) {
                    list.addView(TextView(context).apply {
                        text = "No chats yet. Open a chat once and it will show up here."
                        setTextColor(COLOR_TEXT_SECONDARY)
                        setPadding(0, dp(context, 12), 0, dp(context, 12))
                    })
                }
                chats.forEach { c ->
                    list.addView(CheckBox(context).apply {
                        text = c.name
                        textSize = 15f
                        setTextColor(COLOR_TEXT_PRIMARY)
                        buttonTintList = android.content.res.ColorStateList.valueOf(COLOR_ACCENT)
                        isChecked = c.token in selected
                        background = GradientDrawable().apply { setColor(COLOR_CHIP); cornerRadius = dp(context, 12).toFloat() }
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                            .apply { setMargins(0, dp(context, 3), 0, dp(context, 3)) }
                        setOnCheckedChangeListener { _, on ->
                            if (on) selected.add(c.token) else selected.remove(c.token)
                            GhostChats.setSelected(selected)
                            onChanged()
                        }
                    })
                }
            }
            root.addView(ScrollView(context).apply {
                addView(list)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            })
            root.addView(TextView(context).apply {
                text = "Story seen matches people by name."
                textSize = 11f
                setTextColor(COLOR_TEXT_SECONDARY)
                setPadding(0, dp(context, 8), 0, 0)
            })
            wrapper.addView(root, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 480)))
        }

        render()
        dialog.setView(wrapper)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun dp(context: Context, dp: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics).toInt()
}
