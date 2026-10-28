package com.greenaltambe.vela

import com.greenaltambe.vela.auth.*
import com.greenaltambe.vela.core.settings.AccountFallbackMode
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.chain.*
import com.greenaltambe.vela.extractor.newpipe.NewPipeExtractor
import com.greenaltambe.vela.extractor.ytdlp.*
import com.greenaltambe.vela.innertube.AltClientExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.system.measureTimeMillis
import kotlin.test.Test

/**
 * Task 14 H: the cost of the failure path before (account second) and after (account last), and which tier serves
 * what. Real network, real yt-dlp, the throwaway account. Prints numbers; asserts nothing about speed.
 */
class TierOrderLatencyLiveTest {
    private val ageRestricted = "HtVdAasjOgU"
    private val mix = listOf("dQw4w9WgXcQ" to "normal", "kJQP7kiw5Fk" to "music", "jNQXAC9IVRw" to "short", "9bZkp7q19f0" to "normal-2", ageRestricted to "age-restricted")

    @Test fun measure() {
        val path = System.getenv("VELA_TEST_COOKIES") ?: File(System.getProperty("user.home"), ".vela-test/cookies.txt").path
        assumeTrue("SKIPPED: no cookies", File(path).isFile)
        val jar = CookieJar.parseNetscape(File(path).readText())
        assumeTrue("SKIPPED: cookies have no Google session", jar.looksSignedIn)
        val runner = SystemProcessRunner()
        var args = emptyList<String>()
        val bootLog = DiagnosticsLog()
        val ytDlp = runBlocking { createYtDlpTier(runner, { "yt-dlp" }, bootLog, onReady = { args = it }) }
        assumeTrue("SKIPPED: yt-dlp not installed", ytDlp != null)

        fun chain(order: Boolean, log: DiagnosticsLog): FallbackChain {
            val gov = GovernorRegistry(limits = { GovernorLimits(minSpacingMs = 0, playbackPerHour = 1000) })
            val auth = ExtractorTier(TierId.AUTHENTICATED, AuthenticatedYtDlpExtractor({ AccountCookies("t", "google:test", jar) }, gov, { _, _ -> }, runner = runner, extraArgs = { args }))
            val anon = ExtractorTier(TierId.ANONYMOUS, NewPipeExtractor())
            val alt = ExtractorTier(TierId.ALT_CLIENT, AltClientExtractor())
            val yt = ExtractorTier(TierId.YT_DLP, YtDlpExtractor(runner, { "yt-dlp" }, { args }))
            return if (order) FallbackChain(listOf(anon, alt, yt, auth), { AccountFallbackMode.ONLY_ON_FAILURE }, log, signedInAccount = { "google:test" }, tierOrder = { TierOrder.Default })
            else FallbackChain(listOf(anon, auth, alt, yt), { AccountFallbackMode.ONLY_ON_FAILURE }, log, signedInAccount = { "google:test" })
        }
        fun served(log: DiagnosticsLog) = log.attempts.value.lastOrNull { it.outcome == AttemptOutcome.SUCCESS }?.tier

        for ((label, newOrder) in listOf("OLD order (anon, ACCOUNT, alt, yt-dlp)" to false, "NEW order (anon, alt, yt-dlp, ACCOUNT)" to true)) {
            val times = (1..5).map {
                val log = DiagnosticsLog(); val c = chain(newOrder, log)
                var r: ExtractorResult<*>? = null
                val ms = measureTimeMillis { r = runBlocking { c.streams(ageRestricted) } }
                println("LATENCY $label trial $it: ${ms}ms -> ${if (r is ExtractorResult.Success<*>) "served by ${served(log)}" else "FAILED"}")
                ms
            }
            println("LATENCY $label age-restricted: median=${times.sorted()[2]}ms min=${times.min()}ms max=${times.max()}ms")
        }
        val tally = HashMap<TierId?, Int>()
        for ((id, label) in mix) {
            val log = DiagnosticsLog(); val c = chain(true, log)
            var r: ExtractorResult<*>? = null
            val ms = measureTimeMillis { r = runBlocking { c.streams(id) } }
            val tier = served(log); tally[tier] = (tally[tier] ?: 0) + 1
            println("MIX $label: ${ms}ms -> ${if (r is ExtractorResult.Success<*>) "served by $tier" else "FAILED"}")
        }
        println("MIX tally (new order): $tally")
    }
}
