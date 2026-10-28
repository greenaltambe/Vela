@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.auth.*
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.ui.nav.Route
import org.junit.Test

class HistoryMergeUiTest {
    private val jar = CookieJar(listOf(Cookie("SID", "s", ".google.com"), Cookie("SAPISID", "p", ".youtube.com")))
    private fun reads(h: List<VideoSummary>?) = object : AccountReads {
        override suspend fun recommended(): List<VideoSummary>? = null
        override suspend fun playlists(): List<AccountPlaylist>? = null
        override suspend fun playlistVideos(playlistId: String): List<VideoSummary>? = null
        override suspend fun history() = h
    }

    @Test fun accountHistoryIsShownBelowTheLocalOneWithoutDuplicatesAndLabelled() = runDesktopComposeUiTest(800, 1000) {
        // The harness has FakeData.videos[0..4] watched on this device (as the local account); the signed-in Google account sees the same local history table? No: history is per account, so give this account the same videos.
        val f = buildFixture(withDownloads = false, reads = reads(listOf(FakeData.videos[8], FakeData.videos[9])))
        val g = f.services.accounts.addGoogle(GoogleIdentity("P", "p@example.com", null, "UC1"), jar)
        val h = com.greenaltambe.vela.data.SqlDelightWatchHistoryRepository(f.services.database)
        val id = h.begin(g.id, FakeData.videos[9].id, "UC", "Seen here too", kotlin.time.Clock.System.now(), 1, 1, 1); h.update(id, kotlin.time.Clock.System.now(), 1, 1, 1)
        f.navigator.selectTab(Route.Library); f.navigator.push(Route.History)
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("history.loadAccount").performClick(); waitForIdle()
        waitUntil(timeoutMillis = 3_000) { runCatching { onNodeWithTag("history.accountHeader").assertIsDisplayed() }.isSuccess }
        onNodeWithText("2 on your account, 1 also watched here.").assertIsDisplayed()
    }

    @Test fun aFailedAccountReadSaysSoAndKeepsTheLocalList() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(withDownloads = false, reads = reads(null))
        f.services.accounts.addGoogle(GoogleIdentity("P", "p@example.com", null, "UC1"), jar)
        f.navigator.selectTab(Route.Library); f.navigator.push(Route.History)
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("history.loadAccount").performClick(); waitForIdle()
        waitUntil(timeoutMillis = 3_000) { runCatching { onNodeWithText("Could not read your YouTube history right now.").assertIsDisplayed() }.isSuccess }
    }
}
