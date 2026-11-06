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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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
    /** Where the bar has set aside room for the card; the card sits there, so it follows the bar (shrinking, placement, gap). */
    miniSlot: com.greenaltambe.vela.ui.nav.MiniSlot? = null,
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

    // Opening the mini player (swipe up or tap) while the phone is sideways goes straight to fullscreen: the cramped landscape page is never wanted there.
    val landscapeNow = androidx.compose.runtime.rememberUpdatedState(windowInfo.containerSize.width > windowInfo.containerSize.height)
    val controlsNow = androidx.compose.runtime.rememberUpdatedState(windowControls)
    LaunchedEffect(sheetState) {
        var wasOpen = sheetState.progress > 0.5f
        androidx.compose.runtime.snapshotFlow { sheetState.progress }.collect { p ->
            if (p > 0.99f && !wasOpen && landscapeNow.value && controlsNow.value?.isFullscreen == false) controlsNow.value?.toggleFullscreen()
            if (p > 0.99f) wasOpen = true else if (p < 0.5f) wasOpen = false
        }
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
    // Sideways, the page (rail and all) sits inside the left / right system insets (cutout, gesture area); the sheet must be placed inside them too.
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val leftInsetPx = WindowInsets.safeDrawing.getLeft(density, layoutDirection).toFloat()
    val rightInsetPx = WindowInsets.safeDrawing.getRight(density, layoutDirection).toFloat()

    // Progress is NOT read here: reading it in composition recomposed this sheet and the whole video page on every animation frame. It is read
    // in the layout / draw lambdas below (and through the coarse flags), so a frame only re-measures and redraws.
    val cardHeightPx = with(density) { 56.dp.toPx() }
    val thumbWidthDp = 64.dp
    val thumbWidthPx = with(density) { thumbWidthDp.toPx() }
    val bottomClearPx = bottomInsetPx + with(density) { (if (nav.barStyle == BarStyle.DOCKED) 64.dp else 72.dp).toPx() }
    val marginPx = with(density) { (if (nav.barStyle == BarStyle.DOCKED) 0.dp else Vela.md).toPx() }

    val sizeClass = com.greenaltambe.vela.ui.layout.LocalWindowSize.current
    // The page behind has its rail whenever the window is wide, fullscreen or not; the card is placed by that layout so it does not jump when the window
    // leaves fullscreen. Only the expanded sheet differs: in fullscreen it covers the whole window.
    val pageRail = sizeClass != com.greenaltambe.vela.ui.layout.WindowSizeClass.COMPACT
    val rail = pageRail && !isFullscreen
    val railWidthDp = if (!pageRail) 0.dp else if (nav.railCollapsed) 72.dp else 176.dp
    val railWidthPx = with(density) { railWidthDp.toPx() }
    val contentW = winW - leftInsetPx - rightInsetPx - railWidthPx

    val cardBottom = winH - bottomClearPx
    val cardTop = cardBottom - cardHeightPx
    var rootTopPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    // With the bar showing, the card is where the bar has left room for it; otherwise (a rail, before the first layout) at the fixed place. Read in layout, not composition.
    val cardTopNow = { val t = miniSlot?.top; if (t != null && !t.isNaN() && !isPip && !rail) t - rootTopPx else cardTop }
    // The card is as wide as the page, up to a comfortable width: stretched across a landscape screen it is a thin bar with its controls far apart.
    val cardWidth = minOf(contentW - marginPx * 2, with(density) { 640.dp.toPx() })
    val cardLeft = leftInsetPx + railWidthPx + marginPx + (contentW - marginPx * 2 - cardWidth) / 2f

    // Dynamic morphing sheet bounds:
    val targetExpandedX = if (rail) leftInsetPx + railWidthPx else 0f
    val targetExpandedW = if (rail) contentW else winW
    val pipW = with(density) { PIP_WIDTH.toPx() }
    val pipH = pipW * 9f / 16f

    // Each is a function of the progress `p` (0 collapsed .. 1 expanded); called from layout/draw lambdas only.
    val sheetXAt = { p: Float ->
        if (isPip) {
            val at = pipPos?.pos ?: Offset(winW - pipW - marginPx, cardBottom - pipH)
            at.x * (1f - p) + targetExpandedX * p
        } else cardLeft * (1f - p) + targetExpandedX * p
    }
    val sheetYAt = { p: Float ->
        if (isPip) {
            val at = pipPos?.pos ?: Offset(winW - pipW - marginPx, cardBottom - pipH)
            at.y * (1f - p)
        } else cardTopNow() * (1f - p)
    }
    val sheetWAt = { p: Float -> if (isPip) pipW + (targetExpandedW - pipW) * p else cardWidth + (targetExpandedW - cardWidth) * p }
    val sheetHAt = { p: Float -> if (isPip) pipH + (winH - pipH) * p else cardHeightPx + (winH - cardHeightPx) * p }
    val largeRadiusDp = Vela.look.radii.largeDp
    val cornerDpAt = { p: Float -> (if (isPip) 16f else largeRadiusDp) * (1f - p) }
    val progressNow = { sheetState.progress.coerceIn(0f, 1f) }
    val backgroundExpanded = Vela.colors.background.toColor()
    val backgroundCollapsed = Vela.colors.surfaceVariant.toColor()
    // Coarse flags: composition only changes when one of these flips, not on every frame in between.
    val showCardControls by remember(sheetState) { derivedStateOf { sheetState.progress < 0.6f } }
    val showPipControls by remember(sheetState) { derivedStateOf { sheetState.progress < 0.25f } }

    // Gesture handler attached ONLY to the sheet container (never to full screen when collapsed)
    val controlsForGesture = androidx.compose.runtime.rememberUpdatedState(windowControls)
    val gestureModifier = Modifier.pointerInput(winW, winH, isPip, isFullscreen) {
        var startProgress = 0f
        var startWindowY = 0f
        var isDragging = false
        val tracker = VelocityTracker()

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val currentP = sheetState.progress

            // Fullscreen has its own gestures (swipe down leaves it); the sheet must not also drag the page down under it.
            val canDrag = if (isFullscreen) {
                false
            } else if (currentP > 0.6f) {
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
                cardTopNow() * (1f - sheetState.progress)
            }

            startProgress = currentP
            startWindowY = down.position.y + currentSheetY()
            isDragging = false
            tracker.resetTracking()
            tracker.addPosition(down.uptimeMillis, Offset(down.position.x, startWindowY))

            // Moves slightly faster than fingers (1 / 0.82 = 1.22x finger speed)
            val dragRangePx = (cardTopNow() - topInsetPx).coerceAtLeast(100f) * 0.82f
            val touchSlop = with(density) { 8.dp.toPx() }

            var lastTotal = 0f
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                val nowWindowY = change.position.y + currentSheetY()

                if (!change.pressed) {
                    if (isDragging) {
                        val vY = tracker.calculateVelocity().y
                        // On the open video page, a firm swipe UP on the picture turns it fullscreen (landscape). Down still minimises (below).
                        val swipedUp = startProgress > 0.9f && !isPip && (lastTotal < -with(density) { 72.dp.toPx() } || vY < -1500f)
                        if (swipedUp && controlsForGesture.value?.isFullscreen == false) controlsForGesture.value?.toggleFullscreen()
                        else sheetState.settle(vY)
                    }
                    break
                }

                tracker.addPosition(change.uptimeMillis, Offset(change.position.x, nowWindowY))
                val totalDragY = nowWindowY - startWindowY
                lastTotal = totalDragY

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
    Box(modifier.fillMaxSize().onGloballyPositioned { rootTopPx = it.positionInWindow().y }) {
        // Morphing Sheet Box: exactly sized and positioned, only absorbs touches within its bounds
        Box(
            Modifier
                .offset { val p = progressNow(); IntOffset(sheetXAt(p).roundToInt(), (sheetYAt(p) + sheetState.slideY).roundToInt()) }
                .layout { measurable, _ ->
                    val p = progressNow()
                    val w = sheetWAt(p).roundToInt().coerceAtLeast(0)
                    val h = sheetHAt(p).roundToInt().coerceAtLeast(0)
                    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
                    layout(w, h) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    val p = progressNow()
                    shape = RoundedCornerShape(cornerDpAt(p).dp.toPx())
                    clip = true
                    // The shadow only shows around the small card; drawing it for a near-full-screen layer is wasted GPU work, so it fades out early.
                    shadowElevation = 8.dp.toPx() * (1f - p / 0.3f).coerceIn(0f, 1f)
                }
                .drawBehind { drawRect(if (progressNow() > 0.4f) backgroundExpanded else backgroundCollapsed) }
                .then(gestureModifier)
                // Collapsed, the sheet is the mini player (or the picture-in-picture card): named as such for the tests and for accessibility tooling.
                .then(if (sheetState.isCollapsed) Modifier.testTag(com.greenaltambe.vela.ui.look.ComponentIds.MINI_PLAYER) else Modifier)
        ) {
            // LAYER 0: The persistent VideoScreen with the live video surface
            VideoScreen(
                services = services,
                navigator = navigator,
                video = video,
                startSeconds = sheetState.startSeconds,
                sheetProgress = progressNow,
                onBack = { sheetState.collapse() },
                onExpand = { sheetState.expand() },
                modifier = Modifier.fillMaxSize(),
                pageWidthPx = targetExpandedW,
            )

            // LAYER 1: MiniPlayer Controls & Progress bar (only in card mode when near collapsed)
            if (!isPip && showCardControls) {
                val cardAlphaNow = { ((0.6f - progressNow()) / 0.6f).coerceIn(0f, 1f) }
                val controlsLeftDp = thumbWidthDp + 16.dp

                // Action controls to the right of thumbnail
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(with(density) { cardHeightPx.toDp() })
                        .padding(start = controlsLeftDp)
                        .graphicsLayer { alpha = cardAlphaNow() }
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
                        .graphicsLayer { alpha = cardAlphaNow() }
                        .background(Vela.colors.outline.toColor())
                ) {
                    Box(Modifier.fillMaxWidth(progressFrac.coerceIn(0f, 1f)).fillMaxHeight().background(Vela.wake))
                }
            }

            // LAYER 2: PiP overlay controls (only in PiP mode when collapsed)
            if (isPip && showPipControls) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Vela.colors.mediaBackground.toColor().copy(alpha = 0.35f))
                        .pointerInput(winW, winH) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                pipPos?.let {
                                    it.pos = Offset(
                                        (it.pos?.x ?: (winW - pipW - marginPx)) + drag.x,
                                        (it.pos?.y ?: (cardBottom - pipH)) + drag.y
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
                            tint = Vela.colors.onMedia.toColor()
                        )
                    }
                    IconButton(
                        onClick = { sheetState.dismiss() },
                        modifier = Modifier.align(Alignment.TopEnd).testTag("mini.close")
                    ) {
                        Icon(Vela.icons.close, "Close player", tint = Vela.colors.onMedia.toColor())
                    }
                }
            }
        }
    }
}
