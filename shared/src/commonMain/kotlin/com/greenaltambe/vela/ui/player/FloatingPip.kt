package com.greenaltambe.vela.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.greenaltambe.vela.ui.theme.ExtraColors
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.core.settings.MiniPlayerStyle
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.player.PlayerState
import com.greenaltambe.vela.ui.platform.LocalVolumeSlider
import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The four places the floating window rests in. */
enum class PipCorner(val right: Boolean, val bottom: Boolean) {
    TOP_LEFT(false, false), TOP_RIGHT(true, false), BOTTOM_LEFT(false, true), BOTTOM_RIGHT(true, true),
}

/**
 * Where the floating video window is and how big. It rests in one [corner] (its anchor); while it is dragged it is [offX]/[offY] away from that
 * anchor, and when released it settles into the nearest corner with a spring. The width is the viewer's choice (resize handle); the height follows (16:9).
 * Everything here is in pixels of the area the player sheet fills; the sheet's layout reads it, so a drag never recomposes anything.
 */
@Stable
class FloatingPip(private val scope: CoroutineScope) {
    var corner by mutableStateOf(PipCorner.BOTTOM_RIGHT)
        private set
    private var widthPx by mutableFloatStateOf(0f)
    private var offX by mutableFloatStateOf(0f)
    private var offY by mutableFloatStateOf(0f)
    private var settle: Job? = null

    /** The window's width: the viewer's, else [defaultPx], always within [minPx]..[maxPx]. */
    fun width(minPx: Float, maxPx: Float, defaultPx: Float): Float = (if (widthPx > 0f) widthPx else defaultPx).coerceIn(minPx, maxOf(minPx, maxPx))

    private fun anchor(c: PipCorner, w: Float, h: Float, bounds: Rect) =
        Offset(if (c.right) bounds.right - w else bounds.left, if (c.bottom) bounds.bottom - h else bounds.top)

    /** Top-left of the window now, kept inside [area]. [bounds] is the region its corners rest in (the area less a margin). */
    fun position(w: Float, h: Float, bounds: Rect, area: Rect): Offset {
        val a = anchor(corner, w, h, bounds)
        return Offset((a.x + offX).coerceIn(area.left, maxOf(area.left, area.right - w)), (a.y + offY).coerceIn(area.top, maxOf(area.top, area.bottom - h)))
    }

    /** The finger holds the window so that its top-left is at [topLeft]. */
    fun moveTo(topLeft: Offset, w: Float, h: Float, bounds: Rect) {
        settle?.cancel()
        val a = anchor(corner, w, h, bounds)
        offX = topLeft.x - a.x; offY = topLeft.y - a.y
    }

    /** The finger lifted: settle into the corner nearest to where the window is heading (its centre plus a little of its velocity). */
    fun release(vx: Float, vy: Float, w: Float, h: Float, bounds: Rect, area: Rect) {
        val cur = position(w, h, bounds, area)
        val cx = cur.x + w / 2 + vx * 0.12f
        val cy = cur.y + h / 2 + vy * 0.12f
        val c = when {
            cx > (area.left + area.right) / 2 && cy > (area.top + area.bottom) / 2 -> PipCorner.BOTTOM_RIGHT
            cx > (area.left + area.right) / 2 -> PipCorner.TOP_RIGHT
            cy > (area.top + area.bottom) / 2 -> PipCorner.BOTTOM_LEFT
            else -> PipCorner.TOP_LEFT
        }
        val a = anchor(c, w, h, bounds)
        corner = c
        val sx = cur.x - a.x; val sy = cur.y - a.y
        offX = sx; offY = sy
        settle?.cancel()
        settle = scope.launch {
            animate(0f, 1f, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)) { t, _ -> offX = sx * (1f - t); offY = sy * (1f - t) }
            offX = 0f; offY = 0f
        }
    }

    fun resizeTo(w: Float) { settle?.cancel(); widthPx = w }
}

/**
 * Whether the minimised video is a floating window (true) or the bar (false): the viewer's per-device choice, and for Automatic the window on a
 * desktop (where the app has its own volume control, as a marker of a desktop) and the bar on a phone.
 */
@Composable
fun rememberFloatingMini(services: AppServices): Boolean {
    val revision by services.settings.revision.collectAsState()
    val desktop = LocalVolumeSlider.current
    return remember(revision, desktop) {
        when (services.settings.resolve(Settings.miniPlayerStyle)) {
            MiniPlayerStyle.FLOATING -> true
            MiniPlayerStyle.BAR -> false
            MiniPlayerStyle.AUTOMATIC -> desktop
        }
    }
}

/**
 * What is drawn over the floating window's picture: an outline, and on hover (or always on a touch screen, or while paused) its title, play / pause,
 * the full-page and close buttons, and the resize grip. A tap anywhere else opens the video page. The thin progress line is always there.
 * Dragging and resizing are handled by the sheet's own gesture (it needs the moving window's coordinates); this only draws.
 */
@Composable
fun FloatingPipOverlay(
    video: VideoSummary,
    corner: PipCorner,
    playing: Boolean,
    controller: State<PlayerState>,
    alphaNow: () -> Float,
    onExpand: () -> Unit,
    onToggle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val touchScreen = !LocalVolumeSlider.current
    val showControls = hovered || !playing || touchScreen
    val white = ExtraColors.onMedia
    val scrim = ExtraColors.mediaScrim
    Box(modifier.fillMaxSize().hoverable(hover).graphicsLayer { alpha = alphaNow() }) {
        // A tap on the picture opens the page.
        Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onExpand() }.testTag("pip.surface"))
        AnimatedVisibility(showControls, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(scrim.copy(alpha = 0.55f), Color.Transparent, scrim.copy(alpha = 0.35f))))) {
                Row(Modifier.fillMaxWidth().padding(start = Spacing.sm).align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
                    Text(video.title, Modifier.weight(1f).testTag("pip.title"), style = MaterialTheme.typography.labelLarge, color = white, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = onExpand, modifier = Modifier.size(36.dp).testTag("pip.expand")) { Icon(AppIcons.fullscreen, "Open the video page", Modifier.size(20.dp), tint = white) }
                    IconButton(onClick = onClose, modifier = Modifier.size(36.dp).testTag("pip.close")) { Icon(AppIcons.close, "Close player", Modifier.size(20.dp), tint = white) }
                }
                IconButton(
                    onClick = onToggle,
                    modifier = Modifier.align(Alignment.Center).size(52.dp).clip(CircleShape).background(scrim.copy(alpha = 0.5f)).testTag("pip.toggle"),
                ) { SwapIcon(if (playing) AppIcons.pause else AppIcons.play, if (playing) "Pause" else "Play", white) }
                ResizeGrip(corner, white, Modifier.align(gripAlignment(corner)).padding(4.dp))
            }
        }
        LinearProgressIndicator(
            progress = { controller.value.let { c -> if (c.durationMs > 0) (c.positionMs.toFloat() / c.durationMs).coerceIn(0f, 1f) else 0f } },
            modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomStart),
            color = MaterialTheme.colorScheme.primary, trackColor = white.copy(alpha = 0.25f),
        )
        Box(Modifier.fillMaxSize().border(1.dp, white.copy(alpha = 0.18f), MaterialTheme.shapes.medium))
    }
}

/** The grip sits in the corner opposite the one the window rests in, where dragging outwards makes it bigger. */
private fun gripAlignment(c: PipCorner): Alignment = when {
    c.right && c.bottom -> Alignment.TopStart
    c.right -> Alignment.BottomStart
    c.bottom -> Alignment.TopEnd
    else -> Alignment.BottomEnd
}

@Composable
private fun ResizeGrip(corner: PipCorner, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp).testTag("pip.grip")) {
        val s = size.width
        val w = 1.6.dp.toPx()
        // The grip is in the corner opposite the one the window rests in; strokes run across that corner.
        val gx = if (corner.right) 0f else 1f
        val gy = if (corner.bottom) 0f else 1f
        fun line(f: Float) {
            val a = Offset(kotlin.math.abs(gx - (1f - f)) * s, gy * s)
            val b = Offset(gx * s, kotlin.math.abs(gy - (1f - f)) * s)
            drawLine(tint.copy(alpha = 0.9f), a, b, w, StrokeCap.Round)
        }
        line(0.45f); line(0.85f)
    }
}
