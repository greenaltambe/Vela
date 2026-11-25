package com.greenaltambe.vela.extractor

import com.greenaltambe.vela.core.settings.AccountFallbackMode
import com.greenaltambe.vela.extractor.chain.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Which source is asked for a playlist page: the one that reads the whole page first, and a later page goes back to where its token came from. */
class PlaylistPageChainTest {
    private val asked = mutableListOf<String>()
    private var nativeAnswer: ExtractorResult<PlaylistContents>? = null

    private fun source(name: String, full: Boolean, answer: (String?) -> ExtractorResult<PlaylistContents>) = object : Extractor by StubExtractor() {
        override val capabilities = ExtractorCapabilities(playlistDetails = full)
        override suspend fun playlistPage(playlistId: String, continuation: String?): ExtractorResult<PlaylistContents> { asked += name; return answer(continuation) }
        override suspend fun streams(videoId: String): ExtractorResult<StreamSet> { asked += "$name streams"; return ExtractorResult.Success(StreamSet(emptyList(), emptyList())) }
    }
    private fun ok(token: String? = null) = ExtractorResult.Success(PlaylistContents(null, emptyList(), token))

    private fun chain(): FallbackChain {
        val plain = source("plain", false) { c -> if (c?.startsWith("native-playlist:") == true) ExtractorResult.Failure(ExtractorError.TokenNotMine("no")) else ok("plain-token") }
        val native = source("native", true) { nativeAnswer ?: ok("native-playlist:t") }
        // The order the apps wire them in: the full reader comes after the other.
        return FallbackChain(listOf(ExtractorTier(TierId.ANONYMOUS, plain), ExtractorTier(TierId.ALT_CLIENT, native)), { AccountFallbackMode.NEVER }, DiagnosticsLog(), FakeClock())
    }

    @Test fun theFullReaderIsAskedFirstAndItsTokenGoesBackToIt() = runTest {
        val c = chain()
        assertTrue(c.capabilities.playlistDetails)
        c.playlistPage("PL1"); c.playlistPage("PL1", "native-playlist:t")
        assertEquals(listOf("native", "native"), asked)
    }

    @Test fun whenItCannotReadThePageTheOtherAnswersAndItsTokenStaysWithIt() = runTest {
        nativeAnswer = ExtractorResult.Failure(ExtractorError.ParseFailure("changed"))
        val c = chain()
        val first = assertIs<ExtractorResult.Success<PlaylistContents>>(c.playlistPage("PL1"))
        assertEquals("plain-token", first.value.continuation)
        assertIs<ExtractorResult.Success<PlaylistContents>>(c.playlistPage("PL1", "plain-token"))
        assertEquals(listOf("native", "plain", "plain"), asked)
    }

    @Test fun aPageItCannotReadDoesNotKeepItFromPlayingVideos() = runTest {
        nativeAnswer = ExtractorResult.Failure(ExtractorError.ParseFailure("changed"))
        val native = source("native", true) { nativeAnswer!! }
        val plain = source("plain", false) { ok() }
        val c = FallbackChain(listOf(ExtractorTier(TierId.ALT_CLIENT, native), ExtractorTier(TierId.ANONYMOUS, plain)), { AccountFallbackMode.NEVER }, DiagnosticsLog(), FakeClock())
        c.playlistPage("PL1"); asked.clear()
        c.streams("v")
        assertEquals(listOf("native streams"), asked)
    }
}
