package com.greenaltambe.vela.extractor.newpipe

import com.greenaltambe.vela.extractor.ExtractorError
import com.greenaltambe.vela.extractor.ExtractorResult
import kotlinx.coroutines.test.runTest
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * The replies of a community post's comment carry a native ("n1:") token that only the alt client can read. NewPipe must answer "not supported",
 * NOT "token not mine": the fallback chain ends on the latter, so the alt client behind NewPipe was never asked and no post reply ever loaded.
 */
class NativeReplyTokenTest {
    private val noNetwork = object : Downloader() {
        override fun execute(request: Request): Response = throw AssertionError("no request may be made: ${request.url()}")
    }
    private val extractor = NewPipeExtractor(downloader = { noNetwork })

    @Test fun aNativePostReplyTokenIsPassedOnNotRefusedAsForeign() = runTest {
        val error = assertIs<ExtractorResult.Failure>(extractor.replies("", "c1", "n1:abc")).error
        assertFalse(error is ExtractorError.TokenNotMine, "TokenNotMine would end the chain before the alt client")
    }
}
