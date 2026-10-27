package com.greenaltambe.vela.core

import com.greenaltambe.vela.core.settings.*
import kotlin.test.*

class SettingEditingTest {
    private val settings = SettingsResolver(InMemorySettingsStore())

    @Test fun controlKindComesFromTheSchemaAlone() {
        assertEquals(SettingControl.TOGGLE, Settings.audioOnly.control())
        assertEquals(SettingControl.TOGGLE, Settings.sponsorBlockEnabled.control())
        assertEquals(SettingControl.CHOICE, Settings.maxQualityMetered.control())
        assertEquals(SettingControl.CHOICE, Settings.preferredCodec.control())
        assertEquals(SettingControl.SLIDER, Settings.defaultPlaybackSpeed.control())
        assertEquals(SettingControl.TEXT, Settings.ytDlpPath.control())
    }

    @Test fun everyRegisteredSettingGetsAControlAndTheNewOnesAreInThere() {
        Settings.all.forEach { assertNotNull(it.control(), it.key) }
        val keys = Settings.all.map { it.key }
        listOf("playback.maxQualityWifi", "playback.maxQualityCellular", "playback.preferredCodec", "playback.audioOnly", "ytdlp.path")
            .forEach { assertTrue(it in keys, it) }
        assertEquals(keys.size, keys.toSet().size, "unique keys")
    }

    @Test fun sectionsAreGroupedInDisplayOrderAndCoverEverything() {
        val sections = settingsBySection()
        assertEquals(sections.map { it.first }, sections.map { it.first }.sortedBy { it.ordinal })
        assertEquals(Settings.all.toSet(), sections.flatMap { it.second }.toSet())
        assertTrue(Settings.maxQualityMetered in sections.first { it.first == SettingSection.PLAYBACK }.second)
        assertTrue(Settings.ytDlpPath in sections.first { it.first == SettingSection.ADVANCED }.second)
    }

    @Test fun searchNarrowsTheSectionsAndABlankQueryShowsAll() {
        val found = settingsBySection("codec").flatMap { it.second }
        assertTrue(Settings.preferredCodec in found)
        assertTrue(Settings.downloadsPreferAvc in found, "the download codec preference matches too")
        assertTrue(found.all { s -> (listOf(s.label) + s.keywords).any { it.contains("codec", ignoreCase = true) } })
        assertTrue(settingsBySection("zzzz").isEmpty())
        assertEquals(Settings.all.size, settingsBySection("  ").sumOf { it.second.size })
    }

    @Test fun setRawWritesDecodesAndPersistsToTheGlobalLayer() {
        assertTrue(settings.setRaw(Settings.audioOnly, "true"))
        assertEquals("true", settings.rawValue(Settings.audioOnly))
        assertTrue(settings.setRaw(Settings.maxQualityMetered, "P480"))
        assertEquals(MaxQuality.P480, settings.resolve(Settings.maxQualityMetered))
        assertTrue(settings.setRaw(Settings.ytDlpPath, "/opt/yt-dlp"))
        assertEquals("/opt/yt-dlp", settings.resolve(Settings.ytDlpPath))
    }

    @Test fun invalidValuesAreRejectedAndWriteNothing() {
        assertFalse(settings.setRaw(Settings.audioOnly, "maybe"))
        assertFalse(settings.setRaw(Settings.maxQualityMetered, "P999"))
        assertFalse(settings.setRaw(Settings.defaultPlaybackSpeed, "9.0"), "above the range")
        assertFalse(settings.setRaw(Settings.defaultPlaybackSpeed, "fast"))
        assertFalse(settings.isOverridden(Settings.audioOnly))
        assertEquals(MaxQuality.P720, settings.resolve(Settings.maxQualityMetered))
    }

    @Test fun rangeBoundsAreInclusive() {
        assertTrue(settings.setRaw(Settings.defaultPlaybackSpeed, "0.25"))
        assertTrue(settings.setRaw(Settings.defaultPlaybackSpeed, "4.0"))
        assertEquals(4f, settings.resolve(Settings.defaultPlaybackSpeed))
    }

    @Test fun resetReturnsToTheDefaultAndClearsTheOverride() {
        settings.setRaw(Settings.maxQualityMetered, "P360")
        assertTrue(settings.isOverridden(Settings.maxQualityMetered))
        settings.resetToDefault(Settings.maxQualityMetered)
        assertFalse(settings.isOverridden(Settings.maxQualityMetered))
        assertEquals(MaxQuality.P720, settings.resolve(Settings.maxQualityMetered))
        assertEquals("P720", settings.rawValue(Settings.maxQualityMetered))
    }

    @Test fun resetOnlyTouchesTheGlobalLayer() {
        settings.set(Settings.maxQualityMetered, MaxQuality.P360, SettingLayer.channel("UC1"))
        settings.setRaw(Settings.maxQualityMetered, "P480")
        settings.resetToDefault(Settings.maxQualityMetered)
        assertEquals(MaxQuality.P360, settings.resolve(Settings.maxQualityMetered, channelId = "UC1"))
    }
}
