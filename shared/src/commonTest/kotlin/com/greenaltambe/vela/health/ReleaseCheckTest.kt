package com.greenaltambe.vela.health

import com.greenaltambe.vela.core.http.*
import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.extractor.FakeClock
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.hours

class ReleaseCheckTest {
    private val clock = FakeClock()
    private var calls = 0
    private var answer: () -> HttpResponse = { HttpResponse(200, """{"tag_name":"v0.26.5","published_at":"2026-08-15T19:20:48Z"}""") }
    private val transport = object : HttpTransport {
        override suspend fun execute(request: HttpRequest): HttpResponse { calls++; return answer() }
    }
    private val store = InMemorySettingsStore()
    private fun check(bundled: String = "v0.26.5") = ReleaseCheck("NewPipeExtractor", bundled, "TeamNewPipe/NewPipeExtractor",
        ApiClient(transport, InMemoryApiCache(), clock, retries = 0), store, clock)

    @Test fun newestBundledIsUpToDateNotAWarning() = runTest {
        val s = check().check()
        assertTrue(s.upToDate); assertFalse(s.newerAvailable)
        assertTrue("up to date (checked" in s.describe())
    }

    @Test fun warnsOnlyWhenANewerReleaseExists() = runTest {
        answer = { HttpResponse(200, """{"tag_name":"v0.26.6","published_at":"2026-10-20T00:00:00Z"}""") }
        val s = check().check()
        assertTrue(s.newerAvailable)
        assertTrue("out of date" in s.describe() && "v0.26.6" in s.describe())
    }

    @Test fun atMostOneRequestPerDayAndSurvivesRestart() = runTest {
        check().check(); check().check()      // second instance reads the stored answer
        assertEquals(1, calls)
        clock.advance(25.hours)
        check().check()
        assertEquals(2, calls)
        check().check(force = true)
        assertEquals(3, calls)
    }

    @Test fun offlineKeepsTheLastAnswerAndSaysWhy() = runTest {
        val c = check(); c.check()
        clock.advance(25.hours)
        answer = { throw TransportException("no network") }
        val s = c.check()
        assertEquals("v0.26.5", s.latest); assertNotNull(s.failure)
        assertTrue(s.upToDate)
    }

    @Test fun neverCheckedAndOfflineIsNotAWarning() = runTest {
        answer = { throw TransportException("no network") }
        val s = check().check()
        assertFalse(s.newerAvailable); assertFalse(s.upToDate)
        assertTrue("could not check" in s.describe())
    }

    @Test fun versionsCompareNumerically() {
        assertTrue(compareVersions("v0.26.10", "v0.26.9") > 0)
        assertTrue(compareVersions("2026.10.26.1", "2026.10.26") > 0)
        assertEquals(0, compareVersions("v1.2", "1.2.0"))
    }

    @Test fun staleYtDlpCopyIsNamedWithTheCommand() = runTest {
        answer = { HttpResponse(200, """{"tag_name":"2026.10.26","published_at":"2026-10-26T22:25:26Z"}""") }
        val c = ReleaseCheck("yt-dlp", "2026.10.26", "yt-dlp/yt-dlp", ApiClient(transport, InMemoryApiCache(), clock, retries = 0), store, clock); c.check()
        val lines = ReleaseWatch(c, listOf(ToolCopy("/usr/bin/yt-dlp", "2026.08.19", false, "sudo dnf upgrade yt-dlp"), ToolCopy("/h/.local/bin/yt-dlp", "2026.10.26", true, "pipx upgrade yt-dlp"))).copyLines()
        assertTrue("STALE" in lines[0] && "sudo dnf upgrade yt-dlp" in lines[0])
        assertTrue("[in use]" in lines[1] && "up to date" in lines[1])
    }
}
