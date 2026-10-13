package com.facebookz

import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.luckypray.dexkit.DexKitBridge
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * Story viewer hooks. Facebook's classes are obfuscated, so each target is found by a log/query
 * string that lives inside it (stable across releases, unlike the names).
 *
 * - Hide story views: every "story seen" GraphQL mutation is sent by one method of the class
 *   that builds the request with "story_ids_list". We drop the call.
 * - No auto-swipe: the progress bar's setProgress(0..1000) (the method that logs "Trying to set
 *   negative progress...") tells listeners the card is done at 1000; we cap it at 999.
 * - Story ads: an ad card (AdStory) gets progress 1000 straight away, so the viewer skips it.
 * - The same progress method is our "current story" tracker for the download button.
 */
object Stories {
    private const val TAG = "FacebookZ-Stories"
    private const val STORY_CARD = "com.facebook.stories.model.StoryCard"
    private const val AD_STORY = "com.facebook.audience.snacks.model.AdStory"

    /** Story card currently playing in the viewer (for the download button). */
    @Volatile var current: WeakReference<Any>? = null

    fun init(bridge: DexKitBridge, cl: ClassLoader) {
        try { hookSeen(bridge, cl) } catch (e: Throwable) { Log.e(TAG, "story seen hook failed", e) }
        try { hookProgress(bridge, cl) } catch (e: Throwable) { Log.e(TAG, "progress hook failed", e) }
    }

    private fun hookSeen(bridge: DexKitBridge, cl: ClassLoader) {
        val builder = bridge.findMethod { matcher { usingStrings("story_ids_list", "is_story_peek_view") } }.singleOrNull()
            ?: run { Log.e(TAG, "story seen sender not found"); return }
        val senders = builder.getClassInstance(cl).declaredMethods.filter {
            it.returnType == Void.TYPE && !Modifier.isStatic(it.modifiers) && it.parameterTypes.any { p -> p == Set::class.java }
        }
        senders.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!Prefs.NO_STORY_SEEN.on) return
                    val ids = param.args.firstOrNull { it is Set<*> } as Set<*>?
                    Log.d(TAG, ">>> BLOCKED STORY SEEN for ${ids?.size ?: "?"} story(s) <<<")
                    param.result = null
                }
            })
        }
        Log.d(TAG, "Story seen: hooked ${senders.size} sender(s) in ${builder.className}")
    }

    private fun hookProgress(bridge: DexKitBridge, cl: ClassLoader) {
        val m = bridge.findMethod {
            matcher { usingStrings("Trying to set negative progress in StoryViewerProgressBarController."); paramTypes("int") }
        }.singleOrNull()?.getMethodInstance(cl) ?: run { Log.e(TAG, "progress setter not found"); return }
        val cardClass = Class.forName(STORY_CARD, false, cl)
        val adClass = try { Class.forName(AD_STORY, false, cl) } catch (e: Throwable) { null }
        var cardPath: Pair<Field, Field>? = null // progress callback -> controller -> current card
        XposedBridge.hookMethod(m, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val card = try {
                    val path = cardPath ?: findCardPath(param.thisObject, cardClass)?.also { cardPath = it }
                    path?.let { p -> p.first.get(param.thisObject)?.let { p.second.get(it) } }
                } catch (e: Throwable) { null }
                if (card != null && current?.get() !== card) {
                    current = WeakReference(card)
                    if (Prefs.NO_STORY_ADS.on && adClass?.isInstance(card) == true) Log.d(TAG, ">>> SKIPPED STORY AD <<<")
                }
                if (Prefs.NO_STORY_ADS.on && adClass?.isInstance(card) == true) {
                    param.args[0] = 1000 // finish the ad card at once: the viewer moves on by itself
                    return
                }
                if (Prefs.NO_AUTOSWIPE.on && param.args[0] as Int >= 1000) param.args[0] = 999
            }
        })
        Log.d(TAG, "Story progress hooked (${m.declaringClass.name}.${m.name})")
    }

    /** Field on [callback] pointing at an object that has a StoryCard field (searching superclasses). */
    private fun findCardPath(callback: Any, cardClass: Class<*>): Pair<Field, Field>? {
        for (f in callback.javaClass.declaredFields) {
            if (f.type.isPrimitive) continue
            f.isAccessible = true
            val owner = f.get(callback) ?: continue
            var c: Class<*>? = owner.javaClass
            while (c != null && c != Any::class.java) {
                c.declaredFields.firstOrNull { it.type == cardClass }?.let { it.isAccessible = true; return f to it }
                c = c.superclass
            }
        }
        return null
    }
}
