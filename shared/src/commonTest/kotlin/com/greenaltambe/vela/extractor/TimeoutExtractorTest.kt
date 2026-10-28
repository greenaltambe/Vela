package com.greenaltambe.vela.extractor

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class TimeoutExtractorTest {
    private val hangs = object : Extractor by StubExtractor() {
        override suspend fun trending(continuation: String?): ExtractorResult<Page<VideoSummary>> = awaitCancellation()
        override suspend fun streams(videoId: String): ExtractorResult<StreamSet> = awaitCancellation()
    }

    @Test fun aHangingCallBecomesATimedOutNetworkErrorInsteadOfSpinningForever() = runTest {
        val x = TimeoutExtractor(hangs, 5.seconds)
        val r = x.trending(null) as ExtractorResult.Failure
        assertIs<ExtractorError.Network>(r.error); assertTrue("timed out after 5 s" in r.error.message)
        assertIs<ExtractorResult.Failure>(x.streams("v"))
    }

    @Test fun aFastCallPassesThroughUntouched() = runTest {
        val x = TimeoutExtractor(StubExtractor(), 5.seconds)
        assertIs<ExtractorResult.Success<*>>(x.streams("v"))
    }
}
