package com.greenaltambe.vela.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.core.settings.BarStyle
import com.greenaltambe.vela.core.settings.MiniLook
import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.ui.components.PIP_WIDTH
import com.greenaltambe.vela.ui.look.ComponentIds
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.toColor
import com.greenaltambe.vela.ui.nav.LocalPipPosition
import com.greenaltambe.vela.ui.nav.NavConfig
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.platform.LocalWindowControls
import com.greenaltambe.vela.ui.platform.PlatformBackHandler
import com.greenaltambe.vela.ui.screens.VideoScreen
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Unified, persistent video player overlay (Sheet).
 * Coordinates seamless GPU morphing between:
 * - Collapsed (Miniplayer bar or PiP card, progress == 0f)
 * - Expanded (Full video screen, progress == 1f)
 */
@Composable
fun PlayerSheet(
    services: AppServices,
    navigator: Navigator,
    sheetState: PlayerSheetState,
    modifier: Modifier = Modifier,
) {
    val video = sheetState.currentVideo ?: return
    val host = services.player ?: return
    val controllerState by host.controllerState.collectAsState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val windowControls = LocalWindowControls.current
    val windowInfo = LocalWindowInfo.current
    val isFullscreen = windowControls?.isFullscreen == true

    // Intercept back button when sheet is expanded
    PlatformBackHandler(enabled = sheetState.isExpanded && !isFullscreen) {
        sheetState.collapse()
    }

    val revision by services.settings.revision.collectAsState()
    val nav = remember(revision) { NavConfig.of(services.settings) }
    val isPip = nav.miniLook == MiniLook.PIP
    val pipPos = LocalPipPosition.current

    val winW = windowInfo.containerSize.width.toFloat().coerceAtLeast(100f)
    val winH = windowInfo.containerSize.height.toFloat().coerceAtLeast(100f)
    androidx.compose.runtime.SideEffect { sheetState.windowHeightPx = winH }
    val bottomInsetPx = WindowInsets.safeDrawing.getBottom(density).toFloat()
    val topInsetPx = WindowInsets.safeDrawing.getTop(density).toFloat()

    val p = sheetState.progress.coerceIn(0f, 1f)
    val fraction = 1f - p

    val cardHeightPx = with(density) { 56.dp.toPx() }
    val thumbWidthDp = 64.dp
    val thumbWidthPx = with(density) { thumbWidthDp.toPx() }
    val bottomClearPx = bottomInsetPx + with(density) { (if (nav.barStyle == BarStyle.DOCKED) 64.dp else 72.dp).toPx() }
    val marginPx = with(density) { (if (nav.barStyle == BarStyle.DOCKED) 0.dp else Vela.md).toPx() }

    val sizeClass = com.greenaltambe.vela.ui.layout.LocalWindowSize.current
    val rail = sizeClass != com.greenaltambe.vela.ui.layout.WindowSizeClass.COMPACT && !isFullscreen
    val railWidthDp = if (!rail) 0.dp else if (nav.railCollapsed) 72.dp else 176.dp
    val railWidthPx = with(density) { railWidthDp.toPx() }
    val contentW = winW - railWidthPx

    val cardBottom = winH - bottomClearPx
    val cardTop = cardBottom - cardHeightPx
    val cardLeft = railWidthPx + marginPx
    val cardWidth = contentW - marginPx * 2

    // Dynamic morphing sheet bounds:
    val targetExpandedX = if (rail) railWidthPx else 0f
    val targetExpandedW = if (rail) contentW else winW

    val sheetX = if (isPip) {
        val pipW = with(density) { PIP_WIDTH.toPx() }
        val at = pipPos?.pos ?: Offset(winW - pipW - marginPx, cardBottom - pipW * 9f / 16f)
        at.x * fraction + targetExpandedX * p
    } else {
        cardLeft * fraction + targetExpandedX * p
    }

    val sheetY = if (isPip) {
        val pipW = with(density) { PIP_WIDTH.toPx() }
        val pipH = pipW * 9f / 16f
        val at = pipPos?.pos ?: Offset(winW - pipW - marginPx, cardBottom - pipH)
        at.y * fraction
    } else {
        cardTop * fraction
    }

    val sheetW = if (isPip) {
        val pipW = with(density) { PIP_WIDTH.toPx() }
        pipW + (targetExpandedW - pipW) * p
    } else {
        cardWidth + (targetExpandedW - cardWidth) * p
    }

    val sheetH = if (isPip) {
        val pipW = with(density) { PIP_WIDTH.toPx() }
        val pipH = pipW * 9f / 16f
        pipH + (winH - pipH) * p
    } else {
        cardHeightPx + (winH - cardHeightPx) * p
    }

    val cornerRadiusDp = if (isPip) 16f * fraction else Vela.look.radii.largeDp * fraction
    val sheetShape = RoundedCornerShape(cornerRadiusDp.dp)

    // Gesture handler attached ONLY to the sheet container (never to full screen when collapsed)
    val gestureModifier = Modifier.pointerInput(winW, winH, isPip) {
        var startProgress = 0f
        var startWindowY = 0f
        var isDragging = false
        val tracker = VelocityTracker()

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val currentP = sheetState.progress

            val canDrag = if (currentP > 0.6f) {
                // Expanded: drag down from the video player header area
                down.position.y <= (topInsetPx + winW * 9f / 16f + with(density) { 32.dp.toPx() })
            } else if (!isPip) {
                // Collapsed card: anywhere on the card
                true
            } else {
                false
            }

            if (!canDrag || sheetState.slideY > 50f) return@awaitEachGesture

            fun currentSheetY(): Float = if (isPip) {
                val pipW = with(density) { PIP_WIDTH.toPx() }
                val at = pipPos?.pos ?: Offset(winW - pipW - marginPx, cardBottom - pipW * 9f / 16f)
                at.y * (1f - sheetState.progress)
            } else {
                cardTop * (1f - sheetState.progress)
            }

            startProgress = currentP
            startWindowY = down.position.y + currentSheetY()
            isDragging = false
            tracker.resetTracking()
            tracker.addPosition(down.uptimeMillis, Offset(down.position.x, startWindowY))

            // Moves slightly faster than fingers (1 / 0.82 = 1.22x finger speed)
            val dragRangePx = (cardTop - topInsetPx).coerceAtLeast(100f) * 0.82f
            val touchSlop = with(density) { 8.dp.toPx() }

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                val nowWindowY = change.position.y + currentSheetY()

                if (!change.pressed) {
                    if (isDragging) {
                        val vY = tracker.calculateVelocity().y
                        sheetState.settle(vY)
                    }
                    break
                }

                tracker.addPosition(change.uptimeMillis, Offset(change.position.x, nowWindowY))
                val totalDragY = nowWindowY - startWindowY

                if (!isDragging && abs(totalDragY) > touchSlop) {
                    isDragging = true
                }

                if (isDragging) {
                    change.consume()
                    val newP = (startProgress - totalDragY / dragRangePx).coerceIn(0f, 1f)
                    scope.launch { sheetState.snapProgress(newP) }
                }
            }
        }
    }

    // Outer Box: pure transparent wrapper with NO touch consumption
    Box(modifier.fillMaxSize()) {
        // Morphing Sheet Box: exactly sized and positioned, only absorbs touches within its bounds
        Box(
            Modifier
                .offset { IntOffset(sheetX.roundToInt(), (sheetY + sheetState.slideY).roundToInt()) }
                .size(with(density) { sheetW.toDp() }, with(density) { sheetH.toDp() })
                .shadow(if (fraction > 0.05f) 8.dp else 0.dp, sheetShape)
                .clip(sheetShape)
                .background(if (p > 0.4f) Vela.colors.background.toColor() else Vela.colors.surfaceVariant.toColor())
                .then(gestureModifier)
        ) {
            // LAYER 0: The persistent VideoScreen with the live video surface
            VideoScreen(
                services = services,
                navigator = navigator,
                video = video,
                startSeconds = sheetState.startSeconds,
                sheetProgress = p,
                onBack = { sheetState.collapse() },
                onExpand = { sheetState.expand() },
                modifier = Modifier.fillMaxSize()
            )

            // LAYER 1: MiniPlayer Controls & Progress bar (only in card mode when near collapsed)
            if (!isPip && p < 0.6f) {
                val cardAlpha = ((0.6f - p) / 0.6f).coerceIn(0f, 1f)
                val controlsLeft = thumbWidthPx + with(density) { 16.dp.toPx() }
                val controlsWidth = sheetW - controlsLeft

                // Action controls to the right of thumbnail
                Box(
                    Modifier
                        .size(with(density) { controlsWidth.coerceAtLeast(0f).toDp() }, with(density) { cardHeightPx.toDp() })
                        .offset { IntOffset(controlsLeft.roundToInt(), 0) }
                        .graphicsLayer { alpha = cardAlpha }
                        .clickable { sheetState.expand() }
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(end = Vela.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Vela.sm)
                    ) {
                        Text(
                            video.title,
                            Modifier.weight(1f).testTag("mini.title"),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = {
                                if (controllerState.status == PlaybackStatus.PLAYING) host.session.pause()
                                else host.session.play()
                            },
                            modifier = Modifier.testTag("mini.toggle")
                        ) {
                            Icon(
                                if (controllerState.status == PlaybackStatus.PLAYING) Vela.icons.pause else Vela.icons.play,
                                if (controllerState.status == PlaybackStatus.PLAYING) "Pause" else "Play"
                            )
                        }
                        IconButton(
                            onClick = { sheetState.dismiss() },
                            modifier = Modifier.testTag("mini.close")
                        ) {
                            Icon(Vela.icons.close, "Close player")
                        }
                    }
                }

                // Miniplayer progress line at the bottom
                val progressFrac = if (controllerState.durationMs > 0) {
                    controllerState.positionMs.toFloat() / controllerState.durationMs.toFloat()
                } else 0f
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.BottomStart)
                        .graphicsLayer { alpha = cardAlpha }
                        .background(Vela.colors.outline.toColor())
                ) {
                    Box(Modifier.fillMaxWidth(progressFrac.coerceIn(0f, 1f)).fillMaxHeight().background(Vela.wake))
                }
            }

            // LAYER 2: PiP overlay controls (only in PiP mode when collapsed)
            if (isPip && p < 0.25f) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .pointerInput(winW, winH) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                pipPos?.let {
                                    it.pos = Offset(
                                        (it.pos?.x ?: (winW - sheetW - marginPx)) + drag.x,
                                        (it.pos?.y ?: (cardBottom - sheetH)) + drag.y
                                    )
                                }
                            }
                        }
                        .clickable { sheetState.expand() }
                ) {
                    IconButton(
                        onClick = {
                            if (controllerState.status == PlaybackStatus.PLAYING) host.session.pause()
                            else host.session.play()
                        },
                        modifier = Modifier.align(Alignment.Center).testTag("mini.toggle")
                    ) {
                        Icon(
                            if (controllerState.status == PlaybackStatus.PLAYING) Vela.icons.pause else Vela.icons.play,
                            if (controllerState.status == PlaybackStatus.PLAYING) "Pause" else "Play",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = { sheetState.dismiss() },
                        modifier = Modifier.align(Alignment.TopEnd).testTag("mini.close")
                    ) {
                        Icon(Vela.icons.close, "Close player", tint = Color.White)
                    }
                }
            }
        }
    }
}
