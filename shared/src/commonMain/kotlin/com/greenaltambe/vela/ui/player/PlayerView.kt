package com.greenaltambe.vela.ui.player

import com.greenaltambe.vela.ui.components.LoadingSpinner
import com.greenaltambe.vela.ui.theme.AppIcons
import androidx.compose.foundation.shape.CircleShape
import com.greenaltambe.vela.ui.theme.ExtraColors
import com.greenaltambe.vela.ui.theme.Spacing
import com.greenaltambe.vela.ui.theme.LocalMotion
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.greenaltambe.vela.extractor.Chapter
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.library.SleepTimerMode
import com.greenaltambe.vela.library.SleepTimerPresets
import com.greenaltambe.vela.library.currentChapter
import com.greenaltambe.vela.library.formatTimestamp
import com.greenaltambe.vela.player.PlaybackStats
import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.player.PlayerHost
import com.greenaltambe.vela.player.SessionState
import com.greenaltambe.vela.ui.components.ScrubMarker
import com.greenaltambe.vela.ui.components.Scrubber
import com.greenaltambe.vela.ui.describe
import com.greenaltambe.vela.ui.platform.LocalVolumeSlider
import com.greenaltambe.vela.ui.platform.LocalWindowControls
import kotlinx.coroutines.delay

/** A coloured span of the timeline in milliseconds (a SponsorBlock segment). */
data class SeekMarker(val startMs: Long, val endMs: Long, val color: Color)

/** The brief "+10 s" / "−10 s" badge a double-tap shows; [token] makes each tap a new one so the badge restarts its timer. */
private class SeekFlash(val forward: Boolean, val seconds: Int, val token: Int)

private const val HIDE_AFTER_MS = 3_000L

private fun SessionState.videoIdOrNull(): String? = when (this) {
    SessionState.Idle -> null
    is SessionState.Resolving -> videoId
    is SessionState.Ready -> videoId
    is SessionState.Failed -> videoId
}

/**
 * The picture with its controls over it. Tap toggles the controls; they hide on their own three seconds after the
 * last touch while playing, and stay up while paused, buffering or failed. Speed, quality, sleep timer and stats are
 * menus, never rows. Everything shown is for [video]: while the session is on another video (or still resolving this
 * one) the picture is its poster and the position is zero, so a page can never show the previous video's state.
 */
@Composable
fun PlayerView(
    host: PlayerHost,
    video: VideoSummary,
    chapters: List<Chapter>,
    markers: List<SeekMarker>,
    modifier: Modifier = Modifier,
    fullscreen: Boolean = false,
    /** False in Picture-in-Picture, where only the picture is drawn. */
    controlsEnabled: Boolean = true,
    /** Name the current chapter inside the controls; narrow players show it on the page instead. */
    showChapterTitle: Boolean = false,
    onBack: (() -> Unit)? = null,
    /** Ends the active account's pause (the caller owns the governor); null when there is nothing to end. PlayerView asks first, then retries. */
    tryAccountNow: (() -> Unit)? = null,
    /** One line shown under the spinner while resolving takes long (see [resolveNote]); null shows nothing. */
    resolvingNote: String? = null,
    /** Opens Settings (the age-restricted switch lives there); null hides the button. */
    openAgeSetting: (() -> Unit)? = null,
    /** The page knows the video is live even when the summary did not say so. */
    isLive: Boolean = false,
    /** Drawn over the picture, above the controls' bottom edge: SponsorBlock's skip prompt and "skipped, Undo" notice. */
    overlay: @Composable () -> Unit = {},
    /** Autoplay-next (a setting the page owns); both null hides the row in the settings sheet. */
    autoplay: Boolean? = null,
    onAutoplayChange: ((Boolean) -> Unit)? = null,
    /** How far a double-tap (and the skip buttons) jump, in seconds; and the speed while the picture is held. Both are playback settings. */
    seekStepSeconds: Int = 10,
    holdSpeed: Float = 2f,
    /** The previous / next video buttons beside play. Null disables the button (nothing before / after). */
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    /** Open chapters bottom panel (used by VideoScreen to open in-page panel under player). */
    onOpenChapters: (() -> Unit)? = null,
) {
    val session = host.session
    val sessionState by session.state.collectAsState()
    val player by host.controllerState.collectAsState()
    val stats by host.stats.collectAsState()
    val volume by session.volume.collectAsState()
    val muted by session.muted.collectAsState()
    val sleep by session.sleepTimer.collectAsState()
    val captions by session.captions.collectAsState()
    val audioTracks by session.audioTracks.collectAsState()
    val audioTrack by session.audioTrack.collectAsState()
    val activeCaption by session.activeCaption.collectAsState()
    val window = LocalWindowControls.current
    val haptics = rememberPlayerHaptics()
    val gestureLock = com.greenaltambe.vela.ui.components.LocalPlayerGestureLock.current
    val desktop = LocalVolumeSlider.current // desktop has its own volume; Android uses the system's
    // The small portrait player (not fullscreen, not the desktop window): controls on top, only time / chapter / seek bar below.
    val compact = !fullscreen && !desktop

    val mine = sessionState.videoIdOrNull() == video.id
    val ready = (sessionState as? SessionState.Ready)?.takeIf { mine }
    val failed = (sessionState as? SessionState.Failed)?.takeIf { mine }
    val resolving = !mine || sessionState is SessionState.Resolving || sessionState is SessionState.Idle
    val active = ready != null
    /** A live stream shows how far behind live it is instead of a position (the live window slides, so the position is not stable). */
    val live = active && (video.isLive || isLive)
    val behind = if (live) player.liveOffsetMs else null
    val position = if (active) player.positionMs else 0L
    val duration = if (active) player.durationMs else 0L
    val playing = active && player.status == PlaybackStatus.PLAYING
    val buffering = resolving || (active && player.status == PlaybackStatus.BUFFERING)

    var controlsVisible by remember(video.id) { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<Float?>(null) }
    var menu by remember { mutableStateOf<Menu?>(null) }
    // Tapping the time swaps the elapsed reading for the time left ("-19:26 / 19:28"), as YouTube does; it stays that way across videos.
    var showRemaining by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var chaptersOpen by remember(video.id) { mutableStateOf(false) }
    val showControls = controlsEnabled && (controlsVisible || menu != null || preview != null || chaptersOpen)
    // Pausing (from anywhere) brings the controls up; after that a tap on the picture hides them again even though it stays paused.
    LaunchedEffect(player.status) { if (player.status == PlaybackStatus.PAUSED) controlsVisible = true }

    LaunchedEffect(controlsVisible, playing, touches, menu, preview) {
        if (controlsVisible && playing && menu == null && preview == null) { delay(HIDE_AFTER_MS); controlsVisible = false }
    }
    fun touch() { touches++; controlsVisible = true }

    // Double-tap a half to jump; hold the picture for fast playback (back to normal on release). The latest position is read when the gesture fires.
    val positionNow by androidx.compose.runtime.rememberUpdatedState(position)
    val durationNow by androidx.compose.runtime.rememberUpdatedState(duration)
    val activeNow by androidx.compose.runtime.rememberUpdatedState(active)
    var flash by remember { mutableStateOf<SeekFlash?>(null) }
    var holding by remember { mutableStateOf(false) }
    LaunchedEffect(flash) { if (flash != null) { delay(650); flash = null } }
    androidx.compose.runtime.DisposableEffect(video.id) { onDispose { if (holding) session.holdSpeed(null) } }

    // Where the picture ends on screen: the chapters sheet must stay below it (portrait), never cover the video.
    var videoBottomPx by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
    val boxModifier = if (fullscreen) modifier.fillMaxSize() else modifier.fillMaxWidth().aspectRatio(16f / 9f)
    val onMedia = ExtraColors.onMedia
    // Desktop: the mouse over the picture brings the controls up (they stay while it moves, and go after it rests for a few seconds); leaving hides them.
    val hoverModifier = if (!desktop) Modifier else Modifier.pointerInput(video.id) {
        awaitPointerEventScope {
            // Only a pointer that really moved counts: Compose replays hover events when something changes under a resting pointer.
            var last: androidx.compose.ui.geometry.Offset? = null
            while (true) {
                val event = awaitPointerEvent()
                when (event.type) {
                    androidx.compose.ui.input.pointer.PointerEventType.Enter, androidx.compose.ui.input.pointer.PointerEventType.Move -> {
                        val at = event.changes.firstOrNull()?.position
                        if (at != last) { last = at; controlsVisible = true; touches++ }
                    }
                    androidx.compose.ui.input.pointer.PointerEventType.Exit -> { controlsVisible = false; last = null }
                    else -> {}
                }
            }
        }
    }
    // Fullscreen on desktop: the pointer goes with the controls (it comes back when the mouse moves, as the controls do).
    val blank = com.greenaltambe.vela.ui.platform.LocalBlankPointer.current
    // Always attached, only its icon changes: adding / removing the modifier under a resting pointer made Compose replay a hover event, which showed the controls again.
    val cursorModifier = if (blank != null) Modifier.pointerHoverIcon(if (fullscreen && !showControls) blank else PointerIcon.Default) else Modifier
    Box(boxModifier.then(hoverModifier).then(cursorModifier).background(ExtraColors.mediaBackground).androidx_onPos { videoBottomPx = it }.testTag("player.view")) {
        // The poster fills the box until the video has played: after that it only shows in the bars beside a letterboxed picture (fullscreen on a wide
        // screen), where it looked like part of the home screen bleeding through.
        var hasPlayed by remember(video.id) { mutableStateOf(false) }
        LaunchedEffect(playing) { if (playing) hasPlayed = true }
        if (video.thumbnailUrl != null && !hasPlayed) AsyncImage(video.thumbnailUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        host.Surface(Modifier.fillMaxSize())

        // The poster stands in until this video has a picture of its own.
        if (!active || (player.status == PlaybackStatus.IDLE)) {
            Box(Modifier.fillMaxSize().background(ExtraColors.mediaBackground).testTag("player.poster")) {
                video.thumbnailUrl?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
        }

        // Subtitles, drawn here from the track's WebVTT (the same on every platform), above the controls' bottom row while they show.
        val fetch = com.greenaltambe.vela.ui.platform.LocalTextFetcher.current
        val cues by androidx.compose.runtime.produceState(emptyList<com.greenaltambe.vela.library.Cue>(), activeCaption, fetch) {
            val track = activeCaption
            value = if (track == null || fetch == null) emptyList() else fetch(track.url)?.let { com.greenaltambe.vela.library.Vtt.parse(it) }.orEmpty()
        }
        val cue = if (active && cues.isNotEmpty()) com.greenaltambe.vela.library.Vtt.at(cues, position) else null
        if (cue != null) Text(
            cue.text, Modifier.align(Alignment.BottomCenter).padding(start = Spacing.md, end = Spacing.md, bottom = if (showControls) 96.dp else Spacing.md).clip(MaterialTheme.shapes.small)
                .background(ExtraColors.mediaScrim.copy(alpha = 0.7f)).padding(horizontal = Spacing.sm, vertical = Spacing.xs).testTag("player.caption"),
            color = onMedia, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, maxLines = 3,
        )

        // Taps toggle the controls. Under the controls, so their buttons take their own taps.
        Box(
            Modifier.fillMaxSize().testTag("player.surface")
                .pointerInput(video.id, seekStepSeconds, holdSpeed) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible; touches++ },
                        onDoubleTap = { at ->
                            if (!activeNow || gestureLock?.active == true) return@detectTapGestures
                            val forward = at.x > size.width / 2f
                            val target = (positionNow + (if (forward) 1 else -1) * seekStepSeconds * 1000L).coerceAtLeast(0).let { if (durationNow > 0) it.coerceAtMost(durationNow) else it }
                            session.seekTo(target)
                            haptics.click()
                            flash = SeekFlash(forward, seekStepSeconds, (flash?.token ?: 0) + 1)
                        },
                        onLongPress = { if (activeNow && gestureLock?.active != true) { holding = true; haptics.heavy(); session.holdSpeed(holdSpeed) } },
                        onPress = { tryAwaitRelease(); if (holding) { holding = false; session.holdSpeed(null) } },
                    )
                },
        )
        flash?.let { f -> key(f.token) { SeekBadge(f.forward, f.seconds, onMedia, Modifier.align(if (f.forward) Alignment.CenterEnd else Alignment.CenterStart)) } }
        val held by session.heldSpeed.collectAsState()
        held?.let { speed -> Text(
            "${if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()}× speed", color = onMedia, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = Spacing.md).clip(CircleShape).background(ExtraColors.mediaScrim.copy(alpha = 0.55f)).padding(horizontal = Spacing.md, vertical = Spacing.xs).testTag("player.holdSpeed"),
        ) }

        if (buffering && failed == null) {
            val spinnerSize = if (!controlsEnabled) 18.dp else 40.dp
            LoadingSpinner(
                modifier = Modifier.align(Alignment.Center).size(spinnerSize).testTag("player.spinner"),
                color = onMedia,
            )
        }
        if (resolving && failed == null && resolvingNote != null && controlsEnabled) Text(resolvingNote, Modifier.align(Alignment.BottomCenter).padding(Spacing.gutter).testTag("player.resolvingNote"), color = onMedia, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)

        if (failed != null && controlsEnabled) {
            Column(Modifier.align(Alignment.Center).padding(Spacing.md), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                val tiersFailed = failed.extractorError as? com.greenaltambe.vela.extractor.TiersFailed
                val needsSignIn = failed.extractorError?.reason == com.greenaltambe.vela.extractor.FailureReason.SIGN_IN_REQUIRED
                val uri = androidx.compose.ui.platform.LocalUriHandler.current
                var confirmingTry by remember { mutableStateOf(false) }
                if (failed.extractorError is com.greenaltambe.vela.extractor.ExtractorError.AgeRestrictedDisabled) {
                    Text(com.greenaltambe.vela.extractor.ExtractorError.AgeRestrictedDisabled.MESSAGE, color = onMedia, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.testTag("player.error"))
                    if (openAgeSetting != null) OutlinedButton(onClick = openAgeSetting, modifier = Modifier.testTag("player.openAgeSetting")) { Text("Open setting", maxLines = 1) }
                    OutlinedButton(onClick = { runCatching { uri.openUri("https://www.youtube.com/watch?v=${video.id}") } }, modifier = Modifier.testTag("player.openInBrowser")) { Text("Open in browser", maxLines = 1) }
                } else if (tiersFailed != null) {
                    // Says which tiers failed and why, and what the account did; the buttons are the actions that can change the outcome.
                    Text(tiersFailed.message, color = onMedia, style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.testTag("player.error"))
                    if (tiersFailed.account == com.greenaltambe.vela.extractor.AccountStatus.PAUSED && tryAccountNow != null) {
                        OutlinedButton(onClick = { confirmingTry = true }, modifier = Modifier.testTag("player.tryAccountNow")) { Text("Try account now", maxLines = 1) }
                    }
                    OutlinedButton(onClick = { runCatching { uri.openUri("https://www.youtube.com/watch?v=${video.id}") } }, modifier = Modifier.testTag("player.openInBrowser")) { Text("Open in browser", maxLines = 1) }
                    if (confirmingTry) androidx.compose.material3.AlertDialog(
                        onDismissRequest = { confirmingTry = false },
                        title = { Text("Try the account now?") },
                        text = { Text("The pause protects the account. Trying early sends one request; if Google pushes back again the pause gets longer.") },
                        confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmingTry = false; tryAccountNow?.invoke(); session.start(video.id, video) }, modifier = Modifier.testTag("player.tryAccountNowConfirm")) { Text("Try now") } },
                        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmingTry = false }) { Text("Keep paused") } },
                    )
                } else if (needsSignIn) {
                    // Retrying cannot help: YouTube wants a signed-in viewer (age limit or bot check). Say so once, and offer the browser.
                    Text(
                        "YouTube only plays this video to a signed-in viewer (age limit or bot check). Vela could not use an account for it. Retrying will not change that.",
                        color = onMedia, style = MaterialTheme.typography.bodyMedium, maxLines = 5, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.testTag("player.error"),
                    )
                    OutlinedButton(onClick = { runCatching { uri.openUri("https://www.youtube.com/watch?v=${video.id}") } }, modifier = Modifier.testTag("player.openInBrowser")) { Text("Open in browser", maxLines = 1) }
                } else {
                    Text(failed.message, color = onMedia, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.testTag("player.error"))
                    OutlinedButton(onClick = { session.start(video.id, video) }, modifier = Modifier.testTag("player.retry")) { Text("Retry", maxLines = 1) }
                }
            }
        }

    // The player's menu buttons (mute, captions, audio, quality, speed, sleep). In fullscreen they sit in the bottom row; in the small portrait player
    // they move to the top bar, so the bottom holds only the time, the chapter and the seek bar, well away from the play buttons.
    val captionsButton: @Composable () -> Unit = {
                        if (captions.isEmpty()) ControlButton({}, enabled = false, modifier = Modifier.testTag("player.captions")) { Icon(AppIcons.captions, "No captions for this video", tint = onMedia.copy(alpha = 0.4f)) }
                        else ControlButton({ touch(); session.toggleCaptions() }, haptic = if (activeCaption != null) HapticKind.TOGGLE_OFF else HapticKind.TOGGLE_ON, onLongClick = { touch(); menu = Menu.CAPTIONS }, modifier = Modifier.testTag("player.captions")) { SwapIcon(AppIcons.captions, "Captions", if (activeCaption != null) MaterialTheme.colorScheme.primary else onMedia) }
    }
    val menuButtons: @Composable androidx.compose.foundation.layout.RowScope.(Boolean) -> Unit = { spaced ->
                        ControlButton({ touch(); session.toggleMute() }, haptic = if (muted) HapticKind.TOGGLE_ON else HapticKind.TOGGLE_OFF, modifier = Modifier.testTag("player.mute")) {
                            SwapIcon(if (muted) AppIcons.mute else AppIcons.volume, if (muted) "Unmute" else "Mute", onMedia)
                        }
                        if (desktop) Scrubber(
                            value = if (muted) 0f else volume, onPreview = { touches++ }, onCommit = { touch(); session.setVolume(it) },
                            modifier = Modifier.widthIn(max = 96.dp).testTag("player.volume"), description = "Volume", valueDescription = "${((if (muted) 0f else volume) * 100).toInt()} percent",
                            trackColor = onMedia.copy(alpha = 0.3f), style = com.greenaltambe.vela.core.settings.ProgressStyle.BOLD_DOT, lockGestures = false,
                        )
                        if (spaced) Box(Modifier.weight(1f))
                        captionsButton()
                        // Only videos with more than one soundtrack (languages, dubs) offer a choice.
                        if (audioTracks.isNotEmpty()) ControlButton({ touch(); menu = Menu.AUDIO }, modifier = Modifier.testTag("player.audio-track")) { Icon(AppIcons.audioTrack, "Audio track", tint = onMedia) }
                        ControlButton({ touch(); menu = Menu.QUALITY }, modifier = Modifier.testTag("player.quality")) { Icon(AppIcons.quality, "Quality", tint = onMedia) }
                        ControlButton({ touch(); menu = Menu.SPEED }, modifier = Modifier.testTag("player.speed")) { Icon(AppIcons.speed, "Speed", tint = onMedia) }
                        ControlButton({ touch(); menu = Menu.SLEEP }, modifier = Modifier.testTag("player.sleep-timer")) { SwapIcon(AppIcons.timer, "Sleep timer", if (sleep != null) MaterialTheme.colorScheme.primary else onMedia) }
    }

        AnimatedVisibility(showControls, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(ExtraColors.mediaScrim.copy(alpha = 0.35f))) {
                // In fullscreen the controls keep clear of the screen's edges (a phone's rounded corners and bezel swallow anything flush against them).
                Box(Modifier.fillMaxSize().then(if (fullscreen) Modifier.padding(horizontal = Spacing.gutter * 1.5f, vertical = Spacing.md) else Modifier)) {
                // Top: back.
                Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) ControlButton({ touch(); onBack() }, modifier = Modifier.testTag("player.back")) {
                        Icon(AppIcons.back, "Back", tint = onMedia)
                    }
                    // The small portrait player keeps its top bar to three things (back, captions, settings); quality, speed, soundtrack and the
                    // sleep timer live one tap away in the settings sheet (the bottom of the picture is for the time, chapter and seek bar).
                    if (compact) {
                        Box(Modifier.weight(1f))
                        captionsButton()
                        ControlButton({ touch(); menu = Menu.SETTINGS }, modifier = Modifier.testTag("player.settings")) { Icon(AppIcons.settings, "Player settings", tint = onMedia) }
                    }
                }

                // Centre: previous video, play / pause, next video (seeking is the double tap).
                // In the small portrait player the seek bar and the buttons share little height: the buttons sit higher and smaller so a press on
                // pause can never land on the seek bar.
                // While buffering the spinner takes the centre; the buttons would be drawn on top of it.
                if (active && !buffering) Row(Modifier.align(Alignment.Center).offset(y = if (compact) (-8).dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(if (fullscreen) Spacing.lg else Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    ControlButton({ touch(); onPrevious?.invoke() }, enabled = onPrevious != null, size = if (fullscreen) PlayerButtonSizes.transportFullscreen else PlayerButtonSizes.transportCompact, pressShift = (-5).dp, modifier = Modifier.testTag("player.previous")) { Icon(AppIcons.skipPrevious, "Previous video", tint = onMedia.copy(alpha = if (onPrevious != null) 1f else 0.35f)) }
                    ControlButton({ touch(); if (playing) session.pause() else session.play() }, size = if (fullscreen) PlayerButtonSizes.playFullscreen else PlayerButtonSizes.playCompact, haptic = if (playing) HapticKind.TOGGLE_OFF else HapticKind.TOGGLE_ON, modifier = Modifier.testTag("player.playPause")) {
                        SwapIcon(if (playing) AppIcons.pause else AppIcons.play, if (playing) "Pause" else "Play", onMedia, Modifier.size(40.dp))
                    }
                    ControlButton({ touch(); onNext?.invoke() }, enabled = onNext != null, size = if (fullscreen) PlayerButtonSizes.transportFullscreen else PlayerButtonSizes.transportCompact, pressShift = 5.dp, modifier = Modifier.testTag("player.next")) { Icon(AppIcons.skipNext, "Next video", tint = onMedia.copy(alpha = if (onNext != null) 1f else 0.35f)) }
                }

                // Bottom: seek bar, time, icon row.
                // In the small portrait player the whole block sits 8 dp lower, so the seek bar lies near the picture's bottom edge (as in YouTube) and the
                // time/chapter pills have the room above it, clear of the play buttons. Not lower: the thumb grows to about 13 dp while held, and what
                // lies below the picture is cut off.
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = Spacing.xs).then(if (compact) Modifier.offset(y = 8.dp) else Modifier)) {
                    val shown = preview?.let { (it * duration).toLong() } ?: position
                    // The time and the chapter are two quiet pills on one line (the way YouTube shows them), the fullscreen button at the far end.
                    val pill = Modifier.clip(CircleShape).background(ExtraColors.mediaScrim.copy(alpha = 0.30f))
                    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm).then(if (compact) Modifier.offset(y = 20.dp) else Modifier), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(if (live) (if (behind == null || behind < LIVE_EDGE_MS) "LIVE" else "-${formatTimestamp(behind / 1000)} behind live") else "${if (showRemaining) "-" + formatTimestamp(((duration - shown).coerceAtLeast(0)) / 1000) else formatTimestamp(shown / 1000)} / ${formatTimestamp(duration / 1000)}", color = onMedia, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, modifier = pill.then(if (live) Modifier else Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) { touch(); haptics.tick(); showRemaining = !showRemaining }).padding(horizontal = Spacing.sm, vertical = Spacing.xs).testTag("player.time"))
                        if (live && behind != null && behind >= LIVE_EDGE_MS) TextButton(onClick = { touch(); session.goLive() }, modifier = Modifier.testTag("player.goLive")) { Text("Go live", color = onMedia, maxLines = 1) }
                        // The current chapter's name sits above the seek bar; tapping it opens the chapter list.
                        currentChapter(chapters, shown)?.let { ch ->
                            Row(
                                pill.weight(1f, fill = false).clickable { touch(); if (onOpenChapters != null) onOpenChapters() else chaptersOpen = true }.padding(start = Spacing.sm, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs).testTag("player.chapterTitle"),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) {
                                Text(ch.title, Modifier.weight(1f, fill = false), color = onMedia, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(AppIcons.chevronRight, null, Modifier.size(16.dp), tint = onMedia)
                            }
                        }
                        // Whatever is left over pushes the fullscreen button to the right edge.
                        Box(Modifier.weight(1f))
                        if (compact && window != null) ControlButton({ touch(); window.toggleFullscreen() }, size = PlayerButtonSizes.compactFullscreenToggle, modifier = Modifier.testTag("player.fullscreen")) {
                            Icon(AppIcons.fullscreen, "Fullscreen", tint = onMedia)
                        }
                    }
                    Spacer(Modifier.height(if (compact) 0.dp else Spacing.xs))
                    Scrubber(
                        value = if (live && behind != null && duration > 0) (1f - behind.toFloat() / duration).coerceIn(0f, 1f) else if (duration > 0) position.toFloat() / duration else 0f,
                        onPreview = { preview = it; touches++ },
                        onCommit = { f -> touch(); haptics.confirm(); session.seekTo((f * duration).toLong()) },
                        enabled = duration > 0,
                        buffered = if (duration > 0) ((position + stats.bufferedAheadMs).toFloat() / duration).coerceIn(0f, 1f) else null,
                        markers = if (duration > 0) markers.map { ScrubMarker(it.startMs.toFloat() / duration, it.endMs.toFloat() / duration, it.color) } else emptyList(),
                        ticks = if (duration > 0) chapters.drop(1).map { (it.startSeconds * 1000f / duration) } else emptyList(),
                        animate = playing,
                        chapters = chapters,
                        durationMs = duration,
                        videoThumbnailUrl = video.thumbnailUrl,
                        previewLabel = { f -> formatTimestamp((f * duration / 1000).toLong()) },
                        description = "Seek",
                        valueDescription = "${formatTimestamp(shown / 1000)} of ${formatTimestamp(duration / 1000)}",
                        trackColor = onMedia.copy(alpha = 0.3f), bufferedColor = onMedia.copy(alpha = 0.5f), tickColor = onMedia,
                        modifier = Modifier.testTag("player.seek"),
                    )
                    // Fullscreen and the desktop window (not compact) keep the menu buttons here; only the small phone player moves them to the top bar.
                    if (!compact) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        menuButtons(true)
                        if (window != null) ControlButton({ touch(); window.toggleFullscreen() }, modifier = Modifier.testTag("player.fullscreen")) {
                            SwapIcon(if (window.isFullscreen) AppIcons.fullscreenExit else AppIcons.fullscreen, if (window.isFullscreen) "Exit fullscreen" else "Fullscreen", onMedia)
                        }
                    }
                }
                }
            }
        }

        // The skip prompt and the "skipped, Undo" notice outlive the controls, so they have a place of their own: the bottom-right corner, just above the
        // time / chapter row (as in YouTube), and drawn after the controls so no button of theirs can lie over it.
        if (controlsEnabled) Box(
            Modifier.align(Alignment.BottomEnd).padding(end = if (fullscreen) Spacing.gutter * 1.5f else Spacing.md, bottom = if (compact) 68.dp else 148.dp),
        ) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides onMedia) { overlay() }
        }
    }

    if (chaptersOpen && chapters.isNotEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            ChaptersPanel(
                chapters = chapters,
                positionMs = position,
                videoThumbnailUrl = video.thumbnailUrl,
                onSeek = { session.seekTo(it) },
                onDismiss = { chaptersOpen = false },
                modifier = Modifier.fillMaxWidth().fillMaxHeight(if (fullscreen) 0.65f else 0.75f),
            )
        }
    }
    val dismissMenu = { menu = null }
    when (menu) {
        Menu.CAPTIONS -> PlayerMenuSheet("Captions", dismissMenu, subtitle = activeCaption?.label ?: "Off") {
            PlayerMenuRow("Off", selected = activeCaption == null, onClick = { session.selectCaption(null); menu = null }, index = 0)
            captions.forEachIndexed { i, c -> PlayerMenuRow(c.label, selected = activeCaption == c, onClick = { session.selectCaption(c); menu = null }, index = i + 1) }
        }
        Menu.AUDIO -> PlayerMenuSheet("Audio track", dismissMenu) {
            val inUse = audioTrack ?: audioTracks.firstOrNull { it.original }?.key ?: audioTracks.firstOrNull()?.key
            audioTracks.forEachIndexed { i, t -> PlayerMenuRow(t.label, selected = t.key == inUse, onClick = { session.selectAudioTrack(if (t.original) null else t.key); menu = null }, index = i) }
        }
        Menu.QUALITY -> PlayerMenuSheet("Quality", dismissMenu, subtitle = (sessionState as? SessionState.Ready)?.maxHeight?.let { "${it}p" } ?: "Auto") {
            val st = sessionState as? SessionState.Ready
            PlayerMenuRow("Auto", selected = st?.maxHeight == null, onClick = { session.selectQuality(null); menu = null }, index = 0)
            st?.heights?.forEachIndexed { i, h -> PlayerMenuRow("${h}p", selected = st.maxHeight == h, onClick = { session.selectQuality(h); menu = null }, index = i + 1) }
            PlayerMenuRow("Stats for nerds", selected = false, onClick = { showStats = true; menu = null }, index = (st?.heights?.size ?: 0) + 1, leading = AppIcons.info)
        }
        Menu.SETTINGS -> PlayerMenuSheet("Settings", dismissMenu) {
            PlayerMenuRow("Quality", selected = false, onClick = { menu = Menu.QUALITY }, index = 0, leading = AppIcons.quality, supporting = (sessionState as? SessionState.Ready)?.maxHeight?.let { "${it}p" } ?: "Auto")
            PlayerMenuRow("Playback speed", selected = false, onClick = { menu = Menu.SPEED }, index = 1, leading = AppIcons.speed, supporting = formatSpeed(player.speed))
            if (audioTracks.isNotEmpty()) PlayerMenuRow("Audio track", selected = false, onClick = { menu = Menu.AUDIO }, index = 2, leading = AppIcons.audioTrack)
            PlayerMenuRow("Sleep timer", selected = false, onClick = { menu = Menu.SLEEP }, index = 3, leading = AppIcons.timer, supporting = if (sleep != null) "On" else "Off")
            if (autoplay != null && onAutoplayChange != null) PlayerMenuRow(
                "Autoplay", selected = false, onClick = { onAutoplayChange(!autoplay) }, index = 4, leading = AppIcons.skipNext, supporting = "Play the next video when this one ends",
                trailing = { androidx.compose.material3.Switch(autoplay, { onAutoplayChange(it) }, Modifier.testTag("video.autoplay")) },
            )
        }
        Menu.SPEED -> PlayerMenuSheet("Playback speed", dismissMenu) { SpeedMenuContent(player.speed) { session.setSpeed(it) } }
        Menu.SLEEP -> PlayerMenuSheet("Sleep timer", dismissMenu, subtitle = if (sleep != null) "On" else "Off") {
            SleepTimerPresets.forEachIndexed { i, m -> PlayerMenuRow(describeTimer(m), selected = sleep == m, onClick = { session.setSleepTimer(m); menu = null }, index = i, leading = AppIcons.timer) }
            if (sleep != null) PlayerMenuRow("Turn off", selected = false, onClick = { session.setSleepTimer(null); menu = null }, index = SleepTimerPresets.size)
        }
        null -> {}
    }
    if (showStats) AlertDialog(
        onDismissRequest = { showStats = false },
        title = { Text("Stats for nerds") },
        text = { Text(statsText(stats, sessionState), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("player.stats")) },
        confirmButton = { TextButton(onClick = { showStats = false }) { Text("Close") } },
    )
}

private enum class Menu { QUALITY, SPEED, SLEEP, CAPTIONS, AUDIO, SETTINGS }

private fun describeTimer(mode: SleepTimerMode) = when (mode) {
    is SleepTimerMode.After -> "${mode.duration.inWholeMinutes} min"
    SleepTimerMode.EndOfVideo -> "End of video"
}

private fun statsText(stats: PlaybackStats, state: SessionState): String = buildString {
    val ready = state as? SessionState.Ready
    appendLine("client: ${ready?.client ?: "-"}   plan: ${ready?.plan?.describe() ?: "-"}")
    appendLine("video: ${stats.videoResolution ?: "-"}  ${stats.videoCodec ?: "-"}  ${stats.videoBitrateBps.kbps()}")
    appendLine("audio: ${stats.audioCodec ?: "-"}  ${stats.audioBitrateBps.kbps()}")
    appendLine("bandwidth estimate: ${stats.bandwidthEstimateBps?.let { "${it / 100_000 / 10.0} Mbit/s" } ?: "-"}")
    appendLine("buffered ahead: ${stats.bufferedAheadMs / 1000.0} s")
    append("dropped frames: ${stats.droppedFrames}")
}

private fun Long?.kbps() = this?.let { "${it / 1000} kbit/s" } ?: "-"

/** Within this of the live edge a live stream counts as "live": ExoPlayer's normal offset on YouTube HLS measured 22 s on the phone, mpv starts 13-16 s behind. */
private const val LIVE_EDGE_MS = 40_000L

private fun Modifier.androidx_onPos(onBottom: (Float) -> Unit): Modifier =
    this.onGloballyPositioned { onBottom(it.positionInWindow().y + it.size.height) }

/** The "+10 s" / "−10 s" badge of a double-tap: pops in with a spring, and its arrow leans the way the jump goes. */
@Composable
private fun SeekBadge(forward: Boolean, seconds: Int, onMedia: Color, modifier: Modifier) {
    val motion = LocalMotion.current
    val pop = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(if (motion.reduced) 1f else 0.5f) }
    LaunchedEffect(Unit) { if (!motion.reduced) pop.animateTo(1f, com.greenaltambe.vela.ui.look.VelaMotion.bouncy(motion)) }
    Row(
        modifier.padding(horizontal = Spacing.lg)
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = pop.value.coerceIn(0f, 1f); translationX = (1f - pop.value) * (if (forward) -24.dp.toPx() else 24.dp.toPx()) }
            .clip(CircleShape).background(ExtraColors.mediaScrim.copy(alpha = 0.55f)).padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (!forward) Icon(AppIcons.rewind, null, tint = onMedia)
        Text("${if (forward) "+" else "−"}$seconds s", color = onMedia, style = MaterialTheme.typography.bodyMedium)
        if (forward) Icon(AppIcons.forward, null, tint = onMedia)
    }
}
