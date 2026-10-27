package com.greenaltambe.vela.player

import com.greenaltambe.vela.extractor.StreamSet
import kotlinx.coroutines.flow.StateFlow

enum class PlaybackStatus { IDLE, BUFFERING, PLAYING, PAUSED, ENDED, ERROR }

data class PlayerState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
)

/** Media3 on Android, libmpv on Linux; other backends (e.g. Chromecast) plug in here later. */
interface PlayerController {
    val state: StateFlow<PlayerState>

    fun load(streams: StreamSet, startPositionMs: Long = 0)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    fun release()
}
