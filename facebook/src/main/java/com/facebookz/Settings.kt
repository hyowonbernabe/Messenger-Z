package com.facebookz

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.Toast
import com.zmods.shared.AppControl
import com.zmods.shared.ui.SettingsKit
import com.zmods.shared.ui.SettingsKit.actionRow
import com.zmods.shared.ui.SettingsKit.confirm
import com.zmods.shared.ui.SettingsKit.section
import com.zmods.shared.ui.SettingsKit.switchRow
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

/** Facebook Z settings, opened by long-pressing the "facebook" logo at the top of the feed. */
object Settings {
    private const val GITHUB = "https://github.com/hyowonbernabe/Messenger-Z"

    fun init() {
        XposedHelpers.findAndHookMethod(View::class.java, "setContentDescription", CharSequence::class.java, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.args[0]?.toString() != "Facebook logo") return
                (param.thisObject as View).setOnLongClickListener { v ->
                    try { show(v.context) } catch (e: Throwable) { Log.e("FacebookZ", "settings failed", e) }
                    true
                }
            }
        })
    }

    private fun Context.flags(card: android.widget.LinearLayout, vararg flags: Prefs.Flag) =
        flags.forEach { f -> switchRow(this, card, f.title, f.desc, f.on) { f.set(it) } }

    fun show(context: Context) {
        Prefs.init(context)
        val footer = listOf<Pair<String, (() -> Unit)?>>(
            "Created by Hyowon Bernabe" to null,
            GITHUB.removePrefix("https://") to {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
            },
        )
        SettingsKit.show(context, "Facebook Z", "v${BuildConfig.VERSION_NAME}  ·  Facebook ${BuildConfig.FACEBOOK_VER}", footer) { content, _ ->
            context.flags(section(context, content, "Stories"), Prefs.NO_STORY_SEEN, Prefs.NO_AUTOSWIPE, Prefs.STORY_DOWNLOAD)
            context.flags(section(context, content, "Ads"), Prefs.NO_FEED_ADS, Prefs.NO_STORY_ADS)
            context.flags(section(context, content, "Privacy"), Prefs.APPEAR_OFFLINE)
            section(context, content, "Tools").apply {
                actionRow(context, this, "Restart Facebook", "Apply changes that need a restart") { AppControl.restart(context) }
                actionRow(context, this, "Clear Facebook cache", "Free space used by cached media") {
                    confirm(context, "Clear cache?", "Cached images and videos will be downloaded again when needed.") {
                        val mb = AppControl.clearCache(context) / (1024 * 1024)
                        Toast.makeText(context, "Cleared $mb MB", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
