package com.greenaltambe.vela.cast.dlna

import com.greenaltambe.vela.player.PlaybackError
import com.greenaltambe.vela.player.PlaybackErrorKind
import com.greenaltambe.vela.player.PlaybackMetadata
import com.greenaltambe.vela.player.PlaybackPlan
import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.player.PlayerController
import com.greenaltambe.vela.player.PlayerEvent
import com.greenaltambe.vela.player.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Plays on a DLNA renderer. The TV fetches the URL itself, so only plans with one URL work (muxed, HLS, audio-only); request headers cannot be
 * sent, which is fine for the signed googlevideo URLs Vela gets from most sources and reported as an error when a stream needs a cookie.
 *
 * The renderer is polled every [pollMs] for its transport state and position. Speed, volume and boost are not available over AVTransport and are ignored.
 */
class DlnaController(
    private val transport: AvTransport,
    private val scope: CoroutineScope,
    private val pollMs: Long = 1000,
) : PlayerController {
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state
    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events = _events

    private var poller: Job? = null
    private var loadJob: Job? = null
    /** Bumped by every load/stop so a poll or load that was overtaken drops its result. */
    @Volatile private var generation = 0
    @Volatile private var started = false

    override fun load(plan: PlaybackPlan, startPositionMs: Long, playWhenReady: Boolean, metadata: PlaybackMetadata) {
        val gen = ++generation
        poller?.cancel(); loadJob?.cancel()
        val (url, mime, video, headers) = when (plan) {
            is PlaybackPlan.Muxed -> Source(plan.stream.url, plan.stream.mimeType.substringBefore(';').ifBlank { "video/mp4" }, true, plan.headers)
            is PlaybackPlan.Hls -> Source(plan.url, "application/vnd.apple.mpegurl", true, plan.headers)
            is PlaybackPlan.AudioOnly -> Source(plan.audio.url, plan.audio.mimeType.substringBefore(';').ifBlank { "audio/mp4" }, false, plan.headers)
            else -> return fail(PlaybackErrorKind.Other, "This stream cannot be sent to a TV (it needs separate picture and sound, or is a local file).")
        }
        if (headers.keys.any { it.equals("Cookie", true) || it.equals("Authorization", true) })
            return fail(PlaybackErrorKind.Other, "This stream needs a sign-in header, which a TV cannot send.")
        _state.value = PlayerState(PlaybackStatus.BUFFERING, positionMs = startPositionMs)
        started = false
        loadJob = scope.launch(Dispatchers.IO) {
            try {
                transport.setUri(url, metadata.title ?: "Vela", mime, video)
                transport.play()
                if (startPositionMs > 1500) seekWhenPlaying(startPositionMs)
                if (!playWhenReady) transport.pause()
            } catch (e: Exception) { if (gen == generation) fail(PlaybackErrorKind.Network, e.message ?: "The TV did not accept the video"); return@launch }
            if (gen == generation) poller = scope.launch(Dispatchers.IO) { poll(gen) }
        }
    }

    /** Many renderers refuse a seek until they are playing: try for a few seconds. */
    private suspend fun seekWhenPlaying(positionMs: Long) {
        repeat(8) {
            delay(500)
            if (runCatching { transport.state() }.getOrNull() == "PLAYING") { runCatching { transport.seek(positionMs) }; return }
        }
        runCatching { transport.seek(positionMs) }
    }

    private suspend fun poll(gen: Int) {
        var errors = 0
        while (scope.isActive && gen == generation) {
            try {
                val s = transport.state()
                val (pos, dur) = transport.position()
                errors = 0
                if (gen != generation) return
                val old = _state.value
                val status = when (s) {
                    "PLAYING" -> { started = true; PlaybackStatus.PLAYING }
                    "PAUSED_PLAYBACK", "PAUSED_RECORDING" -> PlaybackStatus.PAUSED
                    "TRANSITIONING" -> PlaybackStatus.BUFFERING
                    "STOPPED", "NO_MEDIA_PRESENT" -> if (started) PlaybackStatus.ENDED else PlaybackStatus.BUFFERING
                    else -> old.status
                }
                _state.value = old.copy(status = status, positionMs = pos ?: old.positionMs, durationMs = dur?.takeIf { it > 0 } ?: old.durationMs)
                if (status == PlaybackStatus.ENDED) return
            } catch (e: Exception) {
                // One missed poll is normal (TVs are slow to answer while loading); a run of them means the TV is gone.
                if (++errors >= 5 && gen == generation) { fail(PlaybackErrorKind.Network, "Lost contact with the TV."); return }
            }
            delay(pollMs)
        }
    }

    private fun fail(kind: PlaybackErrorKind, message: String) {
        _state.value = _state.value.copy(status = PlaybackStatus.ERROR)
        _events.tryEmit(PlayerEvent.Error(PlaybackError(kind, message)))
    }

    private fun command(block: () -> Unit) { scope.launch(Dispatchers.IO) { runCatching(block) } }

    override fun play() { _state.value = _state.value.copy(status = PlaybackStatus.PLAYING); command { transport.play() } }
    override fun pause() { _state.value = _state.value.copy(status = PlaybackStatus.PAUSED); command { transport.pause() } }
    override fun seekTo(positionMs: Long) { _state.value = _state.value.copy(positionMs = positionMs); command { transport.seek(positionMs) } }
    override fun setSpeed(speed: Float) {}

    override fun stop() {
        generation++
        poller?.cancel(); loadJob?.cancel()
        _state.value = PlayerState()
        command { transport.stop() }
    }

    override fun release() { generation++; poller?.cancel(); loadJob?.cancel() }

    private data class Source(val url: String, val mime: String, val video: Boolean, val headers: Map<String, String>)
}
