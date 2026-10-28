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
import kotlin.test.assertTrue

class LibrarySyncUiTest {
    private val jar = CookieJar(listOf(Cookie("SID", "s", ".google.com"), Cookie("SAPISID", "p", ".youtube.com")))
    private val reads = object : AccountReads {
        override suspend fun recommended(): List<VideoSummary>? = null
        override suspend fun playlists() = listOf(AccountPlaylist("WL", "Watch later", 1))
        override suspend fun playlistVideos(playlistId: String) = listOf(VideoSummary("w1", "Saved on the account", "UC", "C"))
    }

    @Test fun theSyncRowCopiesAccountPlaylistsNextToTheLocalOnesAndLabelsThem() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(withDownloads = false, reads = reads)
        f.services.accounts.addGoogle(GoogleIdentity("P", "p@example.com", null, "UC1"), jar)
        f.navigator.selectTab(Route.Library)
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("library.syncAccount").performClick(); waitForIdle()
        waitUntil(timeoutMillis = 3_000) { runCatching { onNodeWithText("Watch later (YouTube)").assertIsDisplayed() }.isSuccess }
        onNodeWithText("YouTube account · 1 video").assertIsDisplayed()
        assertTrue(f.services.playlists.all().any { it.isMirror })
    }

    @Test fun signedOutThereIsNoSyncRow() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(withDownloads = false, reads = reads)
        f.navigator.selectTab(Route.Library)
        setContent { AppUnderTest(f) }; waitForIdle()
        assertTrue(runCatching { onNodeWithTag("library.syncAccount").assertIsDisplayed() }.isFailure)
    }
}
