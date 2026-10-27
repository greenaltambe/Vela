package com.greenaltambe.vela.extractor

import com.greenaltambe.vela.core.settings.AccountFallbackMode
import com.greenaltambe.vela.extractor.chain.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

class AccountAwareChainTest {
    private val clock = FakeClock()
    private val log = DiagnosticsLog()
    private var mode = AccountFallbackMode.ONLY_ON_FAILURE
    private var account: String? = "google:ab12cd"

    private val anonymous = StubExtractor(ExtractorError.SignInRequired())
    private val authenticated = StubExtractor()
    private val altClient = StubExtractor()
    private val ytDlp = StubExtractor()

    private fun chain(auth: Extractor = authenticated, alt: Extractor = altClient) = FallbackChain(
        tiers = listOf(
            ExtractorTier(TierId.ANONYMOUS, anonymous), ExtractorTier(TierId.AUTHENTICATED, auth),
            ExtractorTier(TierId.ALT_CLIENT, alt), ExtractorTier(TierId.YT_DLP, ytDlp),
        ),
        accountMode = { mode }, log = log, clock = clock, memoryTtl = 10.minutes,
        signedInAccount = { account },
    )

    private var mark = 0
    private fun mark() { mark = log.attempts.value.size }
    private fun recent() = log.attempts.value.drop(mark)
    private fun outcomes() = log.attempts.value.map { it.tier to it.outcome }

    @Test fun signInRequiredRoutesToTheAccountTierWhenAnAccountExists() = runTest {
        assertIs<ExtractorResult.Success<*>>(chain().streams("age"))
        assertEquals(listOf(TierId.ANONYMOUS to AttemptOutcome.FAILED, TierId.AUTHENTICATED to AttemptOutcome.SUCCESS), outcomes())
        assertEquals("account google:ab12cd", log.attempts.value.last().detail, "the label, never a name or e-mail")
    }

    @Test fun withoutAnAccountTheAccountTierIsSkippedAndTheNextTierServes() = runTest {
        account = null
        assertIs<ExtractorResult.Success<*>>(chain().streams("age"))
        assertEquals(0, authenticated.calls)
        assertEquals(listOf(TierId.AUTHENTICATED to AttemptOutcome.SKIPPED, TierId.ANONYMOUS to AttemptOutcome.FAILED, TierId.ALT_CLIENT to AttemptOutcome.SUCCESS), outcomes())
        assertEquals("No signed-in account", log.attempts.value.first { it.outcome == AttemptOutcome.SKIPPED }.detail)
    }

    @Test fun orderIsAnonymousThenAccountThenAltClientThenYtDlp() = runTest {
        val failing = { StubExtractor(ExtractorError.Blocked()) }
        val c = FallbackChain(
            listOf(ExtractorTier(TierId.ANONYMOUS, failing()), ExtractorTier(TierId.AUTHENTICATED, failing()), ExtractorTier(TierId.ALT_CLIENT, failing()), ExtractorTier(TierId.YT_DLP, ytDlp)),
            { mode }, log, clock, signedInAccount = { account },
        )
        assertIs<ExtractorResult.Success<*>>(c.streams("v"))
        assertEquals(listOf(TierId.ANONYMOUS, TierId.AUTHENTICATED, TierId.ALT_CLIENT, TierId.YT_DLP), log.attempts.value.map { it.tier })
    }

    @Test fun alwaysPutsTheAccountTierFirstAndNeverDropsIt() = runTest {
        mode = AccountFallbackMode.ALWAYS
        chain().streams("v")
        assertEquals(TierId.AUTHENTICATED, log.attempts.value.first().tier)
        assertEquals(0, anonymous.calls)

        mark(); mode = AccountFallbackMode.NEVER
        chain().streams("v")
        assertEquals(listOf(TierId.ANONYMOUS, TierId.ALT_CLIENT), recent().filter { it.outcome != AttemptOutcome.SKIPPED }.map { it.tier })
    }

    @Test fun tierMemoryIsClearedWhenTheAccountChanges() = runTest {
        val c = chain()
        c.streams("v")                       // anonymous: sign-in required, the account tier works -> remembered
        mark(); c.streams("v")
        assertTrue(recent().any { it.outcome == AttemptOutcome.SKIPPED && it.tier == TierId.ANONYMOUS }, "memory routes around anonymous")
        account = "google:other1"
        mark(); c.streams("v")
        assertTrue(recent().none { it.outcome == AttemptOutcome.SKIPPED }, "the switch cleared what the other account learned")
    }

    @Test fun credentialsNeverReachTheLog() = runTest {
        val leaky = StubExtractor(ExtractorError.Network("failed: Cookie: SAPISID=abc123def456; SID=zzz777 Authorization: SAPISIDHASH 1_deadbeef"))
        chain(auth = leaky).streams("v")
        val text = log.attempts.value.joinToString { "${it.detail}" }
        assertFalse(text.contains("abc123def456") || text.contains("zzz777") || text.contains("deadbeef"), text)
    }
}
