package com.greenaltambe.vela.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

/** The accent as a brush, for the few places a brush (not a colour) is asked for: selected marks, progress fills, outlines. One solid colour: the theme's primary. */
/** A colour the played part of the seek bar (and the watched bar on a card) takes instead of the primary, for a scheme whose primary is neutral (Classic: red). Null: the primary. */
val LocalSeekColor = androidx.compose.runtime.staticCompositionLocalOf<Color?> { null }

/** The played-progress colour of a scheme that needs one of its own ([Palettes.progress]); unlike [LocalSeekColor] the logo and badges do not take it. */
val LocalProgressColor = androidx.compose.runtime.staticCompositionLocalOf<Color?> { null }

object Accent {
    /** The colour of played / watched progress. */
    val seek: Color @Composable @ReadOnlyComposable get() = LocalSeekColor.current ?: LocalProgressColor.current ?: MaterialTheme.colorScheme.primary
    val seekBrush: Brush @Composable @ReadOnlyComposable get() = SolidColor(seek)
    val seekColors: List<Color> @Composable @ReadOnlyComposable get() = seek.let { listOf(it, it) }

    val brush: Brush @Composable @ReadOnlyComposable get() = SolidColor(MaterialTheme.colorScheme.primary)

    /** For the APIs that take a list of gradient stops (a gradient of one colour): kept so a later look can bring a real gradient back. */
    val colors: List<Color> @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary.let { listOf(it, it) }
}
