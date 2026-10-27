package com.greenaltambe.vela.player.mpv

import com.greenaltambe.vela.player.PlaybackPlan
import com.greenaltambe.vela.player.PlaybackMetadata

/** Tunables that stay fixed for the life of a controller. */
data class MpvConfig(
    /** `no` (software), `auto-safe`, `vaapi-copy`...: see the report for what works with the software renderer. */
    val hwdec: String = "no",
    /** Route progressive streams through [ChunkingProxy] (googlevideo throttles open-ended requests). */
    val chunkedRanges: Boolean = true,
    val chunkSizeBytes: Long = 10L * 1024 * 1024,
    /** Seconds of media mpv reads ahead; Android's load control buffers 30-60 s. */
    val readAheadSeconds: Int = 60,
    /** Seconds that must be buffered before resuming after a stall (Android: 5 s). */
    val resumeAfterStallSeconds: Int = 5,
    /** Applied last, so they win. For experiments and per-machine tuning. */
    val extraOptions: Map<String, String> = emptyMap(),
)

/** One `loadfile`, fully worked out. Pure data so it can be tested without libmpv. */
data class MpvLoad(
    val url: String,
    /** Per-file options (`audio-file`, `start`, `pause`...). */
    val fileOptions: Map<String, String>,
    /** `Name: value` lines for `http-header-fields`, for URLs mpv fetches itself (HLS, or chunking off). */
    val headerFields: List<String>,
    val userAgent: String?,
    val mediaTitle: String?,
) {
    /** The `loadfile <url> replace -1 <options>` argument list. */
    fun loadfileArgs(): Array<String> =
        arrayOf("loadfile", url, "replace", "-1", fileOptions.entries.joinToString(",") { (k, v) -> "$k=${escapeOptionValue(v)}" })
}

/** mpv's `%N%value` form: N is the value's length in bytes, so commas and equals signs inside need no escaping. */
internal fun escapeOptionValue(value: String): String = "%${value.toByteArray(Charsets.UTF_8).size}%$value"

/** Which stream a URL belongs to, for the proxy's failure reports. */
enum class StreamRole { Video, Audio, Muxed }

object MpvOptions {
    /** Options set once, before `mpv_initialize`. */
    fun initOptions(config: MpvConfig): List<Pair<String, String>> = baseOptions(config) + config.extraOptions.map { it.key to it.value }

    private fun baseOptions(config: MpvConfig): List<Pair<String, String>> = listOf(
        "vo" to "libmpv",
        "idle" to "yes",
        "keep-open" to "yes", // stay on the last frame at EOF so "ended" is observable and seeking back works
        "audio-client-name" to "Vela",
        "config" to "no",
        "load-scripts" to "no",
        "ytdl" to "no", // the extractor chain already resolved the URLs; never let mpv spawn yt-dlp
        "osc" to "no",
        "input-default-bindings" to "no",
        "input-vo-keyboard" to "no",
        "terminal" to "no",
        "sub-auto" to "no",
        "sid" to "no",
        "hwdec" to config.hwdec,
        "cache" to "yes",
        "cache-secs" to config.readAheadSeconds.toString(),
        "demuxer-readahead-secs" to config.readAheadSeconds.toString(),
        "demuxer-max-bytes" to "200MiB",
        "demuxer-max-back-bytes" to "50MiB",
        "cache-pause" to "yes",
        "cache-pause-wait" to config.resumeAfterStallSeconds.toString(),
        "cache-pause-initial" to "no",
        "network-timeout" to "20",
        "video-sync" to "audio",
        "sws-allow-zimg" to "no", // zimg is much slower than swscale for the 8-bit BGR0 conversion (measured: ~110% vs ~60% of a core at 1080p60)
        "sws-scaler" to "fast-bilinear",
    )

    /**
     * Turns a plan into a load. [route] maps a stream URL (and its headers) to the URL mpv should open: the proxy's
     * loopback URL when chunking, the original otherwise. HLS is never routed (small segments are not throttled).
     * Returns null for [PlaybackPlan.NothingPlayable].
     */
    fun build(
        plan: PlaybackPlan,
        startPositionMs: Long,
        playWhenReady: Boolean,
        metadata: PlaybackMetadata,
        route: (role: StreamRole, url: String, headers: Map<String, String>) -> String,
        chunked: Boolean,
    ): MpvLoad? {
        val options = linkedMapOf<String, String>()
        if (startPositionMs > 0) options["start"] = "%.3f".format(java.util.Locale.ROOT, startPositionMs / 1000.0)
        options["pause"] = if (playWhenReady) "no" else "yes"
        metadata.title?.let { options["force-media-title"] = it }

        val url: String
        val headers: Map<String, String>
        when (plan) {
            is PlaybackPlan.Muxed -> {
                url = route(StreamRole.Muxed, plan.stream.url, plan.headers)
                headers = plan.headers
            }
            is PlaybackPlan.Separate -> {
                url = route(StreamRole.Video, plan.video.url, plan.videoHeaders)
                // External audio: mpv fetches it as a second input and keeps it in sync with the video's timeline.
                options["audio-file"] = route(StreamRole.Audio, plan.audio.url, plan.audioHeaders)
                headers = plan.videoHeaders
            }
            is PlaybackPlan.AudioOnly -> {
                url = route(StreamRole.Audio, plan.audio.url, plan.headers)
                headers = plan.headers
                options["vid"] = "no"
            }
            is PlaybackPlan.Hls -> {
                url = plan.url
                headers = plan.headers
            }
            is PlaybackPlan.Local -> {
                url = plan.uri
                headers = emptyMap()
                if (!plan.hasVideo) options["vid"] = "no"
            }
            is PlaybackPlan.NothingPlayable -> return null
        }
        // Headers only matter for URLs mpv fetches itself; behind the proxy it applies each stream's own headers.
        val direct = plan is PlaybackPlan.Hls || plan is PlaybackPlan.Local || !chunked
        val ua = if (direct) headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value else null
        val fields = if (direct) headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) }.map { (k, v) -> "$k: $v" } else emptyList()
        return MpvLoad(url, options, fields, ua, metadata.title)
    }
}
