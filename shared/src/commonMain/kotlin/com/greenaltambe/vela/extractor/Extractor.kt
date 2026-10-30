package com.greenaltambe.vela.extractor

/**
 * All YouTube access goes through this interface so breakage is fixed in one layer.
 *
 * Continuation tokens are opaque and only valid with the tier that issued them; a tier handed a token from another
 * tier fails with [ExtractorError.Unplayable] ([ContinuationToken.FOREIGN_MESSAGE]) rather than guessing.
 */
/** The orders YouTube offers for a channel's videos. */
enum class ChannelVideoSort { LATEST, POPULAR, OLDEST }

interface Extractor {
    val capabilities: ExtractorCapabilities get() = ExtractorCapabilities()

    suspend fun search(query: String, continuation: String? = null): ExtractorResult<Page<VideoSummary>>
    suspend fun videoInfo(videoId: String): ExtractorResult<VideoInfo>
    suspend fun streams(videoId: String): ExtractorResult<StreamSet>

    /** Like [streams], but asks for a different source than the one that served this video last (a stream that keeps buffering). Single-source extractors just resolve again. */
    suspend fun streamsPreferringOther(videoId: String): ExtractorResult<StreamSet> = streams(videoId)

    /** The channel header plus its first page of videos; use [channelVideos] for the following pages. */
    suspend fun channel(channelId: String): ExtractorResult<ChannelPage>
    suspend fun channelVideos(channelId: String, continuation: String? = null): ExtractorResult<Page<VideoSummary>>

    /**
     * The channel's Videos tab in [sort] order across ALL its videos (YouTube sorts them, as on the site), not just the ones loaded so far.
     * Tiers that cannot do it answer not-supported; the page then sorts what it has. Tokens are only valid with the same [sort].
     */
    suspend fun channelVideosSorted(channelId: String, sort: ChannelVideoSort, continuation: String? = null): ExtractorResult<Page<VideoSummary>> =
        if (sort == ChannelVideoSort.LATEST) channelVideos(channelId, continuation) else notSupported()

    suspend fun comments(
        videoId: String,
        sort: CommentSort = CommentSort.TOP,
        continuation: String? = null,
    ): ExtractorResult<CommentsPage>

    /** Replies to [commentId]. Without a [continuation] (see [Comment.repliesContinuation]) the tier has to find the comment first. */
    suspend fun replies(videoId: String, commentId: String, continuation: String? = null): ExtractorResult<CommentsPage>

    // The four below are optional per tier: a tier that cannot do one answers Unplayable("not supported"), which the
    // chain treats as a failure and moves on from.

    /** What YouTube currently trends, for Home. */
    suspend fun trending(continuation: String? = null): ExtractorResult<Page<VideoSummary>> = notSupported()

    /** Videos YouTube suggests next to [videoId]. */
    suspend fun related(videoId: String): ExtractorResult<List<VideoSummary>> = notSupported()

    /** The channel's Shorts tab. */
    suspend fun channelShorts(channelId: String, continuation: String? = null): ExtractorResult<Page<VideoSummary>> = notSupported()

    /** The channel's Live tab (current and past streams). */
    suspend fun channelLive(channelId: String, continuation: String? = null): ExtractorResult<Page<VideoSummary>> = notSupported()

    /** Search restricted to channels / playlists; tiers that cannot do it answer not-supported and the chain moves on. */
    suspend fun searchChannels(query: String, continuation: String? = null): ExtractorResult<Page<ChannelRef>> = notSupported()
    suspend fun searchPlaylists(query: String, continuation: String? = null): ExtractorResult<Page<PlaylistRef>> = notSupported()

    /** The channel's Playlists tab. */
    suspend fun channelPlaylists(channelId: String, continuation: String? = null): ExtractorResult<Page<PlaylistRef>> = notSupported()

    /** The videos of a YouTube playlist (not one of the app's own). */
    suspend fun playlistVideos(playlistId: String, continuation: String? = null): ExtractorResult<Page<VideoSummary>> = notSupported()

    /**
     * The channel's newest uploads (about the last 15), newest first, for the subscription feed. Each item carries
     * [VideoSummary.uploadedAt] when the source knows it. Durations are often unknown here (see [VideoSummary.durationSeconds]).
     */
    /** Search-as-you-type completions for [query]; tiers that cannot do it answer not-supported and the chain moves on. */
    suspend fun suggestions(query: String): ExtractorResult<List<String>> = notSupported()

    suspend fun recentUploads(channelId: String): ExtractorResult<List<VideoSummary>> = notSupported()
}

const val NOT_SUPPORTED = "not supported"

fun <T> notSupported(): ExtractorResult<T> = ExtractorResult.Failure(ExtractorError.Unplayable(NOT_SUPPORTED))
