package com.greenaltambe.vela.playback

import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.greenaltambe.vela.player.PlaybackConstraints
import com.greenaltambe.vela.player.PlaybackMetadata
import com.greenaltambe.vela.player.PlaybackPlan
import com.greenaltambe.vela.player.VideoCodec
import androidx.core.net.toUri

private const val FALLBACK_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

/** `User-Agent` is a dedicated field of [DefaultHttpDataSource.Factory]; everything else is a plain request property. */
internal class SplitHeaders(val userAgent: String, val rest: Map<String, String>)

internal fun splitHeaders(headers: Map<String, String>): SplitHeaders {
    val ua = headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
    return SplitHeaders(ua ?: FALLBACK_USER_AGENT, headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) })
}

private fun httpFactory(headers: Map<String, String>): DefaultHttpDataSource.Factory {
    val split = splitHeaders(headers)
    return DefaultHttpDataSource.Factory()
        .setUserAgent(split.userAgent)
        .setDefaultRequestProperties(split.rest)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(15_000)
}

private fun item(url: String, metadata: PlaybackMetadata) = MediaItem.Builder()
    .setUri(url)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(metadata.title)
            .setArtist(metadata.artist)
            .setArtworkUri(metadata.artworkUrl?.toUri())
            .build(),
    )
    .build()

/** Progressive streams go through [ChunkedDataSource]: googlevideo throttles open-ended requests (see its KDoc). */
private fun progressive(url: String, headers: Map<String, String>, metadata: PlaybackMetadata): MediaSource =
    ProgressiveMediaSource.Factory(ChunkedDataSource.Factory(httpFactory(headers))).createMediaSource(item(url, metadata))

/**
 * A plan becomes sources like so: muxed and audio-only are one progressive source; separate streams are two
 * progressive sources merged, each with its own headers; HLS is an HLS source.
 */
internal fun PlaybackPlan.toMediaSource(metadata: PlaybackMetadata, local: DataSource.Factory? = null): MediaSource = when (this) {
    is PlaybackPlan.Muxed -> progressive(stream.url, headers, metadata)
    is PlaybackPlan.Separate -> MergingMediaSource(
        progressive(video.url, videoHeaders, metadata),
        progressive(audio.url, audioHeaders, metadata),
    )
    is PlaybackPlan.Hls -> HlsMediaSource.Factory(httpFactory(headers)).createMediaSource(item(url, metadata))
    is PlaybackPlan.AudioOnly -> progressive(audio.url, headers, metadata)
    // A downloaded file: file paths and content:// URIs (SAF) both go through the platform's default data source.
    is PlaybackPlan.Local -> ProgressiveMediaSource.Factory(requireNotNull(local) { "no local data source configured" })
        .createMediaSource(item(if (uri.startsWith("/")) "file://$uri" else uri, metadata))
    is PlaybackPlan.NothingPlayable -> error("NothingPlayable has nothing to play")
}

/** What this device can decode. AV1 only counts when a hardware decoder exists, since software AV1 stutters at high resolutions. */
fun deviceConstraints(@Suppress("UNUSED_PARAMETER") context: Context? = null): PlaybackConstraints {
    val decoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
    fun has(mime: String, hardwareOnly: Boolean) = decoders.any { info ->
        info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
            (!hardwareOnly || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || info.isHardwareAccelerated)
    }
    return PlaybackConstraints(
        supportedCodecs = buildSet {
            add(VideoCodec.AVC)
            if (has("video/x-vnd.on2.vp9", hardwareOnly = false)) add(VideoCodec.VP9)
            if (has("video/av01", hardwareOnly = true)) add(VideoCodec.AV1)
        },
    )
}

/**
 * Buffer sizing for progressive video + audio streamed over a possibly mobile connection:
 *
 * - **min 30 s / max 60 s.** Loading runs until 60 s are buffered, then pauses until the buffer falls below 30 s. That
 *   is long enough to ride out a stall of several seconds, and the 30 s hysteresis means the radio loads in bursts
 *   instead of trickling continuously. With chunked ranges a 60 s refill takes a few seconds on a decent link.
 * - **2 s to start, 5 s to resume after a stall.** Start fast; after a stall resume only once there is a real cushion, to
 *   avoid stalling again immediately. (ExoPlayer's defaults are 2.5 s and 5 s.)
 * - **Size caps stay on** (no time-over-size priority): at 1080p a minute is about 30 MB, but a 4K minute is about 70 MB
 *   and these buffers live on the Java heap, so the byte cap wins over the time target at the highest qualities.
 * - **10 s back buffer, kept.** Small rewinds and quality changes re-use already-downloaded data.
 *
 * ExoPlayer's defaults are 50 s min and max, which refills continuously and keeps a stalled connection busy.
 */
fun streamingLoadControl(): DefaultLoadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        /* minBufferMs = */ 30_000,
        /* maxBufferMs = */ 60_000,
        /* bufferForPlaybackMs = */ 2_000,
        /* bufferForPlaybackAfterRebufferMs = */ 5_000,
    )
    .setBackBuffer(10_000, /* retainBackBufferFromKeyframe = */ true)
    .build()
