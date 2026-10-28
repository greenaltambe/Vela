package com.greenaltambe.vela.extractor

import com.greenaltambe.vela.core.settings.AccountFallbackMode
import com.greenaltambe.vela.extractor.chain.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Task 15 addendum. The owner saw an age-restricted video fail while the account was paused by "Test: simulate a rate
 * limit". The chain must (a) never let a paused account turn an anonymous success into a failure, and (b) when
 * everything fails, name every tier's reason and what the account did.
 */
class AgeRestrictedChainTest {
    private val clock = FakeClock()
    private val log = DiagnosticsLog()
    private val age = "This age-restricted video cannot be watched anonymously"

    private fun newPipe() = StubExtractor(ExtractorError.Unplayable(age))
    private fun alt() = StubExtractor(ExtractorError.SignInRequired("ANDROID: This video may be inappropriate for some users."))
    private fun ytDlpFails() = StubExtractor(ExtractorError.Unplayable("ERROR: [youtube] x: Sign in to confirm your age."))

    private fun chain(
        ytDlp: Extractor?, account: Extractor = StubExtractor(), signedIn: Boolean = true, paused: () -> String? = { null },
        mode: AccountFallbackMode = AccountFallbackMode.ONLY_ON_FAILURE, accountTier: Boolean = true, hint: (TierId) -> String? = { null },
    ) = FallbackChain(
        listOfNotNull(
            ExtractorTier(TierId.ANONYMOUS, newPipe()),
            if (accountTier) ExtractorTier(TierId.AUTHENTICATED, account) else null,
            ExtractorTier(TierId.ALT_CLIENT, alt()),
            ytDlp?.let { ExtractorTier(TierId.YT_DLP, it) },
        ),
        accountMode = { mode }, log = log, clock = clock, signedInAccount = { if (signedIn) "google:ab12cd" else null },
        tierOrder = { TierOrder.Default }, accountPaused = paused, tierHint = hint,
    )

    private suspend fun Extractor.failure() = assertIs<ExtractorResult.Failure>(streams("x")).error

    @Test fun anonymousYtDlpServesWhenSignedOut() = runTest {
        assertIs<ExtractorResult.Success<*>>(chain(StubExtractor(), signedIn = false).streams("x"))
    }

    @Test fun anonymousYtDlpServesWhenTheAccountIsPaused() = runTest {
        val ytDlp = StubExtractor()
        assertIs<ExtractorResult.Success<*>>(chain(ytDlp, paused = { "account cooling down until 09:25 UTC" }).streams("x"))
        assertEquals(1, ytDlp.calls)
        val account = log.attempts.value.single { it.tier == TierId.AUTHENTICATED }
        assertEquals(AttemptOutcome.SKIPPED, account.outcome)
    }

    /** The account served this video earlier (so the chain remembered "anonymous sign-in failures are routed to the account"), then the breaker opened. */
    @Test fun rememberedAccountRouteDoesNotHideYtDlpOnceTheAccountIsPaused() = runTest {
        var pausedNow: String? = null
        val ytDlp = StubExtractor()
        val c = chain(ytDlp = ytDlp, paused = { pausedNow }, account = StubExtractor())
        // First call: yt-dlp is listed before the account in TierOrder.Default, so it serves; use a failing yt-dlp to force the account.
        val c2 = chain(ytDlp = ytDlpFails(), paused = { pausedNow })
        assertIs<ExtractorResult.Success<*>>(c2.streams("x")) // account served: memory now routes NewPipe/alt/yt-dlp -> account
        pausedNow = "account cooling down until 09:25 UTC"
        // Same chain object, account now paused and yt-dlp still failing: must fail with the explanation, not hang or throw.
        val e = assertIs<TiersFailed>(c2.failure())
        assertEquals(AccountStatus.PAUSED, e.account)
        // A healthy yt-dlp on a chain with the same history serves.
        assertIs<ExtractorResult.Success<*>>(c.streams("x"))
    }

    @Test fun pausedAccountFailureNamesEveryTierAndTheAction() = runTest {
        val e = assertIs<TiersFailed>(chain(ytDlpFails(), paused = { "account cooling down until 09:25 UTC" }).failure())
        assertEquals(FailureReason.SIGN_IN_REQUIRED, e.reason)
        assertEquals(AccountStatus.PAUSED, e.account)
        assertTrue(e.ageRestricted)
        assertEquals(listOf("NewPipe", "alt client", "yt-dlp"), e.tiers.map { it.tier })
        for (part in listOf("Age-restricted", "NewPipe (age-restricted)", "alt client (age-restricted)", "yt-dlp (age-restricted)", "paused", "09:25 UTC")) {
            assertTrue(part in e.message, "missing '$part' in: ${e.message}")
        }
    }

    @Test fun signedOutSaysNoAccount() = runTest {
        val e = assertIs<TiersFailed>(chain(ytDlpFails(), signedIn = false).failure())
        assertEquals(AccountStatus.SIGNED_OUT, e.account)
        assertTrue("No account is signed in" in e.message)
    }

    @Test fun accountFallbackNeverSaysTurnedOff() = runTest {
        assertEquals(AccountStatus.DISABLED, assertIs<TiersFailed>(chain(ytDlpFails(), mode = AccountFallbackMode.NEVER).failure()).account)
    }

    @Test fun accountThatAlsoFailedIsReported() = runTest {
        val e = assertIs<TiersFailed>(chain(ytDlpFails(), account = StubExtractor(ExtractorError.SignInRequired("cookies rejected"))).failure())
        assertEquals(AccountStatus.TRIED, e.account)
        assertEquals("account", e.tiers.last().tier)
        assertTrue("The account also failed" in e.message)
    }

    /** Android today: no yt-dlp, no account tier. The message must say there is no anonymous path. */
    @Test fun androidShapeSaysNoAnonymousPathExists() = runTest {
        val e = assertIs<TiersFailed>(chain(ytDlp = null, accountTier = false).failure())
        assertEquals(AccountStatus.NO_ACCOUNT_TIER, e.account)
        assertTrue("No tier on this device can play age-restricted videos without an account yet" in e.message, e.message)
        assertTrue("not available on this device yet" in e.message)
    }

    @Test fun environmentHintsAreIncluded() = runTest {
        val e = assertIs<TiersFailed>(chain(ytDlpFails(), signedIn = false, hint = { if (it == TierId.YT_DLP) "yt-dlp 2026.08.19 is older than 2026.10.26" else null }).failure())
        assertTrue("yt-dlp 2026.08.19 is older" in e.message)
    }

    @Test fun nonSignInFailuresKeepTheLastError() = runTest {
        val c = FallbackChain(listOf(ExtractorTier(TierId.ANONYMOUS, StubExtractor(ExtractorError.Network("offline")))), { AccountFallbackMode.ONLY_ON_FAILURE }, log, clock)
        assertIs<ExtractorError.Network>(c.failure())
    }
}
