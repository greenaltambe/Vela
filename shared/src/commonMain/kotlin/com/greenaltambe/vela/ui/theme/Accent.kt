package com.greenaltambe.vela.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

/** The accent as a brush, for the few places a brush (not a colour) is asked for: selected marks, progress fills, outlines. One solid colour: the theme's primary. */
object Accent {
    val brush: Brush @Composable @ReadOnlyComposable get() = SolidColor(MaterialTheme.colorScheme.primary)

    /** For the APIs that take a list of gradient stops (a gradient of one colour): kept so a later look can bring a real gradient back. */
    val colors: List<Color> @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary.let { listOf(it, it) }
}
