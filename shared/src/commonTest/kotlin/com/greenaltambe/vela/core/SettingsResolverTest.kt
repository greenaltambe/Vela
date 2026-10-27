package com.greenaltambe.vela.core

import com.greenaltambe.vela.core.settings.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SettingsResolverTest {
    private val store = InMemorySettingsStore()
    private val resolver = SettingsResolver(store)

    @Test
    fun fallsBackToDefault() {
        assertEquals(ShortsMode.SHOW, resolver.resolve(Settings.shortsMode, channelId = "c", videoId = "v"))
    }

    @Test
    fun channelOverridesGlobal() {
        resolver.set(Settings.shortsMode, ShortsMode.HIDE)
        resolver.set(Settings.shortsMode, ShortsMode.SEPARATE_TAB, SettingLayer.channel("c1"))
        assertEquals(ShortsMode.SEPARATE_TAB, resolver.resolve(Settings.shortsMode, channelId = "c1"))
        assertEquals(ShortsMode.HIDE, resolver.resolve(Settings.shortsMode, channelId = "c2"))
        assertEquals(ShortsMode.HIDE, resolver.resolve(Settings.shortsMode))
    }

    @Test
    fun videoOverridesChannelOverridesGlobal() {
        resolver.set(Settings.defaultPlaybackSpeed, 1.25f)
        resolver.set(Settings.defaultPlaybackSpeed, 1.5f, SettingLayer.channel("c"))
        resolver.set(Settings.defaultPlaybackSpeed, 2f, SettingLayer.video("v"))
        assertEquals(2f, resolver.resolve(Settings.defaultPlaybackSpeed, channelId = "c", videoId = "v"))
        assertEquals(1.5f, resolver.resolve(Settings.defaultPlaybackSpeed, channelId = "c", videoId = "other"))
        assertEquals(1.25f, resolver.resolve(Settings.defaultPlaybackSpeed, channelId = "x", videoId = "y"))
    }

    @Test
    fun clearingAnOverrideRevealsTheLayerBelow() {
        resolver.set(Settings.defaultPlaybackSpeed, 1.25f)
        resolver.set(Settings.defaultPlaybackSpeed, 2f, SettingLayer.video("v"))
        resolver.clear(Settings.defaultPlaybackSpeed, SettingLayer.video("v"))
        assertEquals(1.25f, resolver.resolve(Settings.defaultPlaybackSpeed, videoId = "v"))
    }

    @Test
    fun scopeLimitsWhichLayersApply() {
        assertFailsWith<IllegalArgumentException> {
            resolver.set(Settings.accountFallbackMode, AccountFallbackMode.NEVER, SettingLayer.channel("c"))
        }
        // Even a stray value in a disallowed layer is ignored on read.
        store.put(Settings.shortsMode.key, SettingLayer.video("v"), ShortsMode.HIDE.name)
        assertEquals(ShortsMode.SHOW, resolver.resolve(Settings.shortsMode, videoId = "v"))
    }

    @Test
    fun undecodableStoredValueFallsThrough() {
        resolver.set(Settings.shortsMode, ShortsMode.HIDE)
        store.put(Settings.shortsMode.key, SettingLayer.channel("c"), "garbage")
        assertEquals(ShortsMode.HIDE, resolver.resolve(Settings.shortsMode, channelId = "c"))
    }

    @Test
    fun keysAreUnique() {
        assertEquals(Settings.all.size, Settings.all.map { it.key }.toSet().size)
    }
}
