@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.*
import com.greenaltambe.vela.core.settings.*
import com.greenaltambe.vela.ui.nav.Route
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingItemTest {
    private fun key(k: String) = Settings.all.first { it.key == k }

    @Test fun eachControlTypeWritesItsValueThroughTheSameRow() = runDesktopComposeUiTest(400, 1200) {
        val f = buildFixture(withDownloads = false)
        f.navigator.selectTab(Route.Settings); f.navigator.push(Route.SettingsPage("playback"))
        setContent { AppUnderTest(f) }; waitForIdle()
        // Switch row: the whole row toggles.
        val failover = key("playback.slowStreamFailover"); val before = f.settings.rawValue(failover)
        onNodeWithTag("settings.pageList").performScrollToNode(hasTestTag("setting.playback.slowStreamFailover.switch"))
        onNodeWithTag("setting.playback.slowStreamFailover.switch").performClick(); waitForIdle()
        assertEquals((before != "true").toString(), f.settings.rawValue(failover)); mainClock.advanceTimeBy(6_000)   // let the undo toast go: it sits over the bottom of the page
        // A longer list is a picker row that opens a sheet.
        val quality = key("playback.maxQualityWifi"); val pick = quality.choices.first { it.raw != f.settings.rawValue(quality) }
        onNodeWithTag("settings.pageList").performScrollToNode(hasTestTag("setting.playback.maxQualityWifi"))
        onNodeWithTag("setting.playback.maxQualityWifi").performClick(); waitForIdle()
        onNodeWithTag("setting.playback.maxQualityWifi.option.${pick.raw}").performClick(); waitForIdle()
        assertEquals(pick.raw, f.settings.rawValue(quality)); mainClock.advanceTimeBy(6_000)
        // The slider's reset icon is there only while the value is not the default, and puts it back.
        val speed = key("playback.speed"); f.settings.setRaw(speed, "2.0"); waitForIdle()
        onNodeWithTag("settings.pageList").performScrollToNode(hasTestTag("setting.playback.speed.reset"))
        onNodeWithTag("setting.playback.speed.reset").performClick(); waitForIdle()
        assertTrue(!f.settings.isOverridden(speed))
        onNodeWithTag("setting.playback.speed.value", useUnmergedTree = true).assertExists()
    }

    @Test fun segmentedButtonsWriteTheTappedOption() = runDesktopComposeUiTest(400, 1200) {
        val f = buildFixture(withDownloads = false)
        f.navigator.selectTab(Route.Settings); f.navigator.push(Route.SettingsPage("playback"))
        setContent { AppUnderTest(f) }; waitForIdle()
        val codec = key("playback.preferredCodec"); val other = codec.choices.first { it.raw != f.settings.rawValue(codec) }
        val tag = "setting.playback.preferredCodec.pills.${codec.choices.indexOf(other)}"
        onNodeWithTag("settings.pageList").performScrollToNode(hasTestTag(tag))
        onNodeWithTag(tag).performClick(); waitForIdle()
        assertEquals(other.raw, f.settings.rawValue(codec))
    }

    @Test fun aTextSettingOpensADialogAndSavesOnce() = runDesktopComposeUiTest(400, 1000) {
        val f = buildFixture(withDownloads = false)
        f.navigator.selectTab(Route.Settings); f.navigator.push(Route.SettingsPage("feed"))
        setContent { AppUnderTest(f) }; waitForIdle()
        val words = key("feed.blockKeywords")
        onNodeWithTag("settings.pageList").performScrollToNode(hasTestTag("setting.feed.blockKeywords"))
        onNodeWithTag("setting.feed.blockKeywords").performClick(); waitForIdle()
        onNodeWithTag("setting.feed.blockKeywords.field").performTextInput("asmr, shorts"); waitForIdle()
        assertEquals("", f.settings.rawValue(words), "nothing is written while typing")
        onNodeWithTag("setting.feed.blockKeywords.save").performClick(); waitForIdle()
        assertEquals("asmr, shorts", f.settings.rawValue(words))
    }

    @Test fun onAWideWindowSettingsIsTwoPanesAndPickingAPageReplacesTheRightOne() = runDesktopComposeUiTest(1200, 800) {
        val f = buildFixture(withDownloads = false)
        f.navigator.selectTab(Route.Settings)
        setContent { AppUnderTest(f) }; waitForIdle()
        // The list is on the left and a page is already open on the right.
        onNodeWithTag("settings.list").assertExists()
        onNodeWithTag("settings.page.playback").assertExists()
        onNodeWithTag("nav.back").assertDoesNotExist()
        onNodeWithTag("settings.page.playback").performClick(); waitForIdle()
        assertEquals(Route.SettingsPage("playback"), f.navigator.current)
        onNodeWithTag("settings.page.extras").assertExists()   // the list is still there
        onNodeWithTag("settings.pageList").assertExists()
        onNodeWithTag("settings.list").performScrollToNode(hasTestTag("settings.page.safety"))
        onNodeWithTag("settings.page.safety").performClick(); waitForIdle()
        assertEquals(Route.SettingsPage("safety"), f.navigator.current)
        f.navigator.back(); waitForIdle()
        assertEquals(Route.Settings, f.navigator.current, "Back leaves Settings in one step, not through every page that was picked")
    }
}
