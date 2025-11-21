package com.messengerz.core

import com.messengerz.features.NoSeenFeature
import de.robv.android.xposed.callbacks.XC_LoadPackage

object FeatureManager {
    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Initialize our No Seen Feature
        NoSeenFeature.init(lpparam)
    }
}