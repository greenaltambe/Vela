package com.greenaltambe.vela.player

import com.greenaltambe.vela.core.network.NetworkType
import com.greenaltambe.vela.core.settings.PreferredCodec
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.extractor.AudioStream
import com.greenaltambe.vela.extractor.StreamSet
import com.greenaltambe.vela.extractor.VideoStream

enum class VideoCodec { AV1, VP9, AVC, OTHER }

/** What the user asked for. */
data class PlaybackPrefs(
    /** An explicit cap for this playback (the quality menu), in pixels; wins over the per-network settings. Null defers to them. */
    val maxHeight: Int? = null,
    /** The settings' caps by connection type; null is no cap. */
    val maxHeightUnmetered: Int? = null,
    val maxHeightMetered: Int? = null,
    val preferredCodec: PreferredCodec = PreferredCodec.VP9,
    val audioOnly: Boolean = false,
    /** BCP-47 language of the audio track to prefer (`en` matches `en-US`); null keeps the video's original track. */
    val audioLanguage: String? = null,
) {
    /** The cap in force on [network]: the explicit one, else the setting for that connection. Offline counts as metered. */
    fun effectiveMaxHeight(network: NetworkType): Int? =
        maxHeight ?: if (network == NetworkType.UNMETERED) maxHeightUnmetered else maxHeightMetered

    companion object {
        fun from(settings: SettingsResolver, channelId: String?, videoId: String? = null) = PlaybackPrefs(
            maxHeightUnmetered = settings.resolve(Settings.maxQualityUnmetered, channelId, videoId).height,
            maxHeightMetered = settings.resolve(Settings.maxQualityMetered, channelId, videoId).height,
            preferredCodec = settings.resolve(Settings.preferredCodec, channelId, videoId),
            audioOnly = settings.resolve(Settings.audioOnly, channelId, videoId),
        )
    }
}

/** What the device can do, as opposed to what the user wants. */
data class PlaybackConstraints(
    val supportedCodecs: Set<VideoCodec> = VideoCodec.entries.toSet(),
    val supportsHls: Boolean = true,
    /** The connection right now; picks which of the settings' quality caps applies. */
    val network: NetworkType = NetworkType.UNMETERED,
)

/**
 * Pure stream choice: no I/O, no clock. The rules, in order:
 *
 * 1. Audio-only preference: the best audio stream alone. (If there is none, the normal video rules apply.)
 * 2. Separate best-quality video + audio. Video-only streams are always preferred over muxed ones because muxed
 *    streams top out at low resolutions; muxed is used only when no video-only stream qualifies or no audio exists.
 * 3. HLS, if the device supports it and the set has a manifest.
 * 4. Audio-only as a last resort, so a playable soundtrack beats an error.
 * 5. [PlaybackPlan.NothingPlayable].
 *
 * **Codec before resolution.** Codecs are tried in fallback order from the preferred one (see [codecOrder]) and the
 * first codec with any stream under the height cap wins, at its best resolution under the cap. So preferring AVC
 * gives you AVC (at most 1080p on YouTube) even when VP9 has 4K; that is the point of the setting.
 *
 * **Quality cap.** The best height not above [PlaybackPrefs.effectiveMaxHeight] (an explicit pick, else the setting for
 * [PlaybackConstraints.network]); if every stream is above it, the lowest one.
 *
 * **Audio.** Language match first, then non-DRC (DRC is dynamic-range-compressed "stable volume", used only when it
 * is all there is), then the original track, then bitrate.
 */
object StreamSelector {
    fun plan(streams: StreamSet, prefs: PlaybackPrefs, constraints: PlaybackConstraints = PlaybackConstraints()): PlaybackPlan {
        val audio = bestAudio(streams.audioStreams, prefs.audioLanguage)
        fun audioPlan() = audio?.let { PlaybackPlan.AudioOnly(it, streams.headers + it.headers) }

        if (prefs.audioOnly) audioPlan()?.let { return it }

        if (audio != null) {
            pickVideo(streams.videoStreams.filter { !it.hasAudio }, prefs, constraints)?.let { video ->
                return PlaybackPlan.Separate(video, audio, streams.headers + video.headers, streams.headers + audio.headers)
            }
        }
        pickVideo(streams.videoStreams.filter { it.hasAudio }, prefs, constraints)?.let {
            return PlaybackPlan.Muxed(it, streams.headers + it.headers)
        }
        streams.hlsUrl?.takeIf { constraints.supportsHls }?.let { return PlaybackPlan.Hls(it, streams.headers) }
        audioPlan()?.let { return it }
        return PlaybackPlan.NothingPlayable(
            if (streams.videoStreams.isEmpty() && streams.audioStreams.isEmpty() && streams.hlsUrl == null) "The stream set is empty"
            else "No stream is playable on this device (supported codecs: ${constraints.supportedCodecs})",
        )
    }

    /** Distinct heights (best first) that [plan] could produce with no cap, for a quality menu. */
    fun availableHeights(streams: StreamSet, constraints: PlaybackConstraints = PlaybackConstraints()): List<Int> =
        streams.videoStreams.filter { codecOf(it.codec) in constraints.supportedCodecs && it.height > 0 }
            .map { it.height }.distinct().sortedDescending()

    fun codecOf(codec: String): VideoCodec {
        val c = codec.lowercase()
        return when {
            c.startsWith("av01") || c.startsWith("av1") -> VideoCodec.AV1
            c.startsWith("vp9") || c.startsWith("vp09") -> VideoCodec.VP9
            c.startsWith("avc") || c.startsWith("h264") -> VideoCodec.AVC
            else -> VideoCodec.OTHER
        }
    }

    /**
     * The preferred codec, then the ones after it in AV1 > VP9 > AVC order, then the ones before it nearest first,
     * then OTHER: VP9 gives VP9, AVC, AV1; AVC gives AVC, VP9, AV1; AV1 gives AV1, VP9, AVC.
     */
    fun codecOrder(preferred: PreferredCodec): List<VideoCodec> {
        val list = listOf(VideoCodec.AV1, VideoCodec.VP9, VideoCodec.AVC)
        val i = list.indexOf(VideoCodec.valueOf(preferred.name))
        return listOf(list[i]) + list.drop(i + 1) + list.take(i).reversed() + VideoCodec.OTHER
    }

    private fun pickVideo(candidates: List<VideoStream>, prefs: PlaybackPrefs, constraints: PlaybackConstraints): VideoStream? {
        val playable = candidates.filter { codecOf(it.codec) in constraints.supportedCodecs }
        val order = codecOrder(prefs.preferredCodec)
        val cap = prefs.effectiveMaxHeight(constraints.network)
        // First choice: the first codec that has anything within the cap.
        for (codec in order) {
            val within = playable.filter { codecOf(it.codec) == codec && (cap == null || it.height <= cap) }
            if (within.isNotEmpty()) return within.bestVideo()
        }
        // Everything is above the cap: the least over it, in the most preferred codec that has that height.
        val lowest = playable.minOfOrNull { it.height } ?: return null
        return order.firstNotNullOfOrNull { codec -> playable.filter { codecOf(it.codec) == codec && it.height == lowest }.takeIf { it.isNotEmpty() }?.bestVideo() }
    }

    private fun List<VideoStream>.bestVideo() = maxWith(compareBy<VideoStream> { it.height }.thenBy { it.bitrate })

    private fun bestAudio(audio: List<AudioStream>, language: String?): AudioStream? =
        audio.maxWithOrNull(
            compareBy<AudioStream> { language != null && it.languageCode.matches(language) }
                .thenBy { !it.isDrc }
                .thenBy { it.isOriginalTrack }
                .thenBy { it.bitrate },
        )

    private fun String?.matches(language: String) =
        this != null && (equals(language, ignoreCase = true) || substringBefore('-').equals(language.substringBefore('-'), ignoreCase = true))
}
