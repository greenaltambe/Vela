package com.greenaltambe.vela.ui.look

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.greenaltambe.vela.ui.theme.Spacing

/**
 * A soft wash of a picture's colours behind a header, fading into the surface. Drawn here so that it can be turned down in one place (spec section 5):
 * with [enabled] false, or no picture, only the plain surface is drawn. Where the platform cannot blur (older Android) the picture stays faint.
 */
@Composable
fun CoverWash(url: String?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val base = MaterialTheme.colorScheme.surfaceContainerLow
    Box(modifier.background(base)) {
        if (enabled && url != null) {
            AsyncImage(url, null, Modifier.matchParentSize().blur(Spacing.lg * 2), contentScale = ContentScale.Crop, alpha = 0.35f)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(base.copy(alpha = 0.15f), base))))
        }
    }
}
