package com.greenaltambe.vela.ui.nav

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A navigation icon that moves when its tab is chosen or left, in the way of the Play Store's bar: the outlined and the filled form cross-fade, the chosen
 * icon hops up and lands with a springy pop, and the one that is left shrinks back. [icon] gives the form for "selected" or not.
 * With [animate] off the icon just changes. Nothing runs on the first composition (a bar that appears must not hop), and the motion is a spring, so it
 * is cut short cleanly if the viewer taps another tab at once.
 */
@Composable
fun AnimatedNavIcon(selected: Boolean, animate: Boolean, contentDescription: String?, tint: Color = Color.Unspecified, modifier: Modifier = Modifier, icon: @Composable (Boolean) -> ImageVector) {
    if (!animate) { Icon(icon(selected), contentDescription, modifier, tint); return }
    val scale = remember { Animatable(if (selected) 1f else REST_SCALE) }
    val lift = remember { Animatable(0f) }
    var first by remember { mutableStateOf(true) }
    val hop = with(LocalDensity.current) { HOP_DP.dp.toPx() }
    LaunchedEffect(selected) {
        if (first) { first = false; scale.snapTo(if (selected) 1f else REST_SCALE); return@LaunchedEffect }
        if (selected) {
            // Chosen: start small and low, spring up with overshoot and settle.
            launch { scale.snapTo(0.72f); scale.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessMedium)) }
            launch { lift.snapTo(hop); lift.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow)) }
        } else {
            // Left: a quick, calm shrink back.
            launch { scale.animateTo(REST_SCALE, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) }
            launch { lift.animateTo(0f, tween(120)) }
        }
    }
    Crossfade(selected, animationSpec = tween(140), label = "nav-icon") { on ->
        Icon(icon(on), contentDescription, modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value; translationY = -lift.value }, tint)
    }
}

private const val REST_SCALE = 0.92f
private const val HOP_DP = 6f
