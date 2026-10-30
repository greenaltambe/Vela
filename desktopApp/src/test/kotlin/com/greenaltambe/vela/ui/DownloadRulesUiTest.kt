@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.downloads.AutoDownloadRule
import com.greenaltambe.vela.ui.nav.Route
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DownloadRulesUiTest {
    @Test fun rulesCanBeAddedToggledEditedAndDeleted() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture()
        f.navigator.selectTab(Route.Library); f.navigator.push(Route.Downloads); f.navigator.push(Route.DownloadRules)
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("rules.add").performClick(); waitForIdle()
        onNodeWithTag("rules.option.${AutoDownloadRule.WATCH_LATER}").performClick(); waitForIdle()
        val key = AutoDownloadRule.WATCH_LATER
        onNodeWithTag("rules.row.$key").assertIsDisplayed()
        assertTrue(f.services.autoDownloadRules.ruleFor(key)!!.wifiOnly, "Wi-Fi only is the default")
        onNodeWithTag("rules.toggle.$key").performClick(); waitForIdle()
        assertFalse(f.services.autoDownloadRules.ruleFor(key)!!.enabled)
        onNodeWithTag("rules.delete.$key").performClick(); waitForIdle()
        assertEquals(null, f.services.autoDownloadRules.ruleFor(key))
    }

    @Test fun theChannelPageSwitchCreatesARuleThatStartsNow() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture()
        // The switch is offered on channels you subscribe to.
        f.services.subscriptions.subscribe("UCswitch", "Switch channel", null, kotlin.time.Clock.System.now())
        f.navigator.push(Route.Channel("UCswitch", "Switch channel"))
        setContent { AppUnderTest(f) }; waitForIdle()
        waitUntil(timeoutMillis = 3_000) { runCatching { onNodeWithTag("channel.autoDownload").assertIsDisplayed() }.isSuccess }
        onNodeWithTag("channel.autoDownload").performClick(); waitForIdle()
        val rule = assertNotNull(f.services.autoDownloadRules.ruleFor("UCswitch"))
        assertTrue(rule.enabled && rule.sinceMs > 0, "only videos from now on, not the backlog")
    }
}
