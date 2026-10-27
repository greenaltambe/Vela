package com.greenaltambe.vela.ui.look

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * The one place blur / glass is applied (SPEC section 5), so performance mode has a single switch. With
 * `performanceMode` on, or the Look's blur flag off, [content] sits on a plain opaque surface. The glass is a
 * translucent, blurred layer behind [content]; the content itself is never blurred.
 */
@Composable
fun Effects(
    modifier: Modifier = Modifier,
    glass: Boolean = true,
    content: @Composable () -> Unit,
) {
    val look = LocalLook.current
    val surface = look.colors.surface.toColor()
    Box(modifier.clip(Vela.mediumShape)) {
        if (glass && glassEnabled()) {
            Box(Modifier.matchParentSize().blur(look.effects.blurRadiusDp.dp).background(surface.copy(alpha = 0.72f)))
        } else {
            Box(Modifier.matchParentSize().background(surface))
        }
        content()
    }
}

/** True when blur and glass should actually be drawn right now. */
@Composable
fun glassEnabled(): Boolean = LocalLook.current.effects.blur && !LocalPerformanceMode.current
