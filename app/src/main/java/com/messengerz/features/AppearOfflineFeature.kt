package com.messengerz.features

import android.util.Log
import com.messengerz.core.Preferences
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.luckypray.dexkit.DexKitBridge

/**
 * Appear offline: Messenger marks you "Active now" by telling the MQTT server the app is in the
 * foreground (XplatNativeClientWrapper.setForeground → native state + "/t_fs" publish). We always
 * report background instead. Unlike Messenger's own Active Status switch (a server setting that is
 * reciprocal), this keeps other people's status visible to you.
 */
object AppearOfflineFeature {
    private const val TAG = "MessengerZ-Offline"

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val cls = XposedHelpers.findClassIfExists("com.facebook.mqtt.service.XplatNativeClientWrapper", lpparam.classLoader)
            if (cls == null) {
                Log.e(TAG, "XplatNativeClientWrapper not found")
                return
            }
            val hook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!Preferences.isAppearOfflineEnabled) return
                    if (param.args.getOrNull(0) == true) {
                        param.args[0] = false
                        Log.d(TAG, ">>> BLOCKED foreground presence (appear offline) via ${param.thisObject.javaClass.simpleName} <<<")
                    }
                }
            }
            // Several MQTT client wrappers implement the same (obfuscated) interface; the active one
            // varies (doze-aware wrapper etc.). Hook setForeground on every implementer.
            val iface = cls.interfaces.firstOrNull { i -> i.declaredMethods.any { it.name == "setForeground" } }
            val classes = mutableSetOf(cls.name)
            if (iface != null) {
                System.loadLibrary("dexkit")
                DexKitBridge.create(lpparam.appInfo.sourceDir).use { bridge ->
                    bridge.findClass { matcher { addInterface(iface.name) } }.forEach { classes.add(it.name) }
                }
            }
            var hooks = 0
            for (cn in classes) {
                val c = XposedHelpers.findClassIfExists(cn, lpparam.classLoader) ?: continue
                c.declaredMethods.filter { it.name == "setForeground" && it.parameterTypes.firstOrNull() == Boolean::class.javaPrimitiveType }
                    .forEach { XposedBridge.hookMethod(it, hook); hooks++ }
            }
            Log.d(TAG, "AppearOffline hooked $hooks setForeground method(s) in ${classes.size} classes")
        } catch (e: Throwable) {
            Log.e(TAG, "Error in AppearOfflineFeature", e)
        }
    }
}
