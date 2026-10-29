package com.greenaltambe.vela.ui.player

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.toColor
import com.greenaltambe.vela.ui.platform.LocalVolumeSlider
import com.greenaltambe.vela.ui.platform.LocalWindowControls
import kotlinx.coroutines.delay

/** A coloured span of the timeline in milliseconds (a SponsorBlock segment). */
data class SeekMarker(val startMs: Long, val endMs: Long, val color: Color)

/** The brief "+10 s" / "−10 s" badge a double-tap shows; [token] makes each tap a new one so the badge restarts its timer. */
private class SeekFlash(val forward: Boolean, val seconds: Int, val token: Int)

private val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
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
    /** How far a double-tap (and the skip buttons) jump, in seconds; and the speed while the picture is held. Both are playback settings. */
    seekStepSeconds: Int = 10,
    holdSpeed: Float = 2f,
) {
    val session = host.session
    val sessionState by session.state.collectAsState()
    val player by host.controllerState.collectAsState()
    val stats by host.stats.collectAsState()
    val volume by session.volume.collectAsState()
    val muted by session.muted.collectAsState()
    val sleep by session.sleepTimer.collectAsState()
    val window = LocalWindowControls.current
    val desktop = LocalVolumeSlider.current // desktop has its own volume; Android uses the system's

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
    var showStats by remember { mutableStateOf(false) }
    val showControls = controlsEnabled && (controlsVisible || menu != null || preview != null)
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

    val boxModifier = if (fullscreen) modifier.fillMaxSize() else modifier.fillMaxWidth().aspectRatio(16f / 9f)
    val onMedia = Vela.colors.onMedia.toColor()
    Box(boxModifier.background(Vela.colors.mediaBackground.toColor()).testTag("player.view")) {
        host.Surface(Modifier.fillMaxSize())

        // The poster stands in until this video has a picture of its own.
        if (!active || (player.status == PlaybackStatus.IDLE)) {
            Box(Modifier.fillMaxSize().background(Vela.colors.mediaBackground.toColor()).testTag("player.poster")) {
                video.thumbnailUrl?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
        }

        // Taps toggle the controls. Under the controls, so their buttons take their own taps.
        Box(
            Modifier.fillMaxSize().testTag("player.surface")
                .pointerInput(video.id, seekStepSeconds, holdSpeed) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible; touches++ },
                        onDoubleTap = { at ->
                            if (!activeNow) return@detectTapGestures
                            val forward = at.x > size.width / 2f
                            val target = (positionNow + (if (forward) 1 else -1) * seekStepSeconds * 1000L).coerceAtLeast(0).let { if (durationNow > 0) it.coerceAtMost(durationNow) else it }
                            session.seekTo(target)
                            flash = SeekFlash(forward, seekStepSeconds, (flash?.token ?: 0) + 1)
                        },
                        onLongPress = { if (activeNow) { holding = true; session.holdSpeed(holdSpeed) } },
                        onPress = { tryAwaitRelease(); if (holding) { holding = false; session.holdSpeed(null) } },
                    )
                },
        )
        flash?.let { f ->
            Row(
                Modifier.align(if (f.forward) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = Vela.lg).clip(Vela.pillShape).background(Vela.colors.mediaScrim.toColor().copy(alpha = 0.55f)).padding(horizontal = Vela.md, vertical = Vela.sm).testTag("player.seekFlash"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.xs),
            ) {
                if (!f.forward) Icon(Vela.icons.rewind, null, tint = onMedia)
                Text("${if (f.forward) "+" else "−"}${f.seconds} s", color = onMedia, style = MaterialTheme.typography.bodyMedium)
                if (f.forward) Icon(Vela.icons.forward, null, tint = onMedia)
            }
        }
        if (holding) Text(
            "${if (holdSpeed == holdSpeed.toInt().toFloat()) holdSpeed.toInt().toString() else holdSpeed.toString()}× speed", color = onMedia, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = Vela.md).clip(Vela.pillShape).background(Vela.colors.mediaScrim.toColor().copy(alpha = 0.55f)).padding(horizontal = Vela.md, vertical = Vela.xs).testTag("player.holdSpeed"),
        )

        if (buffering && failed == null) CircularProgressIndicator(Modifier.align(Alignment.Center).size(40.dp).testTag("player.spinner"), color = onMedia)
        if (resolving && failed == null && resolvingNote != null) Text(resolvingNote, Modifier.align(Alignment.BottomCenter).padding(Vela.gutter).testTag("player.resolvingNote"), color = onMedia, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)

        if (failed != null) {
            Column(Modifier.align(Alignment.Center).padding(Vela.md), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
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

        // The skip prompt and the "skipped, Undo" notice must outlive the controls, so they have a place of their own.
        if (controlsEnabled) Box(Modifier.align(Alignment.TopEnd).padding(top = Vela.sm, end = Vela.sm, start = 56.dp)) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides onMedia) { overlay() }
        }

        AnimatedVisibility(showControls, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Vela.colors.mediaScrim.toColor().copy(alpha = 0.35f))) {
                // Top: back.
                if (onBack != null) IconButton(onClick = { touch(); onBack() }, modifier = Modifier.align(Alignment.TopStart).testTag("player.back")) {
                    Icon(Vela.icons.back, "Back", tint = onMedia)
                }

                // Centre: -10 s, play / pause, +10 s.
                // In the small portrait player the seek bar and the buttons share little height: the buttons sit higher and smaller so a press on
                // pause can never land on the seek bar.
                if (active) Row(Modifier.align(Alignment.Center).offset(y = if (fullscreen) 0.dp else (-26).dp), horizontalArrangement = Arrangement.spacedBy(if (fullscreen) Vela.lg else Vela.md), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { touch(); session.seekTo((position - seekStepSeconds * 1000L).coerceAtLeast(0)) }, modifier = Modifier.size(if (fullscreen) 56.dp else 48.dp).testTag("player.back10")) { Icon(Vela.icons.rewind, "Back $seekStepSeconds seconds", tint = onMedia) }
                    IconButton(onClick = { touch(); if (playing) session.pause() else session.play() }, modifier = Modifier.size(if (fullscreen) 64.dp else 56.dp).testTag("player.playPause")) {
                        Icon(if (playing) Vela.icons.pause else Vela.icons.play, if (playing) "Pause" else "Play", Modifier.size(40.dp), tint = onMedia)
                    }
                    IconButton(onClick = { touch(); session.seekTo((position + seekStepSeconds * 1000L).coerceAtMost(duration.takeIf { it > 0 } ?: Long.MAX_VALUE)) }, modifier = Modifier.size(if (fullscreen) 56.dp else 48.dp).testTag("player.forward10")) { Icon(Vela.icons.forward, "Forward $seekStepSeconds seconds", tint = onMedia) }
                }

                // Bottom: seek bar, time, icon row.
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = Vela.xs)) {
                    val shown = preview?.let { (it * duration).toLong() } ?: position
                    Row(Modifier.fillMaxWidth().padding(horizontal = Vela.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
                        Text(if (live) (if (behind == null || behind < LIVE_EDGE_MS) "LIVE" else "-${formatTimestamp(behind / 1000)} behind live") else "${formatTimestamp(shown / 1000)} / ${formatTimestamp(duration / 1000)}", color = onMedia, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, modifier = Modifier.testTag("player.time"))
                        if (live && behind != null && behind >= LIVE_EDGE_MS) TextButton(onClick = { touch(); session.goLive() }, modifier = Modifier.testTag("player.goLive")) { Text("Go live", color = onMedia, maxLines = 1) }
                        if (showChapterTitle) currentChapter(chapters, shown)?.let { Text(it.title, Modifier.weight(1f).testTag("player.chapterTitle"), color = onMedia, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    Scrubber(
                        value = if (live && behind != null && duration > 0) (1f - behind.toFloat() / duration).coerceIn(0f, 1f) else if (duration > 0) position.toFloat() / duration else 0f,
                        onPreview = { preview = it; touches++ },
                        onCommit = { f -> touch(); session.seekTo((f * duration).toLong()) },
                        enabled = duration > 0,
                        buffered = if (duration > 0) ((position + stats.bufferedAheadMs).toFloat() / duration).coerceIn(0f, 1f) else null,
                        markers = if (duration > 0) markers.map { ScrubMarker(it.startMs.toFloat() / duration, it.endMs.toFloat() / duration, it.color) } else emptyList(),
                        ticks = if (duration > 0) chapters.drop(1).map { (it.startSeconds * 1000f / duration) } else emptyList(),
                        previewLabel = { f -> formatTimestamp((f * duration / 1000).toLong()) },
                        description = "Seek",
                        valueDescription = "${formatTimestamp(shown / 1000)} of ${formatTimestamp(duration / 1000)}",
                        trackColor = onMedia.copy(alpha = 0.3f), bufferedColor = onMedia.copy(alpha = 0.5f), tickColor = onMedia,
                        modifier = Modifier.testTag("player.seek"),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { touch(); session.toggleMute() }, modifier = Modifier.testTag("player.mute")) {
                            Icon(if (muted) Vela.icons.mute else Vela.icons.volume, if (muted) "Unmute" else "Mute", tint = onMedia)
                        }
                        if (desktop) Scrubber(
                            value = if (muted) 0f else volume, onPreview = { touches++ }, onCommit = { touch(); session.setVolume(it) },
                            modifier = Modifier.widthIn(max = 96.dp).testTag("player.volume"), description = "Volume", valueDescription = "${((if (muted) 0f else volume) * 100).toInt()} percent",
                            trackColor = onMedia.copy(alpha = 0.3f), style = com.greenaltambe.vela.core.settings.ProgressStyle.BOLD_DOT,
                        )
                        Box(Modifier.weight(1f))
                        IconButton(onClick = {}, enabled = false, modifier = Modifier.testTag("player.captions")) { Icon(Vela.icons.captions, "Captions (not available yet)", tint = onMedia.copy(alpha = 0.4f)) }
                        MenuButton(Vela.icons.quality, "Quality", Menu.QUALITY, menu, { menu = it }, onMedia, ::touch) {
                            val st = sessionState as? SessionState.Ready
                            DropdownMenuItem(text = { Text("Auto", maxLines = 1) }, onClick = { session.selectQuality(null); menu = null }, enabled = st?.maxHeight != null)
                            st?.heights?.forEach { h -> DropdownMenuItem(text = { Text("${h}p", maxLines = 1) }, onClick = { session.selectQuality(h); menu = null }, enabled = st.maxHeight != h) }
                            DropdownMenuItem(text = { Text("Stats for nerds", maxLines = 1) }, onClick = { showStats = true; menu = null })
                        }
                        MenuButton(Vela.icons.speed, "Speed", Menu.SPEED, menu, { menu = it }, onMedia, ::touch) {
                            speeds.forEach { s -> DropdownMenuItem(text = { Text("${s}x", maxLines = 1) }, onClick = { session.setSpeed(s); menu = null }, enabled = player.speed != s) }
                        }
                        MenuButton(Vela.icons.timer, "Sleep timer", Menu.SLEEP, menu, { menu = it }, if (sleep != null) Vela.colors.primary.toColor() else onMedia, ::touch) {
                            SleepTimerPresets.forEach { m -> DropdownMenuItem(text = { Text(describeTimer(m), maxLines = 1) }, onClick = { session.setSleepTimer(m); menu = null }) }
                            if (sleep != null) DropdownMenuItem(text = { Text("Turn off", maxLines = 1) }, onClick = { session.setSleepTimer(null); menu = null })
                        }
                        if (window != null) IconButton(onClick = { touch(); window.toggleFullscreen() }, modifier = Modifier.testTag("player.fullscreen")) {
                            Icon(if (window.isFullscreen) Vela.icons.fullscreenExit else Vela.icons.fullscreen, if (window.isFullscreen) "Exit fullscreen" else "Fullscreen", tint = onMedia)
                        }
                    }
                }
            }
        }
    }

    if (showStats) AlertDialog(
        onDismissRequest = { showStats = false },
        title = { Text("Stats for nerds") },
        text = { Text(statsText(stats, sessionState), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("player.stats")) },
        confirmButton = { TextButton(onClick = { showStats = false }) { Text("Close") } },
    )
}

private enum class Menu { QUALITY, SPEED, SLEEP }

@Composable
private fun MenuButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, which: Menu, current: Menu?, set: (Menu?) -> Unit,
    tint: Color, touch: () -> Unit, content: @Composable () -> Unit,
) {
    Box {
        IconButton(onClick = { touch(); set(which) }, modifier = Modifier.testTag("player.${label.lowercase().replace(' ', '-')}")) { Icon(icon, label, tint = tint) }
        DropdownMenu(current == which, { set(null) }) { content() }
    }
}

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
