package com.greenaltambe.vela.player

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Where "what was watched" goes (to the signed-in account, on desktop and Android). Calls come from the playback session's thread and must
 * return at once: an implementation queues the work, drops it when it cannot keep up, and never throws.
 */
interface ActivityReporter {
    /** Playback of [videoId] has really started (past loading, playing). [key] names this viewing; every later call for it carries the same key. */
    fun started(key: String, videoId: String, durationMs: Long, offline: Boolean)

    /**
     * Time watched. [segments] are the stretches of the video (start..end, in ms) played since the last report; [positionMs] is where the player is.
     * [playing] is false when paused or ended; [final] marks the last report of this viewing (ended, stopped, or another video started).
     */
    fun watchTime(key: String, videoId: String, positionMs: Long, durationMs: Long, segments: List<LongRange>, playing: Boolean, final: Boolean)

    object None : ActivityReporter {
        override fun started(key: String, videoId: String, durationMs: Long, offline: Boolean) {}
        override fun watchTime(key: String, videoId: String, positionMs: Long, durationMs: Long, segments: List<LongRange>, playing: Boolean, final: Boolean) {}
    }
}

/**
 * Turns a session's player states into the reports YouTube's own player sends: one when playback starts, one every [interval] while playing, one
 * on pause, one at the end and one when playback is stopped or another video replaces it (the last position is always sent). A jump in the
 * position (a seek) closes the stretch being played and opens another, so skipped parts are not counted as watched.
 */
class ActivityAddon(
    private val reporter: ActivityReporter,
    private val clock: Clock = Clock.System,
    private val interval: Duration = 30.seconds,
) : PlaybackAddon {
    private val instance = (0 until 6).map { "abcdefghijklmnopqrstuvwxyz0123456789"[kotlin.random.Random.nextInt(36)] }.joinToString("")
    private var count = 0
    private var key = ""
    private var videoId: String? = null
    private var offline = false
    private var started = false
    private var playingNow = false
    private var segmentStart = -1L
    private var lastPosition = 0L
    private var duration = 0L
    private var lastReport: Instant = Instant.DISTANT_PAST
    private val segments = ArrayList<LongRange>()

    override fun onVideo(videoId: String, channelId: String, offline: Boolean) {
        finish()
        this.videoId = videoId; this.offline = offline
        key = "$instance-${++count}"
        started = false; playingNow = false; segmentStart = -1; lastPosition = 0; duration = 0; segments.clear()
    }

    override fun onPlayerState(state: PlayerState) {
        val id = videoId ?: return
        if (state.durationMs > 0) duration = state.durationMs
        when (state.status) {
            PlaybackStatus.PLAYING -> {
                if (!started) {
                    started = true
                    reporter.started(key, id, duration, offline)
                    lastReport = clock.now()
                } else if (segmentStart >= 0 && (state.positionMs < lastPosition - BACK_MS || state.positionMs > lastPosition + FORWARD_MS)) {
                    closeSegment() // a seek: what was played up to here counts, the new place starts a new stretch
                }
                if (segmentStart < 0) segmentStart = state.positionMs
                lastPosition = state.positionMs
                playingNow = true
                if (clock.now() - lastReport >= interval) report(playing = true, final = false)
            }
            PlaybackStatus.PAUSED -> if (started && playingNow) { lastPosition = state.positionMs; playingNow = false; report(playing = false, final = false) }
            PlaybackStatus.ENDED -> if (started) {
                if (duration > 0) lastPosition = duration
                playingNow = false
                report(playing = false, final = true)
                started = false; segmentStart = -1
            }
            else -> {}
        }
    }

    override fun onStop() { finish(); videoId = null }

    private fun closeSegment() {
        if (segmentStart >= 0 && lastPosition > segmentStart) segments += segmentStart..lastPosition
        segmentStart = -1
    }

    private fun report(playing: Boolean, final: Boolean) {
        val id = videoId ?: return
        closeSegment()
        reporter.watchTime(key, id, lastPosition, duration, segments.toList(), playing, final)
        segments.clear()
        lastReport = clock.now()
        if (playing) segmentStart = lastPosition
    }

    /** The viewing is over (stopped, or another video starts): its last position is sent once. */
    private fun finish() {
        if (started && videoId != null) { playingNow = false; report(playing = false, final = true) }
        started = false
    }

    private companion object {
        const val BACK_MS = 1_000L
        const val FORWARD_MS = 3_000L
    }
}
