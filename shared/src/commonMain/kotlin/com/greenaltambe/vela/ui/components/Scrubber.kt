package com.greenaltambe.vela.ui.components

import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.launch
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

/** One marker piece on the x axis (pixels). */
data class MarkerSpan(val x0: Float, val x1: Float, val color: Color)

/** The marker pieces left of the handle ([played]: they follow the wave) and right of it ([unplayed]: flat). */
data class MarkerSpans(val played: List<MarkerSpan>, val unplayed: List<MarkerSpan>)

/**
 * Where each SponsorBlock marker lies relative to the handle, in pixels. The played region is `trackStart .. xNow - handleGap`, the unplayed one
 * `xNow + handleGap .. trackEnd`; a marker partly in each is split at the handle. A played piece keeps its true width (the wave is drawn through
 * it); an unplayed piece is at least [minFlatWidth] wide so a tiny marker stays visible on the flat track. With [segments] (the chapter segments'
 * x ranges) every piece is also cut at the chapter gaps between them.
 */
fun markerSpans(
    markers: List<ScrubMarker>, g: ScrubGeometry, xNow: Float, handleGap: Float,
    minFlatWidth: Float = 0f, segments: List<ClosedFloatingPointRange<Float>>? = null,
): MarkerSpans {
    val playedEnd = xNow - handleGap
    val unplayedStart = xNow + handleGap
    val played = ArrayList<MarkerSpan>(); val unplayed = ArrayList<MarkerSpan>()
    fun add(into: MutableList<MarkerSpan>, a: Float, b: Float, color: Color) {
        if (b <= a) return
        if (segments == null) { into += MarkerSpan(a, b, color); return }
        for (s in segments) { val x0 = maxOf(a, s.start); val x1 = minOf(b, s.endInclusive); if (x1 > x0) into += MarkerSpan(x0, x1, color) }
    }
    for (mk in markers) {
        val x0 = g.xFor(mk.start)
        val x1 = g.xFor(mk.end)
        add(played, maxOf(x0, g.trackStart), minOf(x1, playedEnd), mk.color)
        // Tiny markers get their minimum width only on the flat part, grown to the right and kept inside the track.
        val flatEnd = maxOf(x1, x0 + minFlatWidth).coerceAtMost(g.trackEnd)
        add(unplayed, maxOf(x0, unplayedStart), flatEnd, mk.color)
    }
    return MarkerSpans(played, unplayed)
}

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
    /** The video's storyboard: when there is one, the preview card shows the picture for the position under the finger. */
    storyboard: com.greenaltambe.vela.extractor.Storyboard? = null,
    previewLabel: ((Float) -> String)? = null,
    description: String = "Seek",
    valueDescription: String? = null,
    style: ProgressStyle = LocalProgressStyle.current,
    trackColor: Color = MaterialTheme.colorScheme.outline,
    bufferedColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
    tickColor: Color = ExtraColors.chapterMarker,
    /** False while paused: the wave stands still instead of redrawing the bar every frame. */
    animate: Boolean = true,
    /**
     * Whether a press alone jumps to the finger and commits on release. Null decides per touch by pointer type: a mouse or stylus click seeks (desktop
     * users expect it), a finger does not: a touch only scrubs after it has moved sideways past the touch slop, so a brush or a tap never seeks.
     */
    tapToSeek: Boolean? = null,
    /** Whether this bar takes part in the player's gesture lock (see [PlayerGestureLock]); the desktop volume bar does not. */
    lockGestures: Boolean = true,
) {
    var widthPx by remember { mutableFloatStateOf(0f) }
    // The position the bar is being dragged to (absolute fraction); while [gliding] the shown position travels from [glideFrom] to it on a spring.
    var dragging by remember { mutableStateOf<Float?>(null) }
    var hoveredFraction by remember { mutableStateOf<Float?>(null) }
    var armed by remember { mutableStateOf(false) }
    var gliding by remember { mutableStateOf(false) }
    var glideFrom by remember { mutableFloatStateOf(0f) }
    val glide = remember { androidx.compose.animation.core.Animatable(1f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val valueNow by androidx.compose.runtime.rememberUpdatedState(value.coerceIn(0f, 1f))
    val lock = if (lockGestures) LocalPlayerGestureLock.current else null

    // When dragging the entire seekbar expands bold smoothly
    val isDragging = dragging != null
    val boldScale by animateFloatAsState(
        if (isDragging) 1.55f else 1f,
        com.greenaltambe.vela.ui.look.VelaMotion.snappy(LocalMotion.current),
        label = "boldScale"
    )

    // The thumb grows a little when a finger is down on the bar (armed) and more while it is held and moving, on a snappy spring
    val thumbGrow by animateFloatAsState(
        if (isDragging) 1.35f else if (armed) 1.15f else 1f,
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
    val shown = dragging?.let { d -> if (gliding) androidx.compose.ui.util.lerp(glideFrom, d, glide.value) else d } ?: value.coerceIn(0f, 1f)
    androidx.compose.runtime.DisposableEffect(lock) { onDispose { if (lock?.bar != null) lock.bar = null } }

    Box(
        modifier
            .fillMaxWidth()
            .height(Spacing.minTouch)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .onGloballyPositioned { c -> if (lock != null) lock.bar = androidx.compose.ui.geometry.Rect(c.positionInRoot(), androidx.compose.ui.geometry.Size(c.size.width.toFloat(), c.size.height.toFloat())) }
            // Hovering (a mouse over the bar) shows the time and chapter under it; it never consumes anything.
            .pointerInput(enabled) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull() ?: continue
                        when (event.type) {
                            PointerEventType.Move -> if (!change.pressed) hoveredFraction = ScrubGeometry(widthPx, thumbPx).fractionAt(change.position.x)
                            PointerEventType.Exit -> hoveredFraction = null
                            else -> {}
                        }
                    }
                }
            }
            .semantics {
                contentDescription = description
                valueDescription?.let { stateDescription = it }
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                if (enabled) setProgress { target -> onCommit(target.coerceIn(0f, 1f)); true }
            }
            .pointerInput(enabled, widthPx, thumbPx, tapToSeek) {
                if (!enabled || widthPx <= 0f) return@pointerInput
                val g = ScrubGeometry(widthPx, thumbPx)
                val grabPx = maxOf(24.dp.toPx(), thumbPx)
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val seekOnPress = tapToSeek ?: (down.type != PointerType.Touch)
                    lock?.begin()
                    down.consume()
                    var lastUptime = down.uptimeMillis
                    var previewed = false
                    var onComplete: (() -> Unit)? = null
                    try {
                        if (seekOnPress) {
                            // Mouse / stylus: the bar jumps to the pointer at once and follows it; letting go seeks.
                            var f = g.fractionAt(down.position.x)
                            gliding = false; dragging = f; previewed = true; onPreview(f)
                            val completed = drag(down.id) { ch ->
                                ch.consume(); lastUptime = ch.uptimeMillis
                                f = g.fractionAt(ch.position.x); dragging = f; onPreview(f)
                            }
                            if (completed) onComplete = { onCommit(f) }
                        } else {
                            // Finger: pressing only arms the bar. Scrubbing starts once the finger has moved sideways past the touch slop.
                            armed = true
                            val startValue = valueNow
                            val grabbed = kotlin.math.abs(down.position.x - g.xFor(startValue)) <= grabPx
                            val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { ch, _ -> ch.consume() }
                            if (slop != null) {
                                armed = false
                                val anchorX = slop.position.x
                                var f = startValue
                                fun shownNow(): Float = if (gliding) androidx.compose.ui.util.lerp(glideFrom, f, glide.value) else f
                                if (grabbed) {
                                    // The handle follows the finger by relative movement: no jump when it is grabbed.
                                    gliding = false
                                } else {
                                    // Elsewhere on the bar the handle glides to the finger on a short spring, then follows it.
                                    f = g.fractionAt(anchorX)
                                    glideFrom = startValue; gliding = true
                                    scope.launch { glide.snapTo(0f); glide.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 500f)) }
                                }
                                dragging = f; previewed = true; onPreview(shownNow())
                                val completed = horizontalDrag(slop.id) { ch ->
                                    ch.consume(); lastUptime = ch.uptimeMillis
                                    // Only x counts: vertical drift moves nothing.
                                    f = if (grabbed) g.fractionAt(g.xFor(startValue) + (ch.position.x - anchorX)) else g.fractionAt(ch.position.x)
                                    dragging = f; onPreview(shownNow())
                                }
                                if (completed) onComplete = { onCommit(f) }
                            }
                        }
                    } finally {
                        armed = false; dragging = null; gliding = false
                        onComplete?.invoke()
                        if (previewed) onPreview(null)
                        lock?.end(lastUptime)
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
            val frame = storyboard?.frameAt(activeMs)
            // A picture area is shown when there is a frame or a still to put in it (a video opened from a link has no poster).
            val hasPicture = thumb != null || frame != null

            val cardW = if (hasPicture) 140.dp else 100.dp
            val cardWPx = with(density) { cardW.toPx() }
            val cardLeftPx = (geometry.xFor(activeFraction) - cardWPx / 2f).coerceIn(4f, (widthPx - cardWPx - 4f).coerceAtLeast(4f))
            val verticalOffset = if (hasPicture) (-112).dp else (-38).dp

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
                    if (hasPicture) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(78.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            // The poster stands in until the sheet holding this frame has loaded; the frame is drawn over it.
                            if (thumb != null) AsyncImage(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            if (storyboard != null && frame != null) StoryboardPicture(storyboard, frame, Modifier.fillMaxSize())
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
    val round = androidx.compose.ui.graphics.StrokeCap.Round
    // The pieces of the SponsorBlock markers left of the handle (drawn as the wave's own stroke in the marker colour) and right of it (flat).
    val chapterRanges = (0 until segmentCount).map { i -> (g.xFor(boundaries[i]) + (if (i == 0) 0f else gapPx / 2f))..(g.xFor(boundaries[i + 1]) - (if (i == segmentCount - 1) 0f else gapPx / 2f)) }
    val spans = markerSpans(markers, g, xNow, handleGap, minFlatWidth = 3.dp.toPx(), segments = if (expressive) chapterRanges else null)
    /** The same stroke again in a marker's colour, clipped to its played span, so the coloured piece rises and falls with the wave. */
    fun DrawScope.tintPath(path: androidx.compose.ui.graphics.Path, width: Float, from: Float = Float.NEGATIVE_INFINITY, to: Float = Float.POSITIVE_INFINITY, alpha: Float = 1f) {
        for (s in spans.played) {
            val l = maxOf(s.x0, from); val r = minOf(s.x1, to)
            if (r <= l) continue
            clipRect(left = l, top = 0f, right = r, bottom = size.height) {
                drawPath(path, s.color, alpha = alpha, style = Stroke(width = width, cap = round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
    }
    // Unplayed pieces are 15 % fainter, so what has already passed reads without hiding the category's colour.
    fun lighter(c: Color) = c.copy(alpha = c.alpha * 0.85f)

    if (style == ProgressStyle.WAVE) {
        val th = baseTrackTh
        val amp = 3.dp.toPx()
        val k = (2 * Math.PI / 22.dp.toPx()).toFloat()
        drawLine(trackColor, Offset(xNow, cy), Offset(g.trackEnd, cy), strokeWidth = th * 0.75f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        // Markers on the unplayed, flat part (the played ones are the wave itself, below).
        for (s in spans.unplayed) drawRoundRect(lighter(s.color), Offset(s.x0, cy - th * 0.75f / 2), Size(s.x1 - s.x0, th * 0.75f), CornerRadius(th * 0.75f / 2, th * 0.75f / 2))
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
            tintPath(path, th)
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

    // 2. SponsorBlock markers. Expressive: only the flat part right of the handle gap, with the track's rounded ends (the played part is the wave
    // itself, tinted below). The other styles: across the entire track (clearly visible, never eaten by round corners).
    if (expressive) {
        for (s in spans.unplayed) drawRoundRect(lighter(s.color), Offset(s.x0, cy - baseTrackTh / 2), Size(s.x1 - s.x0, baseTrackTh), CornerRadius(baseTrackTh / 2, baseTrackTh / 2))
    } else markers.forEach { mk ->
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
            tintPath(path, baseTrackTh, from = x0, to = x1)
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
            tintPath(path, width, alpha = alpha)
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

/**
 * One picture of a storyboard: the page (a sprite sheet) is loaded at its own size and only the cell for [frame] is drawn, scaled to fill this box.
 * Nothing is drawn until the page has loaded, so whatever lies under it (the poster) shows meanwhile.
 */
@Composable
internal fun StoryboardPicture(storyboard: com.greenaltambe.vela.extractor.Storyboard, frame: com.greenaltambe.vela.extractor.StoryboardFrame, modifier: Modifier = Modifier) {
    val request = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current).data(frame.url).size(coil3.size.Size.ORIGINAL).build()
    val painter = coil3.compose.rememberAsyncImagePainter(request)
    val state by painter.state.collectAsState()
    Canvas(modifier.clipToBounds()) {
        if (state !is coil3.compose.AsyncImagePainter.State.Success) return@Canvas
        // While a new sheet replaces the old one the painter can report no size at all (NaN); drawing with that crashed the app, so anything not a usable number draws nothing.
        val sheet = painter.intrinsicSize
        val place = storyboardPlacement(size.width, size.height, storyboard.frameWidth, storyboard.frameHeight, frame.column, frame.row, sheet.width, sheet.height) ?: return@Canvas
        translate(place.left, place.top) { with(painter) { draw(androidx.compose.ui.geometry.Size(place.width, place.height)) } }
    }
}

/** Where to draw a storyboard sheet so that one cell fills a canvas: the sheet's top-left corner and its drawn size. Null when any number is unusable (a sheet still loading reports NaN). */
internal data class StoryboardPlacement(val left: Float, val top: Float, val width: Float, val height: Float)

internal fun storyboardPlacement(canvasW: Float, canvasH: Float, frameW: Int, frameH: Int, column: Int, row: Int, sheetW: Float, sheetH: Float): StoryboardPlacement? {
    val numbers = floatArrayOf(canvasW, canvasH, sheetW, sheetH)
    if (numbers.any { !it.isFinite() } || canvasW <= 0f || canvasH <= 0f || sheetW <= 0f || sheetH <= 0f || frameW <= 0 || frameH <= 0 || column < 0 || row < 0) return null
    val fw = frameW.toFloat(); val fh = frameH.toFloat()
    val scale = maxOf(canvasW / fw, canvasH / fh)
    return StoryboardPlacement((canvasW - fw * scale) / 2f - column * fw * scale, (canvasH - fh * scale) / 2f - row * fh * scale, sheetW * scale, sheetH * scale)
}
