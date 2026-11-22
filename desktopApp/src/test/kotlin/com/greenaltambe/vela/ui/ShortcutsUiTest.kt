@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.ShortsMode
import com.greenaltambe.vela.ui.nav.Route
import org.junit.Test
import kotlin.test.assertEquals

class ShortcutsUiTest {
    private fun ComposeUiTest.press(key: Key, ctrl: Boolean = false) {
        onRoot().performKeyInput { if (ctrl) keyDown(Key.CtrlLeft); pressKey(key); if (ctrl) keyUp(Key.CtrlLeft) }
        waitForIdle()
    }

    @Test fun numberKeysOpenTheNthNavigationItemAndCtrlHOpensHistory() = runDesktopComposeUiTest(1280, 800) {
        val f = buildFixture(withDownloads = false)
        setContent { AppUnderTest(f) }; waitForIdle()
        // default order: Home, Shorts, Subscriptions, Library
        press(Key.Two); assertEquals(Route.Shorts, f.navigator.current)
        press(Key.Three); assertEquals(Route.Subscriptions, f.navigator.current)
        press(Key.Four); assertEquals(Route.Library, f.navigator.current)
        press(Key.One); assertEquals(Route.Home, f.navigator.current)
        press(Key.Nine); assertEquals(Route.Home, f.navigator.current, "no ninth item: nothing happens")
        press(Key.H, ctrl = true); assertEquals(Route.History, f.navigator.current)
    }

    @Test fun hidingShortsRenumbersTheKeys() = runDesktopComposeUiTest(1280, 800) {
        val f = buildFixture(withDownloads = false)
        f.services.settings.set(Settings.shortsMode, ShortsMode.HIDE)
        setContent { AppUnderTest(f) }; waitForIdle()
        press(Key.Two); assertEquals(Route.Subscriptions, f.navigator.current)
        press(Key.Three); assertEquals(Route.Library, f.navigator.current)
    }

    @Test fun ctrlMMinimisesTheVideoAndBringsItBack() = runDesktopComposeUiTest(1280, 800) {
        val f = buildFixture(withDownloads = false)
        f.navigator.push(Route.Video(FakeData.videos[0]))
        setContent { AppUnderTest(f) }; waitForIdle()
        mainClock.advanceTimeBy(1000); waitForIdle()
        val sheet = f.navigator.playerSheetState!!
        assertEquals(true, sheet.isExpanded)
        press(Key.M, ctrl = true); mainClock.advanceTimeBy(1000); waitForIdle()
        assertEquals(false, sheet.isExpanded, "Ctrl+M minimises to the mini player")
        assertEquals(FakeData.videos[0].id, f.navigator.nowPlaying?.id)
        press(Key.M, ctrl = true); mainClock.advanceTimeBy(1000); waitForIdle()
        assertEquals(true, sheet.isExpanded, "Ctrl+M again opens the video page")
        press(Key.Three); mainClock.advanceTimeBy(1000); waitForIdle()
        assertEquals(false, sheet.isExpanded, "a number key minimises a playing video")
        assertEquals(Route.Subscriptions, f.navigator.current)
    }
}
