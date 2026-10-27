package com.greenaltambe.vela.modules

import com.greenaltambe.vela.core.settings.Setting
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver

/** A toggleable feature (SponsorBlock, RYD, DeArrow). Behaviour is added in later phases. */
interface FeatureModule {
    val id: String
    val enabledSetting: Setting<Boolean>

    fun isEnabled(settings: SettingsResolver): Boolean = settings.resolve(enabledSetting)
}

object SponsorBlockModule : FeatureModule {
    override val id = "sponsorblock"
    override val enabledSetting = Settings.sponsorBlockEnabled
}

object ReturnYouTubeDislikeModule : FeatureModule {
    override val id = "ryd"
    override val enabledSetting = Settings.returnYouTubeDislikeEnabled
}

object DeArrowModule : FeatureModule {
    override val id = "dearrow"
    override val enabledSetting = Settings.deArrowEnabled
}
