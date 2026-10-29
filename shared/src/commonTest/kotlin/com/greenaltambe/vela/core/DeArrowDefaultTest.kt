package com.greenaltambe.vela.core

import com.greenaltambe.vela.core.settings.Settings
import kotlin.test.Test
import kotlin.test.assertFalse

class DeArrowDefaultTest {
    @Test fun deArrowIsOffUntilTheOwnerTurnsItOn() {
        assertFalse(Settings.deArrowEnabled.default)
        assertFalse(Settings.deArrowGeneratedThumbnails.default)
    }
}
