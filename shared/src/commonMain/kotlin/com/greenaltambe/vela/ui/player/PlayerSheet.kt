package com.greenaltambe.vela.ui.player

import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.ExtraColors
import com.greenaltambe.vela.ui.theme.Spacing
import com.greenaltambe.vela.ui.theme.Accent
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.ui.look.ComponentIds
import com.greenaltambe.vela.ui.nav.MiniPlayerHeight
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
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
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
    // Only what changes the mini player's look is read in composition (playing or not); the position is read when the bar is drawn, so a tick every
    // half second does not recompose the whole sheet.
    val controllerFlow = host.controllerState.collectAsState()
    val miniPlaying by remember(controllerFlow) { androidx.compose.runtime.derivedStateOf { controllerFlow.value.status == PlaybackStatus.PLAYING } }
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

    val winW = windowInfo.containerSize.width.toFloat().coerceAtLeast(100f)
    val winH = windowInfo.containerSize.height.toFloat().coerceAtLeast(100f)
    androidx.compose.runtime.SideEffect { sheetState.windowHeightPx = winH }
    val bottomInsetPx = WindowInsets.safeDrawing.getBottom(density).toFloat()
    val topInsetPx = WindowInsets.safeDrawing.getTop(density).toFloat()

    // Progress is NOT read here: reading it in composition recomposed this sheet and the whole video page on every animation frame. It is read
    // in the layout / draw lambdas below (and through the coarse flags), so a frame only re-measures and redraws.
    val cardHeightPx = with(density) { MiniPlayerHeight.toPx() }
    val thumbWidthDp = 64.dp

    // The mini player is a standard bar on the navigation bar: the shell sets aside its room (a strip between the page and the bar) and tells us
    // where it is, so the sheet needs no sizes of its own, with a rail or without. Before the first layout it lies along the bottom edge.
    val sizeClass = com.greenaltambe.vela.ui.layout.LocalWindowSize.current
    // On a wide window the open page sits beside the rail (the same strip's left and width); in fullscreen, or on a phone, it covers the window.
    val beside = sizeClass != com.greenaltambe.vela.ui.layout.WindowSizeClass.COMPACT && !isFullscreen
    var rootLeftPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var rootTopPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val slot = miniSlot
    val cardLeftNow = { if (slot != null && slot.isSet) slot.left - rootLeftPx else 0f }
    val cardTopNow = { if (slot != null && slot.isSet) slot.top - rootTopPx else winH - bottomInsetPx - cardHeightPx }
    val cardWidthNow = { if (slot != null && slot.isSet) slot.width else winW }
    val expandedXNow = { if (beside) cardLeftNow() else 0f }
    val expandedWNow = { if (beside) cardWidthNow() else winW }

    // Each is a function of the progress `p` (0 collapsed .. 1 expanded); called from layout/draw lambdas only.
    val sheetXAt = { p: Float -> cardLeftNow() * (1f - p) + expandedXNow() * p }
    val sheetYAt = { p: Float -> cardTopNow() * (1f - p) }
    val sheetWAt = { p: Float -> cardWidthNow() + (expandedWNow() - cardWidthNow()) * p }
    val sheetHAt = { p: Float -> cardHeightPx + (winH - cardHeightPx) * p }
    val progressNow = { sheetState.progress.coerceIn(0f, 1f) }
    val backgroundExpanded = MaterialTheme.colorScheme.background
    val backgroundCollapsed = MaterialTheme.colorScheme.surfaceContainer
    // Coarse flag: composition only changes when it flips, not on every frame in between.
    val showCardControls by remember(sheetState) { derivedStateOf { sheetState.progress < 0.6f } }

    // Gesture handler attached ONLY to the sheet container (never to full screen when collapsed)
    val controlsForGesture = androidx.compose.runtime.rememberUpdatedState(windowControls)
    val gestureModifier = Modifier.pointerInput(winW, winH, isFullscreen) {
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
            } else {
                // Collapsed card: anywhere on the card
                true
            }

            if (!canDrag || sheetState.slideY > 50f) return@awaitEachGesture

            fun currentSheetY(): Float = cardTopNow() * (1f - sheetState.progress)

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
                        val swipedUp = startProgress > 0.9f && (lastTotal < -with(density) { 72.dp.toPx() } || vY < -1500f)
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
                .graphicsLayer { clip = true }
                .drawBehind { drawRect(if (progressNow() > 0.4f) backgroundExpanded else backgroundCollapsed) }
                .then(gestureModifier)
                // Collapsed, the sheet is the mini player: named as such for the tests and for accessibility tooling.
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
                pageWidthPx = expandedWNow(),
            )

            // LAYER 1: MiniPlayer Controls & Progress bar (only in card mode when near collapsed)
            if (showCardControls) {
                val cardAlphaNow = { ((0.6f - progressNow()) / 0.6f).coerceIn(0f, 1f) }
                val controlsLeftDp = thumbWidthDp + 24.dp   // the picture sits 12 dp in; its text starts 12 dp after it

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
                        Modifier.fillMaxSize().padding(end = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
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
                                if (miniPlaying) host.session.pause()
                                else host.session.play()
                            },
                            modifier = Modifier.testTag("mini.toggle")
                        ) {
                            // The icon swaps with a short pop (play becomes pause) instead of cutting.
                            SwapIcon(if (miniPlaying) AppIcons.pause else AppIcons.play, if (miniPlaying) "Pause" else "Play", androidx.compose.material3.LocalContentColor.current)
                        }
                        IconButton(
                            onClick = { sheetState.dismiss() },
                            modifier = Modifier.testTag("mini.close")
                        ) {
                            Icon(AppIcons.close, "Close player")
                        }
                    }
                }

                // Miniplayer progress line at the bottom
                androidx.compose.material3.LinearWavyProgressIndicator(
                    progress = { controllerFlow.value.let { c -> if (c.durationMs > 0) (c.positionMs.toFloat() / c.durationMs).coerceIn(0f, 1f) else 0f } },
                    // Paused: a flat bar, no wave running in the background.
                    amplitude = { if (miniPlaying) 1f else 0f }, waveSpeed = if (miniPlaying) 40.dp else 0.dp,
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomStart).graphicsLayer { alpha = cardAlphaNow() },
                    color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                // The bar's top edge, so it reads as a bar between the page and the navigation.
                HorizontalDivider(Modifier.align(Alignment.TopStart).graphicsLayer { alpha = cardAlphaNow() }, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
