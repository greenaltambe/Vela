package com.greenaltambe.vela.ui.look

import kotlinx.serialization.Serializable

/**
 * Everything visual as data (SPEC section 5). A Look is one of these serialised to JSON, so it can be saved, switched
 * and shared as a file. Composables read from the active instance ([LocalLook]); nothing outside [Looks] spells a
 * colour, size or font.
 *
 * Colours are 0xAARRGGBB.
 */
@Serializable
data class LookTokens(
    val id: String,
    val name: String,
    val colors: ColorTokens,
    val typography: TypographyTokens = TypographyTokens(),
    val radii: RadiusTokens = RadiusTokens(),
    val spacing: SpacingTokens = SpacingTokens(),
    val density: Density = Density.COMFORTABLE,
    val effects: EffectTokens = EffectTokens(),
    /** Which icon set the components draw from; only the built-in "material" set exists so far. */
    val iconSetId: String = "material",
    val accent: AccentTokens = AccentTokens(),
    val icons: IconTokens = IconTokens(),
    val motion: MotionTokens = MotionTokens(),
    val progress: com.greenaltambe.vela.core.settings.ProgressStyle = com.greenaltambe.vela.core.settings.ProgressStyle.BOLD_DOT,
)

/** The palette's gradient (the "wake"): marks what is selected and tints buttons, switches, sliders and the profile ring. Never body text. */
@Serializable
data class AccentTokens(
    val wake: List<Long> = Palettes.stops(com.greenaltambe.vela.core.settings.Palette.VELA),
)

@Serializable
data class IconTokens(
    val style: com.greenaltambe.vela.core.settings.IconStyle = com.greenaltambe.vela.core.settings.IconStyle.FILLED,
    val weight: com.greenaltambe.vela.core.settings.IconWeight = com.greenaltambe.vela.core.settings.IconWeight.REGULAR,
    val rounded: Boolean = true,
)

/**
 * Every animation takes its numbers from here (brief section 6): four durations, three spring feels, two easings. [reduced] (the
 * Reduce motion setting, or Performance mode) shortens durations to [reducedFactor] of their value and removes the non-essential
 * animations (stagger, bounce, shimmer sweep).
 */
@Serializable
data class MotionTokens(
    val durationsMs: List<Int> = listOf(90, 160, 240, 360),
    /** (damping ratio, stiffness) for snappy, standard and bouncy. */
    val snappy: List<Float> = listOf(0.9f, 900f),
    val standard: List<Float> = listOf(0.8f, 400f),
    val bouncy: List<Float> = listOf(0.55f, 380f),
    val reduced: Boolean = false,
    val reducedFactor: Float = 0.4f,
    val staggerMs: Int = 28,
)

@Serializable
data class ColorTokens(
    val background: Long,
    val surface: Long,
    val surfaceVariant: Long,
    val onBackground: Long,
    val onSurface: Long,
    val onSurfaceVariant: Long,
    val primary: Long,
    val onPrimary: Long,
    val outline: Long,
    val error: Long,
    /** Seek-bar markers by SponsorBlock category and the chapter tick; keys are category ids. */
    val markers: Map<String, Long> = emptyMap(),
    val chapterMarker: Long = 0xFFFFFFFF,
    /** Drawn over video: icons and text on the picture, the dimming behind them, and the letterbox. */
    val onMedia: Long = 0xFFFFFFFF,
    val mediaScrim: Long = 0x99000000,
    val mediaBackground: Long = 0xFF000000,
)

@Serializable
data class TypographyTokens(
    /** "system" is the platform default; anything else names an installed family. */
    val fontFamily: String = "system",
    val titleSizeSp: Float = 20f,
    val headingSizeSp: Float = 16f,
    val bodySizeSp: Float = 14f,
    val captionSizeSp: Float = 12f,
    /** The text-size setting (80-130 %) as a factor, applied to every size above. */
    val scale: Float = 1f,
    val titleWeight: com.greenaltambe.vela.core.settings.TitleWeight = com.greenaltambe.vela.core.settings.TitleWeight.LIGHT,
)

@Serializable
data class RadiusTokens(
    val smallDp: Float = 6f,
    val mediumDp: Float = 12f,
    val largeDp: Float = 20f,
    /** Pill-shaped chips and nav. */
    val fullDp: Float = 999f,
)

@Serializable
data class SpacingTokens(
    val xsDp: Float = 4f,
    val smDp: Float = 8f,
    val mdDp: Float = 16f,
    val lgDp: Float = 24f,
    /** Window width where COMPACT ends (phones) and MEDIUM (small tablets, phones in landscape) begins. */
    val mediumMinWidthDp: Float = 600f,
    /** Window width where EXPANDED (large tablets, desktop) begins. */
    val wideLayoutMinWidthDp: Float = 840f,
    /** The standard content margin per window class: one gutter for every screen, so left edges line up. */
    val gutterCompactDp: Float = 16f,
    val gutterMediumDp: Float = 24f,
    val gutterExpandedDp: Float = 32f,
    /** Nothing tappable is smaller than this in either direction. */
    val minTouchDp: Float = 48f,
    /** Nothing readable is smaller than this. */
    val minTextSp: Float = 12f,
    /** Where the grid layouts start a new column: cards are never narrower than this. */
    val gridMinCardDp: Float = 280f,
)

@Serializable
enum class Density(val scale: Float) { COMPACT(0.85f), COMFORTABLE(1f), SPACIOUS(1.15f) }

@Serializable
data class EffectTokens(
    /** Glass / blur surfaces. Ignored (treated as false) when performance mode is on. */
    val blur: Boolean = false,
    val blurRadiusDp: Float = 16f,
    val gradientBackground: Boolean = false,
    /** Bars never let page text show through: the tint over the blur is at least this opaque (chosen so bar labels keep 4.5:1). */
    val barTintAlpha: Float = 0.98f,
)

/** The built-in Looks. */
object Looks {
    private val markers = mapOf(
        "sponsor" to 0xFF00D400,
        "selfpromo" to 0xFFFFFF00,
        "interaction" to 0xFFCC00FF,
        "intro" to 0xFF00FFFF,
        "outro" to 0xFF0202ED,
        "preview" to 0xFF008FD6,
        "music_offtopic" to 0xFFFF9900,
        "filler" to 0xFF7300FF,
        "poi_highlight" to 0xFFFF1684,
        "exclusive_access" to 0xFF008A5C,
    ).mapValues { it.value or 0xFF000000 }

    private val lightColors = ColorTokens(
        background = 0xFFFFFFFF, surface = 0xFFF6F6F8, surfaceVariant = 0xFFE9E9EE,
        onBackground = 0xFF101114, onSurface = 0xFF101114, onSurfaceVariant = 0xFF5A5D66,
        primary = 0xFF1A5FD0, onPrimary = 0xFFFFFFFF, outline = 0xFFC8CAD2, error = 0xFFB3261E,
        markers = markers, chapterMarker = 0xFF101114,
    )

    private val darkColors = ColorTokens(
        background = 0xFF111214, surface = 0xFF1B1C20, surfaceVariant = 0xFF2A2B31,
        onBackground = 0xFFF1F1F4, onSurface = 0xFFF1F1F4, onSurfaceVariant = 0xFFA6A8B2,
        primary = 0xFF8AB4FF, onPrimary = 0xFF0A1B3A, outline = 0xFF44464F, error = 0xFFF2B8B5,
        markers = markers, chapterMarker = 0xFFF1F1F4,
    )

    private val pureBlackColors = darkColors.copy(
        background = 0xFF000000, surface = 0xFF0A0A0B, surfaceVariant = 0xFF1A1A1D, outline = 0xFF303036,
    )

    val Light = LookTokens(id = "default-light", name = "Default (light)", colors = lightColors)
    val Dark = LookTokens(id = "default-dark", name = "Default (dark)", colors = darkColors)
    val PureBlack = LookTokens(id = "pure-black", name = "Pure black", colors = pureBlackColors)

    /** The default Look follows the system; Pure Black is dark whatever the system says. */
    fun forChoice(pureBlack: Boolean, systemDark: Boolean): LookTokens = when {
        pureBlack -> PureBlack
        systemDark -> Dark
        else -> Light
    }
}
