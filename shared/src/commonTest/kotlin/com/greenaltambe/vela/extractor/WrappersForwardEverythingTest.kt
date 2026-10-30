package com.greenaltambe.vela.extractor

import com.greenaltambe.vela.core.settings.AccountFallbackMode
import com.greenaltambe.vela.extractor.chain.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Structural guard: a wrapper that forgets one method silently turns it into "not supported" (that is how search suggestions died behind the
 * pacing wrapper). Every wrapper must pass EVERY Extractor method through to what it wraps.
 */
class WrappersForwardEverythingTest {
    private class Recording : Extractor {
        val calls = mutableSetOf<String>()
        private fun <T> hit(name: String): ExtractorResult<T> { calls += name; return notSupported() }
        override suspend fun search(query: String, continuation: String?) = hit<Page<VideoSummary>>("search")
        override suspend fun videoInfo(videoId: String) = hit<VideoInfo>("videoInfo")
        override suspend fun streams(videoId: String) = hit<StreamSet>("streams")
        override suspend fun streamsPreferringOther(videoId: String) = hit<StreamSet>("streamsPreferringOther")
        override suspend fun channel(channelId: String) = hit<ChannelPage>("channel")
        override suspend fun channelVideos(channelId: String, continuation: String?) = hit<Page<VideoSummary>>("channelVideos")
        override suspend fun comments(videoId: String, sort: CommentSort, continuation: String?) = hit<CommentsPage>("comments")
        override suspend fun replies(videoId: String, commentId: String, continuation: String?) = hit<CommentsPage>("replies")
        override suspend fun trending(continuation: String?) = hit<Page<VideoSummary>>("trending")
        override suspend fun related(videoId: String) = hit<List<VideoSummary>>("related")
        override suspend fun channelVideosSorted(channelId: String, sort: ChannelVideoSort, continuation: String?) = hit<Page<VideoSummary>>("channelVideosSorted")
        override suspend fun channelShorts(channelId: String, continuation: String?) = hit<Page<VideoSummary>>("channelShorts")
        override suspend fun channelLive(channelId: String, continuation: String?) = hit<Page<VideoSummary>>("channelLive")
        override suspend fun channelPlaylists(channelId: String, continuation: String?) = hit<Page<PlaylistRef>>("channelPlaylists")
        override suspend fun playlistVideos(playlistId: String, continuation: String?) = hit<Page<VideoSummary>>("playlistVideos")
        override suspend fun suggestions(query: String) = hit<List<String>>("suggestions")
        override suspend fun recentUploads(channelId: String) = hit<List<VideoSummary>>("recentUploads")
    }

    private val all = setOf("search", "videoInfo", "streams", "streamsPreferringOther", "channel", "channelVideos", "channelVideosSorted", "comments", "replies", "trending", "related", "channelShorts", "channelLive", "channelPlaylists", "playlistVideos", "suggestions", "recentUploads")

    private suspend fun callAll(x: Extractor) {
        x.search("q"); x.videoInfo("v"); x.streams("v"); x.streamsPreferringOther("v"); x.channel("c"); x.channelVideos("c"); x.channelVideosSorted("c", ChannelVideoSort.POPULAR); x.comments("v"); x.replies("v", "id")
        x.trending(); x.related("v"); x.channelShorts("c"); x.channelLive("c"); x.channelPlaylists("c"); x.playlistVideos("p"); x.suggestions("q"); x.recentUploads("c")
    }

    private suspend fun check(name: String, except: Set<String> = emptySet(), wrap: (Extractor) -> Extractor) {
        val rec = Recording(); callAll(wrap(rec))
        assertEquals(emptySet(), all - except - rec.calls, "$name does not forward: ${all - rec.calls}")
    }

    @Test fun everyWrapperForwardsEveryMethod() = runTest {
        check("PacedExtractor") { PacedExtractor(it) }
        check("TimeoutExtractor") { TimeoutExtractor(it) }
        check("CaptchaAwareExtractor") { CaptchaAwareExtractor(it, CaptchaCoordinator()) }
        check("FaultInjectingExtractor") { FaultInjectingExtractor(it, FaultInjector()) }
        // The chain answers streamsPreferringOther with a plain streams() when nothing has been served yet (there is no "other" tier to prefer).
        check("FallbackChain", except = setOf("streamsPreferringOther")) { FallbackChain(listOf(ExtractorTier(TierId.ANONYMOUS, it)), { AccountFallbackMode.NEVER }, DiagnosticsLog(), FakeClock()) }
    }
}
