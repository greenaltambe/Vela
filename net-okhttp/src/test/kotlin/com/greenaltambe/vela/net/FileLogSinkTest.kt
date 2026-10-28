package com.greenaltambe.vela.net

import com.greenaltambe.vela.auth.AuditEntry
import com.greenaltambe.vela.auth.DbAuditLog
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.extractor.FailureReason
import com.greenaltambe.vela.extractor.chain.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class FileLogSinkTest {
    private val dir = Files.createTempDirectory("vela-log").toFile()
    @AfterTest fun clean() { dir.deleteRecursively() }
    private val at = Instant.fromEpochSeconds(1_800_000_000)

    private val markers = listOf("abc123def456", "deadbeef", "VISMARKER", "tok999tok999tok999", "POTMARKER", "SIGMARKER", "zzz777")
    private val leaky = "failed: Cookie: SAPISID=abc123def456; SID=zzz777 Authorization: SAPISIDHASH 1_deadbeef " +
        """{"visitorData":"VISMARKER"} Bearer tok999tok999tok999 https://rr.example/v?pot=POTMARKER&sig=SIGMARKER"""

    @Test fun aSimulatedSessionNeverWritesACredentialAndKeepsTheUsefulLines() {
        val sink = FileLogSink(File(dir, "vela.log"))
        val log = DiagnosticsLog(sink = sink)
        val audit = DbAuditLog(createDatabase(JvmDatabaseDriverFactory()), sink = sink)
        log.record(TierAttempt("streams(v1)", TierId.ANONYMOUS, AttemptOutcome.FAILED, FailureReason.BLOCKED, leaky, 120.milliseconds, at))
        log.record(TierAttempt("streams(v1)", TierId.AUTHENTICATED, AttemptOutcome.FAILED, FailureReason.NETWORK, "account google:ab12cd: $leaky", 80.milliseconds, at))
        log.record(TierAttempt("streams(v1)", TierId.YT_DLP, AttemptOutcome.SUCCESS, null, "yt-dlp 2026.10.26", 4000.milliseconds, at))
        audit.record(AuditEntry("acc1", at, "PLAYBACK", "www.youtube.com", "HTTP 429 $leaky", "breaker opened"))
        audit.record(AuditEntry("acc1", at, "BROWSE", "www.youtube.com", "ok", "allowed"))

        val text = sink.readAll()
        markers.forEach { assertFalse(it in text, "credential marker $it reached the log:\n$text") }
        assertTrue("tier=ANONYMOUS FAILED streams(v1) reason=BLOCKED" in text)
        assertTrue("tier=YT_DLP SUCCESS" in text && "yt-dlp 2026.10.26" in text)
        assertTrue("governor account=acc1 PLAYBACK host=www.youtube.com" in text && "decision=breaker opened" in text)
        assertTrue("decision=allowed" in text)
    }

    @Test fun theFileIsBoundedAndRotates() {
        val file = File(dir, "vela.log"); val sink = FileLogSink(file, maxBytes = 20_000)
        repeat(2_000) { sink.write("line $it " + "x".repeat(100)) }
        assertTrue(file.length() <= 20_000 + 200); assertTrue(File(dir, "vela.log.1").length() <= 20_000 + 200)
        assertTrue("line 1999 " in sink.readAll(), "the newest lines survive")
        assertFalse("line 0 " in sink.readAll(), "the oldest are gone")
        assertEquals(listOf("vela.log", "vela.log.1"), dir.list()!!.sorted(), "only two files ever exist")
    }
}
