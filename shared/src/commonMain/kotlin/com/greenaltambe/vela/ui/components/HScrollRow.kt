package com.greenaltambe.vela.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.toColor
import com.greenaltambe.vela.ui.platform.LocalVolumeSlider
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A row that scrolls sideways. A finger does it everywhere; on desktop, where a mouse wheel only goes up and down, the row can also be dragged
 * with the mouse, follows a sideways wheel (or mouse wheel), and shows round arrows at its ends while there is more to see that way.
 */
@Composable
fun HScrollRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(Vela.sm),
    /** Room before the first and after the last item; with a row that runs through the screen's side gutters it is the gutter, so items start in line with the page. */
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp),
    /** A swipe settles on a card (the next one peeks in at the edge), instead of coasting to any position. */
    snap: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    val state = rememberLazyListState()
    val desktop = LocalVolumeSlider.current
    val scope = rememberCoroutineScope()
    Box(modifier) {
        LazyRow(
            Modifier.fillMaxWidth().then(
                if (!desktop) Modifier else Modifier
                    .pointerInput(state) { detectDragGestures { change, drag -> change.consume(); scope.launch { state.scrollBy(-drag.x) } } }
                    .pointerInput(state) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type != PointerEventType.Scroll) continue
                                val delta = event.changes.first().scrollDelta
                                val dx = if (abs(delta.x) > 0f) delta.x else delta.y
                                val shouldConsume = (dx > 0f && state.canScrollForward) || (dx < 0f && state.canScrollBackward)
                                if (shouldConsume) { scope.launch { state.scrollBy(dx * 60f) }; event.changes.forEach { it.consume() } }
                            }
                        }
                    },
            ),
            state = state, horizontalArrangement = horizontalArrangement, contentPadding = contentPadding, content = content,
            flingBehavior = if (snap) androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(state) else androidx.compose.foundation.gestures.ScrollableDefaults.flingBehavior(),
        )
        if (desktop) {
            fun page(forward: Boolean) = scope.launch { state.animateScrollBy((if (forward) 1 else -1) * state.layoutInfo.viewportSize.width * 0.8f) }
            if (state.canScrollBackward) RowArrow(Vela.icons.chevronLeft, "Scroll left", Modifier.align(Alignment.CenterStart)) { page(false) }
            if (state.canScrollForward) RowArrow(Vela.icons.chevronRight, "Scroll right", Modifier.align(Alignment.CenterEnd)) { page(true) }
        }
    }
}

/**
 * A horizontal scrollable Row container (e.g. for action pill buttons).
 * On Android, supports smooth touch drag and fling with edge fade indicators.
 * On Desktop, supports mouse dragging, direct mouse-wheel scrolling without trapping the wheel, and floating Left/Right chevron arrow buttons with gradient scrims.
 */
@Composable
fun HScrollableRow(
    modifier: Modifier = Modifier,
    rowModifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(Vela.sm),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val desktop = LocalVolumeSlider.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (!desktop) Modifier else Modifier
                        .pointerInput(scrollState) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                scope.launch { scrollState.scrollBy(-drag.x) }
                            }
                        }
                        .pointerInput(scrollState) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type != PointerEventType.Scroll) continue
                                    val delta = event.changes.first().scrollDelta
                                    val dx = if (abs(delta.x) > 0f) delta.x else delta.y
                                    val shouldConsume = (dx > 0f && scrollState.canScrollForward) || (dx < 0f && scrollState.canScrollBackward)
                                    if (shouldConsume) {
                                        scope.launch { scrollState.scrollBy(dx * 45f) }
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                )
                .horizontalScroll(scrollState)
                .padding(vertical = Vela.xs)
                .then(rowModifier),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment,
            content = content
        )

        if (desktop) {
            val stepPx = with(density) { 200.dp.toPx() }
            AnimatedVisibility(
                visible = scrollState.canScrollBackward,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Vela.colors.background.toColor(),
                                    Vela.colors.background.toColor().copy(alpha = 0.85f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(end = Vela.md, start = Vela.xs),
                    contentAlignment = Alignment.CenterStart
                ) {
                    RowArrow(Vela.icons.chevronLeft, "Scroll left") {
                        scope.launch { scrollState.animateScrollBy(-stepPx) }
                    }
                }
            }

            AnimatedVisibility(
                visible = scrollState.canScrollForward,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Vela.colors.background.toColor().copy(alpha = 0.85f),
                                    Vela.colors.background.toColor()
                                )
                            )
                        )
                        .padding(start = Vela.md, end = Vela.xs),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    RowArrow(Vela.icons.chevronRight, "Scroll right") {
                        scope.launch { scrollState.animateScrollBy(stepPx) }
                    }
                }
            }
        } else {
            AnimatedVisibility(
                visible = scrollState.canScrollBackward,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .width(20.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Vela.colors.background.toColor(),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            AnimatedVisibility(
                visible = scrollState.canScrollForward,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .width(20.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Vela.colors.background.toColor()
                                )
                            )
                        )
                )
            }
        }
    }
}

@Composable
fun RowArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.padding(horizontal = Vela.xs).size(36.dp).clip(Vela.pillShape).background(Vela.colors.surface.toColor().copy(alpha = 0.92f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = Vela.colors.onSurface.toColor())
    }
}
