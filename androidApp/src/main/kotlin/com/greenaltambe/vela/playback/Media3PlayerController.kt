package com.greenaltambe.vela.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.upstream.BandwidthMeter
import com.greenaltambe.vela.player.PlaybackMetadata
import com.greenaltambe.vela.player.PlaybackStats
import com.greenaltambe.vela.player.PlaybackPlan
import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.player.PlayerController
import com.greenaltambe.vela.player.PlayerEvent
import com.greenaltambe.vela.player.PlayerState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * [PlayerController] over an [ExoPlayer]. Must be used from the player's application thread (the main thread).
 * The same player backs the [VelaPlaybackService]'s media session, so the notification and lock screen controls act
 * on it directly and show up here as state changes.
 */
class Media3PlayerController(
    private val player: ExoPlayer,
    private val bandwidthMeter: BandwidthMeter? = null,
    /** Opens downloaded files (file and content URIs); see [PlaybackPlan.Local]. */
    private val localDataSource: androidx.media3.datasource.DataSource.Factory? = null,
) : PlayerController {
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state

    private val _stats = MutableStateFlow(PlaybackStats())
    val stats: StateFlow<PlaybackStats> = _stats
    private var droppedFrames = 0

    private val analytics = object : AnalyticsListener {
        override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
            this@Media3PlayerController.droppedFrames += droppedFrames
        }
    }

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 8)
    override val events = _events.asSharedFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var failed = false
    private var loadedPositionMs = 0L
    private var hasLoaded = false

    private val poll = object : Runnable {
        override fun run() {
            publish()
            if (player.isPlaying) handler.postDelayed(this, POSITION_INTERVAL_MS)
        }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish()
            handler.removeCallbacks(poll)
            if (player.isPlaying) handler.postDelayed(poll, POSITION_INTERVAL_MS)
        }

        override fun onPlayerError(error: PlaybackException) {
            failed = true
            publish()
            _events.tryEmit(PlayerEvent.Error(error.toPlaybackError()))
        }
    }

    init {
        player.addListener(listener)
        player.addAnalyticsListener(analytics)
    }

    override fun load(plan: PlaybackPlan, startPositionMs: Long, playWhenReady: Boolean, metadata: PlaybackMetadata) {
        failed = false
        hasLoaded = true
        droppedFrames = 0
        loadedPositionMs = startPositionMs
        player.setMediaSource(plan.toMediaSource(metadata, localDataSource), startPositionMs)
        player.playWhenReady = playWhenReady
        player.prepare()
        publish()
    }

    override fun play() {
        // After a failure the player is idle; prepare() retries the same source.
        if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) player.prepare()
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.play()
    }

    override fun pause() = player.pause()

    override fun seekTo(positionMs: Long) {
        // A live window slides and its end is the live edge: a seek at or past the end goes to the edge instead of into nothing (measured: BUFFERING at a position beyond the window).
        val d = player.duration
        if (player.isCurrentMediaItemLive && d != C.TIME_UNSET && positionMs >= d - 2_000) player.seekToDefaultPosition() else player.seekTo(positionMs)
        publish()
    }

    override fun goLive() { if (player.isCurrentMediaItemLive) { player.seekToDefaultPosition(); publish() } }

    override fun setSpeed(speed: Float) { player.setPlaybackSpeed(speed); publish() }

    override fun setVolume(volume: Float) { player.volume = volume.coerceIn(0f, 1f) }

    override fun stop() {
        handler.removeCallbacks(poll)
        player.stop()
        player.clearMediaItems()
        failed = false
        hasLoaded = false
        _state.value = PlayerState(speed = player.playbackParameters.speed)
    }

    override fun release() {
        handler.removeCallbacks(poll)
        player.removeListener(listener)
        player.removeAnalyticsListener(analytics)
    }

    private fun publish() {
        if (!hasLoaded) return
        val status = when {
            failed -> PlaybackStatus.ERROR
            player.playbackState == Player.STATE_IDLE -> PlaybackStatus.IDLE
            player.playbackState == Player.STATE_ENDED -> PlaybackStatus.ENDED
            player.isPlaying -> PlaybackStatus.PLAYING
            player.playbackState == Player.STATE_BUFFERING && player.playWhenReady -> PlaybackStatus.BUFFERING
            else -> PlaybackStatus.PAUSED
        }
        _state.value = PlayerState(
            status = status,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0,
            speed = player.playbackParameters.speed,
            liveOffsetMs = if (player.isCurrentMediaItemLive) player.currentLiveOffset.takeIf { it != C.TIME_UNSET } else null,
        )
        _stats.value = stats()
    }

    private fun stats(): PlaybackStats {
        val video = player.videoFormat
        val audio = player.audioFormat
        return PlaybackStats(
            videoResolution = video?.takeIf { it.width > 0 }?.let { "${it.width}x${it.height}" + if (it.frameRate > 0) "@${Math.round(it.frameRate)}" else "" },
            videoCodec = video?.codecs ?: video?.sampleMimeType,
            videoBitrateBps = video?.bitrate?.takeIf { it > 0 }?.toLong(),
            audioCodec = audio?.codecs ?: audio?.sampleMimeType,
            audioBitrateBps = audio?.bitrate?.takeIf { it > 0 }?.toLong(),
            bandwidthEstimateBps = bandwidthMeter?.bitrateEstimate?.takeIf { it > 0 },
            bufferedAheadMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0),
            droppedFrames = droppedFrames,
        )
    }

    private companion object {
        const val POSITION_INTERVAL_MS = 500L
    }
}
