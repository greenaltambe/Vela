package com.greenaltambe.vela.extractor.ytdlp

import com.greenaltambe.vela.auth.Cookie
import com.greenaltambe.vela.auth.CookieJar
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

    private val stdout = Fixtures.text("audio_drc.json")

    @Test fun noAccountMeansTheTierIsUnavailableAndNothingRuns() = runTest {
        val runner = FakeRunner.ok("{}")
        val r = AuthenticatedYtDlpExtractor({ null }, { _, _ -> }, dir, runner).streams("v")
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
        AuthenticatedYtDlpExtractor({ AccountCookies("a1", "google:a1", jar()) }, { _, _ -> }, dir, runner, extraArgs = { listOf("--js-runtimes", "node") }).videoInfo("v")
        assertEquals("rw-------", seenPerms)
        assertTrue(seenText!!.contains("SAPISID"))
        assertTrue("--js-runtimes" in runner.commands.single(), "the JS runtime args still apply")
        assertEquals(0, dir.listFiles()!!.size, "temp cookie file deleted")
    }

    @Test fun fileIsDeletedEvenWhenYtDlpFails() = runTest {
        val r = AuthenticatedYtDlpExtractor({ AccountCookies("a1", "google:a1", jar()) }, { _, _ -> }, dir, FakeRunner.failing(1, "ERROR: boom")).streams("v")
        assertIs<ExtractorResult.Failure>(r)
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun rotatedCookiesWrittenBackByYtDlpAreHandedToTheStore() = runTest {
        var updated: CookieJar? = null
        val runner = FakeRunner { cmd ->
            File(cmd[cmd.indexOf("--cookies") + 1]).writeText(jar(psidts = "ts2").toNetscape())
            ProcessOutput(0, stdout, "")
        }
        AuthenticatedYtDlpExtractor({ AccountCookies("a1", "google:a1", jar()) }, { id, j -> assertEquals("a1", id); updated = j }, dir, runner).videoInfo("v")
        assertEquals("ts2", updated!!.get("__Secure-1PSIDTS"))
    }

    @Test fun unchangedCookiesAreNotRewritten() = runTest {
        var calls = 0
        AuthenticatedYtDlpExtractor({ AccountCookies("a1", "google:a1", jar()) }, { _, _ -> calls++ }, dir, FakeRunner.ok(stdout)).videoInfo("v")
        assertEquals(0, calls)
    }
}
