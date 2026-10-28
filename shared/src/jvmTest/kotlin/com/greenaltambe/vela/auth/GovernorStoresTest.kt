package com.greenaltambe.vela.auth

import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.extractor.FakeClock
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class GovernorStoresTest {
    private val db = createDatabase(JvmDatabaseDriverFactory())

    @Test fun breakerStateSurvivesInTheDatabase() = runTest {
        val clock = FakeClock()
        val first = AccountGovernor("a1", clock, { GovernorLimits(minSpacingMs = 0) }, DbBreakerStore(db))
        runCatching { first.call<String>(CallKind.BROWSE, "h") { error("HTTP Error 429") } }
        val again = AccountGovernor("a1", clock, { GovernorLimits(minSpacingMs = 0) }, DbBreakerStore(db))
        assertEquals(BreakerState.OPEN, again.status.value.state)
        assertEquals(BlockSignal.HTTP_429, again.status.value.lastSignal)
        assertNotNull(again.cooldownUntil())
    }

    @Test fun auditLogIsCappedPerAccountPurgeableAndRedacted() = runTest {
        val log = DbAuditLog(db, cap = 5)
        val clock = FakeClock()
        val g = AccountGovernor("a1", clock, { GovernorLimits(minSpacingMs = 0, browsePerMinute = 1000) }, audit = log)
        repeat(8) { clock.advance(kotlin.time.Duration.parse("1s")); g.call(CallKind.BROWSE, "www.youtube.com") { "x" } }
        assertEquals(5, log.recent("a1").size)
        assertTrue(log.recent("a1").zipWithNext().all { (a, b) -> a.at >= b.at }, "newest first")
        assertTrue(log.recent("a1").none { Redaction.looksSecret("${it.host} ${it.result} ${it.decision}") })
        log.purge("a1"); assertTrue(log.recent("a1").isEmpty())
    }

    @Test fun removingAnAccountDoesNotLeaveItsAuditBehindIfPurged() {
        val log = DbAuditLog(db)
        log.record(AuditEntry("g1", kotlin.time.Instant.fromEpochSeconds(1), "BROWSE", "h", "ok", "allowed"))
        log.record(AuditEntry("g2", kotlin.time.Instant.fromEpochSeconds(1), "BROWSE", "h", "ok", "allowed"))
        log.purge(); assertTrue(log.recent("g1").isEmpty() && log.recent("g2").isEmpty())
    }
}
