package com.greenaltambe.vela.player.mpv

import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.player.PlayerState
import kotlin.math.roundToLong

/** The mpv properties the state is derived from, as last observed. Null = unavailable. */
data class MpvSnapshot(
    val loadRequested: Boolean = false,
    val failed: Boolean = false,
    val pause: Boolean = false,
    val pausedForCache: Boolean = false,
    val coreIdle: Boolean = true,
    val eofReached: Boolean = false,
    val timePosSeconds: Double? = null,
    val durationSeconds: Double? = null,
    val speed: Double = 1.0,
    /** Shown instead of mpv's position from a seek or load until mpv reports `playback-restart`. */
    val positionOverrideMs: Long? = null,
    /** Milliseconds of media cached ahead of the position (demuxer-cache-time minus time-pos); for a live stream that is the distance to the live edge. */
    val cacheAheadMs: Long? = null,
)

object MpvStateMapper {
    fun map(s: MpvSnapshot): PlayerState {
        val speed = s.speed.toFloat()
        if (!s.loadRequested) return PlayerState(speed = speed)
        val status = when {
            s.failed -> PlaybackStatus.ERROR
            s.eofReached -> PlaybackStatus.ENDED
            s.pause -> PlaybackStatus.PAUSED
            s.pausedForCache || s.coreIdle -> PlaybackStatus.BUFFERING // wants to play, is not yet producing
            else -> PlaybackStatus.PLAYING
        }
        val position = s.positionOverrideMs ?: s.timePosSeconds?.let { (it * 1000).roundToLong() } ?: 0L
        return PlayerState(
            status = status,
            positionMs = position.coerceAtLeast(0),
            durationMs = s.durationSeconds?.takeIf { it > 0 }?.let { (it * 1000).roundToLong() } ?: 0L,
            speed = speed,
            liveOffsetMs = s.cacheAheadMs,
        )
    }

    /** Position updates arrive per frame; only publish when something visible changed. */
    fun worthPublishing(old: PlayerState, new: PlayerState, positionStepMs: Long = 250): Boolean =
        old.status != new.status || old.durationMs != new.durationMs || old.speed != new.speed || (old.liveOffsetMs ?: -5000L).let { kotlin.math.abs(it - (new.liveOffsetMs ?: -5000L)) >= 1000 } ||
            kotlin.math.abs(old.positionMs - new.positionMs) >= positionStepMs
}
