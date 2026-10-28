package com.greenaltambe.vela.auth

import com.greenaltambe.vela.extractor.FakeClock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class GovernorTest {
    private val clock = FakeClock()
    private val audit = mutableListOf<AuditEntry>()
    private var limits = GovernorLimits(minSpacingMs = 0)
    private val sleeps = mutableListOf<Long>()
    private val store = InMemoryBreakerStore()
    private fun gov() = AccountGovernor("a1", clock, { limits }, store, { audit += it }, { sleeps += it; clock.advance(kotlin.time.Duration.parse("${it}ms")) })
    private class Blocked(override val signal: BlockSignal) : Exception("blocked"), BreakerSignal

    private suspend fun AccountGovernor.ok(kind: CallKind = CallKind.BROWSE) = call(kind, "www.youtube.com") { "ok" }
    private suspend fun AccountGovernor.fail(signal: BlockSignal) { runCatching { call<String>(CallKind.BROWSE, "www.youtube.com") { throw Blocked(signal) } } }

    @Test fun callsPassAndAreAudited() = runTest {
        assertEquals("ok", gov().ok())
        assertEquals(listOf("ok"), audit.map { it.result })
        assertEquals("www.youtube.com", audit.single().host)
    }

    @Test fun killSwitchRefusesEverythingWithoutCalling() = runTest {
        limits = limits.copy(killSwitch = true)
        var called = false
        val e = assertFailsWith<GovernorDenied> { gov().call(CallKind.BROWSE, "h") { called = true } }
        assertEquals(DenyReason.KILL_SWITCH, e.reason); assertFalse(called)
        assertEquals("KILL_SWITCH", audit.single().decision)
    }

    @Test fun spacingWaitsAtLeastTheMinimumPlusBoundedJitter() = runTest {
        limits = limits.copy(minSpacingMs = 900)
        val g = gov(); g.ok(); g.ok(); g.ok()
        assertEquals(2, sleeps.size)
        assertTrue(sleeps.all { it in 900..1200 }, "$sleeps")
    }

    @Test fun budgetsAreEnforcedPerKindAndWindow() = runTest {
        limits = limits.copy(playbackPerHour = 2, browsePerMinute = 1)
        val g = gov()
        g.ok(CallKind.PLAYBACK); g.ok(CallKind.PLAYBACK)
        val e = assertFailsWith<GovernorDenied> { g.ok(CallKind.PLAYBACK) }
        assertEquals(DenyReason.BUDGET, e.reason)
        g.ok(CallKind.BROWSE)                                  // a separate budget
        assertFailsWith<GovernorDenied> { g.ok(CallKind.BROWSE) }
        clock.advance(61.seconds())
        g.ok(CallKind.BROWSE)                                  // the minute window slid
        clock.advance(1.hours)
        g.ok(CallKind.PLAYBACK)
    }

    private fun Int.seconds() = kotlin.time.Duration.parse("${this}s")

    @Test fun aBlockSignalOpensTheBreakerAndNothingIsCalledWhileOpen() = runTest {
        val g = gov(); g.fail(BlockSignal.HTTP_429)
        assertEquals(BreakerState.OPEN, g.status.value.state)
        assertNotNull(g.cooldownUntil())
        var called = false
        val e = assertFailsWith<GovernorDenied> { g.call(CallKind.BROWSE, "h") { called = true } }
        assertEquals(DenyReason.COOLING_DOWN, e.reason); assertFalse(called)
        assertTrue(e.message!!.startsWith("Account cooling down until "), e.message)
    }

    @Test fun cooldownsEscalateOneSixTwentyFourHoursAndStayAtTwentyFour() = runTest {
        val g = gov()
        val seen = mutableListOf<Long>()
        repeat(4) {
            g.fail(BlockSignal.CAPTCHA)
            seen += (g.status.value.openUntil!! - clock.now()).inWholeMinutes
            clock.advance(25.hours)                       // past any cooldown -> half open on the next call
            g.fail(BlockSignal.CAPTCHA)                   // the probe fails too
            clock.advance(25.hours)
        }
        assertEquals(listOf(60L, 360L, 1440L, 1440L).take(1), seen.take(1))
        assertTrue(seen.all { it in setOf(60L, 360L, 1440L) }, "$seen")
        assertEquals(1440L, seen.last())
    }

    @Test fun afterTheCooldownOneProbeIsAllowedAndSuccessClosesTheBreaker() = runTest {
        val g = gov(); g.fail(BlockSignal.HTTP_429)
        clock.advance(61.minutes)
        assertEquals("ok", g.ok())
        assertEquals(BreakerState.CLOSED, g.status.value.state)
    }

    @Test fun aFailedProbeReopensWithALongerCooldown() = runTest {
        val g = gov(); g.fail(BlockSignal.HTTP_429)
        clock.advance(61.minutes)
        g.fail(BlockSignal.HTTP_429)
        assertEquals(BreakerState.OPEN, g.status.value.state)
        assertEquals(360L, (g.status.value.openUntil!! - clock.now()).inWholeMinutes)
    }

    @Test fun sustainedSuccessForgivesEscalation() = runTest {
        limits = limits.copy(browsePerMinute = 100)
        val g = gov(); g.fail(BlockSignal.HTTP_429)
        clock.advance(61.minutes)
        repeat(AccountGovernor.SUSTAINED) { clock.advance(10.seconds()); g.ok() }
        assertEquals(0, g.status.value.level)
        g.fail(BlockSignal.HTTP_429)
        assertEquals(60L, (g.status.value.openUntil!! - clock.now()).inWholeMinutes)
    }

    @Test fun tryNowMovesToHalfOpenAndAllowsOneProbe() = runTest {
        val g = gov(); g.fail(BlockSignal.CAPTCHA)
        g.tryNow()
        assertEquals(BreakerState.HALF_OPEN, g.status.value.state)
        assertEquals("ok", g.ok()); assertEquals(BreakerState.CLOSED, g.status.value.state)
    }

    @Test fun repeatedLoginRequiredOnlyCountsAfterAValidSessionAndTwice() = runTest {
        val g = gov()
        g.loginRequired(); g.loginRequired()
        assertEquals(BreakerState.CLOSED, g.status.value.state, "never worked: that is a plain sign-in failure, not a flag risk")
        g.ok(); g.loginRequired()
        assertEquals(BreakerState.CLOSED, g.status.value.state)
        g.loginRequired()
        assertEquals(BreakerState.OPEN, g.status.value.state)
        assertEquals(BlockSignal.REPEATED_LOGIN_REQUIRED, g.status.value.lastSignal)
    }

    @Test fun textPatternsInErrorsAndInspectedResultsTripTheBreaker() = runTest {
        val g = gov()
        runCatching { g.call<String>(CallKind.BROWSE, "h") { error("Our systems have detected unusual traffic from your computer") } }
        assertEquals(BlockSignal.UNUSUAL_TRAFFIC, g.status.value.lastSignal)
        val g2 = AccountGovernor("a2", clock, { limits }, store, { audit += it }, { clock.advance(kotlin.time.Duration.parse("${it}ms")) })
        g2.call(CallKind.BROWSE, "h", inspect = { s: String -> BlockPatterns.scan(s) }) { "redirected to https://www.google.com/sorry/index?continue=..." }
        assertEquals(BlockSignal.CAPTCHA, g2.status.value.lastSignal)
    }

    @Test fun patternTableCoversTheListedSignals() {
        assertEquals(BlockSignal.HTTP_429, BlockPatterns.scan("ERROR: unable to download: HTTP Error 429: Too Many Requests"))
        assertEquals(BlockSignal.VERIFICATION, BlockPatterns.scan("Please confirm it's you"))
        assertNull(BlockPatterns.scan("Video unavailable"))
    }

    @Test fun callsOfOneAccountNeverOverlap() = runTest {
        val g = gov(); var running = 0; var maxRunning = 0
        List(5) { async { g.call(CallKind.BROWSE, "h") { running++; maxRunning = maxOf(maxRunning, running); yield(); yield(); running-- } } }.awaitAll()
        assertEquals(1, maxRunning)
    }

    @Test fun breakerSurvivesARestartThroughTheStore() = runTest {
        gov().fail(BlockSignal.HTTP_429)
        val again = gov()
        assertEquals(BreakerState.OPEN, again.status.value.state)
        assertFailsWith<GovernorDenied> { again.ok() }
    }

    @Test fun auditEntriesCarryNoCredentialsOrUrls() = runTest {
        val g = gov(); g.ok(); g.fail(BlockSignal.HTTP_429)
        assertTrue(audit.none { Redaction.looksSecret("${it.kind} ${it.host} ${it.result} ${it.decision}") })
        assertTrue(audit.all { !it.host.contains('/') && !it.host.contains('?') })
    }

    // ---- patience: listings wait for the budget window instead of failing halfway ----

    @Test fun aPatientListingWaitsForTheBudgetWindowAndSucceedsWithoutAuditedRefusals() = runTest {
        limits = limits.copy(browsePerMinute = 2)
        val g = gov()
        val results = (1..5).map { g.call(CallKind.BROWSE, "www.youtube.com", patience = 70.seconds) { "page$it" } }
        assertEquals(listOf("page1", "page2", "page3", "page4", "page5"), results)
        assertTrue(sleeps.any { it >= 50_000 }, "it waited for the minute window to reopen: $sleeps")
        assertTrue(audit.none { it.result == "refused" }, "waits are not refusals")
        assertEquals(5, audit.count { it.result == "ok" })
    }

    @Test fun withoutPatienceOrWithTooLittleOrForOtherDenialsTheRefusalIsImmediateAndAudited() = runTest {
        limits = limits.copy(browsePerMinute = 1)
        val g = gov(); g.ok()
        assertFailsWith<GovernorDenied> { g.call(CallKind.BROWSE, "h") { "x" } }
        assertFailsWith<GovernorDenied> { g.call(CallKind.BROWSE, "h", patience = 5.seconds) { "x" } }
        assertEquals(2, audit.count { it.result == "refused" })
        limits = limits.copy(killSwitch = true)
        val before = sleeps.size
        assertFailsWith<GovernorDenied> { g.call(CallKind.BROWSE, "h", patience = 70.seconds) { "x" } }
        assertEquals(before, sleeps.size, "a kill switch is never waited out")
    }
}


class MultiAccountGuardTest {
    private val clock = FakeClock()
    private val registry = GovernorRegistry(clock, { GovernorLimits(minSpacingMs = 0, writesPerHour = 2) })

    @Test fun switchingAccountsNeverRefillsAnAccountsBudgetAndBudgetsAreIndependent() = runTest {
        suspend fun write(id: String) = registry.forAccount(id).call(CallKind.WRITE, "www.youtube.com") { "ok" }
        write("a"); write("a")
        assertFailsWith<GovernorDenied> { write("a") }
        // Switching to B and back, as fast as possible: B has its own budget, A stays exhausted (same governor object per account).
        write("b"); write("b")
        assertFailsWith<GovernorDenied> { write("b") }
        assertFailsWith<GovernorDenied> { write("a") }
        assertSame(registry.forAccount("a"), registry.forAccount("a"))
    }
}
