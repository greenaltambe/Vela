package com.greenaltambe.vela.ui.components

import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import com.greenaltambe.vela.core.settings.ProgressStyle
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
    style: ProgressStyle = Vela.look.progress,
    trackColor: Color = Vela.colors.outline.toColor(),
    bufferedColor: Color = Vela.colors.onSurfaceVariant.toColor().copy(alpha = 0.5f),
    tickColor: Color = Vela.colors.chapterMarker.toColor(),
) {
    var widthPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    // The thumb grows while it is held, on a snappy spring (a quick fade when motion is reduced).
    val thumbGrow by androidx.compose.animation.core.animateFloatAsState(if (dragging != null) 1.3f else 1f, com.greenaltambe.vela.ui.look.VelaMotion.snappy(Vela.motion), label = "thumbGrow")
    val wavePhase = rememberWavePhase(style == ProgressStyle.WAVE || style == ProgressStyle.RIBBON)
    val metrics = style.metrics()
    val wake = Vela.look.accent.wake.map { it.toColor() }
    val surface = Vela.colors.surface.toColor()
    val thumbPx = with(androidx.compose.ui.platform.LocalDensity.current) { metrics.endInset.toPx() }
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
            drawProgress(style, shown, wake, surface, trackColor, bufferedColor, tickColor, buffered, markers, ticks, thumbGrow, wavePhase)
        }
        val label = dragging?.let { f -> previewLabel?.invoke(f) }
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                modifier = Modifier
                    .offset { IntOffset((geometry.xFor(dragging ?: 0f) - 24.dp.toPx()).roundToInt().coerceAtLeast(0), -(28.dp.roundToPx())) }
                    .background(Vela.colors.surfaceVariant.toColor(), Vela.smallShape)
                    .testTag("scrubber.preview"),
            )
        }
    }
}


/** Track thickness, marker radius (0 = none) and how far in from the box edge the track starts, for each [ProgressStyle]. */
class ProgressMetrics(val track: Dp, val thumb: Dp, val endInset: Dp)

fun ProgressStyle.metrics(): ProgressMetrics = when (this) {
    ProgressStyle.HAIRLINE -> ProgressMetrics(2.dp, 0.dp, 4.dp)
    ProgressStyle.HAIRLINE_DOT -> ProgressMetrics(2.dp, 7.dp, 8.dp)
    ProgressStyle.BOLD -> ProgressMetrics(6.dp, 0.dp, 4.dp)
    ProgressStyle.BOLD_DOT -> ProgressMetrics(6.dp, 9.dp, 10.dp)
    ProgressStyle.RIBBON -> ProgressMetrics(6.dp, 9.dp, 10.dp)
    ProgressStyle.WAVE -> ProgressMetrics(4.dp, 0.dp, 6.dp)
}

/** The palette gradient sampled at [fraction] (0..1) of its way across. */
private fun sample(stops: List<Color>, fraction: Float): Color {
    if (stops.size == 1) return stops[0]
    val x = fraction.coerceIn(0f, 1f) * (stops.size - 1)
    val i = x.toInt().coerceAtMost(stops.size - 2)
    return androidx.compose.ui.graphics.lerp(stops[i], stops[i + 1], x - i)
}

/**
 * Draws a seek bar in [style] across the whole width of this scope, [shown] (0..1) of the way played. The played part is the palette
 * gradient, laid across the full track so it is revealed as playback moves, not squashed into what has played.
 * Used by the real scrubber and by the style previews in Customize, so the two can never differ.
 */
fun DrawScope.drawProgress(
    style: ProgressStyle, shown: Float, wake: List<Color>, surface: Color, trackColor: Color, bufferedColor: Color, tickColor: Color,
    buffered: Float? = null, markers: List<ScrubMarker> = emptyList(), ticks: List<Float> = emptyList(), grow: Float = 1f, wavePhase: Float = 0f,
) {
    val m = style.metrics()
    val g = ScrubGeometry(size.width, m.endInset.toPx())
    val cy = size.height / 2
    val brush = androidx.compose.ui.graphics.Brush.horizontalGradient(wake, startX = g.trackStart, endX = g.trackEnd.coerceAtLeast(g.trackStart + 1f))
    val xNow = g.xFor(shown)
    if (style == ProgressStyle.WAVE) {
        // A wiggle up to the current position (it travels along the line), then a still line after it; a small upright handle marks the spot.
        val th = m.track.toPx(); val amp = 3.dp.toPx(); val k = (2 * Math.PI / 22.dp.toPx()).toFloat()
        drawLine(trackColor, Offset(xNow, cy), Offset(g.trackEnd, cy), strokeWidth = th * 0.75f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        if (xNow > g.trackStart) {
            val path = androidx.compose.ui.graphics.Path()
            var x = g.trackStart
            while (x <= xNow) {
                // Easing the wave to flat at both ends keeps its start tidy and lets it meet the handle on the line.
                val ease = minOf(1f, (x - g.trackStart) / 12.dp.toPx(), (xNow - x) / 12.dp.toPx()).coerceIn(0f, 1f)
                val y = cy + amp * ease * kotlin.math.sin(k * (x - g.trackStart) - wavePhase)
                if (x == g.trackStart) path.moveTo(x, y) else path.lineTo(x, y)
                x += 2f
            }
            path.lineTo(xNow, cy)
            drawPath(path, brush, style = androidx.compose.ui.graphics.drawscope.Stroke(width = th, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        val handleH = 16.dp.toPx() * grow
        drawLine(sample(wake, shown), Offset(xNow, cy - handleH / 2), Offset(xNow, cy + handleH / 2), strokeWidth = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        return
    }
    val hasThumb = m.thumb > 0.dp
    val th = m.track.toPx() * (if (hasThumb) 1f else grow)        // without a marker the track itself swells a little while held
    val round = CornerRadius(th / 2, th / 2)
    drawRoundRect(trackColor, Offset(g.trackStart, cy - th / 2), Size(g.trackWidth, th), round)
    buffered?.let { b -> drawRoundRect(bufferedColor, Offset(g.trackStart, cy - th / 2), Size((g.xFor(b) - g.trackStart).coerceAtLeast(0f), th), round) }
    markers.forEach { mk ->
        val x0 = g.xFor(mk.start)
        val x1 = maxOf(g.xFor(mk.end), x0 + 2.dp.toPx()).coerceAtMost(g.trackEnd)
        drawRect(mk.color, Offset(x0, cy - th / 2), Size(x1 - x0, th))
    }
    drawRoundRect(brush, Offset(g.trackStart, cy - th / 2), Size((xNow - g.trackStart).coerceAtLeast(0f), th), round)
    if (style == ProgressStyle.RIBBON && xNow > g.trackStart) {
        // Ribbons: a few thin strands lifting off the top of the watched part and settling back onto the bar at both ends. Each strand is a wave
        // that drifts along the bar (they differ in length, height, speed and direction, so they slip over and past each other like silk). The waves
        // keep their own length, so the further you have watched the more of them there are: one hump at the start, a rolling series later. Each
        // loops a whole number of its own waves per turn.
        val turn = wavePhase / (2f * Math.PI.toFloat())
        val played = xNow - g.trackStart
        val baseY = cy - th / 2 + 1.dp.toPx()                 // they come out of the bar's top edge
        val maxAmp = minOf(15.dp.toPx(), played * 0.3f)
        val pi = Math.PI.toFloat()
        fun strand(wavelength: Float, height: Float, width: Float, alpha: Float, laps: Int, phase: Float) {
            val k = 2f * pi / wavelength
            val a = laps * 2f * pi * turn + phase
            val path = androidx.compose.ui.graphics.Path()
            var x = g.trackStart
            while (true) {
                val t = ((x - g.trackStart) / played).coerceIn(0f, 1f)
                val window = Math.pow(kotlin.math.sin(pi * t).coerceAtLeast(0f).toDouble(), 0.7).toFloat()       // flat on the bar at both ends
                val wave = 0.5f + 0.5f * kotlin.math.sin(k * (x - g.trackStart) - a)
                // Swells at the speed of the wave itself, so it completes a whole number of cycles per loop: no jump where the loop restarts.
                val breathe = 0.9f + 0.1f * kotlin.math.sin(a + 1f)
                val y = baseY - maxAmp * height * breathe * wave * window
                if (x == g.trackStart) path.moveTo(x, y) else path.lineTo(x, y)
                if (x >= xNow) break
                x = minOf(x + 2f, xNow)
            }
            drawPath(path, brush, alpha = alpha, style = androidx.compose.ui.graphics.drawscope.Stroke(width = width, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        strand(wavelength = 56.dp.toPx(), height = 1.0f, width = 1.7.dp.toPx(), alpha = 0.95f, laps = 1, phase = 0f)
        strand(wavelength = 40.dp.toPx(), height = 0.78f, width = 1.4.dp.toPx(), alpha = 0.7f, laps = 1, phase = 1.9f)
        strand(wavelength = 70.dp.toPx(), height = 0.6f, width = 1.2.dp.toPx(), alpha = 0.55f, laps = -1, phase = 3.4f)
        strand(wavelength = 47.dp.toPx(), height = 0.9f, width = 1.0.dp.toPx(), alpha = 0.4f, laps = 2, phase = 4.7f)
    }
    ticks.forEach { t -> val x = g.xFor(t); drawRect(tickColor, Offset(x - 1.dp.toPx(), cy - th), Size(2.dp.toPx(), th * 2)) }
    if (hasThumb) {
        val r = m.thumb.toPx() * grow
        drawCircle(sample(wake, shown), r, Offset(xNow, cy))
    }
}

/** The travelling phase of the Wave and Ribbon styles (radians), looping; 0 (still) unless [active], while the app is on screen, and when motion is not reduced. */
@Composable
fun rememberWavePhase(active: Boolean): Float {
    if (!active || Vela.motion.reduced || !com.greenaltambe.vela.ui.look.LocalAnimationsActive.current) return 0f
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "wave")
    val phase by t.animateFloat(0f, (2 * Math.PI).toFloat(), androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.LinearEasing)), label = "wavePhase")
    return phase
}
