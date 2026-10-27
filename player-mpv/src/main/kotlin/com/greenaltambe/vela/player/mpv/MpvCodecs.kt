package com.greenaltambe.vela.player.mpv

import com.greenaltambe.vela.player.PlaybackConstraints
import com.greenaltambe.vela.player.VideoCodec

/**
 * What this machine's libmpv/ffmpeg can decode, from mpv's `decoder-list` property (a JSON array of
 * `{"codec":"vp9",...}`). Fedora's stock ffmpeg-free has no H.264, so asking the extractor for AVC would be a
 * silent black screen; the stream selector must be told.
 */
fun constraintsFromDecoderList(decoderListJson: String?): PlaybackConstraints {
    val codecs = Regex(""""codec"\s*:\s*"([^"]+)"""").findAll(decoderListJson.orEmpty()).map { it.groupValues[1] }.toSet()
    if (codecs.isEmpty()) return PlaybackConstraints() // could not tell: assume everything and let errors speak
    val supported = buildSet {
        if ("h264" in codecs) add(VideoCodec.AVC)
        if ("vp9" in codecs) add(VideoCodec.VP9)
        if ("av1" in codecs) add(VideoCodec.AV1)
    }
    // YouTube's HLS renditions are H.264.
    return PlaybackConstraints(supportedCodecs = supported, supportsHls = VideoCodec.AVC in supported)
}
