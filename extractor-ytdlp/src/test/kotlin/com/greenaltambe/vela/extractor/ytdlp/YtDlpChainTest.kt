package com.greenaltambe.vela.extractor.ytdlp

import com.greenaltambe.vela.core.settings.AccountFallbackMode
import com.greenaltambe.vela.extractor.*
import com.greenaltambe.vela.extractor.chain.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class YtDlpChainTest {
    private val log = DiagnosticsLog()
    private val runner = FakeRunner.ok(Fixtures.text("video.json"))
    private val anonymous = StubExtractor(ExtractorError.Blocked("IP blocked"))

    private val chain = FallbackChain(
        tiers = listOf(ExtractorTier(TierId.ANONYMOUS, anonymous), ExtractorTier(TierId.YT_DLP, YtDlpExtractor(runner))),
        accountMode = { AccountFallbackMode.ONLY_ON_FAILURE },
        log = log,
        clock = FakeClock(),
    )

    private fun outcomes() = log.attempts.value.map { it.tier to it.outcome }

    @Test
    fun blockedAnonymousFallsThroughToYtDlpAndTheRouteIsRemembered() = runTest {
        val first = chain.streams("dQw4w9WgXcQ")
        assertEquals("yt-dlp", assertIs<ExtractorResult.Success<StreamSet>>(first).value.client)
        assertEquals(listOf(TierId.ANONYMOUS to AttemptOutcome.FAILED, TierId.YT_DLP to AttemptOutcome.SUCCESS), outcomes())

        chain.streams("another")
        assertEquals(1, anonymous.calls, "anonymous is skipped while the route to yt-dlp is remembered")
        assertEquals(2, runner.commands.size)
        assertEquals(TierId.ANONYMOUS to AttemptOutcome.SKIPPED, outcomes()[2])
        assertEquals(TierId.YT_DLP to AttemptOutcome.SUCCESS, outcomes()[3])
    }

    @Test
    fun aPageTokenFromOneTierIsRefusedByTheOtherInsteadOfCrashing() = runTest {
        val result = chain.search("q", "newpipe:whatever")
        val error = assertIs<ExtractorResult.Failure>(result).error
        assertIs<ExtractorError.Unplayable>(error)
        assertEquals(ContinuationToken.FOREIGN_MESSAGE, error.message)
        assertTrue(runner.commands.isEmpty(), "yt-dlp must not be run for a token that isn't its own")
    }
}
