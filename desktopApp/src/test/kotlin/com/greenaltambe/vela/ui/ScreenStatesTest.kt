@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.extractor.*
import com.greenaltambe.vela.ui.nav.Route
import kotlinx.coroutines.awaitCancellation
import org.junit.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The failure states of the screens that load from the network: a dead network, a captive portal's HTML where JSON was
 * expected, and a call that never answers. Each must end in an error row with Retry (never a spinner for ever), and Retry must work.
 */
class ScreenStatesTest {
    enum class Mode { OK, NETWORK, CAPTIVE_PORTAL, HANG }

    private class Flaky(var mode: Mode) : Extractor by FakeExtractor() {
        private val ok = FakeExtractor()
        private fun <T> fail(): ExtractorResult<T>? = when (mode) {
            Mode.OK -> null
            Mode.NETWORK -> ExtractorResult.Failure(ExtractorError.Network("Unable to resolve host \"www.youtube.com\""))
            Mode.CAPTIVE_PORTAL -> ExtractorResult.Failure(ExtractorError.ParseFailure("Expected JSON but got <html><title>Sign in to the Wi-Fi</title>"))
            Mode.HANG -> null
        }
        private suspend fun <T> go(real: suspend () -> ExtractorResult<T>): ExtractorResult<T> { if (mode == Mode.HANG) awaitCancellation(); return fail<T>() ?: real() }
        override suspend fun trending(continuation: String?) = go { ok.trending(continuation) }
        override suspend fun search(query: String, continuation: String?) = go { ok.search(query, continuation) }
        override suspend fun channel(channelId: String) = go { ok.channel(channelId) }
        override suspend fun channelVideos(channelId: String, continuation: String?) = go { ok.channelVideos(channelId, continuation) }
        override suspend fun comments(videoId: String, sort: CommentSort, continuation: String?) = go { ok.comments(videoId, sort, continuation) }
        override suspend fun related(videoId: String) = go { ok.related(videoId) }
        override suspend fun recentUploads(channelId: String) = go { ok.recentUploads(channelId) }
        override suspend fun videoInfo(videoId: String) = ok.videoInfo(videoId)
    }

    private val screens: List<Pair<String, (Fixture) -> Unit>> = listOf(
        "home" to { f -> f.navigator.selectTab(Route.Home) },
        "search" to { f -> f.navigator.push(Route.Search("lofi")) },
        "channel" to { f -> f.navigator.push(Route.Channel("UC1", "A channel")) },
        "subscriptions feed" to { f -> f.navigator.selectTab(Route.Subscriptions) },
        // The video page is a layer over the screen below it, so that screen must be one that does no network work (Library); the comments error shows once the panel is opened.
        "video (comments)" to { f -> f.navigator.selectTab(Route.Library); f.navigator.push(Route.Video(FakeData.videos[0])) },
    )

    private fun errorShown() = true

    @Test fun everyNetworkScreenEndsInAnErrorWithRetryForEachKindOfFailureAndRetryWorks() {
        for (mode in listOf(Mode.NETWORK, Mode.CAPTIVE_PORTAL, Mode.HANG)) for ((name, open) in screens) runDesktopComposeUiTest(800, 1000) {
            val flaky = Flaky(mode)
            val f = buildFixture(withDownloads = false, extractorOverride = TimeoutExtractor(flaky, 1.seconds))
            open(f)
            setContent { AppUnderTest(f) }
            if (name.startsWith("video")) {
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithTagExists("video.commentsButton") }
                onNodeWithTag("video.commentsButton", useUnmergedTree = true).performClick()
            }
            waitUntil("an error with Retry on $name after $mode", 10_000) { onAllNodesWithTagExists("error.retry") }
            onNodeWithTag("error.retry", useUnmergedTree = true).assertIsDisplayed()
            flaky.mode = Mode.OK
            onNodeWithTag("error.retry", useUnmergedTree = true).performClick()
            waitUntil("the error is gone after Retry on $name after $mode", 10_000) { !onAllNodesWithTagExists("error.retry") }
            assertTrue(true, "$name after $mode")
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesWithTagExists(tag: String) =
        onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
}
