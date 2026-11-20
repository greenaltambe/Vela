package com.greenaltambe.vela.feed

import com.greenaltambe.vela.core.settings.*
import com.greenaltambe.vela.extractor.VideoSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class HideMembersOnlyTest {
    private val resolver = SettingsResolver(InMemorySettingsStore())
    private val context = FeedContext(resolver)
    private val items = listOf(
        VideoSummary("a", "t", "c", "n"), VideoSummary("m", "t", "c", "n", isMembersOnly = true), VideoSummary("m2", "t", "other", "n", isMembersOnly = true),
    )

    @Test fun offKeepsEverything() = assertEquals(listOf("a", "m", "m2"), FeedPipeline(listOf(HideMembersOnly)).run(items, context).map { it.id })

    @Test fun onDropsMembersOnly() {
        resolver.set(Settings.hideMembersOnly, true)
        assertEquals(listOf("a"), FeedPipeline(listOf(HideMembersOnly)).run(items, context).map { it.id })
    }

    @Test fun perChannelOverrideKeepsThatChannel() {
        resolver.set(Settings.hideMembersOnly, true)
        resolver.set(Settings.hideMembersOnly, false, SettingLayer.channel("other"))
        assertEquals(listOf("a", "m2"), FeedPipeline(listOf(HideMembersOnly)).run(items, context).map { it.id })
    }
}
