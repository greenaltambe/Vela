package com.greenaltambe.vela.extractor

/**
 * All YouTube access goes through this interface so breakage is fixed in one layer.
 *
 * Continuation tokens are opaque and only valid with the tier that issued them; a tier handed a token from another
 * tier fails with [ExtractorError.Unplayable] ([ContinuationToken.FOREIGN_MESSAGE]) rather than guessing.
 */
interface Extractor {
    val capabilities: ExtractorCapabilities get() = ExtractorCapabilities()

    suspend fun search(query: String, continuation: String? = null): ExtractorResult<Page<VideoSummary>>
    suspend fun videoInfo(videoId: String): ExtractorResult<VideoInfo>
    suspend fun streams(videoId: String): ExtractorResult<StreamSet>

    /** The channel header plus its first page of videos; use [channelVideos] for the following pages. */
    suspend fun channel(channelId: String): ExtractorResult<ChannelPage>
    suspend fun channelVideos(channelId: String, continuation: String? = null): ExtractorResult<Page<VideoSummary>>

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

    /**
     * The channel's newest uploads (about the last 15), newest first, for the subscription feed. Each item carries
     * [VideoSummary.uploadedAt] when the source knows it. Durations are often unknown here (see [VideoSummary.durationSeconds]).
     */
    suspend fun recentUploads(channelId: String): ExtractorResult<List<VideoSummary>> = notSupported()
}

const val NOT_SUPPORTED = "not supported"

fun <T> notSupported(): ExtractorResult<T> = ExtractorResult.Failure(ExtractorError.Unplayable(NOT_SUPPORTED))
