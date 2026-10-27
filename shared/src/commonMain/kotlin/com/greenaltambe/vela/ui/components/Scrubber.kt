package com.greenaltambe.vela.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.toColor
import kotlin.math.roundToInt

/**
 * Where a scrubber's track is inside its box. The track runs from one thumb radius in to one thumb radius from the
 * other edge, so a thumb at either end is wholly inside the box (the Material slider drew a stray bar there). Markers,
 * the buffered span and the thumb all go through [xFor], so they line up exactly.
 */
class ScrubGeometry(val widthPx: Float, val thumbRadiusPx: Float) {
    val trackStart: Float get() = thumbRadiusPx
    val trackEnd: Float get() = (widthPx - thumbRadiusPx).coerceAtLeast(trackStart)
    val trackWidth: Float get() = trackEnd - trackStart

    fun xFor(fraction: Float): Float = trackStart + fraction.coerceIn(0f, 1f) * trackWidth
    fun fractionAt(x: Float): Float = if (trackWidth <= 0f) 0f else ((x - trackStart) / trackWidth).coerceIn(0f, 1f)
}

/** A coloured span of the track, in fractions of the whole. */
class ScrubMarker(val start: Float, val end: Float, val color: Color)

/**
 * The app's own seek / volume / slider control. Tap anywhere to jump; drag to scrub (the value is only reported as a
 * preview until release); a 48 dp tall touch target around a thin track; a time bubble above the thumb while dragging
 * when [previewLabel] is given; accessibility semantics with a value and a set-progress action.
 */
@Composable
fun Scrubber(
    value: Float,
    onPreview: (Float?) -> Unit,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buffered: Float? = null,
    markers: List<ScrubMarker> = emptyList(),
    ticks: List<Float> = emptyList(),
    previewLabel: ((Float) -> String)? = null,
    description: String = "Seek",
    valueDescription: String? = null,
    thumbRadius: Dp = 8.dp,
    trackHeight: Dp = 4.dp,
    playedColor: Color = Vela.colors.primary.toColor(),
    trackColor: Color = Vela.colors.outline.toColor(),
    bufferedColor: Color = Vela.colors.onSurfaceVariant.toColor().copy(alpha = 0.5f),
    tickColor: Color = Vela.colors.chapterMarker.toColor(),
) {
    var widthPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val thumbPx = with(androidx.compose.ui.platform.LocalDensity.current) { thumbRadius.toPx() }
    val geometry = ScrubGeometry(widthPx, thumbPx)
    val shown = dragging ?: value.coerceIn(0f, 1f)

    Box(
        modifier
            .fillMaxWidth()
            .height(Vela.minTouch)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .semantics {
                contentDescription = description
                valueDescription?.let { stateDescription = it }
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                if (enabled) setProgress { target -> onCommit(target.coerceIn(0f, 1f)); true }
            }
            .pointerInput(enabled, widthPx) {
                if (!enabled) return@pointerInput
                detectTapGestures(onTap = { offset -> onCommit(ScrubGeometry(widthPx, thumbPx).fractionAt(offset.x)) })
            }
            .pointerInput(enabled, widthPx) {
                if (!enabled) return@pointerInput
                val g = ScrubGeometry(widthPx, thumbPx)
                detectDragGestures(
                    onDragStart = { offset -> val f = g.fractionAt(offset.x); dragging = f; onPreview(f) },
                    onDrag = { change, _ -> change.consume(); val f = g.fractionAt(change.position.x); dragging = f; onPreview(f) },
                    onDragEnd = { dragging?.let(onCommit); dragging = null; onPreview(null) },
                    onDragCancel = { dragging = null; onPreview(null) },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxWidth().height(Vela.minTouch).testTag("scrubber.canvas")) {
            val g = ScrubGeometry(size.width, thumbRadius.toPx())
            val cy = size.height / 2
            val th = trackHeight.toPx()
            val round = CornerRadius(th / 2, th / 2)
            drawRoundRect(trackColor, Offset(g.trackStart, cy - th / 2), Size(g.trackWidth, th), round)
            buffered?.let { b -> drawRoundRect(bufferedColor, Offset(g.trackStart, cy - th / 2), Size((g.xFor(b) - g.trackStart).coerceAtLeast(0f), th), round) }
            markers.forEach { m ->
                val x0 = g.xFor(m.start)
                val x1 = maxOf(g.xFor(m.end), x0 + 2.dp.toPx()).coerceAtMost(g.trackEnd)
                drawRect(m.color, Offset(x0, cy - th / 2), Size(x1 - x0, th))
            }
            drawRoundRect(playedColor, Offset(g.trackStart, cy - th / 2), Size((g.xFor(shown) - g.trackStart).coerceAtLeast(0f), th), round)
            ticks.forEach { t -> val x = g.xFor(t); drawRect(tickColor, Offset(x - 1.dp.toPx(), cy - th), Size(2.dp.toPx(), th * 2)) }
            drawCircle(playedColor, if (dragging != null) thumbRadius.toPx() * 1.3f else thumbRadius.toPx(), Offset(g.xFor(shown), cy))
        }
        val label = dragging?.let { f -> previewLabel?.invoke(f) }
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                modifier = Modifier
                    .offset { IntOffset((geometry.xFor(dragging ?: 0f) - 24.dp.toPx()).roundToInt().coerceAtLeast(0), -(28.dp.roundToPx())) }
                    .background(Vela.colors.surfaceVariant.toColor(), RoundedCornerShape(6.dp))
                    .testTag("scrubber.preview"),
            )
        }
    }
}
