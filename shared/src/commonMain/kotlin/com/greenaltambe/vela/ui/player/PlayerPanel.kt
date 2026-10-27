package com.greenaltambe.vela.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.core.network.NetworkType
import com.greenaltambe.vela.extractor.Chapter
import com.greenaltambe.vela.library.SleepTimerMode
import com.greenaltambe.vela.library.SleepTimerPresets
import com.greenaltambe.vela.library.currentChapter
import com.greenaltambe.vela.library.formatTimestamp
import com.greenaltambe.vela.player.PlaybackStats
import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.player.PlayerHost
import com.greenaltambe.vela.player.SessionState
import com.greenaltambe.vela.ui.describe
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.platform.LocalWindowControls

private val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/**
 * The picture and its transport: play/pause, seek bar (chapter ticks, markers), volume and fullscreen; speed, quality, sleep
 * timer and stats are [PlayerOptions]. [fullscreen] lays the picture out to fill the window with the controls over it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerPanel(
    host: PlayerHost,
    chapters: List<Chapter>,
    markers: List<SeekMarker>,
    modifier: Modifier = Modifier,
    fullscreen: Boolean = false,
    /** Shown right under the picture: SponsorBlock's skip prompt and "skipped, Undo" notice. */
    overlay: @Composable () -> Unit = {},
) {
    val session = host.session
    val sessionState by session.state.collectAsState()
    val player by host.controllerState.collectAsState()
    val volume by session.volume.collectAsState()
    val muted by session.muted.collectAsState()
    val sleep by session.sleepTimer.collectAsState()
    val window = LocalWindowControls.current
    var preview by remember { mutableStateOf<Long?>(null) }
    val playing = player.status == PlaybackStatus.PLAYING
    val shownPosition = preview ?: player.positionMs

    @Composable
    fun Controls() {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
            SeekBar(player.positionMs, player.durationMs, chapters, markers, onSeek = session::seekTo, onDrag = { preview = it })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
                OutlinedButton(onClick = { if (playing) session.pause() else session.play() }, modifier = Modifier.testTag("player.playPause")) { Text(if (playing) "Pause" else "Play") }
                Text("${formatTimestamp(shownPosition / 1000)} / ${formatTimestamp(player.durationMs / 1000)}", style = MaterialTheme.typography.bodySmall)
                currentChapter(chapters, shownPosition)?.let {
                    Text(it.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.weight(1f).testTag("player.chapterTitle"))
                } ?: Box(Modifier.weight(1f))
                TextButton(onClick = session::toggleMute) { Text(if (muted) "Unmute" else "Mute") }
                Slider(
                    value = if (muted) 0f else volume,
                    onValueChange = session::setVolume,
                    valueRange = 0f..1f,
                    modifier = Modifier.widthIn(max = 120.dp).testTag("player.volume"),
                )
                if (window != null) {
                    TextButton(onClick = window::toggleFullscreen, modifier = Modifier.testTag("player.fullscreen")) { Text(if (window.isFullscreen) "Exit fullscreen" else "Fullscreen") }
                }
            }
        }
    }

    if (fullscreen) {
        Box(modifier.fillMaxSize()) {
            host.Surface(Modifier.fillMaxSize())
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(Vela.md)) { Controls() }
        }
        return
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
        host.Surface(Modifier.fillMaxWidth().aspect16x9())
        overlay()
        Text(statusLine(sessionState, player.status), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("player.status"))
        sleep?.let { Text("Sleep timer: ${describeTimer(it.mode)}", style = MaterialTheme.typography.bodySmall) }
        Controls()
    }
}

/**
 * Speed, quality, sleep timer and the stats panel. Kept apart from [PlayerPanel] so the picture and transport can stay
 * pinned at the top of the page while these scroll with everything else.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerOptions(host: PlayerHost, modifier: Modifier = Modifier) {
    val session = host.session
    val sessionState by session.state.collectAsState()
    val player by host.controllerState.collectAsState()
    val stats by host.stats.collectAsState()
    val network by session.networkType.collectAsState()
    val sleep by session.sleepTimer.collectAsState()
    var showStats by remember { mutableStateOf(false) }
    var showSleepMenu by remember { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Vela.xs), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
            Text("Speed", Modifier.align(Alignment.CenterVertically))
            speeds.forEach { s -> OutlinedButton(onClick = { session.setSpeed(s) }, enabled = player.speed != s) { Text("${s}x") } }
        }
        (sessionState as? SessionState.Ready)?.takeIf { it.heights.isNotEmpty() }?.let { ready ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Vela.xs), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
                Text("Quality", Modifier.align(Alignment.CenterVertically))
                OutlinedButton(onClick = { session.selectQuality(null) }, enabled = ready.maxHeight != null) { Text("Auto") }
                ready.heights.forEach { h -> OutlinedButton(onClick = { session.selectQuality(h) }, enabled = ready.maxHeight != h) { Text("${h}p") } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Vela.xs)) {
            Box {
                OutlinedButton(onClick = { showSleepMenu = true }, modifier = Modifier.testTag("player.sleep")) { Text(if (sleep != null) "Sleep timer on" else "Sleep timer") }
                DropdownMenu(showSleepMenu, { showSleepMenu = false }) {
                    SleepTimerPresets.forEach { mode ->
                        DropdownMenuItem(text = { Text(describeTimer(mode)) }, onClick = { session.setSleepTimer(mode); showSleepMenu = false })
                    }
                    if (sleep != null) DropdownMenuItem(text = { Text("Turn off") }, onClick = { session.setSleepTimer(null); showSleepMenu = false })
                }
            }
            OutlinedButton(onClick = { showStats = !showStats }) { Text(if (showStats) "Hide stats" else "Stats") }
        }
        if (showStats) Text(statsText(stats, sessionState, network), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("player.stats"))
    }
}

/** 16:9, but never taller than 60% of what the parent allows: a wide window must not push the controls off-screen. */
private fun Modifier.aspect16x9(): Modifier = this.layout { measurable, constraints ->
    val width = constraints.maxWidth
    val height = (width * 9 / 16).coerceAtMost((constraints.maxHeight.takeIf { it != androidx.compose.ui.unit.Constraints.Infinity }?.let { it * 6 / 10 } ?: Int.MAX_VALUE))
    val w = if (height * 16 / 9 < width) height * 16 / 9 else width
    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, height))
    layout(width, height) { placeable.place((width - w) / 2, 0) }
}

private fun describeTimer(mode: SleepTimerMode) = when (mode) {
    is SleepTimerMode.After -> "${mode.duration.inWholeMinutes} min"
    SleepTimerMode.EndOfVideo -> "End of video"
}

private fun statusLine(state: SessionState, status: PlaybackStatus) = when (state) {
    SessionState.Idle -> "Idle"
    is SessionState.Resolving -> if (state.refresh) "Refreshing expired streams..." else "Resolving streams..."
    is SessionState.Ready -> "$status via ${state.client}: ${state.plan.describe()}"
    is SessionState.Failed -> "Failed: ${state.message}"
}

private fun statsText(stats: PlaybackStats, state: SessionState, network: NetworkType): String = buildString {
    val ready = state as? SessionState.Ready
    appendLine("network: $network")
    appendLine("client: ${ready?.client ?: "-"}   plan: ${ready?.plan?.describe() ?: "-"}")
    appendLine("video: ${stats.videoResolution ?: "-"}  ${stats.videoCodec ?: "-"}  ${stats.videoBitrateBps.kbps()}")
    appendLine("audio: ${stats.audioCodec ?: "-"}  ${stats.audioBitrateBps.kbps()}")
    appendLine("bandwidth estimate: ${stats.bandwidthEstimateBps?.let { "${it / 100_000 / 10.0} Mbit/s" } ?: "-"}")
    appendLine("buffered ahead: ${stats.bufferedAheadMs / 1000.0} s")
    append("dropped frames: ${stats.droppedFrames}")
}

private fun Long?.kbps() = this?.let { "${it / 1000} kbit/s" } ?: "-"
