package com.greenaltambe.vela.ui.components

import com.greenaltambe.vela.ui.theme.Accent
import com.greenaltambe.vela.ui.theme.ExtraColors
import com.greenaltambe.vela.ui.theme.Spacing
import com.greenaltambe.vela.ui.theme.LocalMotion
import com.greenaltambe.vela.ui.theme.LocalProgressStyle
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.greenaltambe.vela.core.settings.ProgressStyle
import com.greenaltambe.vela.extractor.Chapter
import com.greenaltambe.vela.library.currentChapter
import com.greenaltambe.vela.library.formatTimestamp
import kotlin.math.roundToInt

/**
 * Where a scrubber's track is inside its box. The track runs from one thumb radius in to one thumb radius from the
 * other edge, so a thumb at either end is wholly inside the box. Markers, the buffered span and the thumb all go through [xFor].
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
 * The app's seek / volume / slider control. Tap anywhere to jump; drag to scrub; segmented chapter gaps;
 * expands bold when dragged; desktop hover segment bolding with chapter name tooltip; and video frame
 * preview strip above the thumb.
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
    chapters: List<Chapter> = emptyList(),
    durationMs: Long = 0L,
    videoThumbnailUrl: String? = null,
    previewLabel: ((Float) -> String)? = null,
    description: String = "Seek",
    valueDescription: String? = null,
    style: ProgressStyle = LocalProgressStyle.current,
    trackColor: Color = MaterialTheme.colorScheme.outline,
    bufferedColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
    tickColor: Color = ExtraColors.chapterMarker,
    /** False while paused: the wave stands still instead of redrawing the bar every frame. */
    animate: Boolean = true,
) {
    var widthPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    var hoveredFraction by remember { mutableStateOf<Float?>(null) }

    // When dragging the entire seekbar expands bold smoothly
    val isDragging = dragging != null
    val boldScale by animateFloatAsState(
        if (isDragging) 1.55f else 1f,
        com.greenaltambe.vela.ui.look.VelaMotion.snappy(LocalMotion.current),
        label = "boldScale"
    )

    // The thumb grows while it is held, on a snappy spring
    val thumbGrow by animateFloatAsState(
        if (isDragging) 1.35f else 1f,
        com.greenaltambe.vela.ui.look.VelaMotion.snappy(LocalMotion.current),
        label = "thumbGrow"
    )

    val wavePhase = rememberWavePhase(animate && (style == ProgressStyle.WAVE || style == ProgressStyle.RIBBON || style == ProgressStyle.EXPRESSIVE))
    val metrics = style.metrics()
    val wake = Accent.colors
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val density = LocalDensity.current
    val thumbPx = with(density) { metrics.endInset.toPx() }
    val geometry = ScrubGeometry(widthPx, thumbPx)
    val shown = dragging ?: value.coerceIn(0f, 1f)

    Box(
        modifier
            .fillMaxWidth()
            .height(Spacing.minTouch)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .semantics {
                contentDescription = description
                valueDescription?.let { stateDescription = it }
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                if (enabled) setProgress { target -> onCommit(target.coerceIn(0f, 1f)); true }
            }
            .pointerInput(enabled, widthPx) {
                if (!enabled || widthPx <= 0f) return@pointerInput
                val g = ScrubGeometry(widthPx, thumbPx)
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        when (event.type) {
                            PointerEventType.Press -> {
                                val change = event.changes.firstOrNull()
                                if (change != null) {
                                    change.consume()
                                    val f = g.fractionAt(change.position.x)
                                    dragging = f
                                    onPreview(f)
                                }
                            }
                            PointerEventType.Move -> {
                                val change = event.changes.firstOrNull()
                                if (change != null) {
                                    val f = g.fractionAt(change.position.x)
                                    if (change.pressed || dragging != null) {
                                        change.consume()
                                        dragging = f
                                        onPreview(f)
                                    } else {
                                        hoveredFraction = f
                                    }
                                }
                            }
                            PointerEventType.Release -> {
                                val change = event.changes.firstOrNull()
                                change?.consume()
                                val target = dragging
                                if (target != null) {
                                    onCommit(target)
                                }
                                dragging = null
                                onPreview(null)
                            }
                            PointerEventType.Exit -> {
                                hoveredFraction = null
                                if (dragging != null) {
                                    val target = dragging
                                    if (target != null) {
                                        onCommit(target)
                                    }
                                    dragging = null
                                    onPreview(null)
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxWidth().height(Spacing.minTouch).testTag("scrubber.canvas")) {
            drawProgress(
                style = style,
                shown = shown,
                wake = wake,
                surface = surface,
                trackColor = trackColor,
                bufferedColor = bufferedColor,
                tickColor = tickColor,
                buffered = buffered,
                markers = markers,
                ticks = ticks,
                grow = thumbGrow,
                wavePhase = wavePhase.value,
                hoveredFraction = if (!isDragging) hoveredFraction else null,
                boldScale = boldScale,
            )
        }

        // Active preview position (dragging takes priority, desktop hover when not dragging)
        val activeFraction = dragging ?: hoveredFraction
        if (activeFraction != null && widthPx > 0f) {
            val activeMs = if (durationMs > 0) (activeFraction * durationMs).toLong() else 0L
            val activeChapter = if (chapters.isNotEmpty()) currentChapter(chapters, activeMs) else null
            val timeLabel = previewLabel?.invoke(activeFraction) ?: formatTimestamp(activeMs / 1000)
            val thumb = activeChapter?.thumbnailUrl ?: videoThumbnailUrl

            val cardW = if (thumb != null) 140.dp else 100.dp
            val cardWPx = with(density) { cardW.toPx() }
            val cardLeftPx = (geometry.xFor(activeFraction) - cardWPx / 2f).coerceIn(4f, (widthPx - cardWPx - 4f).coerceAtLeast(4f))
            val verticalOffset = if (thumb != null) (-112).dp else (-38).dp

            Box(
                modifier = Modifier
                    .wrapContentSize(unbounded = true)
                    .offset { IntOffset(cardLeftPx.roundToInt(), verticalOffset.roundToPx()) }
                    .width(cardW)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), MaterialTheme.shapes.medium)
                    .padding(4.dp)
                    .testTag("scrubber.preview"),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (thumb != null) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(78.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            AsyncImage(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Text(
                                timeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = ExtraColors.onMedia,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(2.dp)
                                    .background(ExtraColors.mediaBackground.copy(alpha = 0.75f), MaterialTheme.shapes.small)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    } else {
                        Text(
                            timeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    if (activeChapter != null) {
                        Text(
                            activeChapter.title,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 2.dp)
                        )
                    }
                }
            }
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
    ProgressStyle.EXPRESSIVE -> ProgressMetrics(4.dp, 0.dp, 8.dp)
}

/** The palette gradient sampled at [fraction] (0..1) of its way across. */
private fun sample(stops: List<Color>, fraction: Float): Color {
    if (stops.size == 1) return stops[0]
    val x = fraction.coerceIn(0f, 1f) * (stops.size - 1)
    val i = x.toInt().coerceAtMost(stops.size - 2)
    return androidx.compose.ui.graphics.lerp(stops[i], stops[i + 1], x - i)
}

/**
 * Draws a seek bar in [style] with small segmented chapters, drag bolding, desktop hover highlighting,
 * SponsorBlock markers, and buffered & played overlays.
 */
fun DrawScope.drawProgress(
    style: ProgressStyle,
    shown: Float,
    wake: List<Color>,
    surface: Color,
    trackColor: Color,
    bufferedColor: Color,
    tickColor: Color,
    buffered: Float? = null,
    markers: List<ScrubMarker> = emptyList(),
    ticks: List<Float> = emptyList(),
    grow: Float = 1f,
    wavePhase: Float = 0f,
    hoveredFraction: Float? = null,
    boldScale: Float = 1f,
) {
    val m = style.metrics()
    val g = ScrubGeometry(size.width, m.endInset.toPx())
    val cy = size.height / 2
    val brush = androidx.compose.ui.graphics.Brush.horizontalGradient(wake, startX = g.trackStart, endX = g.trackEnd.coerceAtLeast(g.trackStart + 1f))
    val xNow = g.xFor(shown)

    // Segment boundaries for chaptered seekbar (Item 6 & 8)
    val boundaries = if (ticks.isEmpty()) listOf(0f, 1f)
    else (listOf(0f) + ticks + listOf(1f)).filter { it in 0f..1f }.distinct().sorted()
    val segmentCount = boundaries.size - 1
    val gapPx = if (segmentCount > 1) 2.5f.dp.toPx() else 0f

    val hasThumb = m.thumb > 0.dp
    val expressive = style == ProgressStyle.EXPRESSIVE
    // Material's wavy bar: the played part is a wave, a small gap, then the flat rest of the track; the handle is a vertical pill in the gap.
    val handleGap = if (expressive) 6.dp.toPx() else 0f
    val baseTrackTh = m.track.toPx() * (if (hasThumb) 1f else grow) * boldScale

    if (style == ProgressStyle.WAVE) {
        val th = baseTrackTh
        val amp = 3.dp.toPx()
        val k = (2 * Math.PI / 22.dp.toPx()).toFloat()
        drawLine(trackColor, Offset(xNow, cy), Offset(g.trackEnd, cy), strokeWidth = th * 0.75f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        if (xNow > g.trackStart) {
            val path = androidx.compose.ui.graphics.Path()
            var x = g.trackStart
            while (x <= xNow) {
                val ease = minOf(1f, (x - g.trackStart) / 12.dp.toPx(), (xNow - x) / 12.dp.toPx()).coerceIn(0f, 1f)
                val y = cy + amp * ease * kotlin.math.sin(k * (x - g.trackStart) - wavePhase)
                if (x == g.trackStart) path.moveTo(x, y) else path.lineTo(x, y)
                x += 2f
            }
            path.lineTo(xNow, cy)
            drawPath(path, brush, style = Stroke(width = th, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        val handleH = 16.dp.toPx() * grow * boldScale
        drawLine(sample(wake, shown), Offset(xNow, cy - handleH / 2), Offset(xNow, cy + handleH / 2), strokeWidth = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        return
    }

    // 1. Base tracks and buffered overlays for each segment
    for (i in 0 until segmentCount) {
        val startF = boundaries[i]
        val endF = boundaries[i + 1]

        val x0 = maxOf(g.xFor(startF) + (if (i == 0) 0f else gapPx / 2f), if (expressive) xNow + handleGap else Float.NEGATIVE_INFINITY)
        val x1 = g.xFor(endF) - (if (i == segmentCount - 1) 0f else gapPx / 2f)
        val segW = (x1 - x0).coerceAtLeast(0f)
        if (segW <= 0f) continue

        // Distinct hover bolding on desktop
        val isHoveredSeg = hoveredFraction != null && hoveredFraction >= startF && hoveredFraction <= endF
        val segTh = if (isHoveredSeg) maxOf(baseTrackTh * 1.8f, baseTrackTh + 4.dp.toPx()) else baseTrackTh
        val round = CornerRadius(segTh / 2, segTh / 2)

        val segBg = if (isHoveredSeg) trackColor.copy(alpha = 0.85f) else trackColor
        drawRoundRect(segBg, Offset(x0, cy - segTh / 2), Size(segW, segTh), round)

        // Buffered progress within this segment
        if (buffered != null && buffered > startF) {
            val xBuf = minOf(x1, maxOf(x0, g.xFor(buffered) - (if (i == segmentCount - 1) 0f else gapPx / 2f)))
            if (xBuf > x0) {
                drawRoundRect(bufferedColor, Offset(x0, cy - segTh / 2), Size(xBuf - x0, segTh), round)
            }
        }
    }

    // 2. SponsorBlock markers across the entire track (clearly visible, never eaten by round corners)
    markers.forEach { mk ->
        val x0 = g.xFor(mk.start)
        val x1 = maxOf(g.xFor(mk.end), x0 + 3.dp.toPx()).coerceAtMost(g.trackEnd)
        if (x1 > x0) {
            drawRect(mk.color, Offset(x0, cy - baseTrackTh / 2), Size(x1 - x0, baseTrackTh))
        }
    }

    // 3. Played progress within each segment
    for (i in 0 until segmentCount) {
        val startF = boundaries[i]
        val endF = boundaries[i + 1]
        val x0 = g.xFor(startF) + (if (i == 0) 0f else gapPx / 2f)
        val x1 = g.xFor(endF) - (if (i == segmentCount - 1) 0f else gapPx / 2f)
        if (expressive && xNow - handleGap > x0) {
            val xEnd = minOf(x1, xNow - handleGap)
            val amp = 3.dp.toPx() * (if (boldScale > 1.01f) 0.6f else 1f)
            val k = (2 * Math.PI / 40.dp.toPx()).toFloat()
            val ramp = 14.dp.toPx()
            val xPlayedEnd = xNow - handleGap
            val path = androidx.compose.ui.graphics.Path()
            var x = x0
            while (true) {
                // The wave swells in at the very start of the bar and eases out just before the handle; it does not pinch at each chapter gap.
                val t = minOf(1f, (x - g.trackStart) / ramp, (xPlayedEnd - x) / ramp).coerceIn(0f, 1f)
                val ease = t * t * (3f - 2f * t)
                val y = cy + amp * ease * kotlin.math.sin(k * (x - g.trackStart) - wavePhase)
                if (x == x0) path.moveTo(x, y) else path.lineTo(x, y)
                if (x >= xEnd) break
                x = minOf(x + 2f, xEnd)
            }
            drawPath(path, brush, style = Stroke(width = baseTrackTh, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        } else if (!expressive && xNow > x0) {
            val isHoveredSeg = hoveredFraction != null && hoveredFraction >= startF && hoveredFraction <= endF
            val segTh = if (isHoveredSeg) maxOf(baseTrackTh * 1.8f, baseTrackTh + 4.dp.toPx()) else baseTrackTh
            val round = CornerRadius(segTh / 2, segTh / 2)
            val xPlay = minOf(x1, xNow)
            drawRoundRect(brush, Offset(x0, cy - segTh / 2), Size(xPlay - x0, segTh), round)
        }
    }

    if (style == ProgressStyle.RIBBON && xNow > g.trackStart) {
        val turn = wavePhase / (2f * Math.PI.toFloat())
        val played = xNow - g.trackStart
        val baseY = cy - baseTrackTh / 2 + 1.dp.toPx()
        val maxAmp = minOf(15.dp.toPx(), played * 0.3f)
        val pi = Math.PI.toFloat()
        fun strand(wavelength: Float, height: Float, width: Float, alpha: Float, laps: Int, phase: Float) {
            val k = 2f * pi / wavelength
            val a = laps * 2f * pi * turn + phase
            val path = androidx.compose.ui.graphics.Path()
            var x = g.trackStart
            while (true) {
                val t = ((x - g.trackStart) / played).coerceIn(0f, 1f)
                val window = Math.pow(kotlin.math.sin(pi * t).coerceAtLeast(0f).toDouble(), 0.7).toFloat()
                val wave = 0.5f + 0.5f * kotlin.math.sin(k * (x - g.trackStart) - a)
                val breathe = 0.9f + 0.1f * kotlin.math.sin(a + 1f)
                val y = baseY - maxAmp * height * breathe * wave * window
                if (x == g.trackStart) path.moveTo(x, y) else path.lineTo(x, y)
                if (x >= xNow) break
                x = minOf(x + 2f, xNow)
            }
            drawPath(path, brush, alpha = alpha, style = Stroke(width = width, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        strand(wavelength = 56.dp.toPx(), height = 1.0f, width = 1.7.dp.toPx(), alpha = 0.95f, laps = 1, phase = 0f)
        strand(wavelength = 40.dp.toPx(), height = 0.78f, width = 1.4.dp.toPx(), alpha = 0.7f, laps = 1, phase = 1.9f)
        strand(wavelength = 70.dp.toPx(), height = 0.6f, width = 1.2.dp.toPx(), alpha = 0.55f, laps = -1, phase = 3.4f)
        strand(wavelength = 47.dp.toPx(), height = 0.9f, width = 1.0.dp.toPx(), alpha = 0.4f, laps = 2, phase = 4.7f)
    }

    if (expressive) {
        val handleH = 20.dp.toPx() * grow * boldScale
        drawLine(sample(wake, shown), Offset(xNow, cy - handleH / 2), Offset(xNow, cy + handleH / 2), strokeWidth = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }

    if (hasThumb) {
        val r = m.thumb.toPx() * grow * boldScale
        drawCircle(sample(wake, shown), r, Offset(xNow, cy))
    }
}

/** The travelling phase of the Wave and Ribbon styles (radians). */
@Composable
fun rememberWavePhase(active: Boolean): androidx.compose.runtime.State<Float> {
    // A State read inside the draw block, so the 60 or 120 frames a second redraw the bar only and never recompose what holds it.
    val still = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    if (!active || LocalMotion.current.reduced || !com.greenaltambe.vela.ui.look.LocalAnimationsActive.current) return still
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "wave")
    val phase = t.animateFloat(0f, (2 * Math.PI).toFloat(), androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.LinearEasing)), label = "wavePhase")
    return phase
}
