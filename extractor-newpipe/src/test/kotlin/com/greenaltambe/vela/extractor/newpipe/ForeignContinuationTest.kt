package com.greenaltambe.vela.extractor.newpipe

import com.greenaltambe.vela.extractor.ContinuationToken
import com.greenaltambe.vela.extractor.ExtractorError
import com.greenaltambe.vela.extractor.ExtractorResult
import kotlinx.coroutines.test.runTest
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** A token from another tier must fail before any network use, so these run offline. */
class ForeignContinuationTest {
    private val noNetwork = object : Downloader() {
        override fun execute(request: Request): Response = throw AssertionError("no request may be made: ${request.url()}")
    }
    private val extractor = NewPipeExtractor { noNetwork }
    private val foreign = "ytdlp:21"

    private fun assertForeign(result: ExtractorResult<*>) {
        val error = assertIs<ExtractorResult.Failure>(result).error
        assertIs<ExtractorError.Unplayable>(error)
        assertEquals(ContinuationToken.FOREIGN_MESSAGE, error.message)
    }

    @Test fun search() = runTest { assertForeign(extractor.search("q", foreign)) }
    @Test fun channelVideos() = runTest { assertForeign(extractor.channelVideos("UC1", foreign)) }
    @Test fun comments() = runTest { assertForeign(extractor.comments("v", continuation = foreign)) }
    @Test fun replies() = runTest { assertForeign(extractor.replies("v", "c", foreign)) }
    @Test fun garbage() = runTest { assertForeign(extractor.search("q", "not even a token")) }
    @Test fun doesNotClaimCommentSortSupport() { assertEquals(false, extractor.capabilities.supportsCommentSort) }
}
