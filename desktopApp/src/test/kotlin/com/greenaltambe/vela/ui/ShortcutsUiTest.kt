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

    @Test fun ctrlNumberKeysOpenTheNthNavigationItemAndCtrlHOpensHistory() = runDesktopComposeUiTest(1280, 800) {
        val f = buildFixture(withDownloads = false)
        setContent { AppUnderTest(f) }; waitForIdle()
        // default order: Home, Shorts, Subscriptions, Library
        press(Key.Two, ctrl = true); assertEquals(Route.Shorts, f.navigator.current)
        press(Key.Three, ctrl = true); assertEquals(Route.Subscriptions, f.navigator.current)
        press(Key.Four, ctrl = true); assertEquals(Route.Library, f.navigator.current)
        press(Key.One, ctrl = true); assertEquals(Route.Home, f.navigator.current)
        press(Key.Nine, ctrl = true); assertEquals(Route.Home, f.navigator.current, "no ninth item: nothing happens")
        press(Key.H, ctrl = true); assertEquals(Route.History, f.navigator.current)
    }

    @Test fun hidingShortsRenumbersTheKeys() = runDesktopComposeUiTest(1280, 800) {
        val f = buildFixture(withDownloads = false)
        f.services.settings.set(Settings.shortsMode, ShortsMode.HIDE)
        setContent { AppUnderTest(f) }; waitForIdle()
        press(Key.Two, ctrl = true); assertEquals(Route.Subscriptions, f.navigator.current)
        press(Key.Three, ctrl = true); assertEquals(Route.Library, f.navigator.current)
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
        press(Key.Three, ctrl = true); mainClock.advanceTimeBy(1000); waitForIdle()
        assertEquals(false, sheet.isExpanded, "Ctrl+number minimises a playing video")
        assertEquals(Route.Subscriptions, f.navigator.current)
    }

    private fun ComposeUiTest.videoPage(): Fixture {
        val f = buildFixture(withDownloads = false)
        f.navigator.push(Route.Video(FakeData.videos[0]))
        setContent { AppUnderTest(f) }; waitForIdle()
        mainClock.advanceTimeBy(1000); waitForIdle()
        return f
    }

    private fun ComposeUiTest.shift(key: Key) {
        onRoot().performKeyInput { keyDown(Key.ShiftLeft); pressKey(key); keyUp(Key.ShiftLeft) }
        waitForIdle()
    }

    @Test fun theVideoKeysWorkOnTheVideoPage() = runDesktopComposeUiTest(1280, 800) {
        val f = videoPage()
        val state = f.controller.state
        val start = state.value.positionMs
        press(Key.K); assertEquals(com.greenaltambe.vela.player.PlaybackStatus.PAUSED, state.value.status, "k pauses")
        press(Key.Spacebar); mainClock.advanceTimeBy(600); waitForIdle()
        assertEquals(com.greenaltambe.vela.player.PlaybackStatus.PLAYING, state.value.status, "space plays")
        press(Key.DirectionRight); assertEquals(start + 5_000, state.value.positionMs, "right arrow: +5 s")
        press(Key.DirectionLeft); assertEquals(start, state.value.positionMs, "left arrow: -5 s")
        shift(Key.DirectionRight); assertEquals(start + 10_000, state.value.positionMs, "shift+right: +10 s")
        shift(Key.DirectionLeft); assertEquals(start, state.value.positionMs, "shift+left: -10 s")
        press(Key.L); assertEquals(start + 10_000, state.value.positionMs, "l: +10 s")
        press(Key.J); assertEquals(start, state.value.positionMs, "j: -10 s")
        press(Key.Five); assertEquals(state.value.durationMs * 5 / 10, state.value.positionMs, "5: half way")
        press(Key.Zero); assertEquals(0L, state.value.positionMs, "0: the start")
        press(Key.MoveEnd); assertEquals(state.value.durationMs, state.value.positionMs, "End: the end")
        press(Key.MoveHome); assertEquals(0L, state.value.positionMs, "Home: the start")
        press(Key.Period); assertEquals(1.25f, state.value.speed, "period: faster")
        press(Key.Comma); press(Key.Comma); assertEquals(0.75f, state.value.speed, "comma: slower")
        val muted0 = f.services.player!!.session.muted.value
        press(Key.M); assertEquals(!muted0, f.services.player!!.session.muted.value, "m toggles mute")
        press(Key.DirectionUp); press(Key.DirectionDown)    // volume keys: must not navigate or crash
        assertEquals(true, f.navigator.playerSheetState!!.isExpanded, "the video page stays open")
    }

    @Test fun modifiedKeysAreNeverVideoKeys() = runDesktopComposeUiTest(1280, 800) {
        val f = videoPage()
        val state = f.controller.state
        val start = state.value.positionMs
        press(Key.K, ctrl = true); assertEquals(com.greenaltambe.vela.player.PlaybackStatus.PLAYING, state.value.status, "Ctrl+K is not play/pause")
        press(Key.DirectionRight, ctrl = true); assertEquals(start, state.value.positionMs, "Ctrl+Right does not seek")
        press(Key.F, ctrl = true); press(Key.C, ctrl = true)
        assertEquals(start, state.value.positionMs)
    }
}
