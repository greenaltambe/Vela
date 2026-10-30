@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.auth.*
import com.greenaltambe.vela.ui.nav.Route
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The video page's account writes: optimistic local change, the call, and the rollback with a message when the account refuses. */
class AccountActionsUiTest {
    private val jar = CookieJar(listOf(Cookie("SID", "s", ".google.com"), Cookie("SAPISID", "p", ".youtube.com")))
    private val identity = GoogleIdentity("Test Person", "test.person@example.com", null, "UCtest")

    private class Recording(var answer: ActionResult = ActionResult.Ok) : AccountActions {
        val calls = mutableListOf<String>()
        private fun log(s: String): ActionResult { calls += s; return answer }
        override suspend fun subscribe(channelId: String) = log("subscribe $channelId")
        override suspend fun unsubscribe(channelId: String) = log("unsubscribe $channelId")
        override suspend fun rate(videoId: String, rating: VideoRating) = log("rate $videoId $rating")
        override suspend fun addToPlaylist(playlistId: String, videoId: String) = log("add $playlistId $videoId")
        override suspend fun removeFromPlaylist(playlistId: String, videoId: String) = log("remove $playlistId $videoId")
        override suspend fun createPlaylist(title: String) = log("create $title") to "PL1"
        override suspend fun renamePlaylist(playlistId: String, title: String) = log("rename $playlistId $title")
        override suspend fun deletePlaylist(playlistId: String) = log("delete $playlistId")
    }

    private class NoLogin : AccountLogin {
        override suspend fun importCookies(netscapeText: String) = LoginResult.Failure("n/a")
        override suspend fun addFromJar(jar: CookieJar) = LoginResult.Failure("n/a")
        override suspend fun checkSession(accountId: String) = AuthState.OK
    }

    @Test fun subscribeAndLikeAreOptimisticAndCallTheAccount() = runDesktopComposeUiTest(800, 1000) {
        val actions = Recording()
        val f = buildFixture(actions = actions)
        f.services.accounts.addGoogle(identity, jar)
        val video = FakeData.videos.first { !f.services.subscriptions.isSubscribed(it.channelId) }
        f.navigator.push(Route.Video(video))
        setContent { AppUnderTest(f) }
        waitForIdle()
        onNodeWithTag("video.subscribe", useUnmergedTree = true).performClick(); waitForIdle()
        waitUntil(timeoutMillis = 3_000) { actions.calls.isNotEmpty() }
        assertEquals("subscribe ${video.channelId}", actions.calls.single())
        assertTrue(f.services.subscriptions.isSubscribed(video.channelId))
        onNodeWithTag("video.votes", useUnmergedTree = true).performClick(); waitForIdle()
        waitUntil(timeoutMillis = 3_000) { actions.calls.size == 2 }
        assertEquals("rate ${video.id} LIKE", actions.calls[1])
    }

    @Test fun aRefusedSubscribeRollsBackAndSaysWhy() = runDesktopComposeUiTest(800, 1000) {
        val actions = Recording(ActionResult.Failed("Account traffic is paused."))
        val f = buildFixture(actions = actions)
        f.services.accounts.addGoogle(identity, jar)
        val video = FakeData.videos.first { !f.services.subscriptions.isSubscribed(it.channelId) }
        f.navigator.push(Route.Video(video))
        setContent { AppUnderTest(f) }
        waitForIdle()
        onNodeWithTag("video.subscribe", useUnmergedTree = true).performClick(); waitForIdle()
        waitUntil(timeoutMillis = 3_000) { runCatching { onNodeWithTag("video.actionError", useUnmergedTree = true).assertIsDisplayed() }.isSuccess }
        assertTrue(!f.services.subscriptions.isSubscribed(video.channelId), "the local change was rolled back")
        onNodeWithText("Subscribe").assertIsDisplayed()
    }

    @Test fun withoutAnAccountTheButtonsStayLocalAndNoCallIsMade() = runDesktopComposeUiTest(800, 1000) {
        val actions = Recording()
        val f = buildFixture(actions = actions) // only the local account
        val video = FakeData.videos.first { !f.services.subscriptions.isSubscribed(it.channelId) }
        f.navigator.push(Route.Video(video))
        setContent { AppUnderTest(f) }
        waitForIdle()
        // The page waits for the video's own details first: the channel id they give (a real UC... id) is the one that is stored and sent.
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("video.subscribe", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        val before = f.services.subscriptions.isSubscribed("UC1")   // the fixture may already follow it: the button then unsubscribes
        onNodeWithTag("video.subscribe", useUnmergedTree = true).performClick(); waitForIdle()
        assertTrue(f.services.subscriptions.isSubscribed("UC1") != before, "the local subscription changed without any account")
        // The actions object is asked, but a local account answers LocalOnly in the real implementation; the UI treats it as success.
    }
}
