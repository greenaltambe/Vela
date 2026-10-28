package com.greenaltambe.vela.extractor.ytdlp

import com.greenaltambe.vela.auth.CookieJar
import com.greenaltambe.vela.extractor.ExtractorResult
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Tier 2 on desktop against the real service with the throwaway account's cookies; skipped without them or without yt-dlp. */
class LiveAccountTierTest {
    private val ageRestricted = "HtVdAasjOgU"

    @Test fun ageRestrictedVideoPlaysThroughTheAccountTier() {
        val path = System.getenv("VELA_TEST_COOKIES") ?: File(System.getProperty("user.home"), ".vela-test/cookies.txt").path
        assumeTrue("SKIPPED: no cookies at VELA_TEST_COOKIES / ~/.vela-test/cookies.txt", File(path).isFile)
        val jar = CookieJar.parseNetscape(File(path).readText())
        assumeTrue("SKIPPED: cookies file has no Google session", jar.looksSignedIn)
        val runner = SystemProcessRunner()
        val probe = runBlocking { probeYtDlp(runner, "yt-dlp") }
        assumeTrue("SKIPPED: yt-dlp is not installed", probe is YtDlpProbe.Ready)
        val args = (probe as YtDlpProbe.Ready).jsRuntimeArgs
        println("TIER2 js runtime args present=${args.isNotEmpty()}")

        var rotated = 0
        val tier2 = AuthenticatedYtDlpExtractor({ AccountCookies("test", "google:test", jar) }, com.greenaltambe.vela.auth.GovernorRegistry(), { _, _ -> rotated++ }, runner = runner, extraArgs = { args })
        val r = runBlocking { tier2.streams(ageRestricted) }
        println("TIER2 age-restricted with account -> ${r::class.simpleName}; cookie rotation write-backs=$rotated")
        assertIs<ExtractorResult.Success<*>>(r)
        val set = (r as ExtractorResult.Success).value
        println("TIER2 videoStreams=${set.videoStreams.size} audioStreams=${set.audioStreams.size}")
        assertTrue(set.videoStreams.isNotEmpty() || set.audioStreams.isNotEmpty())

        val anon = runBlocking { YtDlpExtractor(runner, { "yt-dlp" }, { args }).streams(ageRestricted) }
        println("TIER2 same video anonymously -> ${anon::class.simpleName}${(anon as? ExtractorResult.Failure)?.let { " (${it.error.reason})" } ?: ""}")
    }
}
