package com.messengerz.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.messengerz.core.Preferences

object SettingsDialog {

    fun show(context: Context) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Messenger Z")

        // Main Container
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 30, 50, 30)

        // --- FEATURE 1: NO SEEN ---
        val noSeenSwitch = Switch(context)
        noSeenSwitch.text = "Disable Seen Indicator"
        noSeenSwitch.textSize = 16f
        noSeenSwitch.isChecked = Preferences.isNoSeenEnabled
        noSeenSwitch.setPadding(0, 20, 0, 10)

        noSeenSwitch.setOnCheckedChangeListener { _, isChecked ->
            Preferences.setNoSeenEnabled(isChecked)
        }

        val descSeen = TextView(context)
        descSeen.text = "Prevents others from knowing you read their messages."
        descSeen.textSize = 12f
        descSeen.setTextColor(Color.GRAY)
        descSeen.setPadding(0, 0, 0, 30)

        // --- FEATURE 2: NO TYPING ---
        val noTypingSwitch = Switch(context)
        noTypingSwitch.text = "Disable Typing Indicator"
        noTypingSwitch.textSize = 16f
        noTypingSwitch.isChecked = Preferences.isNoTypingEnabled
        noTypingSwitch.setPadding(0, 20, 0, 10)

        noTypingSwitch.setOnCheckedChangeListener { _, isChecked ->
            Preferences.setNoTypingEnabled(isChecked)
        }

        val descTyping = TextView(context)
        descTyping.text = "Prevents others from seeing the typing animation."
        descTyping.textSize = 12f
        descTyping.setTextColor(Color.GRAY)
        descTyping.setPadding(0, 0, 0, 30)

        // Add views to layout
        layout.addView(noSeenSwitch)
        layout.addView(descSeen)
        layout.addView(noTypingSwitch)
        layout.addView(descTyping)

        builder.setView(layout)
        builder.setPositiveButton("Close", null)
        builder.show()
    }
}