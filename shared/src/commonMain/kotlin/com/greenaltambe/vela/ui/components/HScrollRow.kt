package com.greenaltambe.vela.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.toColor
import com.greenaltambe.vela.ui.platform.LocalVolumeSlider
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A row that scrolls sideways. A finger does it everywhere; on desktop, where a mouse wheel only goes up and down, the row can also be dragged
 * with the mouse, follows a sideways wheel (or Shift + wheel), and shows round arrows at its ends while there is more to see that way.
 */
@Composable
fun HScrollRow(modifier: Modifier = Modifier, horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(Vela.sm), content: LazyListScope.() -> Unit) {
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
                                val dx = if (abs(delta.x) > 0f) delta.x else if (event.keyboardModifiers.isShiftPressed) delta.y else 0f
                                if (dx != 0f) { scope.launch { state.scrollBy(dx * 60f) }; event.changes.forEach { it.consume() } }
                            }
                        }
                    },
            ),
            state = state, horizontalArrangement = horizontalArrangement, content = content,
        )
        if (desktop) {
            fun page(forward: Boolean) = scope.launch { state.animateScrollBy((if (forward) 1 else -1) * state.layoutInfo.viewportSize.width * 0.8f) }
            if (state.canScrollBackward) RowArrow(Vela.icons.chevronLeft, "Scroll left", Modifier.align(Alignment.CenterStart)) { page(false) }
            if (state.canScrollForward) RowArrow(Vela.icons.chevronRight, "Scroll right", Modifier.align(Alignment.CenterEnd)) { page(true) }
        }
    }
}

@Composable
private fun RowArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.padding(horizontal = Vela.xs).size(36.dp).clip(Vela.pillShape).background(Vela.colors.surface.toColor().copy(alpha = 0.92f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = Vela.colors.onSurface.toColor())
    }
}
