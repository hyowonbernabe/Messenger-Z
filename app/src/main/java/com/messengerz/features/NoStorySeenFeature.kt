package com.messengerz.features

import android.os.SystemClock
import android.util.Log
import com.messengerz.core.GhostChats
import com.messengerz.core.Preferences
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Modifier

/**
 * Anonymous story viewing. Messenger marks a story seen through an interface method
 * `(MontageCard, boolean) -> void` with (v582) two implementations: an MSYS handler and a
 * GraphQL `MontageMarkSeenMutation` handler. Blocking that method on every implementation
 * stops both reports (and Messenger's local "watched" state, so the ring stays unseen).
 *
 * Class/method names are obfuscated and change every release, so they are found at runtime
 * with DexKit via stable anchors: the unobfuscated MontageCard type plus a log string that
 * lives inside the MSYS handler's method.
 */
object NoStorySeenFeature {
    private const val TAG = "MessengerZ"
    private const val MONTAGE_CARD = "com.facebook.messaging.montage.model.MontageCard"
    private const val ANCHOR = "MontageMsysMarkReadHandler"

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val t0 = SystemClock.elapsedRealtime()
            System.loadLibrary("dexkit")
            // ponytail: full dex scan every launch; cache the found names per Messenger versionCode if startup cost matters
            val targets = DexKitBridge.create(lpparam.appInfo.sourceDir).use { bridge ->
                val anchor = bridge.findMethod {
                    matcher {
                        paramTypes(MONTAGE_CARD, "boolean")
                        returnType = "void"
                        usingStrings(ANCHOR)
                    }
                }.singleOrNull() ?: run {
                    Log.e(TAG, "NoStorySeen: mark-read handler not found")
                    return
                }
                // The interface that declares the mark-read method; every implementer reports a view.
                val handlerCls = anchor.getClassInstance(lpparam.classLoader)
                val iface = handlerCls.interfaces.firstOrNull { i ->
                    i.declaredMethods.any { it.name == anchor.name && it.parameterTypes.size == 2 }
                }?.name
                val classes = if (iface == null) listOf(anchor.className)
                              else bridge.findClass { matcher { addInterface(iface) } }.map { it.name }
                classes.mapNotNull { cn ->
                    Class.forName(cn, false, lpparam.classLoader).declaredMethods.firstOrNull {
                        it.name == anchor.name && !Modifier.isAbstract(it.modifiers) &&
                            it.parameterTypes.size == 2 && it.parameterTypes[0].name == MONTAGE_CARD
                    }
                }
            }

            for (m in targets) {
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!Preferences.isNoStorySeenEnabled) return
                        val owner = if (GhostChats.mode == GhostChats.Mode.EVERYONE) null else ownerName(param.args[0])
                        if (!GhostChats.appliesToPerson(owner)) {
                            Log.d(TAG, "Story seen allowed for '$owner' (ghost exception)")
                            return
                        }
                        Log.d(TAG, ">>> BLOCKED STORY SEEN (${m.declaringClass.simpleName}.${m.name}) owner=$owner <<<")
                        param.result = null
                    }
                })
            }
            Log.d(TAG, "NoStorySeenFeature: hooked ${targets.size} handlers " +
                "(${targets.joinToString { it.declaringClass.simpleName }}) in ${SystemClock.elapsedRealtime() - t0} ms")
        } catch (e: Throwable) {
            Log.e(TAG, "Error initializing NoStorySeenFeature", e)
        }
    }

    /** Story owner's display name: the MontageCard's MontageUser field, first readable String in it. */
    private fun ownerName(card: Any?): String? = try {
        val user = card?.javaClass?.declaredFields?.firstOrNull { it.type.simpleName == "MontageUser" }
            ?.apply { isAccessible = true }?.get(card)
        user?.javaClass?.declaredFields?.asSequence()
            ?.filter { it.type == String::class.java }
            ?.mapNotNull { f -> f.isAccessible = true; f.get(user) as String? }
            ?.firstOrNull { s -> s.length in 2..80 && s.any(Char::isLetter) && !s.all { it.isDigit() } && !s.startsWith("http") }
    } catch (e: Throwable) { null }
}
