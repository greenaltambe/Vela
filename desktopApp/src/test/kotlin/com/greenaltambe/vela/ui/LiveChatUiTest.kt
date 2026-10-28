@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.extractor.*
import com.greenaltambe.vela.ui.nav.Route
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveChatUiTest {
    private class Chat(override val isReplay: Boolean = false, var polls: Int = 0) : ChatChannel {
        override suspend fun poll(): ChatBatch { polls++; return ChatBatch(listOf(ChatMessage("m$polls", "Alice", "hello $polls"), ChatMessage("p$polls", "Bob", "thanks", ChatKind.SUPERCHAT, "$5.00")), 3_000) }
    }
    private class Source(val chat: ChatChannel?) : LiveChatSource { var opened = 0; override suspend fun open(videoId: String): ChatChannel? { opened++; return chat } }

    private val live = FakeData.videos[0].copy(isLive = true)

    @Test fun theChatPanelAppearsOnlyForLiveVideosPollsOnlyWhileOpenAndShowsPaidMessages() = runDesktopComposeUiTest(800, 1000) {
        val source = Source(Chat())
        val f = buildFixture(withDownloads = false, liveChat = source)
        f.navigator.push(Route.Video(live))
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("video.chat.toggle", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, source.opened, "nothing is requested until the viewer opens the chat")
        onNodeWithTag("video.chat.toggle", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 5_000) { runCatching { onNodeWithText("Alice: hello 1", useUnmergedTree = true).assertIsDisplayed() }.isSuccess }
        onNodeWithText("Bob (\$5.00): thanks", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun aVideoWithoutChatSaysSoAndANormalVideoHasNoPanel() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(withDownloads = false, liveChat = Source(null))
        f.navigator.push(Route.Video(live))
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("video.chat.toggle", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 5_000) { runCatching { onNodeWithText("This video has no live chat.", useUnmergedTree = true).assertIsDisplayed() }.isSuccess }
        val g = buildFixture(withDownloads = false, liveChat = Source(Chat()))
        g.navigator.push(Route.Video(FakeData.videos[1]))
        setContent { AppUnderTest(g) }; waitForIdle()
        assertTrue(runCatching { onNodeWithTag("video.chat.toggle", useUnmergedTree = true).assertIsDisplayed() }.isFailure)
    }

    @Test fun aReplayShowsOnlyTheMessagesAtOrBeforeThePlayhead() = runDesktopComposeUiTest(800, 1000) {
        val replay = object : ChatChannel {
            override val isReplay = true
            override suspend fun poll() = ChatBatch(listOf(ChatMessage("a", "Early", "before the playhead", offsetMs = 1_000), ChatMessage("b", "Late", "after the playhead", offsetMs = 5_000_000)), 0)
        }
        val f = buildFixture(withDownloads = false, liveChat = object : LiveChatSource { override suspend fun open(videoId: String): ChatChannel? = replay })
        f.navigator.push(Route.Video(FakeData.videos[0]))   // the harness video is 1845 s long and the playhead starts at 754 s
        setContent { AppUnderTest(f) }; waitForIdle()
        waitUntil(timeoutMillis = 5_000) { runCatching { onNodeWithTag("video.chat.toggle", useUnmergedTree = true).assertIsDisplayed() }.isSuccess }
        onNodeWithText("Show chat replay", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("video.chat.toggle", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 5_000) { runCatching { onNodeWithText("Early: before the playhead", useUnmergedTree = true).assertIsDisplayed() }.isSuccess }
        assertTrue(runCatching { onNodeWithText("Late: after the playhead", useUnmergedTree = true).assertIsDisplayed() }.isFailure, "a message from later in the video is not shown yet")
    }
}
