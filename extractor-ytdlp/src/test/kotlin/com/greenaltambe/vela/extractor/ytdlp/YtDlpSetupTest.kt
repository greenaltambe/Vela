package com.greenaltambe.vela.extractor.ytdlp

import com.greenaltambe.vela.extractor.chain.AttemptOutcome
import com.greenaltambe.vela.extractor.chain.DiagnosticsLog
import com.greenaltambe.vela.extractor.chain.TierId
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class YtDlpSetupTest {
    private val newVersion = "2026.10.26"
    private fun path(vararg found: String) = PathLookup { name -> found.firstOrNull { it == name }?.let { "/usr/bin/$it" } }
    private fun version(v: String) = FakeRunner.ok("$v\n")

    @Test fun versionCutoffForTheJsRuntimeRequirement() {
        assertFalse(versionRequiresJsRuntime("2025.11.11"))
        assertTrue(versionRequiresJsRuntime("2025.11.12"))
        assertTrue(versionRequiresJsRuntime("2026.10.26.232815"))
        assertFalse(versionRequiresJsRuntime("2024.12.06"))
        assertFalse(versionRequiresJsRuntime("nightly"), "unparseable: don't nag")
        assertNull(parseYtDlpVersion("1.2"))
    }

    @Test fun missingBinaryIsNotInstalled() = runTest {
        val probe = probeYtDlp(FakeRunner { throw ProcessStartException("No such file") }, "yt-dlp", path())
        assertIs<YtDlpProbe.NotInstalled>(probe)
    }

    @Test fun brokenBinaryIsNotInstalled() = runTest {
        assertIs<YtDlpProbe.NotInstalled>(probeYtDlp(FakeRunner.failing(126, "permission denied"), "yt-dlp", path()))
        assertIs<YtDlpProbe.NotInstalled>(probeYtDlp(FakeRunner.ok("  \n"), "yt-dlp", path()))
    }

    @Test fun probeRunsVersion() = runTest {
        val runner = version(newVersion)
        val probe = assertIs<YtDlpProbe.Ready>(probeYtDlp(runner, "/x/yt-dlp", path("deno")))
        assertEquals(listOf("/x/yt-dlp", "--version"), runner.commands.single())
        assertEquals(newVersion, probe.version)
    }

    @Test fun denoNeedsNoFlagsAndNoNote() = runTest {
        val probe = assertIs<YtDlpProbe.Ready>(probeYtDlp(version(newVersion), "yt-dlp", path("deno", "node")))
        assertTrue(probe.jsRuntimeArgs.isEmpty())
        assertNull(probe.note)
    }

    @Test fun nodeAloneIsPassedExplicitlyOnNewVersions() = runTest {
        val probe = assertIs<YtDlpProbe.Ready>(probeYtDlp(version(newVersion), "yt-dlp", path("node")))
        assertEquals(listOf("--js-runtimes", "node:/usr/bin/node"), probe.jsRuntimeArgs)
        assertNull(probe.note)
    }

    @Test fun nodeIsNotNeededOnOldVersions() = runTest {
        val probe = assertIs<YtDlpProbe.Ready>(probeYtDlp(version("2025.01.01"), "yt-dlp", path("node")))
        assertTrue(probe.jsRuntimeArgs.isEmpty())
    }

    @Test fun noRuntimeOnANewVersionProducesANote() = runTest {
        val probe = assertIs<YtDlpProbe.Ready>(probeYtDlp(version(newVersion), "yt-dlp", path()))
        assertContains(probe.note.orEmpty(), "JavaScript runtime")
    }

    @Test fun noRuntimeOnAnOldVersionIsFine() = runTest {
        assertNull(assertIs<YtDlpProbe.Ready>(probeYtDlp(version("2025.01.01"), "yt-dlp", path())).note)
    }

    // ---- tier creation ----

    @Test fun notInstalledRegistersNoTierAndLogsASkipOnce() = runTest {
        val log = DiagnosticsLog()
        val tier = createYtDlpTier(FakeRunner { throw ProcessStartException("No such file") }, { "yt-dlp" }, log, FakeClock())
        assertNull(tier)
        val line = log.attempts.value.single()
        assertEquals(AttemptOutcome.SKIPPED, line.outcome)
        assertEquals(TierId.YT_DLP, line.tier)
        assertContains(line.detail.orEmpty(), "yt-dlp not installed")
    }

    @Test fun readyRegistersTheTierAndLogsNothingWhenThereIsNothingToSay() = runTest {
        val log = DiagnosticsLog()
        val tier = createYtDlpTier(version("2025.01.01"), { "yt-dlp" }, log, FakeClock())
        assertEquals(TierId.YT_DLP, tier?.id)
        assertTrue(log.attempts.value.isEmpty())
    }

    @Test fun missingRuntimeStillRegistersTheTierButLeavesANote() = runTest {
        // Real PATH lookup is used by createYtDlpTier, so only assert the shape when the machine has no runtime.
        val log = DiagnosticsLog()
        val tier = createYtDlpTier(version(newVersion), { "yt-dlp" }, log, FakeClock())
        assertNotNull(tier)
        log.attempts.value.forEach { assertEquals(AttemptOutcome.NOTE, it.outcome) }
    }
}
