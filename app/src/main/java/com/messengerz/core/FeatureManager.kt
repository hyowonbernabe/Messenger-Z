package com.messengerz.core

import com.messengerz.features.NoSeenFeature
import com.messengerz.features.NoTypingFeature
import com.messengerz.features.MessageLoggerFeature
import com.messengerz.features.SpoofVersionFeature
import de.robv.android.xposed.callbacks.XC_LoadPackage

object FeatureManager {
    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        NoSeenFeature.init(lpparam)
        NoTypingFeature.init(lpparam)
        SpoofVersionFeature.init(lpparam)
        MessageLoggerFeature.init(lpparam)
    }
}