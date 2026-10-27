package com.greenaltambe.vela.player

import com.greenaltambe.vela.extractor.AudioStream
import com.greenaltambe.vela.extractor.VideoStream

/**
 * What to hand the player. Headers are already resolved: the set-level headers with each stream's own headers
 * laid over them (the stream wins), so a backend never has to merge.
 */
sealed interface PlaybackPlan {
    /** One stream that carries both picture and sound. */
    data class Muxed(val stream: VideoStream, val headers: Map<String, String>) : PlaybackPlan

    /** Video-only and audio-only streams played in sync; each is fetched with its own headers. */
    data class Separate(
        val video: VideoStream,
        val audio: AudioStream,
        val videoHeaders: Map<String, String>,
        val audioHeaders: Map<String, String>,
    ) : PlaybackPlan

    data class Hls(val url: String, val headers: Map<String, String>) : PlaybackPlan

    data class AudioOnly(val audio: AudioStream, val headers: Map<String, String>) : PlaybackPlan

    /**
     * A finished download: one file with picture and sound (or sound only when [hasVideo] is false). [uri] is a file
     * path or a content URI, whatever the platform's player can open. No network is involved.
     */
    data class Local(val uri: String, val hasVideo: Boolean) : PlaybackPlan

    /** Nothing in the stream set can be played under the given preferences and constraints. */
    data class NothingPlayable(val reason: String) : PlaybackPlan
}

/** Height of the picture a plan shows; null for audio-only, HLS (unknown until played) and failures. */
val PlaybackPlan.videoHeight: Int? get() = when (this) {
    is PlaybackPlan.Muxed -> stream.height
    is PlaybackPlan.Separate -> video.height
    else -> null
}
