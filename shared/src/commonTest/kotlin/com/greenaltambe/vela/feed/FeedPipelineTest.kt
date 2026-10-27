package com.greenaltambe.vela.feed

import com.greenaltambe.vela.core.settings.*
import com.greenaltambe.vela.extractor.VideoSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class FeedPipelineTest {
    private val resolver = SettingsResolver(InMemorySettingsStore())
    private val context = FeedContext(resolver)

    private fun video(id: String, channel: String = "c", short: Boolean = false) =
        VideoSummary(id, "t$id", channel, "name", isShort = short)

    private val items = listOf(video("a"), video("b", short = true), video("c"), video("d", channel = "other", short = true))

    @Test
    fun hideShortsRemovesShortsWhenModeIsHide() {
        resolver.set(Settings.shortsMode, ShortsMode.HIDE)
        assertEquals(listOf("a", "c"), FeedPipeline(listOf(HideShorts)).run(items, context).map { it.id })
    }

    @Test
    fun showModeKeepsEverything() {
        resolver.set(Settings.shortsMode, ShortsMode.SHOW)
        assertEquals(items, FeedPipeline(listOf(HideShorts)).run(items, context))
    }

    @Test
    fun separateTabRemovesShortsFromMainFeed() {
        resolver.set(Settings.shortsMode, ShortsMode.SEPARATE_TAB)
        assertEquals(listOf("a", "c"), FeedPipeline(listOf(HideShorts)).run(items, context).map { it.id })
    }

    @Test
    fun perChannelOverrideIsRespected() {
        resolver.set(Settings.shortsMode, ShortsMode.HIDE)
        resolver.set(Settings.shortsMode, ShortsMode.SHOW, SettingLayer.channel("other"))
        assertEquals(listOf("a", "c", "d"), FeedPipeline(listOf(HideShorts)).run(items, context).map { it.id })
    }

    @Test
    fun stepsRunInOrder() {
        val upper = FeedStep { list, _ -> list.map { it.copy(title = it.title.uppercase()) } }
        val firstOnly = FeedStep { list, _ -> list.take(1) }
        val out = FeedPipeline(listOf(upper, firstOnly)).run(items, context)
        assertEquals(listOf("TA"), out.map { it.title })
    }
}
