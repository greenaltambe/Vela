@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.auth.*
import com.greenaltambe.vela.data.RowState
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.ui.nav.Route
import org.junit.Test
import kotlin.test.assertTrue
import kotlin.time.Clock

/** Task 84: the sync box, the badge and the wording of the destructive actions are the same everywhere a playlist is touched. */
class PlaylistSyncUiTest {
    private val jar = CookieJar(listOf(Cookie("SID", "s", ".google.com"), Cookie("SAPISID", "p", ".youtube.com")))
    private val reads = object : AccountReads {
        override suspend fun recommended(): List<VideoSummary>? = null
        override suspend fun playlists() = emptyList<AccountPlaylist>()
        override suspend fun playlistVideos(playlistId: String) = emptyList<VideoSummary>()
    }
    private val actions = object : AccountActions {
        override suspend fun subscribe(channelId: String) = ActionResult.Ok
        override suspend fun unsubscribe(channelId: String) = ActionResult.Ok
        override suspend fun rate(videoId: String, rating: VideoRating) = ActionResult.Ok
        override suspend fun addToPlaylist(playlistId: String, videoId: String) = ActionResult.Ok
        override suspend fun removeFromPlaylist(playlistId: String, videoId: String) = ActionResult.Ok
        override suspend fun createPlaylist(title: String): Pair<ActionResult, String?> = ActionResult.Ok to "PLx"
        override suspend fun renamePlaylist(playlistId: String, title: String) = ActionResult.Ok
        override suspend fun deletePlaylist(playlistId: String) = ActionResult.Ok
    }

    @Test fun theNewPlaylistDialogOffersTheSyncBoxOnlyToASignedInAccount() = runDesktopComposeUiTest(800, 1000) {
        val local = buildFixture(withDownloads = false, actions = actions, reads = reads)
        local.navigator.selectTab(Route.Library)
        setContent { AppUnderTest(local) }; waitForIdle()
        onNodeWithTag("library.newPlaylist").performClick(); waitForIdle()
        assertTrue(runCatching { onNodeWithTag("playlist.syncCheckbox").assertIsDisplayed() }.isFailure, "no box for the local profile")
    }

    @Test fun aSyncedPlaylistShowsTheBadgeAndItsDeleteDialogSaysItDeletesOnYouTubeToo() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(withDownloads = false, actions = actions, reads = reads)
        f.services.accounts.addGoogle(GoogleIdentity("P", "p@example.com", null, "UC1"), jar)
        f.services.playlists.linked("PLx", "Synced list", Clock.System.now())
        f.navigator.selectTab(Route.Library)
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithContentDescription("Synced with YouTube").assertIsDisplayed()
        onNodeWithText("Synced list").assertIsDisplayed()
        onAllNodesWithContentDescription("Playlist options").onLast().performClick(); waitForIdle()
        onNodeWithText("Delete").performClick(); waitForIdle()
        onNodeWithTag("library.deleteText").assertIsDisplayed()
        onNodeWithText("Deletes it here and on your YouTube account", substring = true).assertIsDisplayed()
    }

    @Test fun aPlaylistThatDidNotFitOnYouTubeSaysSoAndOffersTheRest() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(withDownloads = false, actions = actions, reads = reads)
        f.services.accounts.addGoogle(GoogleIdentity("P", "p@example.com", null, "UC1"), jar)
        val id = f.services.playlists.linked("PLfull", "Full list", Clock.System.now())
        f.services.playlists.add(id, listOf(VideoSummary("o1", "Left out", "UC", "C")), Clock.System.now(), RowState.OVERFLOW)
        f.navigator.push(Route.Playlist(id))
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("playlist.overflowNote").assertIsDisplayed()
        onNodeWithText("Not on YouTube", substring = true).assertIsDisplayed()
        onNodeWithTag("playlist.overflowAction").performClick(); waitForIdle()
        onNodeWithTag("overflow.new").assertIsDisplayed()
        onNodeWithTag("overflow.confirm").assertIsDisplayed()
    }
}
