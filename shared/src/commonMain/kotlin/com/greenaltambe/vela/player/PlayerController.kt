package com.greenaltambe.vela.player

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class PlaybackStatus { IDLE, BUFFERING, PLAYING, PAUSED, ENDED, ERROR }

data class PlayerState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    /** Right after [PlayerController.load] this is the requested start position, until playback moves it. */
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
)

enum class PlaybackErrorKind {
    /** The server refused or no longer knows the URL (HTTP 401/403/410...): re-resolving the streams usually fixes it. */
    StreamExpiredOrForbidden,
    Network,
    Decoder,
    Other,
}

data class PlaybackError(
    val kind: PlaybackErrorKind,
    val message: String,
    /** HTTP status when the failure was an HTTP response, else null. */
    val httpStatus: Int? = null,
    val cause: Throwable? = null,
)

sealed interface PlayerEvent {
    data class Error(val error: PlaybackError) : PlayerEvent
}

/** What the OS media controls (notification, lock screen) show. */
data class PlaybackMetadata(val title: String? = null, val artist: String? = null, val artworkUrl: String? = null)

/**
 * Media3 on Android, libmpv on Linux; other backends (e.g. Chromecast) plug in here later.
 *
 * Implementations report every change through [state] and every failure through [events]; they never decide to
 * retry. Re-resolving expired URLs is [PlaybackSession]'s job.
 */
interface PlayerController {
    val state: StateFlow<PlayerState>

    /** One-shot events; collect before calling [load] to be sure not to miss one. */
    val events: Flow<PlayerEvent>

    /** Replaces whatever is loaded and starts at [startPositionMs]; plays immediately when [playWhenReady]. */
    fun load(plan: PlaybackPlan, startPositionMs: Long = 0, playWhenReady: Boolean = true, metadata: PlaybackMetadata = PlaybackMetadata())
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)

    /** Output volume, 0 (silent) to 1 (full). Backends without a volume of their own ignore it. */
    fun setVolume(volume: Float) {}

    /** Unloads the media and goes [PlaybackStatus.IDLE]; the controller stays usable. */
    fun stop()
    fun release()
}
