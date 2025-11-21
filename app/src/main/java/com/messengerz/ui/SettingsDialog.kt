package com.messengerz.ui

import android.app.AlertDialog
import android.content.Context
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.graphics.Color
import com.messengerz.core.Preferences

object SettingsDialog {

    fun show(context: Context) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Messenger Z")

        // Main Container
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 30, 50, 30)

        // Switch: No Seen
        val noSeenSwitch = Switch(context)
        noSeenSwitch.text = "Disable Seen Indicator"
        noSeenSwitch.textSize = 16f
        noSeenSwitch.isChecked = Preferences.isNoSeenEnabled
        noSeenSwitch.setPadding(0, 20, 0, 20)

        noSeenSwitch.setOnCheckedChangeListener { _, isChecked ->
            Preferences.setNoSeenEnabled(isChecked)
        }

        // Description
        val desc = TextView(context)
        desc.text = "Prevents others from knowing you read their messages."
        desc.textSize = 12f
        desc.setTextColor(Color.GRAY)
        desc.setPadding(0, 0, 0, 30)

        layout.addView(noSeenSwitch)
        layout.addView(desc)

        builder.setView(layout)
        builder.setPositiveButton("Close", null)
        builder.show()
    }
}