package com.greenaltambe.vela.player

/** What the "stats for nerds" panel shows. Null / zero when the backend does not know (yet). */
data class PlaybackStats(
    /** e.g. `1920x1080@30`. */
    val videoResolution: String? = null,
    val videoCodec: String? = null,
    val videoBitrateBps: Long? = null,
    val audioCodec: String? = null,
    val audioBitrateBps: Long? = null,
    /** The player's own estimate of the connection's throughput, which adaptive logic and buffering decisions use. */
    val bandwidthEstimateBps: Long? = null,
    /** How much is buffered beyond the playback position. */
    val bufferedAheadMs: Long = 0,
    /** Since the current media was loaded. */
    val droppedFrames: Int = 0,
)
