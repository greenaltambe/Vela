package com.greenaltambe.vela.extractor.ytdlp

import com.greenaltambe.vela.auth.Cookie
import com.greenaltambe.vela.auth.CookieJar
import com.greenaltambe.vela.auth.GovernorLimits
import com.greenaltambe.vela.auth.GovernorRegistry
import com.greenaltambe.vela.extractor.*
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.*

class AuthenticatedYtDlpExtractorTest {
    private val dir = Files.createTempDirectory("vela-auth-test").toFile()
    @AfterTest fun clean() { dir.deleteRecursively() }

    private fun jar(psidts: String = "ts1") = CookieJar(listOf(
        Cookie("SID", "sid-secret", ".google.com"), Cookie("SAPISID", "sapisid-secret", ".youtube.com"), Cookie("__Secure-1PSIDTS", psidts, ".youtube.com")))

    private fun tier(account: () -> AccountCookies?, onUpdated: (String, CookieJar) -> Unit, d: File, runner: ProcessRunner, extraArgs: () -> List<String> = { emptyList() }) =
        AuthenticatedYtDlpExtractor(account, GovernorRegistry(limits = { GovernorLimits(minSpacingMs = 0) }), onUpdated, d, runner, extraArgs = extraArgs)

    private val stdout = Fixtures.text("audio_drc.json")

    @Test fun noAccountMeansTheTierIsUnavailableAndNothingRuns() = runTest {
        val runner = FakeRunner.ok("{}")
        val r = tier({ null }, { _, _ -> }, dir, runner).streams("v")
        assertIs<ExtractorError.TierUnavailable>(assertIs<ExtractorResult.Failure>(r).error)
        assertTrue(runner.commands.isEmpty())
    }

    @Test fun cookiesGoToAPrivateTempFileThatIsGoneAfterwards() = runTest {
        var seenPerms: String? = null; var seenText: String? = null
        val runner = FakeRunner { cmd ->
            val f = File(cmd[cmd.indexOf("--cookies") + 1])
            seenPerms = PosixFilePermissions.toString(Files.getPosixFilePermissions(f.toPath())); seenText = f.readText()
            ProcessOutput(0, stdout, "")
        }
        tier({ AccountCookies("a1", "google:a1", jar()) }, { _, _ -> }, dir, runner, extraArgs = { listOf("--js-runtimes", "node") }).videoInfo("v")
        assertEquals("rw-------", seenPerms)
        assertTrue(seenText!!.contains("SAPISID"))
        assertTrue("--js-runtimes" in runner.commands.single(), "the JS runtime args still apply")
        assertEquals(0, dir.listFiles()!!.size, "temp cookie file deleted")
    }

    @Test fun fileIsDeletedEvenWhenYtDlpFails() = runTest {
        val r = tier({ AccountCookies("a1", "google:a1", jar()) }, { _, _ -> }, dir, FakeRunner.failing(1, "ERROR: boom")).streams("v")
        assertIs<ExtractorResult.Failure>(r)
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun fileIsDeletedWhenTheRunnerThrowsOrTimesOutOrIsCancelled() = runTest {
        val acct = { AccountCookies("a1", "google:a1", jar()) }
        for (boom in listOf<Throwable>(IllegalStateException("python crashed"), ProcessTimeoutException(kotlin.time.Duration.parse("60s")), ProcessStartException("cannot start"))) {
            val r = runCatching { tier(acct, { _, _ -> }, dir, FakeRunner { throw boom }).streams("v") }
            // A crash is either reported as a failure or rethrown; either way the cookie file must be gone.
            assertEquals(0, dir.listFiles()!!.size, "cookie file left behind after ${boom::class.simpleName} ($r)")
        }
        val cancelled = runCatching { tier(acct, { _, _ -> }, dir, FakeRunner { throw kotlinx.coroutines.CancellationException("cancelled") }).streams("v") }
        assertTrue(cancelled.exceptionOrNull() is kotlinx.coroutines.CancellationException); assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun rotatedCookiesWrittenBackByYtDlpAreHandedToTheStore() = runTest {
        var updated: CookieJar? = null
        val runner = FakeRunner { cmd ->
            File(cmd[cmd.indexOf("--cookies") + 1]).writeText(jar(psidts = "ts2").toNetscape())
            ProcessOutput(0, stdout, "")
        }
        tier({ AccountCookies("a1", "google:a1", jar()) }, { id, j -> assertEquals("a1", id); updated = j }, dir, runner).videoInfo("v")
        assertEquals("ts2", updated!!.get("__Secure-1PSIDTS"))
    }

    @Test fun unchangedCookiesAreNotRewritten() = runTest {
        var calls = 0
        tier({ AccountCookies("a1", "google:a1", jar()) }, { _, _ -> calls++ }, dir, FakeRunner.ok(stdout)).videoInfo("v")
        assertEquals(0, calls)
    }

    @Test fun stderrThatLooksLikeABlockOpensTheBreakerAndNoFurtherProcessRuns() = runTest {
        val governors = GovernorRegistry(limits = { GovernorLimits(minSpacingMs = 0) })
        val runner = FakeRunner.failing(1, "ERROR: [youtube] v: HTTP Error 429: Too Many Requests")
        val t = AuthenticatedYtDlpExtractor({ AccountCookies("a1", "google:a1", jar()) }, governors, { _, _ -> }, dir, runner)
        assertIs<ExtractorResult.Failure>(t.streams("v"))
        val again = assertIs<ExtractorResult.Failure>(t.streams("v")).error
        assertEquals(1, runner.commands.size, "the second call never reached yt-dlp")
        assertIs<ExtractorError.TierUnavailable>(again)
        assertTrue(again.message.startsWith("Account cooling down until"), again.message)
    }

    @Test fun playbackIsLimitedByTheHourlyBudget() = runTest {
        val governors = GovernorRegistry(limits = { GovernorLimits(playbackPerHour = 2, minSpacingMs = 0) })
        val runner = FakeRunner.ok(stdout)
        val t = AuthenticatedYtDlpExtractor({ AccountCookies("a1", "google:a1", jar()) }, governors, { _, _ -> }, dir, runner)
        t.videoInfo("v"); t.streams("v")
        val third = assertIs<ExtractorResult.Failure>(t.streams("v")).error
        assertIs<ExtractorError.TierUnavailable>(third); assertEquals(2, runner.commands.size)
    }

    @Test fun cookieValuesNeverAppearOnTheCommandLineAndAnonymousCallsHaveNoCookiesFlag() = runTest {
        val authRunner = FakeRunner.ok(stdout)
        AuthenticatedYtDlpExtractor({ AccountCookies("a1", "google:a1", jar()) }, GovernorRegistry(limits = { GovernorLimits(minSpacingMs = 0) }), { _, _ -> }, dir, authRunner).videoInfo("v")
        val argv = authRunner.commands.single().joinToString(" ")
        assertTrue(listOf("sid-secret", "sapisid-secret").none { argv.contains(it) }, "only a file path is passed")
        val anonRunner = FakeRunner.ok(stdout)
        YtDlpExtractor(anonRunner).videoInfo("v"); YtDlpExtractor(anonRunner).search("q")
        assertTrue(anonRunner.commands.none { "--cookies" in it || "--cookies-from-browser" in it }, "the anonymous yt-dlp tier never gets cookies")
    }
}
