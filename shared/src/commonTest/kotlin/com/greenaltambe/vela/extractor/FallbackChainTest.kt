package com.greenaltambe.vela.extractor

import com.greenaltambe.vela.core.settings.AccountFallbackMode
import com.greenaltambe.vela.extractor.chain.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class FakeClock(var current: Instant = Instant.fromEpochSeconds(1_000_000)) : Clock {
    override fun now() = current
    fun advance(by: Duration) { current += by }
}

class FallbackChainTest {
    private val clock = FakeClock()
    private val log = DiagnosticsLog()
    private var mode = AccountFallbackMode.ONLY_ON_FAILURE

    private val anonymous = StubExtractor(ExtractorError.Blocked())
    private val authenticated = StubExtractor()
    private val altClient = StubExtractor()
    private val ytDlp = StubExtractor()

    private fun chain(
        anon: Extractor = anonymous,
        auth: Extractor = authenticated,
        ttl: Duration = 10.minutes,
    ) = FallbackChain(
        tiers = listOf(
            ExtractorTier(TierId.ANONYMOUS, anon),
            ExtractorTier(TierId.AUTHENTICATED, auth),
            ExtractorTier(TierId.ALT_CLIENT, altClient),
            ExtractorTier(TierId.YT_DLP, ytDlp),
        ),
        accountMode = { mode },
        log = log,
        clock = clock,
        memoryTtl = ttl,
    )

    private fun outcomes() = log.attempts.value.map { it.tier to it.outcome }

    @Test
    fun triesTiersInOrderUntilOneSucceeds() = runTest {
        val result = chain().streams("v")
        assertIs<ExtractorResult.Success<*>>(result)
        assertEquals(
            listOf(TierId.ANONYMOUS to AttemptOutcome.FAILED, TierId.AUTHENTICATED to AttemptOutcome.SUCCESS),
            outcomes(),
        )
        assertEquals(0, altClient.calls)
    }

    @Test
    fun allTiersFailingReturnsLastError() = runTest {
        val failing = { reason: ExtractorError -> StubExtractor(reason) }
        val c = FallbackChain(
            tiers = listOf(
                ExtractorTier(TierId.ANONYMOUS, failing(ExtractorError.Blocked())),
                ExtractorTier(TierId.YT_DLP, failing(ExtractorError.Unplayable("gone"))),
            ),
            accountMode = { mode }, log = log, clock = clock,
        )
        val result = c.streams("v")
        assertIs<ExtractorError.Unplayable>(assertIs<ExtractorResult.Failure>(result).error)
        assertEquals(2, log.attempts.value.size)
    }

    @Test
    fun alwaysModeTriesAccountFirst() = runTest {
        mode = AccountFallbackMode.ALWAYS
        chain(anon = StubExtractor()).streams("v")
        assertEquals(listOf(TierId.AUTHENTICATED to AttemptOutcome.SUCCESS), outcomes())
    }

    @Test
    fun neverModeSkipsAccountEvenWhenItWouldWork() = runTest {
        mode = AccountFallbackMode.NEVER
        chain().streams("v")
        assertEquals(0, authenticated.calls)
        assertEquals(1, altClient.calls)
        assertTrue(log.attempts.value.any { it.tier == TierId.AUTHENTICATED && it.outcome == AttemptOutcome.SKIPPED })
    }

    @Test
    fun rememberedTierSkipsAnonymousUntilExpiry() = runTest {
        val c = chain()
        c.streams("v1")
        assertEquals(1, anonymous.calls)

        c.streams("v2")
        assertEquals(1, anonymous.calls, "anonymous should be skipped while the memory is live")
        assertEquals(TierId.ANONYMOUS to AttemptOutcome.SKIPPED, outcomes()[2])

        clock.advance(11.minutes)
        c.streams("v3")
        assertEquals(2, anonymous.calls, "anonymous is retried after expiry")
    }

    @Test
    fun networkFailuresAreNotRemembered() = runTest {
        val flaky = StubExtractor(ExtractorError.Network())
        val c = chain(anon = flaky)
        c.streams("v1")
        c.streams("v2")
        assertEquals(2, flaky.calls)
    }

    @Test
    fun rememberedTierFailingResetsMemory() = runTest {
        var authFails = false
        val auth = object : Extractor by authenticated {
            override suspend fun streams(videoId: String) =
                if (authFails) ExtractorResult.Failure(ExtractorError.RateLimited()) else authenticated.streams(videoId)
        }
        val c = chain(auth = auth)
        c.streams("v1")                 // anonymous blocked -> auth works, remembered
        authFails = true
        c.streams("v2")                 // auth (remembered) fails -> altClient serves, memory for auth dropped
        assertEquals(1, anonymous.calls)
        c.streams("v3")                 // anonymous is tried again because its route was dropped
        assertEquals(2, anonymous.calls)
    }

    @Test
    fun rememberedRouteIsIgnoredWhenItsWorkedTierIsNotACandidate() = runTest {
        val c = chain()
        c.streams("v1")                 // anonymous blocked -> authenticated works, remembered
        assertEquals(1, anonymous.calls)

        mode = AccountFallbackMode.NEVER
        c.streams("v2")                 // authenticated is no longer a candidate, so the route must not hide anonymous
        assertEquals(2, anonymous.calls)
        assertEquals(1, authenticated.calls, "authenticated is not retried under NEVER")
        assertTrue(log.attempts.value.none { it.tier == TierId.ANONYMOUS && it.outcome == AttemptOutcome.SKIPPED && it.detail?.startsWith("Remembered") == true })
    }

    @Test
    fun successfulStreamSetKeepsItsClientLabel() = runTest {
        val set = assertIs<ExtractorResult.Success<StreamSet>>(chain().streams("v")).value
        assertEquals("stub", set.client)
        assertTrue(set.headers.isEmpty())
    }

    @Test
    fun recordsDurationFromClock() = runTest {
        val slow = object : Extractor by StubExtractor() {
            override suspend fun streams(videoId: String): ExtractorResult<StreamSet> {
                clock.advance(250.milliseconds)
                return ExtractorResult.Success(StreamSet(emptyList(), emptyList()))
            }
        }
        chain(anon = slow).streams("v")
        assertEquals(250.milliseconds, log.attempts.value.single().duration)
    }
}
