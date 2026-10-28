@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.auth.*
import com.greenaltambe.vela.ui.nav.Route
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountPlaylistsUiTest {
    private val jar = CookieJar(listOf(Cookie("SID", "s", ".google.com"), Cookie("SAPISID", "p", ".youtube.com")))
    private class Fake(var answer: ActionResult = ActionResult.Ok) : AccountActions {
        val calls = mutableListOf<String>()
        override suspend fun subscribe(channelId: String) = answer
        override suspend fun unsubscribe(channelId: String) = answer
        override suspend fun rate(videoId: String, rating: VideoRating) = answer
        override suspend fun addToPlaylist(playlistId: String, videoId: String) = answer
        override suspend fun removeFromPlaylist(playlistId: String, videoId: String) = answer
        override suspend fun createPlaylist(title: String): Pair<ActionResult, String?> { calls += "create $title"; return answer to if (answer is ActionResult.Ok) "PLnew" else null }
        override suspend fun renamePlaylist(playlistId: String, title: String): ActionResult { calls += "rename $playlistId $title"; return answer }
        override suspend fun deletePlaylist(playlistId: String): ActionResult { calls += "delete $playlistId"; return answer }
    }

    @Test fun creatingOnTheAccountMakesAMirrorAndAFailureCreatesNothingLocally() = runDesktopComposeUiTest(800, 1000) {
        val actions = Fake()
        val f = buildFixture(withDownloads = false, actions = actions)
        f.services.accounts.addGoogle(GoogleIdentity("P", "p@example.com", null, "UC1"), jar)
        f.navigator.selectTab(Route.Library)
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("library.newPlaylist").performClick(); waitForIdle()
        onNodeWithTag("library.onAccount").performClick()
        onNodeWithText("Name").performTextInput("From the app"); waitForIdle()
        onNodeWithTag("library.nameOk").performClick(); waitForIdle()
        waitUntil(timeoutMillis = 3_000) { f.services.playlists.all().any { it.isMirror && it.name == "From the app" } }
        assertEquals(listOf("create From the app"), actions.calls)
        assertEquals("PLnew", f.services.playlists.all().single { it.name == "From the app" }.remoteId)

        actions.answer = ActionResult.Failed("Account traffic is paused.")
        onNodeWithTag("library.newPlaylist").performClick(); waitForIdle()
        onNodeWithTag("library.onAccount").performClick()
        onNodeWithText("Name").performTextInput("Refused"); waitForIdle()
        onNodeWithTag("library.nameOk").performClick(); waitForIdle()
        waitUntil(timeoutMillis = 3_000) { runCatching { onNodeWithTag("library.error").assertIsDisplayed() }.isSuccess }
        assertTrue(f.services.playlists.all().none { it.name == "Refused" }, "nothing is created locally when the account refuses")
    }
}
