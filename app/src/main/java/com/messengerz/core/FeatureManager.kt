package com.messengerz.core

import com.messengerz.features.NoSeenFeature
import com.messengerz.features.NoTypingFeature
import de.robv.android.xposed.callbacks.XC_LoadPackage

object FeatureManager {
    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        NoSeenFeature.init(lpparam)
        NoTypingFeature.init(lpparam)
    }
}