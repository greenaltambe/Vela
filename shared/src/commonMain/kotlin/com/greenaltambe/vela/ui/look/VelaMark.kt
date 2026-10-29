package com.greenaltambe.vela.ui.look

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Vela's mark: a sail that is also a play button, on its mast with a star at the top. Same art as the launcher icon (the SVGs in docs/icon,
 * numbers in a 512-unit square), without the wake line and the stray dot so it stays legible at 24 dp. The sail takes the palette's
 * gradient, so the mark follows the colour palette (the launcher icon stays the default one).
 */
private object Mark {
    // A square around the art (star at the top y=42, mast and sail down to y=408, x=120..394) with a margin, so the tile's rounded corners never cut it.
    const val CROP_X = 59f; const val CROP_Y = 27f; const val CROP = 396f
    fun sail() = Path().apply {
        moveTo(214f, 126f); cubicTo(262f, 158f, 318f, 198f, 366f, 232f); cubicTo(398f, 254f, 398f, 258f, 366f, 280f)
        cubicTo(318f, 314f, 262f, 354f, 214f, 386f); cubicTo(190f, 402f, 172f, 392f, 172f, 366f); lineTo(172f, 146f)
        cubicTo(172f, 122f, 192f, 112f, 214f, 126f); close()
    }
    fun star() = Path().apply {
        moveTo(148f, 42f); quadraticTo(159f, 59f, 176f, 70f); quadraticTo(159f, 81f, 148f, 98f)
        quadraticTo(137f, 81f, 120f, 70f); quadraticTo(137f, 59f, 148f, 42f); close()
    }
}

@Composable
fun VelaMark(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val wake = Vela.look.accent.wake.map { it.toColor() }
    val onSurface = Vela.colors.onSurface.toColor()
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / Mark.CROP
        val ink = onSurface   // the mast and star follow the theme, since there is no dark tile behind them
        // Draw in the art's 512-unit coordinates: shift so the art's box starts at the corner, then scale it to fill the tile.
        withTransform({ translate(-Mark.CROP_X * k, -Mark.CROP_Y * k); scale(k, k, Offset.Zero) }) {
            val sail = Mark.sail()
            drawPath(sail, Brush.linearGradient(wake, start = Offset(172f, 106f), end = Offset(394f, 408f)))
            drawPath(sail, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.Transparent), startY = 106f, endY = 260f))
            drawLine(ink, Offset(148f, 92f), Offset(148f, 404f), strokeWidth = 12f, cap = StrokeCap.Round)
            drawPath(Mark.star(), ink)
        }
    }
}
