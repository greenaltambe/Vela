package com.greenaltambe.vela.extractor

/** All YouTube access goes through this interface so breakage is fixed in one layer. */
interface Extractor {
    suspend fun search(query: String): ExtractorResult<List<VideoSummary>>
    suspend fun videoInfo(videoId: String): ExtractorResult<VideoInfo>
    suspend fun streams(videoId: String): ExtractorResult<StreamSet>
    suspend fun channel(channelId: String): ExtractorResult<ChannelPage>
    suspend fun comments(videoId: String, sort: CommentSort = CommentSort.TOP): ExtractorResult<CommentsPage>
}
