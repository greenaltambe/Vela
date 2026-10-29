package com.greenaltambe.vela.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.core.settings.BarStyle
import com.greenaltambe.vela.core.settings.MiniLook
import com.greenaltambe.vela.core.settings.MiniPlacement
import com.greenaltambe.vela.core.settings.NavLabels
import com.greenaltambe.vela.core.settings.NavTab
import com.greenaltambe.vela.ui.components.BottomNav
import com.greenaltambe.vela.ui.components.MiniPlayer
import com.greenaltambe.vela.ui.components.MiniState
import com.greenaltambe.vela.ui.components.NavEntry
import com.greenaltambe.vela.ui.look.VelaMotion
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.components.PIP_WIDTH
import com.greenaltambe.vela.ui.look.toColor
import kotlin.math.abs

/**
 * What the page's scrolling tells the bar: [shrunk] turns on after scrolling down a little and off after scrolling up. The shell puts
 * [connection] on the page's container, so every list and scroll view inside reports through it without knowing about the bar.
 */
@Stable
class ScrollChrome {
    var shrunk by mutableStateOf(false)
        internal set
    private var travelled = 0f

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (abs(available.y) < 0.5f) return Offset.Zero
            // The direction of travel decides: a few dozen px down shrinks, a few up restores; a change of direction starts over.
            if ((available.y < 0) != (travelled < 0)) travelled = 0f
            travelled += available.y
            if (travelled < -SHRINK_AFTER_PX) shrunk = true else if (travelled > RESTORE_AFTER_PX) shrunk = false
            return Offset.Zero
        }
    }

    /** Back to normal (another page was opened). */
    fun reset() { shrunk = false; travelled = 0f }

    private companion object { const val SHRINK_AFTER_PX = 60f; const val RESTORE_AFTER_PX = 30f }
}

@Composable
fun rememberScrollChrome(): ScrollChrome = remember { ScrollChrome() }

/** Height of the floating bar + mini player, so a page can leave that much room at its end; 0 when the bar is docked (the page already ends above it). */
val LocalChromeInset = compositionLocalOf { 0.dp }

/**
 * The shapes of the two parts at shrink fraction [f] (0 = full bar, 1 = icon pill), so a contracting bar changes its corners smoothly
 * instead of switching. [joined] means the mini player is attached to a full-width bar: one card with only its outer corners round,
 * which comes apart as the bar contracts. The pill's corner is [pillRadius] (half the bar's height; larger values are clamped by the shape).
 */
fun chromeShapes(radius: Dp, pillRadius: Dp, joined: Boolean, docked: Boolean, f: Float): Pair<androidx.compose.ui.graphics.Shape, androidx.compose.ui.graphics.Shape> {
    fun mix(a: Dp, b: Dp) = a + (b - a) * f
    val miniBottom = if (joined) mix(0.dp, radius) else radius
    val mini = RoundedCornerShape(topStart = radius, topEnd = radius, bottomStart = miniBottom, bottomEnd = miniBottom)
    val bar = when {
        docked -> RoundedCornerShape(mix(0.dp, pillRadius))
        joined -> RoundedCornerShape(topStart = mix(0.dp, pillRadius), topEnd = mix(0.dp, pillRadius), bottomStart = mix(radius, pillRadius), bottomEnd = mix(radius, pillRadius))
        else -> RoundedCornerShape(pillRadius)
    }
    return mini to bar
}

/**
 * The tab bar and the mini player together, so they can move as one:
 *  - FLOATING runs over the page (the page scrolls behind it), MINIMAL is a small icon-only pill that also floats, DOCKED sits below the page.
 *  - Labels: hidden, always, or hidden while the page scrolls. Shrink-on-scroll turns the bar into an icon pill while scrolling down.
 *  - The mini player is as wide as the bar was (regular look) or a small corner card (picture-in-picture look); attached it touches
 *    the bar, apart it floats [miniGap] above it. It sits above the bar in the same column, so it moves whenever the bar shrinks.
 *  - Corners follow the corner-radius setting.
 */
@Composable
fun BottomChrome(
    nav: NavConfig, entries: List<NavEntry>, selected: NavTab?, scroll: ScrollChrome, mini: MiniState?, onSelect: (NavTab) -> Unit,
    modifier: Modifier = Modifier, onHeight: (Int) -> Unit = {},
) {
    val docked = nav.barStyle == BarStyle.DOCKED
    // One number drives the whole contraction (width, height, corners, spacing, labels, the mini player's position), so it is one smooth motion.
    val target = if (nav.barStyle == BarStyle.MINIMAL || (nav.shrinkOnScroll && scroll.shrunk)) 1f else 0f
    val f by androidx.compose.animation.core.animateFloatAsState(target, VelaMotion.tween(Vela.motion, VelaMotion.Level.STANDARD, emphasized = true), label = "barShrink")
    val radius = Vela.look.radii.largeDp.dp
    val pip = nav.miniLook == MiniLook.PIP
    val joined = mini != null && !pip && nav.miniPlacement == MiniPlacement.ATTACHED
    val (miniShape, barShape) = chromeShapes(radius, radius, joined, docked, f)
    val gap = when {
        mini == null -> 0.dp
        joined -> SEPARATE_AFTER_SHRINK_GAP * f          // touching at full size; a small gap once the bar is a pill
        pip -> maxOf(nav.miniGapDp, 4f).dp
        else -> nav.miniGapDp.dp
    }
    val margin = if (docked) 0.dp else Vela.md

    Column(
        // Measured outermost, so the height the page reserves includes the bar's margin and the gesture-area inset, not just its content.
        modifier.fillMaxWidth().onSizeChanged { onHeight(it.height) }
            .windowInsetsPadding(WindowInsets.safeDrawing.only(if (docked) WindowInsetsSides.Horizontal else WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .let { if (docked) it else it.padding(horizontal = margin, vertical = Vela.xs) },
        horizontalAlignment = if (pip) Alignment.End else Alignment.CenterHorizontally,
    ) {
        if (mini != null) {
            val miniModifier = if (pip) Modifier.padding(end = if (docked) Vela.md else 0.dp).shadow(6.dp, miniShape) else if (docked) Modifier.fillMaxWidth().padding(horizontal = if (joined && f == 0f) 0.dp else Vela.md) else Modifier.fillMaxWidth()
            MiniPlayer(mini, nav.miniLook, miniShape, miniModifier)
            Spacer(Modifier.height(gap))
        }
        BottomNav(entries, selected, labelsVisible = nav.labels == NavLabels.ALWAYS, shrink = f, shape = barShape, onSelect = onSelect, bottomInset = docked, modifier = Modifier.fillMaxWidth())
    }
}

/** Once the bar has contracted to a pill, an attached mini player floats this far above it. */
private val SEPARATE_AFTER_SHRINK_GAP = 8.dp
