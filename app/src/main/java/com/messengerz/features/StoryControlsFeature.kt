package com.messengerz.features

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.util.Log
import com.messengerz.core.Preferences
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * Disable story auto-swipe. The story viewer's progress bar (MontageProgressIndicatorView, an
 * unobfuscated view) runs a ValueAnimator whose "finished" listener advances to the next story.
 * After the view (re)creates its animator we detach that one listener, so the bar fills and then
 * waits; tapping to skip uses a different path and keeps working.
 */
object StoryControlsFeature {
    private const val TAG = "MessengerZ-Stories"
    private const val VIEW = "com.facebook.messaging.montage.viewer.progressindicator.MontageProgressIndicatorView"

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val cls = XposedHelpers.findClassIfExists(VIEW, lpparam.classLoader) ?: run {
                Log.e(TAG, "MontageProgressIndicatorView not found")
                return
            }
            // Fields by type, not by (obfuscated) name.
            val animField = cls.declaredFields.firstOrNull { it.type == ValueAnimator::class.java }?.apply { isAccessible = true }
            val listenerField = cls.declaredFields.firstOrNull { it.type == AnimatorListenerAdapter::class.java }?.apply { isAccessible = true }
            if (animField == null || listenerField == null) {
                Log.e(TAG, "progress animator/listener fields not found")
                return
            }
            val hook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!Preferences.isStoryAutoSwipeDisabled) return
                    val view = param.thisObject ?: param.args.firstOrNull { it != null && cls.isInstance(it) } ?: return
                    detach(view, animField, listenerField)
                }
            }
            var n = 0
            cls.declaredMethods.filter { !Modifier.isAbstract(it.modifiers) && it.name != "onDraw" }.forEach {
                XposedBridge.hookMethod(it, hook); n++
            }
            Log.d(TAG, "Story auto-swipe control hooked $n methods")
        } catch (e: Throwable) {
            Log.e(TAG, "Error in StoryControlsFeature", e)
        }
    }

    private fun detach(view: Any, animField: Field, listenerField: Field) {
        val anim = animField.get(view) as? ValueAnimator ?: return
        val listener = listenerField.get(view) as? Animator.AnimatorListener ?: return
        if (anim.listeners?.contains(listener) == true) {
            anim.removeListener(listener)
            Log.d(TAG, ">>> Story auto-advance disabled for this card <<<")
        }
    }
}
