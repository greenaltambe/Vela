package com.greenaltambe.vela.core

import com.greenaltambe.vela.core.settings.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsSearchTest {
    @Test
    fun matchesLabelAndKeywordsCaseInsensitively() {
        assertEquals(listOf(Settings.sponsorBlockEnabled), Settings.search("SPONSOR"))
        assertEquals(listOf(Settings.defaultPlaybackSpeed), Settings.search("faster"))
    }

    @Test
    fun everyWordMustMatch() {
        assertEquals(listOf(Settings.accountFallbackMode), Settings.search("sign blocked"))
        assertTrue(Settings.search("sign shorts").isEmpty())
    }

    @Test
    fun labelMatchesRankAboveKeywordMatches() {
        // "feed" is a label-less keyword of Shorts; "account" is in the fallback label and as a keyword elsewhere.
        val fake = listOf(
            Setting("a", true, SettingScope.GLOBAL, "Other", listOf("login"), SettingCodecs.boolean, SettingSection.MODULES),
            Setting("b", true, SettingScope.GLOBAL, "Login method", emptyList(), SettingCodecs.boolean, SettingSection.MODULES),
        )
        assertEquals(listOf("b", "a"), Settings.search("login", fake).map { it.key })
    }

    @Test
    fun blankOrUnknownQueryMatchesNothing() {
        assertTrue(Settings.search("").isEmpty())
        assertTrue(Settings.search("   ").isEmpty())
        assertTrue(Settings.search("zzzz").isEmpty())
    }

    @Test
    fun metadataIsPopulatedForExistingSettings() {
        assertEquals(3, Settings.accountFallbackMode.choices.size)
        assertEquals(ShortsMode.entries.map { it.name }, Settings.shortsMode.choices.map { it.raw })
        assertTrue(Settings.accountFallbackMode.choices.map { it.raw }.all { Settings.accountFallbackMode.codec.decode(it) != null })
        assertEquals(1.0, Settings.defaultPlaybackSpeed.default.toDouble())
        assertTrue(Settings.defaultPlaybackSpeed.default.toDouble() in Settings.defaultPlaybackSpeed.range!!.min..Settings.defaultPlaybackSpeed.range!!.max)
        assertEquals(SettingSection.MODULES, Settings.deArrowEnabled.section)
    }
}
