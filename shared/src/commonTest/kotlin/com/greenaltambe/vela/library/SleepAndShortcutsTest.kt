package com.greenaltambe.vela.library

import androidx.compose.ui.input.key.Key
import com.greenaltambe.vela.ui.player.ShortcutAction
import com.greenaltambe.vela.ui.player.shortcutFor
import com.greenaltambe.vela.ui.player.steppedSpeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShortcutsTest {
    @Test
    fun keysMapToActions() {
        assertEquals(ShortcutAction.TogglePlay, shortcutFor(Key.Spacebar, false))
        assertEquals(ShortcutAction.SeekBack5, shortcutFor(Key.DirectionLeft, false))
        assertEquals(ShortcutAction.SeekForward10, shortcutFor(Key.DirectionRight, true))
        assertEquals(ShortcutAction.ToggleFullscreen, shortcutFor(Key.F, false))
        assertEquals(ShortcutAction.ToggleMute, shortcutFor(Key.M, false))
        assertEquals(ShortcutAction.VolumeUp, shortcutFor(Key.DirectionUp, false))
        assertEquals(ShortcutAction.VolumeDown, shortcutFor(Key.DirectionDown, false))
        assertEquals(ShortcutAction.SpeedDown, shortcutFor(Key.Comma, true))
        assertEquals(ShortcutAction.SpeedUp, shortcutFor(Key.Period, true))
        assertNull(shortcutFor(Key.A, false))
    }

    @Test
    fun speedStepsClampAtTheEnds() {
        assertEquals(1.25f, steppedSpeed(1f, up = true))
        assertEquals(0.75f, steppedSpeed(1f, up = false))
        assertEquals(2f, steppedSpeed(2f, up = true))
        assertEquals(0.25f, steppedSpeed(0.25f, up = false))
        assertEquals(1.25f, steppedSpeed(1.1f, up = true), "snaps to the nearest step first")
    }
}
