package com.greenaltambe.vela.modules

import com.greenaltambe.vela.core.settings.Setting
import com.greenaltambe.vela.core.settings.SettingsResolver

/** A toggleable feature: SponsorBlock, Return YouTube Dislike and DeArrow each implement this (see Modules.kt). */
interface FeatureModule {
    val id: String
    val enabledSetting: Setting<Boolean>

    fun isEnabled(settings: SettingsResolver): Boolean = settings.resolve(enabledSetting)
}
