package com.greenaltambe.vela.extractor

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** One page of a paged listing. [continuation] is an opaque, tier-specific token for the next page; null when there is none. */
data class Page<T>(val items: List<T>, val continuation: String? = null)

data class VideoSummary(
    val id: String,
    val title: String,
    val channelId: String,
    val channelName: String,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long? = null,
    val viewCount: Long? = null,
    val isShort: Boolean = false,
    /** When the video was published, if the source says (the RSS feed does; search results mostly don't). */
    val uploadedAt: Instant? = null,
    /** Set when DeArrow replaced [title] / [thumbnailUrl]: what they were, for "show original". */
    val originalTitle: String? = null,
    val originalThumbnailUrl: String? = null,
)

@Serializable
data class Chapter(val title: String, val startSeconds: Long)

data class VideoInfo(
    val id: String,
    val title: String,
    val description: String,
    val channelId: String,
    val channelName: String,
    val durationSeconds: Long,
    val viewCount: Long? = null,
    val likeCount: Long? = null,
    val thumbnailUrl: String? = null,
    val chapters: List<Chapter> = emptyList(),
    val isLive: Boolean = false,
    val uploadDate: Instant? = null,
    val isShort: Boolean = false,
    val ageRestricted: Boolean = false,
)

data class VideoStream(
    val url: String,
    val mimeType: String,
    val codec: String,
    val bitrate: Int,
    val width: Int,
    val height: Int,
    val fps: Int,
    /** True for muxed streams that already carry audio. */
    val hasAudio: Boolean = false,
    val itag: Int? = null,
    /** Size in bytes if the source reports it; needed for download progress and range requests. */
    val contentLength: Long? = null,
    /** Dynamic range compression ("stable volume") variant of the track. */
    val isDrc: Boolean = false,
    /** Extra headers for this stream only; merged over [StreamSet.headers]. */
    val headers: Map<String, String> = emptyMap(),
)

data class AudioStream(
    val url: String,
    val mimeType: String,
    val codec: String,
    val bitrate: Int,
    val languageCode: String? = null,
    val itag: Int? = null,
    val contentLength: Long? = null,
    val isDrc: Boolean = false,
    /** The video's original / default-language track (as opposed to a dub); false for everything on single-language videos. */
    val isOriginalTrack: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
)

/** Highest resolution first, then highest bitrate. The order every tier must hand [StreamSet.videoStreams] in. */
fun List<VideoStream>.videoBestFirst(): List<VideoStream> =
    sortedWith(compareByDescending<VideoStream> { it.height }.thenByDescending { it.bitrate })

/** Original / default track first, then highest bitrate. The order every tier must hand [StreamSet.audioStreams] in. */
fun List<AudioStream>.audioBestFirst(): List<AudioStream> =
    sortedWith(compareByDescending<AudioStream> { it.isOriginalTrack }.thenByDescending { it.bitrate })

/**
 * Every tier must build this with both stream lists already in best-first order ([videoBestFirst] / [audioBestFirst]); consumers may rely
 * on `videoStreams.first()` / `audioStreams.first()` being the best choice absent any preference.
 */
data class StreamSet(
    /** Muxed and video-only streams together, best first: height descending, then bitrate descending. */
    val videoStreams: List<VideoStream>,
    /** Best first: the original / default-language track(s), then bitrate descending. */
    val audioStreams: List<AudioStream>,
    val hlsUrl: String? = null,
    /** When the stream URLs stop working; null if unknown. */
    val expiresAt: Instant? = null,
    /**
     * Not before this instant do the URLs work: some clients' URLs (yt-dlp's TV client) answer 403 for the first few
     * seconds after they were issued. Null when there is no such delay. Callers wait for it before the first request.
     */
    val availableAt: Instant? = null,
    /** Request headers (User-Agent, Cookie, Referer...) the player and downloader must send with every stream request. */
    val headers: Map<String, String> = emptyMap(),
    /** Which client type produced this set (e.g. "web", "android", "tv", "yt-dlp"); null if not applicable. */
    val client: String? = null,
)

data class ChannelPage(
    val id: String,
    val name: String,
    val description: String = "",
    val avatarUrl: String? = null,
    val subscriberCount: Long? = null,
    val videos: List<VideoSummary> = emptyList(),
    val continuation: String? = null,
)

@Serializable
data class Comment(
    val id: String,
    val author: String,
    val text: String,
    val likeCount: Long = 0,
    val publishedText: String = "",
    val replyCount: Int = 0,
    val isPinned: Boolean = false,
    /** Pass to [Extractor.replies] to load this comment's replies; null when it has none (or the tier can't list them). */
    val repliesContinuation: String? = null,
)

data class CommentsPage(val comments: List<Comment>, val continuation: String? = null)

/**
 * Order of a comments listing. [NEWEST] is currently ignored by the NewPipe tier (NewPipeExtractor v0.26.5 has no
 * sort option for YouTube comments), which always returns "top". Check [ExtractorCapabilities.supportsCommentSort]
 * before offering the choice.
 */
enum class CommentSort { TOP, NEWEST }

/** What an extractor can actually do, so the UI can hide controls that would silently do nothing. */
data class ExtractorCapabilities(
    /** True only if [CommentSort.NEWEST] is honoured rather than ignored. */
    val supportsCommentSort: Boolean = false,
)
