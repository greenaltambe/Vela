package com.greenaltambe.vela.ui.look

import com.greenaltambe.vela.core.settings.*

/** Everything the appearance settings say, read once (so building the tokens is a pure function and testable without a store). */
data class LookSnapshot(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val pureBlack: Boolean = false,
    val palette: Palette = Palette.VELA,
    val corners: Float = LookSettings.corners.default,
    val density: DensityChoice = DensityChoice.COMFORTABLE,
    val textScalePercent: Float = 100f,
    val font: FontChoice = FontChoice.VELA,
    val titleWeight: TitleWeight = TitleWeight.LIGHT,
    val iconStyle: IconStyle = IconStyle.FILLED,
    val iconWeight: IconWeight = IconWeight.REGULAR,
    val iconRounded: Boolean = true,
    val progress: ProgressStyle = ProgressStyle.BOLD_DOT,
    val glass: Boolean = true,
    val reduceMotion: Boolean = false,
    val performance: Boolean = false,
) {
    companion object {
        fun of(s: SettingsResolver) = LookSnapshot(
            theme = s.resolve(LookSettings.theme), pureBlack = s.resolve(Settings.look) == LookChoice.PURE_BLACK,
            palette = s.resolve(LookSettings.palette),
            corners = s.resolve(LookSettings.corners), density = s.resolve(LookSettings.density), textScalePercent = s.resolve(LookSettings.textScale),
            font = s.resolve(LookSettings.font), titleWeight = s.resolve(LookSettings.titleWeight),
            iconStyle = s.resolve(LookSettings.iconStyle), iconWeight = s.resolve(LookSettings.iconWeight), iconRounded = s.resolve(LookSettings.iconRounded), progress = s.resolve(LookSettings.progressStyle),
            glass = s.resolve(LookSettings.glass), reduceMotion = s.resolve(LookSettings.reduceMotion), performance = s.resolve(Settings.performanceMode),
        )
    }
}

/**
 * Settings -> [LookTokens]. The one place the appearance settings turn into tokens; every screen reads the tokens only.
 * Performance mode wins over glass and motion: it forces glass off and motion reduced, whatever those settings say.
 */
object LookBuilder {
    /** "#RRGGBB" (or "RRGGBB") as an opaque colour; null for anything else, which falls back to the default accent. */
    fun parseHex(text: String): Long? {
        val t = text.trim().removePrefix("#")
        if (t.length != 6 || t.any { it !in "0123456789abcdefABCDEF" }) return null
        return 0xFF000000 or t.toLong(16)
    }

    fun build(s: LookSnapshot, systemDark: Boolean): LookTokens {
        val dark = when (s.theme) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> systemDark } || s.pureBlack
        val base = when { s.pureBlack -> Looks.PureBlack; dark -> Looks.Dark; else -> Looks.Light }
        // The accent is the palette\'s middle stop, nudged until text-sized things in it reach 4.5:1 on both the page and the surface.
        val stops = Palettes.stops(s.palette)
        val picked = stops[1]
        val primary = Contrast.ensure(Contrast.ensure(picked, base.colors.background), base.colors.surface)
        val colors = base.colors.copy(primary = primary, onPrimary = Contrast.onColor(primary))

        val c = s.corners.coerceIn(0f, 16f)
        val radii = RadiusTokens(smallDp = c * 0.5f, mediumDp = c, largeDp = c * 1.5f, fullDp = 999f)
        val glass = s.glass && !s.performance
        val reduced = s.reduceMotion || s.performance
        val scale = (s.textScalePercent / 100f).coerceIn(0.8f, 1.3f)
        return base.copy(
            id = "custom", name = "Custom", colors = colors, radii = radii,
            density = when (s.density) { DensityChoice.COMPACT -> Density.COMPACT; DensityChoice.COMFORTABLE -> Density.COMFORTABLE; DensityChoice.ROOMY -> Density.SPACIOUS },
            typography = base.typography.copy(fontFamily = s.font.name.lowercase(), scale = scale, titleWeight = s.titleWeight),
            effects = base.effects.copy(blur = glass),
            accent = AccentTokens(stops),
            icons = IconTokens(s.iconStyle, s.iconWeight, s.iconRounded),
            motion = MotionTokens(reduced = reduced),
            progress = s.progress,
        )
    }
}
