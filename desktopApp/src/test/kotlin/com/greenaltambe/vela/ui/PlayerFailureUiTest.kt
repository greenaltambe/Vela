@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.SqlDelightWatchHistoryRepository
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.extractor.*
import com.greenaltambe.vela.player.PlaybackSession
import com.greenaltambe.vela.ui.player.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The player's failure UI: the sign-in-required explanation with "Open in browser" (task 14 left it untested) and the
 * task 15 message that names each tier and the account's state, with "Try account now" only while the account is paused.
 */
class PlayerFailureUiTest {
    private class FailingExtractor(val error: ExtractorError) : Extractor by StubExtractor(error)

    private val tierMessage = "Age-restricted: anonymous tiers failed — NewPipe (age-restricted), alt client (sign-in required), yt-dlp (age-restricted). The account is paused: account cooling down until 09:25 UTC."

    private fun tiersFailed(status: AccountStatus) = TiersFailed(
        tierMessage, listOf(TierFailure("NewPipe", FailureReason.UNPLAYABLE, "age-restricted")), status, "account cooling down until 09:25 UTC", true, emptyList(),
    )

    private fun session(error: ExtractorError): Pair<PlaybackSession, FakeHost> {
        val db = createDatabase(JvmDatabaseDriverFactory())
        val controller = FakeController()
        val s = PlaybackSession(FailingExtractor(error), controller, SettingsResolver(InMemorySettingsStore()), SqlDelightWatchHistoryRepository(db), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
        return s to FakeHost(s, controller)
    }

    private class RecordingUri : UriHandler { val opened = mutableListOf<String>(); override fun openUri(uri: String) { opened += uri } }

    @Test fun plainSignInRequiredExplainsAndOffersTheBrowserNotRetry() = runDesktopComposeUiTest(800, 600) {
        val (s, host) = session(ExtractorError.SignInRequired("Sign in to confirm you're not a bot"))
        val uri = RecordingUri()
        setContent { CompositionLocalProvider(LocalUriHandler provides uri) { PlayerView(host, FakeData.videos[0], emptyList(), emptyList()) } }
        s.start("v01", FakeData.videos[0]); waitUntil(timeoutMillis = 5_000) { s.state.value is com.greenaltambe.vela.player.SessionState.Failed }; waitForIdle()
        onNodeWithTag("player.error").assertIsDisplayed()
        assertTrue(runCatching { onNodeWithTag("player.retry").assertIsDisplayed() }.isFailure, "no Retry for a sign-in failure")
        onNodeWithTag("player.openInBrowser").performClick(); waitForIdle()
        assertEquals(listOf("https://www.youtube.com/watch?v=v01"), uri.opened)
    }

    @Test fun pausedAccountShowsTheTiersAndTryNowAsksFirst() = runDesktopComposeUiTest(800, 600) {
        val (s, host) = session(tiersFailed(AccountStatus.PAUSED))
        var tried = 0
        setContent { PlayerView(host, FakeData.videos[0], emptyList(), emptyList(), tryAccountNow = { tried++ }) }
        s.start("v01", FakeData.videos[0]); waitUntil(timeoutMillis = 5_000) { s.state.value is com.greenaltambe.vela.player.SessionState.Failed }; waitForIdle()
        onNodeWithTag("player.error", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("player.openInBrowser", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("player.tryAccountNow", useUnmergedTree = true).performClick(); waitForIdle()
        assertEquals(0, tried, "asks before ending the pause")
        onNodeWithTag("player.tryAccountNowConfirm", useUnmergedTree = true).performClick(); waitForIdle()
        assertEquals(1, tried)
    }

    @Test fun noTryButtonWhenTheAccountIsNotPaused() = runDesktopComposeUiTest(800, 600) {
        for (status in listOf(AccountStatus.SIGNED_OUT, AccountStatus.NO_ACCOUNT_TIER, AccountStatus.DISABLED)) {
            val (s, host) = session(tiersFailed(status))
            setContent { PlayerView(host, FakeData.videos[0], emptyList(), emptyList(), tryAccountNow = { }) }
            s.start("v01", FakeData.videos[0]); waitUntil(timeoutMillis = 5_000) { s.state.value is com.greenaltambe.vela.player.SessionState.Failed }; waitForIdle()
            onNodeWithTag("player.openInBrowser", useUnmergedTree = true).assertIsDisplayed()
            assertTrue(runCatching { onNodeWithTag("player.tryAccountNow", useUnmergedTree = true).assertIsDisplayed() }.isFailure, "$status must not offer Try now")
        }
    }

    @Test fun ageRestrictedDisabledShowsTheClearMessageAndAButtonToTheSetting() = runDesktopComposeUiTest(800, 600) {
        val (s, host) = session(ExtractorError.AgeRestrictedDisabled())
        var opened = 0
        setContent { PlayerView(host, FakeData.videos[0], emptyList(), emptyList(), openAgeSetting = { opened++ }) }
        s.start("v01", FakeData.videos[0]); waitUntil(timeoutMillis = 5_000) { s.state.value is com.greenaltambe.vela.player.SessionState.Failed }; waitForIdle()
        onNodeWithTag("player.error", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("player.error", useUnmergedTree = true).assertTextEquals("Age-restricted videos are disabled in settings")
        onNodeWithTag("player.openAgeSetting", useUnmergedTree = true).performClick(); waitForIdle()
        assertEquals(1, opened)
    }
}

