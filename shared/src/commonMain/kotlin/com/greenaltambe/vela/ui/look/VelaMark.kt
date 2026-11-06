package com.greenaltambe.vela.ui.look

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Vela's mark: the sails of the launcher icon, in the theme's colours (red sails and wave, the mast in the text colour). The numbers are the ones of
 * `tools/make-icon.py`, in a 512-unit square around the art (x 130..396, y 84..392).
 */
private object Mark {
    const val CENTER_X = 263f; const val CENTER_Y = 238f; const val CROP = 330f
    const val SAIL = "M236,98C322,150 388,238 396,334C396,340 392,342 386,342L246,342C240,342 236,338 236,332Z"
    const val JIB = "M208,176C182,226 168,282 164,334C164,340 168,342 174,342L208,342Z"
    const val MAST = "M222,86L222,352"
    const val WAVE = "M134,378C170,398 206,360 258,378C310,396 346,360 392,382"
}

@Composable
fun VelaMark(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val sail = remember { PathParser().parsePathString(Mark.SAIL).toPath() }
    val jib = remember { PathParser().parsePathString(Mark.JIB).toPath() }
    val mast = remember { PathParser().parsePathString(Mark.MAST).toPath() }
    val wave = remember { PathParser().parsePathString(Mark.WAVE).toPath() }
    val red = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / Mark.CROP
        withTransform({ translate(-(Mark.CENTER_X - Mark.CROP / 2) * k, -(Mark.CENTER_Y - Mark.CROP / 2) * k); scale(k, k, Offset.Zero) }) {
            drawPath(sail, red); drawPath(jib, red)
            drawPath(mast, ink, style = Stroke(width = 10f, cap = StrokeCap.Round))
            drawPath(wave, red, style = Stroke(width = 16f, cap = StrokeCap.Round))
        }
    }
}
