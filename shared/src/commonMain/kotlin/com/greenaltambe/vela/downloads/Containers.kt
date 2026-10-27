package com.greenaltambe.vela.downloads

import com.greenaltambe.vela.extractor.AudioStream
import com.greenaltambe.vela.extractor.VideoStream
import com.greenaltambe.vela.player.StreamSelector
import com.greenaltambe.vela.player.VideoCodec

/** The file formats a download can end up in. */
enum class Container(val extension: String) { MP4("mp4"), WEBM("webm"), MKV("mkv") }

/**
 * The container for a video + audio pair merged without re-encoding, chosen so that it (a) can hold both streams,
 * (b) plays in our players (Media3 and mpv read all three) and (c) is something other apps open:
 *
 * - H.264 + AAC: **mp4**, the one format everything plays.
 * - VP9 or AV1 + Opus/Vorbis: **webm**, which Android, browsers and mpv all take.
 * - H.264 + Opus, VP9/AV1 + AAC: no standard-conformant common container, so **mkv**, which holds anything.
 *
 * Android's MediaMuxer writes mp4 and webm only; for mkv pairs the Android merger falls back to webm/mp4 where the
 * codecs allow it, otherwise it keeps the two files separate (see AndroidMediaMerger).
 */
object Containers {
    fun forPair(video: VideoStream, audio: AudioStream): Container = forCodecs(video.codec, audio.codec)

    /**
     * The ideal container (see above) if the merger can write it, else the first of MP4, WebM, MKV that it can (a
     * pair that does not belong in the container is then left to the muxer to accept or reject, with a clear error),
     * else the ideal one, which fails with the merger's own message.
     */
    fun forCodecs(videoCodec: String, audioCodec: String, allowed: Set<Container>): Container {
        val ideal = forCodecs(videoCodec, audioCodec)
        return if (ideal in allowed) ideal else listOf(Container.MP4, Container.WEBM, Container.MKV).firstOrNull { it in allowed } ?: ideal
    }

    /**
     * What Android's MediaMuxer accepts, from the "Supported media formats" table (Muxer column): H.264 and AV1 only go
     * into MP4, VP9 into WebM or MP4, AAC into MP4, Opus into WebM, Ogg or MP4 (Android 10+). So VP9 + Opus is WebM,
     * and every other pair is MP4 (AV1 + Opus included, which would be WebM elsewhere).
     */
    fun forAndroidMuxer(videoCodec: String, audioCodec: String): Container {
        val v = StreamSelector.codecOf(videoCodec)
        return if (v == VideoCodec.VP9 && audioFamily(audioCodec) == AudioFamily.OPUS_OR_VORBIS) Container.WEBM else Container.MP4
    }

    fun forCodecs(videoCodec: String, audioCodec: String): Container {
        val v = StreamSelector.codecOf(videoCodec)
        val a = audioFamily(audioCodec)
        return when {
            v == VideoCodec.AVC && a == AudioFamily.AAC -> Container.MP4
            (v == VideoCodec.VP9 || v == VideoCodec.AV1) && a == AudioFamily.OPUS_OR_VORBIS -> Container.WEBM
            else -> Container.MKV
        }
    }

    /** File extension of a single stream kept as it is. */
    fun extensionFor(mimeType: String, audioOnly: Boolean): String {
        val m = mimeType.lowercase()
        return when {
            audioOnly && (m.contains("mp4") || m.contains("m4a") || m.contains("aac")) -> "m4a"
            audioOnly && m.contains("webm") -> "webm"
            audioOnly && m.contains("ogg") -> "ogg"
            m.contains("webm") -> "webm"
            m.contains("mp4") || m.contains("3gpp") -> "mp4"
            else -> "bin"
        }
    }

    private enum class AudioFamily { AAC, OPUS_OR_VORBIS, OTHER }

    private fun audioFamily(codec: String): AudioFamily {
        val c = codec.lowercase()
        return when {
            c.startsWith("mp4a") || c.contains("aac") -> AudioFamily.AAC
            c.contains("opus") || c.contains("vorbis") -> AudioFamily.OPUS_OR_VORBIS
            else -> AudioFamily.OTHER
        }
    }
}
