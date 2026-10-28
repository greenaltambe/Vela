package com.greenaltambe.vela.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
    /** Drawn over the picture, above the controls' bottom edge: SponsorBlock's skip prompt and "skipped, Undo" notice. */
    overlay: @Composable () -> Unit = {},
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
    val position = if (active) player.positionMs else 0L
    val duration = if (active) player.durationMs else 0L
    val playing = active && player.status == PlaybackStatus.PLAYING
    val buffering = resolving || (active && player.status == PlaybackStatus.BUFFERING)

    var controlsVisible by remember(video.id) { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<Float?>(null) }
    var menu by remember { mutableStateOf<Menu?>(null) }
    var showStats by remember { mutableStateOf(false) }
    val showControls = controlsEnabled && (controlsVisible || !playing || menu != null || preview != null)

    LaunchedEffect(controlsVisible, playing, touches, menu, preview) {
        if (controlsVisible && playing && menu == null && preview == null) { delay(HIDE_AFTER_MS); controlsVisible = false }
    }
    fun touch() { touches++; controlsVisible = true }

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
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controlsVisible = !controlsVisible; touches++ },
        )

        if (buffering && failed == null) CircularProgressIndicator(Modifier.align(Alignment.Center).size(40.dp).testTag("player.spinner"), color = onMedia)

        if (failed != null) {
            Column(Modifier.align(Alignment.Center).padding(Vela.md), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
                val needsSignIn = failed.extractorError?.reason == com.greenaltambe.vela.extractor.FailureReason.SIGN_IN_REQUIRED
                val uri = androidx.compose.ui.platform.LocalUriHandler.current
                if (needsSignIn) {
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
                if (active) Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(Vela.lg), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { touch(); session.seekTo((position - 10_000).coerceAtLeast(0)) }, modifier = Modifier.size(56.dp).testTag("player.back10")) { Icon(Vela.icons.rewind, "Back 10 seconds", tint = onMedia) }
                    IconButton(onClick = { touch(); if (playing) session.pause() else session.play() }, modifier = Modifier.size(64.dp).testTag("player.playPause")) {
                        Icon(if (playing) Vela.icons.pause else Vela.icons.play, if (playing) "Pause" else "Play", Modifier.size(40.dp), tint = onMedia)
                    }
                    IconButton(onClick = { touch(); session.seekTo((position + 10_000).coerceAtMost(duration.takeIf { it > 0 } ?: Long.MAX_VALUE)) }, modifier = Modifier.size(56.dp).testTag("player.forward10")) { Icon(Vela.icons.forward, "Forward 10 seconds", tint = onMedia) }
                }

                // Bottom: seek bar, time, icon row.
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = Vela.xs)) {
                    val shown = preview?.let { (it * duration).toLong() } ?: position
                    Row(Modifier.fillMaxWidth().padding(horizontal = Vela.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
                        Text("${formatTimestamp(shown / 1000)} / ${formatTimestamp(duration / 1000)}", color = onMedia, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, modifier = Modifier.testTag("player.time"))
                        if (showChapterTitle) currentChapter(chapters, shown)?.let { Text(it.title, Modifier.weight(1f).testTag("player.chapterTitle"), color = onMedia, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    Scrubber(
                        value = if (duration > 0) position.toFloat() / duration else 0f,
                        onPreview = { preview = it; touches++ },
                        onCommit = { f -> touch(); session.seekTo((f * duration).toLong()) },
                        enabled = duration > 0,
                        buffered = if (duration > 0) ((position + stats.bufferedAheadMs).toFloat() / duration).coerceIn(0f, 1f) else null,
                        markers = if (duration > 0) markers.map { ScrubMarker(it.startMs.toFloat() / duration, it.endMs.toFloat() / duration, it.color) } else emptyList(),
                        ticks = if (duration > 0) chapters.drop(1).map { (it.startSeconds * 1000f / duration) } else emptyList(),
                        previewLabel = { f -> formatTimestamp((f * duration / 1000).toLong()) },
                        description = "Seek",
                        valueDescription = "${formatTimestamp(shown / 1000)} of ${formatTimestamp(duration / 1000)}",
                        playedColor = onMedia, trackColor = onMedia.copy(alpha = 0.3f), bufferedColor = onMedia.copy(alpha = 0.5f), tickColor = onMedia,
                        modifier = Modifier.testTag("player.seek"),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { touch(); session.toggleMute() }, modifier = Modifier.testTag("player.mute")) {
                            Icon(if (muted) Vela.icons.mute else Vela.icons.volume, if (muted) "Unmute" else "Mute", tint = onMedia)
                        }
                        if (desktop) Scrubber(
                            value = if (muted) 0f else volume, onPreview = { touches++ }, onCommit = { touch(); session.setVolume(it) },
                            modifier = Modifier.widthIn(max = 96.dp).testTag("player.volume"), description = "Volume", valueDescription = "${((if (muted) 0f else volume) * 100).toInt()} percent",
                            playedColor = onMedia, trackColor = onMedia.copy(alpha = 0.3f), thumbRadius = 6.dp,
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
