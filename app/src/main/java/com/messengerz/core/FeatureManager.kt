package com.messengerz.core

import com.messengerz.features.NoSeenFeature
import com.messengerz.features.NoTypingFeature
import com.messengerz.features.NoStorySeenFeature
import com.messengerz.features.MessageLoggerFeature
import com.messengerz.features.SpoofVersionFeature
import com.messengerz.features.DispatchProbe
import de.robv.android.xposed.callbacks.XC_LoadPackage

object FeatureManager {
    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        NoSeenFeature.init(lpparam)
        NoTypingFeature.init(lpparam)
        NoStorySeenFeature.init(lpparam)
        SpoofVersionFeature.init(lpparam)
        com.messengerz.features.CleanLinksFeature.init(lpparam)
        MessageLoggerFeature.init(lpparam)
        DispatchProbe.init(lpparam)
    }
}