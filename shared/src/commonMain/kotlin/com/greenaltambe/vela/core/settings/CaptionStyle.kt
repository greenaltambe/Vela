package com.greenaltambe.vela.core.settings

/** How the viewer wants captions drawn: size, what is behind them, and whether a creator's own colours and positions are used. */
data class CaptionStyle(
    val size: CaptionSize = CaptionSize.NORMAL,
    val background: CaptionBackground = CaptionBackground.SHADE,
    val creatorStyle: Boolean = true,
) {
    companion object {
        fun read(settings: SettingsResolver) = CaptionStyle(
            settings.resolve(Settings.captionSize), settings.resolve(Settings.captionBackground), settings.resolve(Settings.captionCreatorStyle),
        )
    }

    fun write(settings: SettingsResolver) {
        settings.set(Settings.captionSize, size); settings.set(Settings.captionBackground, background); settings.set(Settings.captionCreatorStyle, creatorStyle)
    }
}
