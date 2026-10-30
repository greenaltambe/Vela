package com.greenaltambe.vela.extractor

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The last line of defence against a screen that spins forever: every call through this wrapper either answers or fails with a
 * [ExtractorError.Network] "timed out" within [timeout], and every list screen already turns a failure into a message with Retry.
 * (Each transport has its own, shorter timeouts; this bounds the whole chain of tiers and retries.)
 */
class TimeoutExtractor(private val inner: Extractor, private val timeout: Duration = 75.seconds) : Extractor {
    override val capabilities get() = inner.capabilities

    private suspend fun <T> bounded(what: String, call: suspend () -> ExtractorResult<T>): ExtractorResult<T> =
        try { withTimeout(timeout) { call() } }
        catch (e: TimeoutCancellationException) { ExtractorResult.Failure(ExtractorError.Network("$what timed out after ${timeout.inWholeSeconds} s")) }

    override suspend fun search(query: String, continuation: String?) = bounded("Search") { inner.search(query, continuation) }
    override suspend fun videoInfo(videoId: String) = bounded("Loading the video") { inner.videoInfo(videoId) }
    override suspend fun streams(videoId: String) = bounded("Resolving the stream") { inner.streams(videoId) }
    override suspend fun streamsPreferringOther(videoId: String) = bounded("Resolving the stream") { inner.streamsPreferringOther(videoId) }
    override suspend fun channel(channelId: String) = bounded("Loading the channel") { inner.channel(channelId) }
    override suspend fun channelVideos(channelId: String, continuation: String?) = bounded("Loading the channel's videos") { inner.channelVideos(channelId, continuation) }
    override suspend fun comments(videoId: String, sort: CommentSort, continuation: String?) = bounded("Loading comments") { inner.comments(videoId, sort, continuation) }
    override suspend fun replies(videoId: String, commentId: String, continuation: String?) = bounded("Loading replies") { inner.replies(videoId, commentId, continuation) }
    override suspend fun trending(continuation: String?) = bounded("Loading trending") { inner.trending(continuation) }
    override suspend fun related(videoId: String) = bounded("Loading related videos") { inner.related(videoId) }
    override suspend fun channelShorts(channelId: String, continuation: String?) = bounded("Loading shorts") { inner.channelShorts(channelId, continuation) }
    override suspend fun searchChannels(query: String, continuation: String?) = bounded("Search") { inner.searchChannels(query, continuation) }
    override suspend fun searchPlaylists(query: String, continuation: String?) = bounded("Search") { inner.searchPlaylists(query, continuation) }
    override suspend fun channelLive(channelId: String, continuation: String?) = bounded("Loading live streams") { inner.channelLive(channelId, continuation) }
    override suspend fun channelPlaylists(channelId: String, continuation: String?) = bounded("Loading playlists") { inner.channelPlaylists(channelId, continuation) }
    override suspend fun playlistVideos(playlistId: String, continuation: String?) = bounded("Loading the playlist") { inner.playlistVideos(playlistId, continuation) }
    override suspend fun suggestions(query: String) = bounded("Suggestions") { inner.suggestions(query) }
    override suspend fun recentUploads(channelId: String) = bounded("Loading uploads") { inner.recentUploads(channelId) }
}
