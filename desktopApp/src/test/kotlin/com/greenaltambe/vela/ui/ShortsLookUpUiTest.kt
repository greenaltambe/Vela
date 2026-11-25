@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.extractor.Extractor
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.VideoInfo
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.ui.nav.Route
import kotlinx.coroutines.CompletableDeferred
import org.junit.Test
import kotlin.test.assertEquals

/** A Short listed without a channel plays at once and shows the channel when the lookup answers. */
class ShortsLookUpUiTest {
    private val gate = CompletableDeferred<Unit>()
    private val extractor = object : Extractor by FakeExtractor() {
        override suspend fun videoInfo(videoId: String): ExtractorResult<VideoInfo> {
            gate.await()
            return ExtractorResult.Success(VideoInfo(videoId, "Looked-up title", "", "UCfound", "Found Channel", 30))
        }
    }

    @Test fun theShortPlaysFirstAndItsChannelAppearsAfterwards() = runDesktopComposeUiTest(412, 900) {
        val f = buildFixture(withDownloads = false, extractorOverride = extractor)
        f.navigator.push(Route.ShortsPlayer(VideoSummary("s1", "Listed title", "", "", isShort = true)))
        setContent { AppUnderTest(f) }; waitForIdle()
        waitUntil(timeoutMillis = 5_000) { f.services.player!!.session.media.value?.videoId == "s1" && f.controller.state.value.durationMs > 0 }
        assertEquals(0, onAllNodesWithText("Found Channel").fetchSemanticsNodes().size)
        onAllNodesWithText("Listed title")[0].assertExists()
        gate.complete(Unit); waitForIdle()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Found Channel").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("shorts.channel", useUnmergedTree = true).assertExists()
        assertEquals(0, onAllNodesWithText("Looked-up title").fetchSemanticsNodes().size, "the listing's own title stays")
    }
}
