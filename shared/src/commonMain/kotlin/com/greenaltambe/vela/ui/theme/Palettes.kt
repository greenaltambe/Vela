package com.greenaltambe.vela.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.greenaltambe.vela.core.settings.ColorPalette

/**
 * The colour schemes you can pick in Customize. Each palette is a handful of hand-chosen accent colours (primary, secondary and tertiary with their containers,
 * for the light and the dark theme) and one tint; the neutral surfaces and the text colours are built from that tint, so a scheme's page, cards and sheets
 * all carry a little of its colour (a plain grey page is what made the old dark theme look dull). [Vela red] is the app's own scheme from [RedColorSchemes].
 * Every text/background pair is checked to 4.5:1 by `PaletteContrastTest`. The raw colour values live in this file and in [RedColorSchemes] only.
 */
object Palettes {
    fun light(p: ColorPalette): ColorScheme = when (p) { ColorPalette.VELA_RED -> RedColorSchemes.Light; ColorPalette.CLASSIC -> ClassicColorSchemes.Light; else -> spec(p).let { lightScheme(it) } }
    fun dark(p: ColorPalette): ColorScheme = when (p) { ColorPalette.VELA_RED -> RedColorSchemes.Dark; ColorPalette.CLASSIC -> ClassicColorSchemes.Dark; else -> spec(p).let { darkScheme(it) } }

    /** One palette: accent roles for light then dark, and the tint the neutrals take. */
    private class Spec(
        val tint: Color,
        val lPrimary: Color, val lPrimaryContainer: Color, val lOnPrimaryContainer: Color,
        val lSecondary: Color, val lSecondaryContainer: Color, val lOnSecondaryContainer: Color,
        val lTertiary: Color, val lTertiaryContainer: Color, val lOnTertiaryContainer: Color,
        val dPrimary: Color, val dOnPrimary: Color, val dPrimaryContainer: Color, val dOnPrimaryContainer: Color,
        val dSecondary: Color, val dOnSecondary: Color, val dSecondaryContainer: Color, val dOnSecondaryContainer: Color,
        val dTertiary: Color, val dOnTertiary: Color, val dTertiaryContainer: Color, val dOnTertiaryContainer: Color,
    )

    private fun c(v: Long) = Color(v)

    private fun spec(p: ColorPalette): Spec = when (p) {
        // Soft, cute pinks (blossom, not hot pink): the primary is a muted rose, the containers are pale petals, the dark theme's containers a dusty mauve.
        ColorPalette.SAKURA -> Spec(
            tint = c(0xFFFFA3C6),
            lPrimary = c(0xFFB9467A), lPrimaryContainer = c(0xFFFFE0EC), lOnPrimaryContainer = c(0xFF4A1230),
            lSecondary = c(0xFF93596F), lSecondaryContainer = c(0xFFFFE8F0), lOnSecondaryContainer = c(0xFF35182A),
            lTertiary = c(0xFF7F65B0), lTertiaryContainer = c(0xFFF0E4FF), lOnTertiaryContainer = c(0xFF2A1260),
            dPrimary = c(0xFFFFB4D2), dOnPrimary = c(0xFF5A1B3A), dPrimaryContainer = c(0xFF8C4B6B), dOnPrimaryContainer = c(0xFFFFE0EC),
            dSecondary = c(0xFFF3C4D8), dOnSecondary = c(0xFF4A2A3C), dSecondaryContainer = c(0xFF6E4A5C), dOnSecondaryContainer = c(0xFFFFE0EC),
            dTertiary = c(0xFFD6C4FF), dOnTertiary = c(0xFF35206F), dTertiaryContainer = c(0xFF574682), dOnTertiaryContainer = c(0xFFF0E4FF),
        )
        ColorPalette.OCEAN -> Spec(
            tint = c(0xFF3D8FE8),
            lPrimary = c(0xFF0B63C5), lPrimaryContainer = c(0xFFD6E6FF), lOnPrimaryContainer = c(0xFF001B3E),
            lSecondary = c(0xFF4F6078), lSecondaryContainer = c(0xFFD8E3F8), lOnSecondaryContainer = c(0xFF0F1D2E),
            lTertiary = c(0xFF00696B), lTertiaryContainer = c(0xFF9DF1F2), lOnTertiaryContainer = c(0xFF002021),
            dPrimary = c(0xFF8FC1FF), dOnPrimary = c(0xFF00315F), dPrimaryContainer = c(0xFF0F4C9A), dOnPrimaryContainer = c(0xFFD6E6FF),
            dSecondary = c(0xFFB8C8E2), dOnSecondary = c(0xFF22324A), dSecondaryContainer = c(0xFF3A4A60), dOnSecondaryContainer = c(0xFFD8E3F8),
            dTertiary = c(0xFF6FD8DA), dOnTertiary = c(0xFF003738), dTertiaryContainer = c(0xFF004F51), dOnTertiaryContainer = c(0xFF9DF1F2),
        )
        ColorPalette.FOREST -> Spec(
            tint = c(0xFF4CAF62),
            lPrimary = c(0xFF1F7A3A), lPrimaryContainer = c(0xFFC8F2CF), lOnPrimaryContainer = c(0xFF002108),
            lSecondary = c(0xFF52634F), lSecondaryContainer = c(0xFFD5E8D0), lOnSecondaryContainer = c(0xFF10200F),
            lTertiary = c(0xFF3A6471), lTertiaryContainer = c(0xFFBDEAF8), lOnTertiaryContainer = c(0xFF001F28),
            dPrimary = c(0xFF7FDB91), dOnPrimary = c(0xFF00390F), dPrimaryContainer = c(0xFF0F5B24), dOnPrimaryContainer = c(0xFFC8F2CF),
            dSecondary = c(0xFFB9CCB4), dOnSecondary = c(0xFF253423), dSecondaryContainer = c(0xFF3B4B39), dOnSecondaryContainer = c(0xFFD5E8D0),
            dTertiary = c(0xFFA3CDDC), dOnTertiary = c(0xFF033542), dTertiaryContainer = c(0xFF214B58), dOnTertiaryContainer = c(0xFFBDEAF8),
        )
        ColorPalette.TWILIGHT -> Spec(
            tint = c(0xFF8A6CF0),
            lPrimary = c(0xFF6847D6), lPrimaryContainer = c(0xFFE6DEFF), lOnPrimaryContainer = c(0xFF1D0066),
            lSecondary = c(0xFF625B71), lSecondaryContainer = c(0xFFE8DEF8), lOnSecondaryContainer = c(0xFF1D192B),
            lTertiary = c(0xFF9A5878), lTertiaryContainer = c(0xFFFFE0EF), lOnTertiaryContainer = c(0xFF3A0B26),
            dPrimary = c(0xFFC9B8FF), dOnPrimary = c(0xFF2D0F85), dPrimaryContainer = c(0xFF4B2DB0), dOnPrimaryContainer = c(0xFFE6DEFF),
            dSecondary = c(0xFFCCC2DC), dOnSecondary = c(0xFF332D41), dSecondaryContainer = c(0xFF4A4458), dOnSecondaryContainer = c(0xFFE8DEF8),
            dTertiary = c(0xFFF0B6D3), dOnTertiary = c(0xFF4A2538), dTertiaryContainer = c(0xFF6B4558), dOnTertiaryContainer = c(0xFFFFE0EF),
        )
        ColorPalette.SUNSET -> Spec(
            tint = c(0xFFFF8A3D),
            lPrimary = c(0xFFB84A00), lPrimaryContainer = c(0xFFFFDBC6), lOnPrimaryContainer = c(0xFF3A1400),
            lSecondary = c(0xFF7A5748), lSecondaryContainer = c(0xFFFFE2D3), lOnSecondaryContainer = c(0xFF2B160B),
            lTertiary = c(0xFF6B6000), lTertiaryContainer = c(0xFFF4E67A), lOnTertiaryContainer = c(0xFF201C00),
            dPrimary = c(0xFFFFB27A), dOnPrimary = c(0xFF5A2100), dPrimaryContainer = c(0xFF9A3A00), dOnPrimaryContainer = c(0xFFFFDBC6),
            dSecondary = c(0xFFE5BFAE), dOnSecondary = c(0xFF442A1E), dSecondaryContainer = c(0xFF5D4033), dOnSecondaryContainer = c(0xFFFFDBC6),
            dTertiary = c(0xFFD8CA62), dOnTertiary = c(0xFF363100), dTertiaryContainer = c(0xFF504600), dOnTertiaryContainer = c(0xFFF4E67A),
        )
        ColorPalette.VELA_RED, ColorPalette.CLASSIC -> error("${p.name} has its own scheme object")
    }

    private val white = c(0xFFFFFFFF)
    private val errLight = c(0xFFBA1A1A); private val errDark = c(0xFFFFB4AB)

    private fun lightScheme(s: Spec): ColorScheme {
        val t = s.tint
        fun tinted(base: Color, amount: Float) = lerp(base, t, amount)
        val page = tinted(white, 0.05f)
        return lightColorScheme(
            primary = s.lPrimary, onPrimary = white, primaryContainer = s.lPrimaryContainer, onPrimaryContainer = s.lOnPrimaryContainer,
            secondary = s.lSecondary, onSecondary = white, secondaryContainer = s.lSecondaryContainer, onSecondaryContainer = s.lOnSecondaryContainer,
            tertiary = s.lTertiary, onTertiary = white, tertiaryContainer = s.lTertiaryContainer, onTertiaryContainer = s.lOnTertiaryContainer,
            error = errLight, onError = white, errorContainer = c(0xFFFFDAD6), onErrorContainer = c(0xFF410002),
            background = page, onBackground = tinted(c(0xFF1C1B1F), 0.12f), surface = page, onSurface = tinted(c(0xFF1C1B1F), 0.12f),
            surfaceVariant = tinted(white, 0.16f), onSurfaceVariant = tinted(c(0xFF49454F), 0.2f),
            outline = tinted(c(0xFF79747E), 0.25f), outlineVariant = tinted(c(0xFFCAC4D0), 0.3f), scrim = Color.Black,
            inverseSurface = tinted(c(0xFF313033), 0.15f), inverseOnSurface = tinted(c(0xFFF4EFF4), 0.1f), inversePrimary = s.dPrimary,
            surfaceDim = tinted(white, 0.14f), surfaceBright = page,
            surfaceContainerLowest = white, surfaceContainerLow = tinted(white, 0.07f), surfaceContainer = tinted(white, 0.10f),
            surfaceContainerHigh = tinted(white, 0.13f), surfaceContainerHighest = tinted(white, 0.16f),
        )
    }

    private fun darkScheme(s: Spec): ColorScheme {
        val t = s.tint
        fun tinted(base: Color, amount: Float) = lerp(base, t, amount)
        val page = tinted(c(0xFF0E0D10), 0.10f)
        return darkColorScheme(
            primary = s.dPrimary, onPrimary = s.dOnPrimary, primaryContainer = s.dPrimaryContainer, onPrimaryContainer = s.dOnPrimaryContainer,
            secondary = s.dSecondary, onSecondary = s.dOnSecondary, secondaryContainer = s.dSecondaryContainer, onSecondaryContainer = s.dOnSecondaryContainer,
            tertiary = s.dTertiary, onTertiary = s.dOnTertiary, tertiaryContainer = s.dTertiaryContainer, onTertiaryContainer = s.dOnTertiaryContainer,
            error = errDark, onError = c(0xFF690005), errorContainer = c(0xFF93000A), onErrorContainer = c(0xFFFFDAD6),
            background = page, onBackground = tinted(c(0xFFE6E1E5), 0.08f), surface = page, onSurface = tinted(c(0xFFE6E1E5), 0.08f),
            surfaceVariant = tinted(c(0xFF2B292E), 0.2f), onSurfaceVariant = tinted(c(0xFFCAC4D0), 0.15f),
            outline = tinted(c(0xFF938F99), 0.2f), outlineVariant = tinted(c(0xFF49454F), 0.25f), scrim = Color.Black,
            inverseSurface = tinted(c(0xFFE6E1E5), 0.08f), inverseOnSurface = tinted(c(0xFF313033), 0.12f), inversePrimary = s.lPrimary,
            surfaceDim = page, surfaceBright = tinted(c(0xFF38363B), 0.2f),
            surfaceContainerLowest = tinted(c(0xFF09080A), 0.08f), surfaceContainerLow = tinted(c(0xFF151417), 0.13f), surfaceContainer = tinted(c(0xFF1A191C), 0.16f),
            surfaceContainerHigh = tinted(c(0xFF242327), 0.18f), surfaceContainerHighest = tinted(c(0xFF2F2E32), 0.2f),
        )
    }
}
