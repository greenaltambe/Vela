package com.greenaltambe.vela.extractor

/** Placeholder tier: returns canned data, or [error] for every call when set. Replaced by real tiers later. */
class StubExtractor(private val error: ExtractorError? = null) : Extractor {
    var calls = 0
        private set

    private fun <T> respond(value: () -> T): ExtractorResult<T> {
        calls++
        return error?.let { ExtractorResult.Failure(it) } ?: ExtractorResult.Success(value())
    }

    override suspend fun search(query: String, continuation: String?) = respond { Page<VideoSummary>(emptyList()) }

    override suspend fun videoInfo(videoId: String) = respond {
        VideoInfo(videoId, "Stub video", "", "stub-channel", "Stub channel", durationSeconds = 0)
    }

    override suspend fun streams(videoId: String) = respond { StreamSet(emptyList(), emptyList(), client = "stub") }

    override suspend fun channel(channelId: String) = respond { ChannelPage(channelId, "Stub channel") }

    override suspend fun channelVideos(channelId: String, continuation: String?) = respond { Page<VideoSummary>(emptyList()) }

    override suspend fun comments(videoId: String, sort: CommentSort, continuation: String?) =
        respond { CommentsPage(emptyList()) }

    override suspend fun replies(videoId: String, commentId: String, continuation: String?) =
        respond { CommentsPage(emptyList()) }

    override suspend fun trending(continuation: String?) = respond { Page<VideoSummary>(emptyList()) }

    override suspend fun related(videoId: String) = respond { emptyList<VideoSummary>() }

    override suspend fun channelShorts(channelId: String, continuation: String?) = respond { Page<VideoSummary>(emptyList()) }

    override suspend fun recentUploads(channelId: String) = respond { emptyList<VideoSummary>() }
}
