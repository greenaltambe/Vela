package com.greenaltambe.vela.ui

import com.greenaltambe.vela.core.settings.LookChoice
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.control
import com.greenaltambe.vela.core.settings.SettingControl
import com.greenaltambe.vela.core.settings.settingsBySection
import com.greenaltambe.vela.ui.look.ComponentIds
import com.greenaltambe.vela.ui.look.LookTokens
import com.greenaltambe.vela.ui.look.Looks
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LookTokensTest {
    @Test
    fun aLookSurvivesBeingSavedAsJsonAndLoadedBack() {
        for (look in listOf(Looks.Light, Looks.Dark, Looks.PureBlack)) {
            val json = Json.encodeToString(LookTokens.serializer(), look)
            assertEquals(look, Json.decodeFromString(LookTokens.serializer(), json))
        }
    }

    @Test
    fun pureBlackIsReallyBlackAndTheDefaultFollowsTheSystem() {
        assertEquals(0xFF000000, Looks.PureBlack.colors.background)
        assertEquals(Looks.Dark, Looks.forChoice(pureBlack = false, systemDark = true))
        assertEquals(Looks.Light, Looks.forChoice(pureBlack = false, systemDark = false))
        assertEquals(Looks.PureBlack, Looks.forChoice(pureBlack = true, systemDark = false), "pure black ignores the system setting")
    }

    @Test
    fun everySponsorBlockCategoryHasAMarkerColourInEveryLook() {
        val categories = listOf("sponsor", "selfpromo", "interaction", "intro", "outro", "preview", "music_offtopic", "filler", "poi_highlight", "exclusive_access")
        for (look in listOf(Looks.Light, Looks.Dark, Looks.PureBlack)) {
            assertEquals(categories.toSet(), look.colors.markers.keys)
            assertTrue(look.colors.markers.values.all { it ushr 24 == 0xFFL }, "markers are opaque")
        }
    }

    @Test
    fun componentIdsAreUniqueStableStrings() {
        assertEquals(ComponentIds.all.size, ComponentIds.all.toSet().size)
        assertTrue(ComponentIds.all.all { it.startsWith("component.") })
        assertEquals("component.video_card", ComponentIds.VIDEO_CARD, "ids are persisted by later features; they must not change")
    }

    @Test
    fun taskNineSettingsAreInTheSchemaWithLabelsKeywordsAndSections() {
        val added = listOf(
            Settings.look, Settings.performanceMode, Settings.localOnlyHistory, Settings.hideWatched, Settings.minLengthSeconds,
            Settings.blockKeywords, Settings.allowKeywords, Settings.keywordsAreRegex,
        )
        added.forEach { s ->
            assertTrue(s in Settings.all, s.key)
            assertTrue(s.label.isNotBlank() && s.keywords.isNotEmpty(), s.key)
        }
        assertEquals(SettingControl.CHOICE, Settings.look.control())
        assertEquals(LookChoice.entries.map { it.name }, Settings.look.choices.map { it.raw })
        assertEquals(SettingControl.SLIDER, Settings.minLengthSeconds.control())
        assertEquals(false, Settings.localOnlyHistory.default, "history.localOnly defaults to off")
        assertEquals(Settings.all.map { it.key }.toSet().size, Settings.all.size, "keys are unique")
        assertTrue(Settings.search("regex").contains(Settings.keywordsAreRegex))
        assertTrue(settingsBySection("keyword").flatMap { it.second }.containsAll(listOf(Settings.blockKeywords, Settings.allowKeywords)))
    }
}
