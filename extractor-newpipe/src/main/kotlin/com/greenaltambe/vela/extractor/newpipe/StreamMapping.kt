package com.greenaltambe.vela.extractor.newpipe

import com.greenaltambe.vela.extractor.AudioStream
import com.greenaltambe.vela.extractor.Chapter
import com.greenaltambe.vela.extractor.StreamSet
import com.greenaltambe.vela.extractor.VideoInfo
import com.greenaltambe.vela.extractor.VideoStream
import com.greenaltambe.vela.extractor.audioBestFirst
import com.greenaltambe.vela.extractor.videoBestFirst
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import kotlin.time.Instant
import org.schabi.newpipe.extractor.stream.AudioStream as NpAudioStream
import org.schabi.newpipe.extractor.stream.VideoStream as NpVideoStream

internal const val CLIENT_LABEL = "newpipe"

/** Headers every stream request needs; individual streams override them (see [streamHeaders]). */
internal val webHeaders = mapOf(
    "User-Agent" to OkHttpDownloader.DEFAULT_USER_AGENT,
    "Origin" to "https://www.youtube.com",
    "Referer" to "https://www.youtube.com/",
)

/** URLs minted for the Android, iOS and visionOS InnerTube clients are only served to that client's user agent. */
internal fun streamHeaders(url: String): Map<String, String> = when (queryParam(url, "c")) {
    "ANDROID" -> mapOf("User-Agent" to YoutubeParsingHelper.getAndroidUserAgent(null))
    "IOS" -> mapOf("User-Agent" to YoutubeParsingHelper.getIosUserAgent(null))
    "VISIONOS" -> mapOf("User-Agent" to YoutubeParsingHelper.getVisionOsUserAgent(null))
    else -> emptyMap()
}

/** Plain-URL streams only: DASH/HLS-manifest variants can't be handed to a player as a single URL. */
private fun Stream.isPlainUrl() = isUrl && deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP

private fun Stream.contentLengthOrNull(declared: Long?): Long? =
    declared?.takeIf { it > 0 } ?: queryParam(content, "clen")?.toLongOrNull()?.takeIf { it > 0 }

internal fun NpVideoStream.toVideoStream() = VideoStream(
    url = content,
    mimeType = format?.mimeType ?: "video/*",
    codec = codec.orEmpty(),
    bitrate = bitrate,
    width = width,
    height = height,
    fps = fps,
    hasAudio = !isVideoOnly,
    itag = itag.takeIf { it >= 0 },
    contentLength = contentLengthOrNull(itagItem?.contentLength),
    isDrc = false,
    headers = streamHeaders(content),
)

internal fun NpAudioStream.toAudioStream() = AudioStream(
    url = content,
    mimeType = format?.mimeType ?: "audio/*",
    codec = codec.orEmpty(),
    bitrate = bitrate.takeIf { it > 0 } ?: averageBitrate,
    languageCode = audioLocale?.language?.takeIf { it.isNotEmpty() },
    itag = itag.takeIf { it >= 0 },
    contentLength = contentLengthOrNull(itagItem?.contentLength),
    isDrc = itagItem?.isDrc() == true,
    isOriginalTrack = audioTrackType == AudioTrackType.ORIGINAL,
    headers = streamHeaders(content),
)

internal fun StreamInfo.toStreamSet(): StreamSet {
    val video = videoStreams.filter { it.isPlainUrl() }.map { it.toVideoStream() } +
        videoOnlyStreams.filter { it.isPlainUrl() }.map { it.toVideoStream() }
    val sortedVideo = video.videoBestFirst()
    val audio = audioStreams.filter { it.isPlainUrl() }.map { it.toAudioStream() }.audioBestFirst()
    return StreamSet(
        videoStreams = sortedVideo,
        audioStreams = audio,
        hlsUrl = hlsUrl?.takeIf { it.isNotEmpty() },
        // The soonest-expiring URL decides when the whole set needs re-resolving.
        expiresAt = (video.map { it.url } + audio.map { it.url }).mapNotNull(::expiryOf).minOrNull(),
        headers = webHeaders,
        client = CLIENT_LABEL,
    )
}

internal fun StreamInfo.toVideoInfo() = VideoInfo(
    id = id,
    title = name,
    description = description?.content.orEmpty(),
    channelId = channelIdFromUrl(uploaderUrl).orEmpty(),
    channelName = uploaderName.orEmpty(),
    durationSeconds = duration.coerceAtLeast(0),
    viewCount = viewCount.takeIf { it >= 0 },
    likeCount = likeCount.takeIf { it >= 0 },
    thumbnailUrl = thumbnails.bestUrl(),
    chapters = streamSegments.orEmpty().map { Chapter(it.title.orEmpty(), it.startTimeSeconds.toLong()) },
    isLive = streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM,
    uploadDate = uploadDate?.instant?.let { Instant.fromEpochMilliseconds(it.toEpochMilli()) },
    isShort = isShortFormContent,
    ageRestricted = ageLimit != StreamExtractor.NO_AGE_LIMIT,
)

/** The largest image; images with unknown size lose to ones with a known size. */
internal fun List<Image>?.bestUrl(): String? = this?.maxByOrNull { it.height }?.url
