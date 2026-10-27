package com.greenaltambe.vela.extractor

import com.greenaltambe.vela.core.settings.AccountFallbackMode
import com.greenaltambe.vela.extractor.chain.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** A tier that pages through `<family>:<n>` tokens and refuses anyone else's. */
private class PagingTier(private val family: String, var failure: ExtractorError? = null) : Extractor by StubExtractor() {
    val seen = mutableListOf<String?>()

    override suspend fun search(query: String, continuation: String?): ExtractorResult<Page<VideoSummary>> {
        seen += continuation
        failure?.let { return ExtractorResult.Failure(it) }
        val n = if (continuation == null) 0 else
            ContinuationToken.unwrap(family, continuation)?.toIntOrNull() ?: return ContinuationToken.foreign()
        return ExtractorResult.Success(Page(listOf(video("$family$n")), ContinuationToken.wrap(family, "${n + 1}")))
    }

    private fun video(id: String) = VideoSummary(id, id, "c", "c")
}

class PagingTest {
    private val log = DiagnosticsLog()

    private fun chain(vararg tiers: ExtractorTier) = FallbackChain(tiers.toList(), { AccountFallbackMode.ONLY_ON_FAILURE }, log)

    @Test
    fun continuationIsForwardedToTheTier() = runTest {
        val tier = PagingTier("a")
        val c = chain(ExtractorTier(TierId.ANONYMOUS, tier))
        val first = assertIs<ExtractorResult.Success<Page<VideoSummary>>>(c.search("q")).value
        val second = assertIs<ExtractorResult.Success<Page<VideoSummary>>>(c.search("q", first.continuation)).value
        assertEquals(listOf<String?>(null, "a:1"), tier.seen)
        assertEquals("a1", second.items.single().id)
        assertEquals("a:2", second.continuation)
    }

    @Test
    fun pagedCallsAreLabelledInDiagnostics() = runTest {
        val c = chain(ExtractorTier(TierId.ANONYMOUS, PagingTier("a")))
        c.search("q")
        c.search("q", "a:1")
        assertEquals(listOf("search(q)", "search(q, more)"), log.attempts.value.map { it.subject })
    }

    @Test
    fun fallingBackMidPaginationFailsCleanlyOnTheForeignToken() = runTest {
        val a = PagingTier("a")
        val b = PagingTier("b")
        val c = chain(ExtractorTier(TierId.ANONYMOUS, a), ExtractorTier(TierId.YT_DLP, b))
        val first = assertIs<ExtractorResult.Success<Page<VideoSummary>>>(c.search("q")).value

        a.failure = ExtractorError.Blocked()                       // tier a dies between page 1 and page 2
        val result = c.search("q", first.continuation)
        val error = assertIs<ExtractorResult.Failure>(result).error
        assertIs<ExtractorError.Unplayable>(error)
        assertEquals(ContinuationToken.FOREIGN_MESSAGE, error.message)
        assertEquals(listOf<String?>(null, "a:1"), a.seen)
        assertEquals(listOf<String?>("a:1"), b.seen, "b was asked, and refused the token instead of crashing")
    }

    @Test
    fun tokensAreStampedAndUnwrappedPerTier() {
        val token = ContinuationToken.wrap("newpipe", "abc:def")
        assertEquals("newpipe:abc:def", token)
        assertEquals("abc:def", ContinuationToken.unwrap("newpipe", token))
        assertNull(ContinuationToken.unwrap("ytdlp", token))
        assertNull(ContinuationToken.unwrap("newpipe", "newpipefoo"))
    }

    @Test
    fun commentSortIsOnlyOfferedWhenEveryTierHonoursIt() {
        class Sorting(val ok: Boolean) : Extractor by StubExtractor() {
            override val capabilities = ExtractorCapabilities(supportsCommentSort = ok)
        }
        assertTrue(chain(ExtractorTier(TierId.ANONYMOUS, Sorting(true)), ExtractorTier(TierId.YT_DLP, Sorting(true))).capabilities.supportsCommentSort)
        assertFalse(chain(ExtractorTier(TierId.ANONYMOUS, Sorting(true)), ExtractorTier(TierId.YT_DLP, Sorting(false))).capabilities.supportsCommentSort)
        assertFalse(chain().capabilities.supportsCommentSort)
    }

    @Test
    fun stubTiersCanLeaveOutTheYtDlpTier() {
        assertEquals(4, stubTiers().size)
        assertEquals(listOf(TierId.ANONYMOUS, TierId.AUTHENTICATED, TierId.ALT_CLIENT), stubTiers(ytDlp = null).map { it.id })
    }

    @Test
    fun stubExtractorImplementsPagedCalls() = runTest {
        val s = StubExtractor()
        assertEquals(Page<VideoSummary>(emptyList()), assertIs<ExtractorResult.Success<Page<VideoSummary>>>(s.channelVideos("c")).value)
        assertIs<ExtractorResult.Success<CommentsPage>>(s.replies("v", "c"))
    }
}
