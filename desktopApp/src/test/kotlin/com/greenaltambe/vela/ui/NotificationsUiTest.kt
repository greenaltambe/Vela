@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.data.NotificationKind
import com.greenaltambe.vela.ui.nav.Route
import org.junit.Test
import kotlin.test.assertEquals

class NotificationsUiTest {
    @Test fun anEmptyFeedSaysSo() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(withDownloads = false)
        f.navigator.push(Route.Notifications)
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("empty").assertIsDisplayed()
    }

    @Test fun rowsOpenTheirVideoAndMarkAllReadClearsTheUnreadDots() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(withDownloads = false)
        val now = f.services.clock.now()
        f.services.notifications.add(NotificationKind.UPLOAD, "v1", "First video", now, channelName = "Chan", videoId = "v1", channelId = "UC1")
        f.services.notifications.add(NotificationKind.DOWNLOAD_DONE, "d1", "Saved clip", now)
        f.navigator.push(Route.Notifications)
        setContent { AppUnderTest(f) }; waitForIdle()
        assertEquals(2, onAllNodesWithTag("notification.row").fetchSemanticsNodes().size)
        assertEquals(2, onAllNodesWithTag("notification.unread", useUnmergedTree = true).fetchSemanticsNodes().size)
        onNodeWithTag("notifications.markAll").performClick(); waitForIdle()
        assertEquals(0, onAllNodesWithTag("notification.unread", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, f.services.notifications.unreadCount())
    }
}
