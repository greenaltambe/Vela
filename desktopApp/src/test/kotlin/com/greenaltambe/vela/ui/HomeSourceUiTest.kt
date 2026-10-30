@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.auth.*
import com.greenaltambe.vela.core.settings.HomeSource
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.extractor.VideoSummary
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeSourceUiTest {
    private val jar = CookieJar(listOf(Cookie("SID", "s", ".google.com"), Cookie("SAPISID", "p", ".youtube.com")))
    private class Reads(var answer: List<VideoSummary>?) : AccountReads {
        var calls = 0
        override suspend fun recommended(): List<VideoSummary>? { calls++; return answer }
        override suspend fun playlists(): List<AccountPlaylist>? = null
        override suspend fun playlistVideos(playlistId: String): List<VideoSummary>? = null
    }
    private val rec = listOf(VideoSummary("rec1", "A recommended one", "UCr", "Rec Channel"))
    private fun signIn(f: Fixture) = f.services.accounts.addGoogle(GoogleIdentity("P", "p@example.com", null, "UC1"), jar)

    @Test fun signedInShowsRecommendedFromTheAccount() = runDesktopComposeUiTest(800, 1000) {
        val reads = Reads(rec); val f = buildFixture(withDownloads = false, reads = reads); signIn(f)
        setContent { AppUnderTest(f) }; waitForIdle()
        waitUntil(timeoutMillis = 3_000) { runCatching { onNodeWithText("A recommended one").assertIsDisplayed() }.isSuccess }
        // (Home no longer prints a "Recommended" heading; the account's video being shown is the evidence.)
    }

    @Test fun aFailedOrEmptyAccountFeedFallsBackToTrendingWithANote() = runDesktopComposeUiTest(800, 1000) {
        for ((answer, text) in listOf<Pair<List<VideoSummary>?, String>>(null to "unavailable", emptyList<VideoSummary>() to "no recommendations")) {
            val reads = Reads(answer); val f = buildFixture(withDownloads = false, reads = reads); signIn(f)
            setContent { AppUnderTest(f) }; waitForIdle()
            waitUntil(timeoutMillis = 3_000) { runCatching { onNodeWithTag("home.note", useUnmergedTree = true).assertIsDisplayed() }.isSuccess }
            onNodeWithText(FakeData.videos.first().title, substring = true).assertExists()   // the fallback is Trending, which the fake extractor answers
            assertTrue(reads.calls >= 1)
        }
    }

    @Test fun signedOutOrTheTrendingSettingNeverAsksTheAccount() = runDesktopComposeUiTest(800, 1000) {
        val reads = Reads(rec)
        val signedOut = buildFixture(withDownloads = false, reads = reads)
        setContent { AppUnderTest(signedOut) }; waitForIdle()
        assertEquals(0, reads.calls, "signed out = Trending, no account call")
        val f = buildFixture(withDownloads = false, reads = reads); signIn(f); f.services.settings.set(Settings.homeSource, HomeSource.TRENDING)
        setContent { AppUnderTest(f) }; waitForIdle()
        assertEquals(0, reads.calls, "the Trending setting wins even when signed in")
    }
}
