package com.facebookz

import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.luckypray.dexkit.DexKitBridge

/**
 * Appear offline: same mechanism as Messenger Z. "Active now" comes from the MQTT client telling
 * the server the app is in the foreground (setForeground(true) on the XplatNativeClientWrapper
 * family); we always report background.
 */
object AppearOffline {
    private const val TAG = "FacebookZ-Offline"

    fun init(bridge: DexKitBridge, cl: ClassLoader) {
        try {
            val cls = XposedHelpers.findClassIfExists("com.facebook.mqtt.service.XplatNativeClientWrapper", cl)
                ?: run { Log.e(TAG, "XplatNativeClientWrapper not found"); return }
            val hook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (Prefs.APPEAR_OFFLINE.on && param.args[0] == true) {
                        param.args[0] = false
                        Log.d(TAG, ">>> BLOCKED foreground presence via ${param.thisObject.javaClass.simpleName} <<<")
                    }
                }
            }
            val iface = cls.interfaces.firstOrNull { i -> i.declaredMethods.any { it.name == "setForeground" } }
            val classes = mutableSetOf(cls.name)
            if (iface != null) bridge.findClass { matcher { addInterface(iface.name) } }.forEach { classes.add(it.name) }
            var hooks = 0
            for (cn in classes) {
                val c = XposedHelpers.findClassIfExists(cn, cl) ?: continue
                c.declaredMethods.filter { it.name == "setForeground" && it.parameterTypes.firstOrNull() == Boolean::class.javaPrimitiveType }
                    .forEach { XposedBridge.hookMethod(it, hook); hooks++ }
            }
            Log.d(TAG, "AppearOffline hooked $hooks setForeground method(s) in ${classes.size} classes")
        } catch (e: Throwable) {
            Log.e(TAG, "Error in AppearOffline", e)
        }
    }
}
