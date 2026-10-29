package com.greenaltambe.vela.extractor.ytdlp

import com.greenaltambe.vela.extractor.AudioStream
import com.greenaltambe.vela.extractor.ChannelPage
import com.greenaltambe.vela.extractor.Chapter
import com.greenaltambe.vela.extractor.StreamSet
import com.greenaltambe.vela.extractor.StreamUrls
import com.greenaltambe.vela.extractor.VideoInfo
import com.greenaltambe.vela.extractor.VideoStream
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.extractor.audioBestFirst
import com.greenaltambe.vela.extractor.videoBestFirst
import kotlinx.serialization.json.JsonObject
import kotlin.math.roundToInt
import kotlin.time.Instant

internal const val CLIENT_LABEL = "yt-dlp"

internal fun videoUrl(videoId: String) = "https://www.youtube.com/watch?v=$videoId"

internal fun channelVideosUrl(channelId: String) = "https://www.youtube.com/channel/$channelId/videos"

// ---- formats -> StreamSet --------------------------------------------------------------------------------------

private fun String?.isCodec() = this != null && this != "none"

/** Plain http(s) media files only: storyboards (mhtml) and manifest-only entries (HLS/DASH) can't be handed to a player as one URL. */
private fun JsonObject.isPlainMedia(): Boolean {
    if (str("url") == null) return false
    if (str("ext") == "mhtml" || str("format_note") == "storyboard") return false
    val protocol = str("protocol") ?: "https"
    if (protocol != "https" && protocol != "http") return false
    return str("vcodec").isCodec() || str("acodec").isCodec()
}

/** `251` -> 251, `251-drc` -> 251, `137-1` -> 137; `hls-1080p`, `sb0` -> null. */
internal fun itagOf(formatId: String?): Int? =
    formatId?.substringBefore('-')?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull()

private fun JsonObject.isDrc() =
    str("format_id")?.endsWith("-drc") == true || str("format_note")?.contains("DRC", ignoreCase = true) == true

private fun mimeType(kind: String, ext: String?) = when (ext) {
    null -> "$kind/*"
    "m4a" -> "audio/mp4"
    else -> "$kind/$ext"
}

/** yt-dlp reports bitrates in kbit/s as floats; our models use bit/s. */
private fun JsonObject.bitsPerSecond(vararg keys: String): Int =
    keys.firstNotNullOfOrNull { double(it) }?.let { (it * 1000).roundToInt() } ?: 0

private fun JsonObject.contentLength() = (long("filesize") ?: long("filesize_approx"))?.takeIf { it > 0 }

/** Headers that differ from the set-level ones; the merge rule makes the rest redundant. */
private fun JsonObject.extraHeaders(setLevel: Map<String, String>) =
    stringMap("http_headers").filter { (k, v) -> setLevel[k] != v }

private fun JsonObject.toVideoStream(setHeaders: Map<String, String>) = VideoStream(
    url = str("url")!!,
    mimeType = mimeType("video", str("ext")),
    codec = str("vcodec").orEmpty(),
    bitrate = bitsPerSecond("vbr", "tbr"),
    width = long("width")?.toInt() ?: 0,
    height = long("height")?.toInt() ?: 0,
    fps = double("fps")?.roundToInt() ?: 0,
    hasAudio = str("acodec").isCodec(),
    itag = itagOf(str("format_id")),
    contentLength = contentLength(),
    isDrc = false,
    headers = extraHeaders(setHeaders),
)

private fun JsonObject.toAudioStream(setHeaders: Map<String, String>) = AudioStream(
    url = str("url")!!,
    mimeType = mimeType("audio", str("ext")),
    codec = str("acodec").orEmpty(),
    bitrate = bitsPerSecond("abr", "tbr"),
    languageCode = str("language"),
    itag = itagOf(str("format_id")),
    contentLength = contentLength(),
    isDrc = isDrc(),
    // language_preference > 0 is yt-dlp's "default" marker (10 on the original track, -1 on dubs).
    isOriginalTrack = (long("language_preference") ?: 0) > 0,
    headers = extraHeaders(setHeaders),
)

/**
 * `--dump-single-json` has no top-level `http_headers` (only a download of one selected format does); every format
 * carries its own copy. The set-level headers are therefore those all the plain formats agree on.
 */
private fun JsonObject.commonHeaders(plain: List<JsonObject>): Map<String, String> {
    stringMap("http_headers").takeIf { it.isNotEmpty() }?.let { return it }
    val all = plain.map { it.stringMap("http_headers") }
    val first = all.firstOrNull() ?: return emptyMap()
    return first.filter { (k, v) -> all.all { it[k] == v } }
}

internal fun JsonObject.toStreamSet(): StreamSet {
    val formats = objects("formats")
    val plain = formats.filter { it.isPlainMedia() }
    val setHeaders = commonHeaders(plain)
    val video = plain.filter { it.str("vcodec").isCodec() }.map { it.toVideoStream(setHeaders) }.videoBestFirst()
    val audio = plain.filter { !it.str("vcodec").isCodec() && it.str("acodec").isCodec() }
        .map { it.toAudioStream(setHeaders) }.audioBestFirst()
    return StreamSet(
        videoStreams = video,
        audioStreams = audio,
        hlsUrl = formats.firstOrNull { it.str("protocol")?.startsWith("m3u8") == true && it.str("manifest_url") != null }
            ?.str("manifest_url"),
        // The soonest-expiring URL decides when the whole set needs re-resolving.
        expiresAt = (video.map { it.url } + audio.map { it.url }).mapNotNull(StreamUrls::expiryOf).minOrNull(),
        // yt-dlp's own downloader sleeps until this ("Sleeping 4.00 seconds as required by the site"): requests sooner get a 403.
        availableAt = plain.mapNotNull { it.long("available_at") }.maxOrNull()?.let(Instant::fromEpochSeconds),
        headers = setHeaders,
        client = CLIENT_LABEL,
    )
}

// ---- single video -> VideoInfo ---------------------------------------------------------------------------------

/** `timestamp` is exact; `upload_date` (YYYYMMDD) only gives the day, taken as midnight UTC. */
internal fun JsonObject.uploadInstant(): Instant? {
    long("timestamp")?.let { return Instant.fromEpochSeconds(it) }
    val d = str("upload_date")?.takeIf { it.length == 8 && it.all(Char::isDigit) } ?: return null
    return runCatching { Instant.parse("${d.substring(0, 4)}-${d.substring(4, 6)}-${d.substring(6, 8)}T00:00:00Z") }.getOrNull()
}

internal fun JsonObject.toVideoInfo() = VideoInfo(
    id = str("id").orEmpty(),
    title = str("title").orEmpty(),
    description = str("description").orEmpty(),
    channelId = str("channel_id") ?: channelIdFromUrl(str("channel_url") ?: str("uploader_url")).orEmpty(),
    channelName = str("channel") ?: str("uploader").orEmpty(),
    durationSeconds = (double("duration") ?: 0.0).toLong().coerceAtLeast(0),
    viewCount = long("view_count"),
    likeCount = long("like_count"),
    thumbnailUrl = str("thumbnail") ?: bestThumbnail(),
    chapters = objects("chapters").mapNotNull { c ->
        c.str("title")?.let { Chapter(it, (c.double("start_time") ?: 0.0).toLong()) }
    },
    isLive = bool("is_live") == true || str("live_status") == "is_live",
    uploadDate = uploadInstant(),
    // We always ask for a /watch URL, and yt-dlp then reports /watch too, so the URL cannot tell; media_type can.
    isShort = str("media_type") == "short" || listOfNotNull(str("webpage_url"), str("original_url")).any { "/shorts/" in it },
    ageRestricted = (long("age_limit") ?: 0) > 0,
)

/** yt-dlp lists thumbnails worst to best. */
private fun JsonObject.bestThumbnail() = objects("thumbnails").lastOrNull { it.str("url") != null }?.str("url")

// ---- flat playlists -> summaries / channel ---------------------------------------------------------------------

internal fun channelIdFromUrl(url: String?): String? =
    url?.substringBefore('?')?.substringAfter("/channel/", "")?.substringBefore('/')?.takeIf { it.isNotEmpty() }

/** Null for entries that aren't playable videos (playlists, channels) or have no id. */
internal fun JsonObject.toVideoSummary(parent: JsonObject? = null): VideoSummary? {
    val id = str("id") ?: return null
    val type = str("_type")
    if (type != null && type != "url" && type != "video") return null
    val url = str("url").orEmpty()
    if ("/channel/" in url || "/playlist" in url || "/@" in url) return null
    return VideoSummary(
        id = id,
        title = str("title").orEmpty(),
        // Entries of a channel's own listing carry no channel fields at all; the listing itself names the channel.
        channelId = str("channel_id") ?: channelIdFromUrl(str("channel_url") ?: str("uploader_url"))
            ?: parent?.str("channel_id").orEmpty(),
        channelName = str("channel") ?: str("uploader") ?: parent?.let { it.str("channel") ?: it.str("uploader") }.orEmpty(),
        thumbnailUrl = bestThumbnail() ?: str("thumbnail"),
        durationSeconds = double("duration")?.toLong()?.takeIf { it >= 0 },
        viewCount = long("view_count"),
        isShort = "/shorts/" in url,
    )
}

internal fun JsonObject.flatVideos(): List<VideoSummary> = objects("entries").mapNotNull { it.toVideoSummary(parent = this) }

internal fun JsonObject.toChannelPage(channelId: String, continuation: String?): ChannelPage {
    val avatar = objects("thumbnails").firstOrNull { it.str("id")?.startsWith("avatar") == true }?.str("url")
    val banner = objects("thumbnails").firstOrNull { it.str("id")?.startsWith("banner") == true }?.str("url")
    return ChannelPage(
        id = str("channel_id") ?: str("id") ?: channelId,
        name = str("channel") ?: str("uploader") ?: str("title").orEmpty(),
        description = str("description").orEmpty(),
        avatarUrl = avatar,
        subscriberCount = long("channel_follower_count"),
        videos = flatVideos(),
        continuation = continuation,
        bannerUrl = banner,
    )
}
