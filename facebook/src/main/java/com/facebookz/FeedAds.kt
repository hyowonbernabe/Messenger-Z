package com.facebookz

import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * Hide sponsored posts. Facebook's feed engine (FreshFeed client-side ranking) asks each story
 * collection "can you insert a story here?" and "give me your next story". Both answers use one
 * status enum (SUCCESS, FAIL, ..., SPONSORED_CANNOT_INSERT). For the sponsored collections
 * (collection classes that mention "Sponsored", e.g. FeedSponsoredCollectionProxy) we always answer
 * SPONSORED_CANNOT_INSERT and hand back no story, so ads never enter the feed.
 */
object FeedAds {
    private const val TAG = "FacebookZ-Ads"
    private const val NO = "SPONSORED_CANNOT_INSERT"

    fun init(bridge: DexKitBridge, cl: ClassLoader) {
        try {
            val status = bridge.findClass { matcher { usingStrings(NO, "SPONSORED_SPEED_BUMP_BLOCK") } }.singleOrNull()
                ?.getInstance(cl) ?: run { Log.e(TAG, "vend status enum not found"); return }
            @Suppress("UNCHECKED_CAST")
            val no = java.lang.Enum.valueOf(status as Class<out Enum<*>>, NO)

            val sponsored = bridge.findClass { matcher { usingStrings("Sponsored") } }.map { it.name }.toSet()
            // "Can insert?" checks answer with the status itself.
            val checks = bridge.findMethod { matcher { returnType(status.name); paramCount(0) } }
                .filter { it.className in sponsored }.map { it.getMethodInstance(cl) }.filter(::implementsInterface)
            checks.forEach { XposedBridge.hookMethod(it, constant(no)) }
            // "Next story" answers with a (story, status) pair; find that type from a check's siblings.
            val pair = checks.asSequence().flatMap { it.declaringClass.declaredMethods.asSequence() }
                .firstNotNullOfOrNull { m -> m.returnType.declaredConstructors.firstOrNull { it.parameterTypes.size == 2 && it.parameterTypes[1] == status } }
            val vends = if (pair == null) emptyList() else bridge.findMethod { matcher { returnType(pair.declaringClass.name) } }
                .filter { it.className in sponsored && it.isMethod }.map { it.getMethodInstance(cl) }.filter(::implementsInterface)
            vends.forEach { XposedBridge.hookMethod(it, empty(pair!!, no)) }
            Log.d(TAG, "Feed ads: hooked ${checks.size} insert check(s) + ${vends.size} vend(s) in sponsored classes")
        } catch (e: Throwable) {
            Log.e(TAG, "Error in FeedAds", e)
        }
    }

    /**
     * Only collection-interface methods: the feed loader that *calls* every collection also
     * mentions "Sponsored" and returns the same types from private helpers; hooking those empties the feed.
     */
    private fun implementsInterface(m: Method) = m.declaringClass.interfaces.any { i ->
        i.methods.any { it.name == m.name && it.parameterTypes.contentEquals(m.parameterTypes) }
    }

    private fun constant(value: Any) = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            if (Prefs.NO_FEED_ADS.on) param.result = value
        }
    }

    private fun empty(pair: Constructor<*>, no: Any) = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            if (!Prefs.NO_FEED_ADS.on) return
            pair.isAccessible = true
            param.result = pair.newInstance(null, no)
            Log.d(TAG, ">>> BLOCKED AD (${param.method.declaringClass.name}) <<<")
        }
    }
}
