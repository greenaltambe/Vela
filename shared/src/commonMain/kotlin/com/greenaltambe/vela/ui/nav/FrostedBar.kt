package com.greenaltambe.vela.ui.nav

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * What lets a bar laid over a page show that page blurred behind itself, as YouTube's bottom bar does. The page is marked as the source
 * ([recordsBackdrop]); the bar blurs what is behind it ([frostedBackdrop]). The drawing is done by the Haze library: on Android 12 and newer and on
 * desktop it blurs; on older Android it draws a more opaque tint instead.
 */
@Stable
class BarBackdrop internal constructor(internal val state: HazeState)

@Composable
fun rememberBarBackdrop(): BarBackdrop {
    val state = rememberHazeState()
    return remember(state) { BarBackdrop(state) }
}

/** On the page: it is what the bar blurs. It still draws as usual. */
fun Modifier.recordsBackdrop(backdrop: BarBackdrop): Modifier = hazeSource(backdrop.state)

/**
 * On the bar: blurs the page behind it and lays [tint] over the blur. [fallbackTint] is what is drawn instead where blur is not available (it is more opaque, so the
 * icons and names stay readable over a sharp page). [base] fills anything the blur has no page for, such as the strip under the gesture area. While [active] says no, nothing is drawn.
 */
@Composable
fun Modifier.frostedBackdrop(backdrop: BarBackdrop, tint: Color, fallbackTint: Color, base: Color, radius: Dp = 28.dp, active: () -> Boolean = { true }, mask: () -> Brush? = { null }): Modifier {
    val maskNow = androidx.compose.runtime.rememberUpdatedState(mask)
    val style = remember(tint, fallbackTint, base, radius) {
        HazeBlurStyle {
            // [active] may read animated state (the mini player is frosted only while collapsed): the effect follows it without recomposition.
            val on = active()
            blurEnabled(on)
            alpha(if (on) 1f else 0f)
            // [mask] may read animated state too (a part of the area fading with a swipe).
            mask(maskNow.value())
            blurRadius(radius)
            backgroundColor(base)
            noiseFactor(0f)
            colorEffects(listOf(HazeColorEffect.tint(tint)))
            fallbackColorEffect(HazeColorEffect.tint(fallbackTint))
        }
    }
    return hazeBlur(input = HazeInput.Sources(backdrop.state), style = style)
}

/**
 * One frosted surface for a group of neighbours (the top bar with Home's chip strip; the mini player with the bottom bar), so they read as a single piece with no
 * seam where they meet. With a [backdrop] the page behind is blurred under a light tint; without one (no blur on this platform) it is a nearly opaque tint.
 * [edge] draws a hairline along the side facing the page.
 */
@Composable
fun Modifier.frostedArea(
    backdrop: BarBackdrop?, base: Color, edge: Color? = null, edgeAtBottom: Boolean = true,
    /** Fades the part of the area at its edge side (and the edge line) to nothing as this goes from 1 to 0: the strip a swiped-away mini player leaves behind. */
    fade: (() -> Float)? = null,
    /** How much of the area's height that fading part is, 0..1. */
    fadeFraction: () -> Float = { 0f },
): Modifier {
    val frosted = if (backdrop != null) frostedBackdrop(
        backdrop, base.copy(alpha = 0.62f), base.copy(alpha = 0.94f), base,
        mask = {
            val a = fade?.invoke() ?: 1f
            if (a >= 0.999f) null else {
                val f = fadeFraction().coerceIn(0f, 1f)
                // Only the alpha of the mask matters; it is made of the area's own colour.
                val whole = base.copy(alpha = 1f)
                val part = base.copy(alpha = a)
                // The faded part is at the edge side: the top for an area whose edge is at the top, the bottom otherwise.
                if (edgeAtBottom) Brush.verticalGradient(colorStops = arrayOf(0f to whole, (1f - f) to whole, (1f - f + 0.001f).coerceAtMost(1f) to part, 1f to part))
                else Brush.verticalGradient(colorStops = arrayOf(0f to part, f to part, (f + 0.001f).coerceAtMost(1f) to whole, 1f to whole))
            }
        },
    ) else background(base.copy(alpha = 0.94f))
    if (edge == null) return frosted
    return frosted.drawWithContent {
        drawContent()
        val h = 1.dp.toPx()
        drawRect(edge.copy(alpha = edge.alpha * (fade?.invoke() ?: 1f)), topLeft = Offset(0f, if (edgeAtBottom) size.height - h else 0f), size = Size(size.width, h))
    }
}
