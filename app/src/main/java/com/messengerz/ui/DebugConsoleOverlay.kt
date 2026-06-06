package com.messengerz.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.messengerz.core.DebugLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Floating in-app console drawn inside Messenger's own content view (no draw-over-apps
 * permission). Shows MailboxSDKJNI dispatch signals (and Message Logger diagnostics) live.
 *
 * Click-through: the panel itself and the log text are NOT clickable, so touches on them
 * fall through to Messenger underneath (you can still tap the text box / messages). Only
 * the buttons consume touches. This is why there is no ScrollView — a ScrollView would eat
 * touches for scrolling; instead the log shows the most recent lines and auto-refreshes.
 */
object DebugConsoleOverlay {
    private const val COLOR_BG = 0xCC161616.toInt() // ~80% opaque so the app shows through
    private const val COLOR_ACCENT = 0xFFFF3B30.toInt()
    private const val COLOR_TEXT = 0xFFE0E0E0.toInt()
    private const val COLOR_NOTE = 0xFF7CFFB2.toInt() // green for logger notes
    private const val COLOR_BTN = 0xFF3E4042.toInt()

    // Alternating colors for O/0 so runs (e.g. dispatchVOOOOO) are countable.
    private const val COLOR_O_A = 0xFF80D8FF.toInt() // light blue
    private const val COLOR_O_B = 0xFFFFD180.toInt() // light orange

    private const val VISIBLE_LINES = 16

    private var overlay: View? = null
    private var logView: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val refresher = object : Runnable {
        override fun run() {
            updateText()
            if (overlay != null) handler.postDelayed(this, 500)
        }
    }

    fun show(context: Context) {
        val activity = findActivity(context) ?: run {
            Toast.makeText(context, "Debug Console: no activity", Toast.LENGTH_SHORT).show()
            return
        }
        if (overlay != null) return
        val root = activity.window?.decorView?.findViewById<ViewGroup>(android.R.id.content) ?: return

        DebugLog.capturing = true

        val panel = LinearLayout(context)
        panel.orientation = LinearLayout.VERTICAL
        // NOTE: do NOT make the panel clickable — that keeps it click-through.
        val bg = GradientDrawable()
        bg.setColor(COLOR_BG)
        bg.setStroke(dp(context, 2), COLOR_ACCENT)
        panel.background = bg
        val p = dp(context, 8)
        panel.setPadding(p, p, p, p)

        // --- top bar (the only touch-consuming part) ---
        val bar = LinearLayout(context)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL

        val title = TextView(context)
        title.text = "Z Debug Console (tap-through)"
        title.setTextColor(COLOR_ACCENT)
        title.textSize = 12f
        title.setTypeface(null, Typeface.BOLD)
        title.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        bar.addView(title)

        bar.addView(makeButton(context, "Copy") {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("mz-debug", fullText()))
            Toast.makeText(context, "Copied full log", Toast.LENGTH_SHORT).show()
        })
        bar.addView(makeButton(context, "Clear") {
            DebugLog.clear()
            updateText()
        })
        bar.addView(makeButton(context, "Close") { hide() })
        panel.addView(bar)

        // --- log area: plain TextView, not clickable -> touches pass through ---
        val tv = TextView(context)
        tv.setTextColor(COLOR_TEXT)
        tv.textSize = 11f
        tv.typeface = Typeface.MONOSPACE
        tv.isClickable = false
        tv.isFocusable = false
        tv.gravity = Gravity.BOTTOM
        tv.setPadding(0, dp(context, 6), 0, 0)
        tv.text = "Reading signals…\nType in a chat to catch dispatchVOOOZ (typing); read a message for dispatchVOOOO (seen)."
        tv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        panel.addView(tv)

        logView = tv

        val height = (activity.resources.displayMetrics.heightPixels * 0.38f).toInt()
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, height, Gravity.BOTTOM)
        root.addView(panel, lp)
        overlay = panel

        handler.post(refresher)
    }

    fun hide() {
        handler.removeCallbacks(refresher)
        val o = overlay
        if (o != null) (o.parent as? ViewGroup)?.removeView(o)
        overlay = null
        logView = null
        DebugLog.capturing = false
    }

    private fun updateText() {
        val tv = logView ?: return
        val all = DebugLog.snapshot()
        val show = if (all.size > VISIBLE_LINES) all.subList(all.size - VISIBLE_LINES, all.size) else all
        if (show.isEmpty()) {
            tv.text = "No signals captured yet."
            return
        }
        val sb = SpannableStringBuilder()
        for (e in show) appendEntry(sb, e)
        tv.text = sb
    }

    private fun appendEntry(sb: SpannableStringBuilder, e: DebugLog.Entry) {
        if (e.argCount == -1) {
            // diagnostic note
            val start = sb.length
            sb.append("» ").append(e.method).append('\n')
            sb.setSpan(ForegroundColorSpan(COLOR_NOTE), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return
        }
        sb.append(sdf.format(Date(e.time))).append("  ")
        appendMethod(sb, e.method)
        sb.append("  id=").append(e.cmdId?.toString() ?: "-")
            .append("  n=").append(e.argCount.toString())
            .append('\n')
    }

    /** Appends a method name, coloring each O/0 alternately so runs are easy to count. */
    private fun appendMethod(sb: SpannableStringBuilder, name: String) {
        var oIndex = 0
        for (c in name) {
            val start = sb.length
            sb.append(c)
            if (c == 'O' || c == '0') {
                val color = if (oIndex % 2 == 0) COLOR_O_A else COLOR_O_B
                sb.setSpan(ForegroundColorSpan(color), start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                oIndex++
            }
        }
    }

    /** Full plain-text log (all entries) for the clipboard. */
    private fun fullText(): String {
        val sb = StringBuilder()
        for (e in DebugLog.snapshot()) {
            if (e.argCount == -1) {
                sb.append("» ").append(e.method).append('\n')
            } else {
                sb.append(sdf.format(Date(e.time)))
                    .append("  ").append(e.method)
                    .append("  id=").append(e.cmdId?.toString() ?: "-")
                    .append("  n=").append(e.argCount)
                    .append('\n')
            }
        }
        return sb.toString()
    }

    private fun makeButton(context: Context, label: String, onClick: () -> Unit): Button {
        val b = Button(context)
        b.text = label
        b.textSize = 11f
        b.setTextColor(Color.WHITE)
        b.isAllCaps = false
        b.setPadding(dp(context, 10), 0, dp(context, 10), 0)
        b.minHeight = dp(context, 32)
        b.minimumHeight = dp(context, 32)
        val bg = GradientDrawable()
        bg.setColor(COLOR_BTN)
        bg.cornerRadius = dp(context, 8).toFloat()
        b.background = bg
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(context, 4), 0, 0, 0)
        b.layoutParams = lp
        b.setOnClickListener { onClick() }
        return b
    }

    private fun findActivity(c: Context?): Activity? {
        var ctx = c
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun dp(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
