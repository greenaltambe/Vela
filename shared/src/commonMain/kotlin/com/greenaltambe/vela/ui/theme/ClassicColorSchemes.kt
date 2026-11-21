package com.greenaltambe.vela.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The "Classic" scheme for people who do not want accent colours: a white page (near-black in the dark theme) with neutral grey cards, black-and-white
 * buttons and selection, and red kept for the few things that carry it (the seek bar, which is the app's own accent, and the tertiary role). Hand-authored
 * like [RedColorSchemes]; the raw colour values live in the theme files only.
 */
object ClassicColorSchemes {
    /** The seek bar's played part in Classic: the one red in an otherwise neutral scheme (a red that reads on both the light and the dark page). */
    val SeekRed = Color(0xFFF00000)

    val Light: ColorScheme = lightColorScheme(
        primary = Color(0xFF0F0F0F), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE5E5E5), onPrimaryContainer = Color(0xFF0F0F0F),
        secondary = Color(0xFF4D4D4D), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE5E5E5), onSecondaryContainer = Color(0xFF0F0F0F),
        tertiary = Color(0xFFCC0000), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDAD6), onTertiaryContainer = Color(0xFF410002),
        error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFFFFFF), onBackground = Color(0xFF0F0F0F),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF0F0F0F),
        surfaceVariant = Color(0xFFF2F2F2), onSurfaceVariant = Color(0xFF5E5E5E),
        outline = Color(0xFF737373), outlineVariant = Color(0xFFD9D9D9),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFF272727), inverseOnSurface = Color(0xFFF1F1F1), inversePrimary = Color(0xFFF1F1F1),
        surfaceDim = Color(0xFFE3E3E3), surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF7F7F7), surfaceContainer = Color(0xFFF2F2F2),
        surfaceContainerHigh = Color(0xFFEBEBEB), surfaceContainerHighest = Color(0xFFE5E5E5),
    )

    val Dark: ColorScheme = darkColorScheme(
        primary = Color(0xFFF1F1F1), onPrimary = Color(0xFF0F0F0F),
        primaryContainer = Color(0xFF3F3F3F), onPrimaryContainer = Color(0xFFF1F1F1),
        secondary = Color(0xFFAAAAAA), onSecondary = Color(0xFF0F0F0F),
        secondaryContainer = Color(0xFF3F3F3F), onSecondaryContainer = Color(0xFFF1F1F1),
        tertiary = Color(0xFFFF4E45), onTertiary = Color(0xFF0F0F0F),
        tertiaryContainer = Color(0xFF8C1B16), onTertiaryContainer = Color(0xFFFFDAD6),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF0F0F0F), onBackground = Color(0xFFF1F1F1),
        surface = Color(0xFF0F0F0F), onSurface = Color(0xFFF1F1F1),
        surfaceVariant = Color(0xFF272727), onSurfaceVariant = Color(0xFFAAAAAA),
        outline = Color(0xFF717171), outlineVariant = Color(0xFF3F3F3F),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFFF1F1F1), inverseOnSurface = Color(0xFF272727), inversePrimary = Color(0xFF0F0F0F),
        surfaceDim = Color(0xFF0F0F0F), surfaceBright = Color(0xFF3A3A3A),
        surfaceContainerLowest = Color(0xFF0A0A0A), surfaceContainerLow = Color(0xFF181818), surfaceContainer = Color(0xFF1F1F1F),
        surfaceContainerHigh = Color(0xFF272727), surfaceContainerHighest = Color(0xFF303030),
    )
}
