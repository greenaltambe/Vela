package com.greenaltambe.vela.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The app's one colour story: Material 3 roles from the red tonal palette (primary red, warm neutral surfaces). Hand-authored, not generated,
 * so every role is visible in one place; [com.greenaltambe.vela.ui.theme.AppearanceSource] is where a different scheme would come from later.
 * The raw values live here and nowhere else (a source scan enforces it).
 */
object RedColorSchemes {
    val Light: ColorScheme = lightColorScheme(
        primary = Color(0xFFBB1614), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFDAD5), onPrimaryContainer = Color(0xFF410001),
        secondary = Color(0xFF775652), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFDAD5), onSecondaryContainer = Color(0xFF2C1512),
        tertiary = Color(0xFF705C2E), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFBDFA6), onTertiaryContainer = Color(0xFF251A00),
        error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFFF8F7), onBackground = Color(0xFF231918),
        surface = Color(0xFFFFF8F7), onSurface = Color(0xFF231918),
        surfaceVariant = Color(0xFFF5DDDA), onSurfaceVariant = Color(0xFF534341),
        outline = Color(0xFF857370), outlineVariant = Color(0xFFD8C2BE),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFF392E2D), inverseOnSurface = Color(0xFFFFEDEA), inversePrimary = Color(0xFFFFB4AA),
        surfaceDim = Color(0xFFE8D6D3), surfaceBright = Color(0xFFFFF8F7),
        surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFF0EE), surfaceContainer = Color(0xFFFCEAE8),
        surfaceContainerHigh = Color(0xFFF6E4E2), surfaceContainerHighest = Color(0xFFF0DEDC),
    )

    val Dark: ColorScheme = darkColorScheme(
        primary = Color(0xFFFF8A80), onPrimary = Color(0xFF4A0004),
        primaryContainer = Color(0xFFA81A20), onPrimaryContainer = Color(0xFFFFDAD6),
        secondary = Color(0xFFF0B8B1), onSecondary = Color(0xFF4A1F1B),
        secondaryContainer = Color(0xFF6E3430), onSecondaryContainer = Color(0xFFFFDAD5),
        tertiary = Color(0xFFF0C060), onTertiary = Color(0xFF3D2900),
        tertiaryContainer = Color(0xFF5A4200), onTertiaryContainer = Color(0xFFFFE08A),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        // A page with a little more red in it (it was a muddy brown-grey): the cards keep their steps above it.
        background = Color(0xFF170D0E), onBackground = Color(0xFFF6E3E1),
        surface = Color(0xFF170D0E), onSurface = Color(0xFFF6E3E1),
        surfaceVariant = Color(0xFF4A3534), onSurfaceVariant = Color(0xFFE2C7C4),
        outline = Color(0xFFB08F8C), outlineVariant = Color(0xFF574140),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFFF6E3E1), inverseOnSurface = Color(0xFF392E2D), inversePrimary = Color(0xFFBB1614),
        surfaceDim = Color(0xFF170D0E), surfaceBright = Color(0xFF4A3836),
        surfaceContainerLowest = Color(0xFF110809), surfaceContainerLow = Color(0xFF211214), surfaceContainer = Color(0xFF2A171A),
        surfaceContainerHigh = Color(0xFF362022), surfaceContainerHighest = Color(0xFF442B2D),
    )
}
