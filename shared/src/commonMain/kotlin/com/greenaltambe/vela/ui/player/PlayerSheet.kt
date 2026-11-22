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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.onSizeChanged
import com.greenaltambe.vela.ui.components.LocalPlayerGestureLock
import com.greenaltambe.vela.ui.components.PlayerGestureLock
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.layout.positionInRoot
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
    val host = com.greenaltambe.vela.ui.nav.LocalTabPlayer.current?.host ?: services.player ?: return
    // Only what changes the mini player's look is read in composition (playing or not); the position is read when the bar is drawn, so a tick every
    // half second does not recompose the whole sheet.
    val controllerFlow = host.controllerState.collectAsState()
    val miniPlaying by remember(controllerFlow) { androidx.compose.runtime.derivedStateOf { controllerFlow.value.status == PlaybackStatus.PLAYING } }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // The viewer's choice (per device) of how the video looks minimised: the bar on the navigation, or a floating window they can drag and resize.
    val floating = rememberFloatingMini(services)
    val pip = sheetState.pip
    val pipShape = MaterialTheme.shapes.medium
    val windowControls = LocalWindowControls.current
    val windowInfo = LocalWindowInfo.current
    val isFullscreen = windowControls?.isFullscreen == true

    // Intercept back button when sheet is expanded
    PlatformBackHandler(enabled = sheetState.isExpanded && !isFullscreen) {
        sheetState.collapse()
    }

    // Opening the mini player (swipe up or tap) while the phone is sideways goes straight to fullscreen: the cramped landscape page is never wanted there.
    val landscapeNow = androidx.compose.runtime.rememberUpdatedState(windowInfo.containerSize.width > windowInfo.containerSize.height)
    val desktopWindow = com.greenaltambe.vela.ui.platform.LocalVolumeSlider.current // a desktop window is always wider than tall; that is not "phone turned sideways"
    val controlsNow = androidx.compose.runtime.rememberUpdatedState(windowControls)
    LaunchedEffect(sheetState) {
        var wasOpen = sheetState.progress > 0.5f
        androidx.compose.runtime.snapshotFlow { sheetState.progress }.collect { p ->
            if (p > 0.99f && !wasOpen && landscapeNow.value && controlsNow.value?.isFullscreen == false && !desktopWindow) controlsNow.value?.toggleFullscreen()
            if (p > 0.99f) wasOpen = true else if (p < 0.5f) wasOpen = false
        }
    }

    // The area the sheet fills, not the window: on desktop a tab strip sits above the app, so the window is taller than the page.
    var areaSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val winW = (if (areaSize.width > 0) areaSize.width else windowInfo.containerSize.width).toFloat().coerceAtLeast(100f)
    val winH = (if (areaSize.height > 0) areaSize.height else windowInfo.containerSize.height).toFloat().coerceAtLeast(100f)
    androidx.compose.runtime.SideEffect { sheetState.windowHeightPx = winH; sheetState.windowWidthPx = winW }
    val toasts = com.greenaltambe.vela.ui.look.LocalUndoToasts.current
    val haptics = rememberPlayerHaptics()
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
    // The strip the shell keeps beside the rail (or the whole window on a phone): where the open page sits, and where the floating window may roam.
    val slotLeftNow = { if (slot != null && slot.isSet) slot.left - rootLeftPx else 0f }
    val slotWidthNow = { if (slot != null && slot.isSet) slot.width else winW }
    // Floating window: its size and place come from [pip]; at least 240 dp wide, at most 60% of the strip, 360 dp to start with.
    val pipMarginPx = with(density) { 16.dp.toPx() }
    val pipMinPx = with(density) { 240.dp.toPx() }
    val pipDefaultPx = with(density) { 360.dp.toPx() }
    val pipWNow = { pip.width(pipMinPx, minOf(slotWidthNow() * 0.6f, with(density) { 720.dp.toPx() }), pipDefaultPx) }
    val pipHNow = { pipWNow() * 9f / 16f }
    val pipArea = { Rect(slotLeftNow(), 0f, slotLeftNow() + slotWidthNow(), winH) }
    val pipBounds = { Rect(slotLeftNow() + pipMarginPx, topInsetPx + pipMarginPx, slotLeftNow() + slotWidthNow() - pipMarginPx, winH - bottomInsetPx - pipMarginPx) }
    val pipPosNow = { pip.position(pipWNow(), pipHNow(), pipBounds(), pipArea()) }

    val cardLeftNow = { if (floating) pipPosNow().x else if (slot != null && slot.isSet) slot.left - rootLeftPx else 0f }
    val cardTopNow = { if (floating) pipPosNow().y else if (slot != null && slot.isSet) slot.top - rootTopPx else winH - bottomInsetPx - cardHeightPx }
    val cardWidthNow = { if (floating) pipWNow() else if (slot != null && slot.isSet) slot.width else winW }
    val cardHeightNow = { if (floating) pipHNow() else cardHeightPx }
    val expandedXNow = { if (beside) slotLeftNow() else 0f }
    val expandedWNow = { if (beside) slotWidthNow() else winW }

    // Each is a function of the progress `p` (0 collapsed .. 1 expanded); called from layout/draw lambdas only. The same morph for the bar and the window.
    val sheetXAt = { p: Float -> cardLeftNow() * (1f - p) + expandedXNow() * p }
    val sheetYAt = { p: Float -> cardTopNow() * (1f - p) }
    val sheetWAt = { p: Float -> cardWidthNow() + (expandedWNow() - cardWidthNow()) * p }
    val sheetHAt = { p: Float -> cardHeightNow() + (winH - cardHeightNow()) * p }
    val progressNow = { sheetState.progress.coerceIn(0f, 1f) }
    val backgroundExpanded = MaterialTheme.colorScheme.background
    val backgroundCollapsed = MaterialTheme.colorScheme.surfaceContainer
    // Coarse flag: composition only changes when it flips, not on every frame in between.
    val showCardControls by remember(sheetState) { derivedStateOf { sheetState.progress < 0.6f } }

    // Gesture handler attached ONLY to the sheet container (never to full screen when collapsed)
    val controlsForGesture = androidx.compose.runtime.rememberUpdatedState(windowControls)
    // The seek bar tells every other gesture of the player to keep out while a finger is on it (see PlayerGestureLock).
    val gestureLock = remember { PlayerGestureLock() }
    var sheetRoot by remember { mutableStateOf(Offset.Zero) }
    var areaRoot by remember { mutableStateOf(Offset.Zero) }
    val gestureModifier = Modifier.onGloballyPositioned { sheetRoot = it.positionInRoot() }.pointerInput(winW, winH, isFullscreen, floating) {
        var startProgress = 0f
        var startWindowY = 0f
        var isDragging = false
        val tracker = VelocityTracker()
        val swipeTracker = VelocityTracker()

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val currentP = sheetState.progress
            // The finger is on / right beside the seek bar (it locks at once, before this parent sees the touch): this is not a sheet drag. NOTE: a
            // consumed DOWN is not a reason to drop it: the picture's own tap detector consumes every down, and swiping that picture must still work.
            if (gestureLock.isLocked(down.uptimeMillis) || gestureLock.onBar(sheetRoot + down.position, with(density) { PlayerGestureLock.BAND_DP.dp.toPx() })) return@awaitEachGesture

            // The floating window (page closed): drag it anywhere and it settles into the nearest corner; start on the grip to resize it. A tap is the overlay's.
            if (floating && currentP < 0.15f && !isFullscreen) {
                if (sheetState.slideY > 50f) return@awaitEachGesture
                val w = pipWNow(); val h = pipHNow()
                val gripPx = with(density) { 40.dp.toPx() }
                val grab = down.position
                val inGrip = (if (pip.corner.right) grab.x < gripPx else grab.x > w - gripPx) && (if (pip.corner.bottom) grab.y < gripPx else grab.y > h - gripPx)
                val origin = { sheetRoot - areaRoot }
                // The corner that stays put while resizing, in the sheet area's coordinates.
                val fixed = origin() + Offset(if (pip.corner.right) w else 0f, if (pip.corner.bottom) h else 0f)
                val pipTracker = VelocityTracker()
                var started = false
                val slop = with(density) { 6.dp.toPx() }
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!started && change.isConsumed) break
                    val pointer = origin() + change.position
                    if (!change.pressed) {
                        if (started && !inGrip) { val v = pipTracker.calculateVelocity(); pip.release(v.x, v.y, pipWNow(), pipHNow(), pipBounds(), pipArea()) }
                        break
                    }
                    if (!started && (change.position - grab).getDistance() > slop) started = true
                    if (started) {
                        change.consume()
                        if (inGrip) pip.resizeTo((abs(pointer.x - fixed.x) + abs(pointer.y - fixed.y) * 16f / 9f) / 2f)
                        else { pipTracker.addPosition(change.uptimeMillis, pointer); pip.moveTo(pointer - grab, pipWNow(), pipHNow(), pipBounds()) }
                    }
                }
                return@awaitEachGesture
            }

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
            // Swiping the collapsed bar away: sideways or down. The finger's place is measured as if the bar were not moving (it moves with the finger).
            var swiping = false
            val startRawX = down.position.x + sheetState.swipeX.value
            val startRawY = down.position.y + sheetState.swipeY.value
            swipeTracker.resetTracking()
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
                val rawX = change.position.x + sheetState.swipeX.value
                val rawY = change.position.y + sheetState.swipeY.value
                val swipeDx = rawX - startRawX
                val swipeDy = rawY - startRawY

                // Something else took the gesture before it became a drag (a child consumed it, the seek bar got a finger): drop it.
                if (!isDragging && (change.isConsumed || gestureLock.isLocked(change.uptimeMillis))) break
                if (isDragging && gestureLock.active) break

                if (!change.pressed) {
                    if (swiping) {
                        val v = swipeTracker.calculateVelocity()
                        val dxEnd = rawX - startRawX; val dyEnd = rawY - startRawY
                        val leaving = sheetState.releaseSwipe(
                            dxEnd, dyEnd, v.x, v.y, thresholdX = winW * 0.3f, thresholdY = with(density) { 56.dp.toPx() },
                            onClosed = { closed -> toasts.show("Player closed") { sheetState.reopen(closed) } },
                        )
                        if (leaving) haptics.click()
                        break
                    }
                    if (isDragging) {
                        val vY = tracker.calculateVelocity().y
                        // On the open video page, a firm swipe UP on the picture turns it fullscreen (landscape). Down still minimises (below).
                        val swipedUp = startProgress > 0.9f && !gestureLock.isLocked(change.uptimeMillis) && (lastTotal < -with(density) { 72.dp.toPx() } || vY < -1500f)
                        if (swipedUp && controlsForGesture.value?.isFullscreen == false) controlsForGesture.value?.toggleFullscreen()
                        else sheetState.settle(vY)
                    }
                    break
                }

                if (!isDragging && !swiping && startProgress < 0.15f && (abs(swipeDx) > touchSlop || swipeDy > touchSlop)) {
                    // Down or sideways takes the bar away; up is the existing drag that opens it.
                    if (abs(swipeDx) > abs(swipeDy) || swipeDy > 0f) swiping = true
                }
                if (swiping) {
                    change.consume()
                    swipeTracker.addPosition(change.uptimeMillis, Offset(rawX, rawY))
                    scope.launch { sheetState.dragSwipe(swipeDx, swipeDy) }
                    continue
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
    Box(modifier.fillMaxSize().onSizeChanged { areaSize = it }.onGloballyPositioned { rootTopPx = it.positionInWindow().y; areaRoot = it.positionInRoot() }) {
        // Morphing Sheet Box: exactly sized and positioned, only absorbs touches within its bounds
        Box(
            Modifier
                .offset { val p = progressNow(); IntOffset((sheetXAt(p) + sheetState.swipeX.value).roundToInt(), (sheetYAt(p) + sheetState.slideY + sheetState.swipeY.value).roundToInt()) }
                .layout { measurable, _ ->
                    val p = progressNow()
                    val w = sheetWAt(p).roundToInt().coerceAtLeast(0)
                    val h = sheetHAt(p).roundToInt().coerceAtLeast(0)
                    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
                    layout(w, h) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    clip = true; alpha = sheetState.swipeAlpha()
                    // The floating window has rounded corners and a shadow that fade as it opens into the page.
                    if (floating) {
                        val q = 1f - progressNow()
                        // The corner radius of the look's medium shape, fading out as the window opens into the page.
                        shape = RoundedCornerShape(((pipShape as? androidx.compose.foundation.shape.CornerBasedShape)?.topStart?.toPx(size, this) ?: 0f) * q)
                        shadowElevation = Spacing.sm.toPx() * q
                    }
                }
                .drawBehind { drawRect(if (progressNow() > 0.4f) backgroundExpanded else backgroundCollapsed) }
                .then(gestureModifier)
                // Collapsed, the sheet is the mini player: named as such for the tests and for accessibility tooling.
                .then(if (sheetState.isCollapsed) Modifier.testTag(com.greenaltambe.vela.ui.look.ComponentIds.MINI_PLAYER) else Modifier)
        ) {
            // LAYER 0: The persistent VideoScreen with the live video surface
            androidx.compose.runtime.CompositionLocalProvider(LocalPlayerGestureLock provides gestureLock) {
                androidx.compose.runtime.key(navigator.pageRefresh) { VideoScreen(
                    services = services,
                    navigator = navigator,
                    video = video,
                    startSeconds = sheetState.startSeconds,
                    sheetProgress = progressNow,
                    onBack = { sheetState.collapse() },
                    onExpand = { sheetState.expand() },
                    modifier = Modifier.fillMaxSize(),
                    pageWidthPx = expandedWNow(),
                    pageHeightPx = winH,
                    miniPicture = if (floating) ({ Rect(0f, 0f, pipWNow(), pipHNow()) }) else null,
                ) }
            }

            // LAYER 1 (floating window): outline, buttons on hover, progress line.
            if (floating && showCardControls) FloatingPipOverlay(
                video = video, corner = pip.corner, playing = miniPlaying, controller = controllerFlow,
                alphaNow = { ((0.6f - progressNow()) / 0.6f).coerceIn(0f, 1f) },
                onExpand = { sheetState.expand() },
                onToggle = { if (miniPlaying) host.session.pause() else host.session.play() },
                onClose = { sheetState.dismiss() },
            )

            // LAYER 1 (mini bar): Controls & Progress bar (only in card mode when near collapsed)
            if (!floating && showCardControls) {
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
