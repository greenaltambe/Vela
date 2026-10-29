package com.greenaltambe.vela.ui.player

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker

/**
 * Vertical swipes on the picture. [onDelta] gets each step (positive = down); [onEnd] gets the net distance and the release velocity
 * (pixels, pixels per second; negative = up) when the finger lifts, or (0, 0) if the gesture is cancelled. Only gestures that start where [startsOn] says count.
 * Put this on a node that is NOT itself transformed by the drag: positions are measured in its own space, so a page that shrinks under the finger would fight it. Horizontal drags (the seek bar)
 * and taps are left alone: a child that consumed the drag never reaches this.
 */
@Composable
fun Modifier.playerVerticalSwipe(
    startsOn: (androidx.compose.ui.geometry.Offset) -> Boolean = { true },
    onDelta: (Float) -> Unit, onEnd: (total: Float, velocityY: Float) -> Unit,
): Modifier {
    val accepts by rememberUpdatedState(startsOn)
    val delta by rememberUpdatedState(onDelta)
    val end by rememberUpdatedState(onEnd)
    return this.pointerInput(Unit) {
        var total = 0f
        var active = true
        val tracker = VelocityTracker()
        detectVerticalDragGestures(
            onDragStart = { at -> total = 0f; active = accepts(at); tracker.resetTracking() },
            onVerticalDrag = { change, dy -> if (active) { total += dy; tracker.addPosition(change.uptimeMillis, change.position); change.consume(); delta(dy) } },
            onDragEnd = { if (active) end(total, tracker.calculateVelocity().y) },
            onDragCancel = { if (active) end(0f, 0f) },
        )
    }
}
