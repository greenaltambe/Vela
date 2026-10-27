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
)

@Serializable
data class TypographyTokens(
    /** "system" is the platform default; anything else names an installed family. */
    val fontFamily: String = "system",
    val titleSizeSp: Float = 20f,
    val headingSizeSp: Float = 16f,
    val bodySizeSp: Float = 14f,
    val captionSizeSp: Float = 12f,
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
    /** Below this window width the app uses the bottom bar, above it the side rail. */
    val wideLayoutMinWidthDp: Float = 840f,
)

@Serializable
enum class Density(val scale: Float) { COMPACT(0.85f), COMFORTABLE(1f), SPACIOUS(1.15f) }

@Serializable
data class EffectTokens(
    /** Glass / blur surfaces. Ignored (treated as false) when performance mode is on. */
    val blur: Boolean = false,
    val blurRadiusDp: Float = 16f,
    val gradientBackground: Boolean = false,
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
