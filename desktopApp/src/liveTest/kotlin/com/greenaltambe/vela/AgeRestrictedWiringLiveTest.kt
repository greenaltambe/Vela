package com.greenaltambe.vela

import com.greenaltambe.vela.auth.BlockSignal
import com.greenaltambe.vela.auth.CookieJar
import com.greenaltambe.vela.auth.GoogleIdentity
import com.greenaltambe.vela.auth.InMemorySecretStore
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.SqlDelightSettingsStore
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.chain.DiagnosticsLog
import com.greenaltambe.vela.extractor.ytdlp.BinaryLocator
import com.greenaltambe.vela.extractor.ytdlp.SystemProcessRunner
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Task 15 addendum: the age-restricted video through the REAL desktop wiring (`buildDesktopWiring`, the function `main`
 * calls) in three states: signed out, signed in with the breaker closed, signed in with the breaker open. Also with a
 * launcher-like PATH (`/usr/bin:/bin`). Prints each state's Diagnostics lines. Uses the throwaway account once (state 2).
 */
class AgeRestrictedWiringLiveTest {
    private val video = "HtVdAasjOgU"

    private fun cookies(): CookieJar? {
        val path = System.getenv("VELA_TEST_COOKIES") ?: File(System.getProperty("user.home"), ".vela-test/cookies.txt").path
        return File(path).takeIf { it.isFile }?.let { CookieJar.parseNetscape(it.readText()) }?.takeIf { it.looksSignedIn }
    }

    private fun wiring(locator: BinaryLocator, signedIn: Boolean, jar: CookieJar?): DesktopWiring {
        val db = createDatabase(JvmDatabaseDriverFactory(File.createTempFile("vela-live", ".db").also { it.deleteOnExit() }.path))
        val w = buildDesktopWiring(db, SettingsResolver(SqlDelightSettingsStore(db)), InMemorySecretStore(), SystemProcessRunner(), DiagnosticsLog(), locator)
        if (signedIn) w.accounts.addGoogle(GoogleIdentity("Test", "t@example.invalid", null, "UCtest"), jar!!)
        return w
    }

    private fun play(label: String, w: DesktopWiring): Boolean {
        val r = runBlocking { w.chain.streams(video) }
        println("STATE $label -> ${if (r is ExtractorResult.Success<*>) "PLAYS" else "FAILS: " + (r as ExtractorResult.Failure).error.let { "${it.reason}: ${it.message}" }}")
        w.log.attempts.value.forEach { println("   [$label] ${it.tier} ${it.outcome} ${it.subject} reason=${it.reason} ${it.detail.orEmpty().take(160)} (${it.duration})") }
        return r is ExtractorResult.Success<*>
    }

    @Test fun threeStatesWithProcessPath() {
        val jar = cookies(); assumeTrue("SKIPPED: no test cookies", jar != null)
        val ok1 = play("1 signed-out", wiring(BinaryLocator(), false, null))
        val w2 = wiring(BinaryLocator(), true, jar)
        val ok2 = play("2 signed-in, breaker closed", w2)
        val w3 = wiring(BinaryLocator(), true, jar)
        w3.governors.forAccount(w3.accounts.active.value.id).trip(BlockSignal.HTTP_429)
        val ok3 = play("3 signed-in, breaker open", w3)
        assertTrue(ok1 && ok2 && ok3, "age-restricted must play in all three states: $ok1 $ok2 $ok3")
    }

    @Test fun launcherLikePath() {
        val jar = cookies(); assumeTrue("SKIPPED: no test cookies", jar != null)
        val w = wiring(BinaryLocator(pathEnv = { "/usr/bin:/bin" }), false, null)
        println("LAUNCHER yt-dlp=${w.ytDlp.description} | js=${w.jsRuntime.description} | ffmpeg=${w.ffmpeg.description}")
        assertTrue(play("L signed-out, PATH=/usr/bin:/bin", w))
    }
}
