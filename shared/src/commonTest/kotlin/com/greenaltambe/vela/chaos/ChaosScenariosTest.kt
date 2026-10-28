package com.greenaltambe.vela.chaos

import com.greenaltambe.vela.auth.*
import com.greenaltambe.vela.chaos.Fault.*
import com.greenaltambe.vela.core.settings.AccountFallbackMode
import com.greenaltambe.vela.core.settings.AccountFallbackMode.*
import com.greenaltambe.vela.extractor.*
import com.greenaltambe.vela.extractor.chain.*
import com.greenaltambe.vela.extractor.chain.TierId.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

/**
 * Data-driven fault scenarios: each row scripts the four tiers, runs a call (or several), and asserts which tiers were
 * tried in which order, how each attempt ended, whether the account was used, and what the caller got.
 */
class ChaosScenariosTest {
    private class Row(
        val name: String, val mode: AccountFallbackMode = ONLY_ON_FAILURE, val account: String? = "google:ab12cd",
        val anon: List<Fault>, val alt: List<Fault> = listOf(OK), val ytDlp: List<Fault> = listOf(OK), val auth: List<Fault> = listOf(OK),
        val calls: Int = 1,
        /** tier -> outcome of the LAST call, in the order the chain touched them. */
        val expectLast: List<Pair<TierId, AttemptOutcome>>,
        val expectAccountCalls: Int, val expectSuccess: Boolean = true,
    )

    private val rows = listOf(
        Row("anonymous blocked -> alt client serves; the account is never touched", anon = listOf(FORBIDDEN_403),
            expectLast = listOf(ANONYMOUS to AttemptOutcome.FAILED, ALT_CLIENT to AttemptOutcome.SUCCESS), expectAccountCalls = 0),
        Row("everything anonymous fails -> account is last and serves", anon = listOf(SORRY_CAPTCHA), alt = listOf(FORBIDDEN_403), ytDlp = listOf(RATE_LIMITED_429),
            expectLast = listOf(ANONYMOUS to AttemptOutcome.FAILED, ALT_CLIENT to AttemptOutcome.FAILED, YT_DLP to AttemptOutcome.FAILED, AUTHENTICATED to AttemptOutcome.SUCCESS), expectAccountCalls = 1),
        Row("sign-in required twice: the second request jumps straight to the account", anon = listOf(LOGIN_REQUIRED), alt = listOf(LOGIN_REQUIRED), ytDlp = listOf(LOGIN_REQUIRED), calls = 2,
            expectLast = listOf(ANONYMOUS to AttemptOutcome.SKIPPED, ALT_CLIENT to AttemptOutcome.SKIPPED, YT_DLP to AttemptOutcome.SKIPPED, AUTHENTICATED to AttemptOutcome.SUCCESS), expectAccountCalls = 2),
        Row("NEVER: the account is not used even when everything else fails", mode = NEVER, anon = listOf(FORBIDDEN_403), alt = listOf(FORBIDDEN_403), ytDlp = listOf(LOGIN_REQUIRED),
            expectLast = listOf(AUTHENTICATED to AttemptOutcome.SKIPPED, ANONYMOUS to AttemptOutcome.FAILED, ALT_CLIENT to AttemptOutcome.FAILED, YT_DLP to AttemptOutcome.FAILED), expectAccountCalls = 0, expectSuccess = false),
        Row("ALWAYS: the account goes first and the rest are not needed", mode = ALWAYS, anon = listOf(OK),
            expectLast = listOf(AUTHENTICATED to AttemptOutcome.SUCCESS), expectAccountCalls = 1),
        Row("signed out: the account tier is skipped with a reason", account = null, anon = listOf(LOGIN_REQUIRED), alt = listOf(UNPLAYABLE), ytDlp = listOf(OK),
            expectLast = listOf(AUTHENTICATED to AttemptOutcome.SKIPPED, ANONYMOUS to AttemptOutcome.FAILED, ALT_CLIENT to AttemptOutcome.FAILED, YT_DLP to AttemptOutcome.SUCCESS), expectAccountCalls = 0),
        Row("offline: every tier is tried once and the caller gets a network error", anon = listOf(OFFLINE), alt = listOf(OFFLINE), ytDlp = listOf(TIMEOUT), auth = listOf(OFFLINE),
            expectLast = listOf(ANONYMOUS to AttemptOutcome.FAILED, ALT_CLIENT to AttemptOutcome.FAILED, YT_DLP to AttemptOutcome.FAILED, AUTHENTICATED to AttemptOutcome.FAILED), expectAccountCalls = 1, expectSuccess = false),
        Row("malformed and truncated bodies are failures, not empty successes", anon = listOf(MALFORMED_JSON), alt = listOf(TRUNCATED_BODY),
            expectLast = listOf(ANONYMOUS to AttemptOutcome.FAILED, ALT_CLIENT to AttemptOutcome.FAILED, YT_DLP to AttemptOutcome.SUCCESS), expectAccountCalls = 0),
        Row("unplayable everywhere: the account is still asked last, then the failure is reported", anon = listOf(UNPLAYABLE), alt = listOf(UNPLAYABLE), ytDlp = listOf(UNPLAYABLE), auth = listOf(UNPLAYABLE),
            expectLast = listOf(ANONYMOUS to AttemptOutcome.FAILED, ALT_CLIENT to AttemptOutcome.FAILED, YT_DLP to AttemptOutcome.FAILED, AUTHENTICATED to AttemptOutcome.FAILED), expectAccountCalls = 1, expectSuccess = false),
    )

    @Test fun scenarioTable() = runTest {
        val failures = mutableListOf<String>()
        for (r in rows) {
            val clock = com.greenaltambe.vela.extractor.FakeClock()
            val log = DiagnosticsLog()
            val anon = ChaosExtractor(*r.anon.toTypedArray()); val alt = ChaosExtractor(*r.alt.toTypedArray())
            val yt = ChaosExtractor(*r.ytDlp.toTypedArray()); val auth = ChaosExtractor(*r.auth.toTypedArray())
            val chain = FallbackChain(
                listOf(ExtractorTier(ANONYMOUS, anon), ExtractorTier(ALT_CLIENT, alt), ExtractorTier(YT_DLP, yt), ExtractorTier(AUTHENTICATED, auth)),
                { r.mode }, log, clock, signedInAccount = { r.account }, tierOrder = { TierOrder.Default },
            )
            var result: ExtractorResult<*>? = null
            var before = 0
            repeat(r.calls) { before = log.attempts.value.size; result = chain.streams("v") }
            val last = log.attempts.value.drop(before).filter { it.outcome != AttemptOutcome.NOTE }.map { it.tier to it.outcome }
            if (last != r.expectLast) failures += "${r.name}: tried $last, expected ${r.expectLast}"
            if (auth.calls != r.expectAccountCalls) failures += "${r.name}: account calls ${auth.calls}, expected ${r.expectAccountCalls}"
            if ((result is ExtractorResult.Success<*>) != r.expectSuccess) failures += "${r.name}: result $result"
            if (log.attempts.value.any { Redaction.looksSecret(it.detail ?: "") }) failures += "${r.name}: a credential reached the log"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test fun defaultOrderSpendsTheAccountLastAndTheSettingCannotMoveItForward() {
        assertEquals(listOf(ANONYMOUS, ALT_CLIENT, YT_DLP, AUTHENTICATED), TierOrder.Default)
        assertEquals(listOf(YT_DLP, ANONYMOUS, ALT_CLIENT, AUTHENTICATED), TierOrder.sanitize(TierOrder.parse("AUTHENTICATED, YT_DLP"), ONLY_ON_FAILURE))
        assertEquals(AUTHENTICATED, TierOrder.sanitize(TierOrder.Default, ALWAYS).first())
        assertEquals(TierOrder.Default, TierOrder.parse("  nonsense ,, "))
        assertEquals(listOf(ALT_CLIENT, ANONYMOUS, YT_DLP, AUTHENTICATED), TierOrder.parse("alt_client,anonymous"))
    }

    @Test fun anAccountThatIsCoolingDownIsSkippedWithTheTimeAndComesBackAfterTheCooldown() = runTest {
        val clock = com.greenaltambe.vela.extractor.FakeClock(); val log = DiagnosticsLog()
        val gov = AccountGovernor("a1", clock, { GovernorLimits(minSpacingMs = 0) })
        val authInner = ChaosExtractor(RATE_LIMITED_429, OK)
        // the account tier as the apps build it: every call goes through the governor, a 429-looking failure opens the breaker
        val governedAuth = object : Extractor by authInner {
            override suspend fun streams(videoId: String): ExtractorResult<StreamSet> = try {
                gov.call(CallKind.PLAYBACK, "www.youtube.com", inspect = { r: ExtractorResult<StreamSet> -> (r as? ExtractorResult.Failure)?.let { BlockPatterns.scan(it.error.message) } }) { authInner.streams(videoId) }
            } catch (d: GovernorDenied) { ExtractorResult.Failure(ExtractorError.TierUnavailable(d.message!!)) }
        }
        val chain = FallbackChain(
            listOf(ExtractorTier(ANONYMOUS, ChaosExtractor(LOGIN_REQUIRED)), ExtractorTier(AUTHENTICATED, governedAuth)),
            { ONLY_ON_FAILURE }, log, clock, signedInAccount = { "google:ab12cd" }, tierOrder = { TierOrder.Default },
            accountPaused = { gov.cooldownUntil()?.let { "account cooling down until ${it.epochSeconds}" } },
        )
        assertIs<ExtractorResult.Failure>(chain.streams("v"))                 // 429 -> breaker opens
        assertEquals(BreakerState.OPEN, gov.status.value.state)
        val mark = log.attempts.value.size
        assertIs<ExtractorResult.Failure>(chain.streams("v"))
        val skipped = log.attempts.value.drop(mark).single { it.tier == AUTHENTICATED }
        assertEquals(AttemptOutcome.SKIPPED, skipped.outcome); assertTrue(skipped.detail!!.startsWith("account cooling down until"))
        assertEquals(1, authInner.calls, "nothing was sent to the account tier while open")
        clock.advance(61.minutes)
        assertIs<ExtractorResult.Success<*>>(chain.streams("v"))               // half-open probe succeeds
        assertEquals(BreakerState.CLOSED, gov.status.value.state)
    }

    @Test fun switchingAccountsAfterAFailureStartsWithAFreshMemory() = runTest {
        val clock = com.greenaltambe.vela.extractor.FakeClock(); val log = DiagnosticsLog(); var account: String? = "google:one111"
        val chain = FallbackChain(
            listOf(ExtractorTier(ANONYMOUS, ChaosExtractor(LOGIN_REQUIRED)), ExtractorTier(AUTHENTICATED, ChaosExtractor(OK))),
            { ONLY_ON_FAILURE }, log, clock, signedInAccount = { account }, tierOrder = { TierOrder.Default },
        )
        chain.streams("v"); var mark = log.attempts.value.size; chain.streams("v")
        assertTrue(log.attempts.value.drop(mark).any { it.outcome == AttemptOutcome.SKIPPED }, "memory routes around anonymous")
        account = "google:two222"; mark = log.attempts.value.size; chain.streams("v")
        assertTrue(log.attempts.value.drop(mark).none { it.outcome == AttemptOutcome.SKIPPED })
    }

    @Test fun downloadsDoNotUseTheAccountUnlessTheUserAllowedIt() = runTest {
        assertEquals(NEVER, downloadAccountMode(allowed = false))
        assertEquals(ONLY_ON_FAILURE, downloadAccountMode(allowed = true))
        val auth = ChaosExtractor(OK)
        val chain = FallbackChain(
            listOf(ExtractorTier(ANONYMOUS, ChaosExtractor(LOGIN_REQUIRED)), ExtractorTier(AUTHENTICATED, auth)),
            { downloadAccountMode(allowed = false) }, DiagnosticsLog(), signedInAccount = { "google:ab12cd" }, tierOrder = { TierOrder.Default },
        )
        assertIs<ExtractorResult.Failure>(chain.streams("v")); assertEquals(0, auth.calls)
    }
}

/** The jump rule on its own, without TierMemory in the way: anonymous tiers that recently said "sign in" are not asked again before the account. */
class SignInJumpTest {
    @Test fun aTierThatSaidSignInRecentlyIsSkippedWhenAnEarlierTierSaysItAgain() = runTest {
        val clock = com.greenaltambe.vela.extractor.FakeClock(); val log = DiagnosticsLog()
        val alt = ChaosExtractor(LOGIN_REQUIRED, OK)          // second time it would even work: still skipped, the video is the same kind
        val chain = FallbackChain(
            listOf(ExtractorTier(ANONYMOUS, ChaosExtractor(LOGIN_REQUIRED)), ExtractorTier(ALT_CLIENT, alt), ExtractorTier(AUTHENTICATED, ChaosExtractor(UNPLAYABLE, OK))),
            { ONLY_ON_FAILURE }, log, clock, memoryTtl = 10.minutes, signedInAccount = { "google:ab12cd" }, tierOrder = { TierOrder.Default },
        )
        chain.streams("v")                                    // anon LR, alt LR, account UNPLAYABLE (no route is remembered)
        val mark = log.attempts.value.size
        chain.streams("v")
        val second = log.attempts.value.drop(mark).map { it.tier to it.outcome }
        assertEquals(listOf(ANONYMOUS to AttemptOutcome.FAILED, ALT_CLIENT to AttemptOutcome.SKIPPED, AUTHENTICATED to AttemptOutcome.SUCCESS), second)
        assertEquals(1, alt.calls, "alt was called on the first request only")
    }
}

/** Strictly anonymous operations: even when every anonymous tier fails, the account is never asked, and policy gates the rest. */
class AnonymousOnlyOperationsTest {
    private fun chain(auth: ChaosExtractor, details: Boolean = false, channels: Boolean = false) = FallbackChain(
        listOf(ExtractorTier(ANONYMOUS, ChaosExtractor(FORBIDDEN_403)), ExtractorTier(AUTHENTICATED, auth)),
        { ONLY_ON_FAILURE }, DiagnosticsLog(), signedInAccount = { "google:ab12cd" }, tierOrder = { TierOrder.Default },
        accountMayServe = { op -> when (op) { AccountOp.PLAYBACK -> true; AccountOp.DETAILS -> details; AccountOp.CHANNELS -> channels; AccountOp.NEVER -> false } },
    )

    @Test fun searchTrendingCommentsRelatedAndFeedsNeverReachTheAccount() = runTest {
        val auth = ChaosExtractor(OK); val c = chain(auth, details = true, channels = true)
        c.search("q"); c.trending(); c.comments("v"); c.replies("v", "c"); c.related("v"); c.recentUploads("UC1")
        assertEquals(0, auth.calls)
    }

    @Test fun channelsAndDetailsFollowTheirSettingsAndPlaybackAlwaysMay() = runTest {
        val off = ChaosExtractor(OK); val c = chain(off)
        c.channel("UC1"); c.channelVideos("UC1"); c.videoInfo("v")
        assertEquals(0, off.calls, "off by default")
        assertIs<ExtractorResult.Success<*>>(c.streams("v")); assertEquals(1, off.calls)
        val on = ChaosExtractor(OK); val c2 = chain(on, details = true, channels = true)
        c2.channel("UC1"); c2.videoInfo("v"); assertEquals(2, on.calls)
    }
}
