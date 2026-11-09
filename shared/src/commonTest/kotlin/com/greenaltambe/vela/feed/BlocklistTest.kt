package com.greenaltambe.vela.feed

import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.extractor.VideoSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlocklistTest {
    private fun v(id: String, title: String, ch: String) = VideoSummary(id, title, ch, "Chan $ch")
    private fun list() = Blocklist(InMemorySettingsStore())

    @Test fun aBlockedChannelAndABlockedWordBothHideAVideo() {
        val r = BlockRules(setOf("UC1"), listOf("reaction"))
        assertTrue(r.blocks(v("a", "Fine", "UC1")))
        assertTrue(r.blocks(v("b", "My REACTION video", "UC2")), "words ignore case")
        assertFalse(r.blocks(v("c", "Fine", "UC2")))
    }

    @Test fun blankEntriesNeverBlockEverything() {
        val r = BlockRules(setOf("", " "), listOf("", "  "))
        assertTrue(r.isEmpty)
        assertFalse(r.blocks(v("a", "Anything", "")), "a video with no channel id is not matched by a blank id")
    }

    @Test fun channelsAndWordsSurviveInTheStoreWithoutDuplicates() {
        val l = list()
        assertTrue(l.blockChannel("UC1", "One")); assertFalse(l.blockChannel("UC1", "One again"))
        assertTrue(l.addKeyword("Reaction")); assertFalse(l.addKeyword("reaction")); assertFalse(l.addKeyword("   "))
        assertEquals(listOf(BlockedChannel("UC1", "One")), l.channels())
        assertEquals(listOf("Reaction"), l.keywords())
        l.unblockChannel("UC1"); l.removeKeyword("REACTION")
        assertTrue(l.rules().isEmpty)
    }

    @Test fun namesWithTabsAndNewlinesDoNotBreakTheStorage() {
        val l = list()
        l.blockChannel("UC1", "Odd\tname\nwith breaks")
        assertEquals("Odd name with breaks", l.channels().single().name)
    }

    @Test fun exportThenImportRebuildsTheSameListAndCountsOnlyNewEntries() {
        val a = list(); a.blockChannel("UC1", "One"); a.addKeyword("clickbait"); a.addKeyword("top 10")
        val text = a.export()
        val b = list()
        assertEquals(3, b.import(text))
        assertEquals(a.channels(), b.channels()); assertEquals(a.keywords(), b.keywords())
        assertEquals(0, b.import(text), "importing twice adds nothing")
    }

    @Test fun importAlsoTakesPlainWordsOneToALineAndSkipsComments() {
        val l = list()
        assertEquals(2, l.import("# my list\nshorts compilation\n\nprank\n"))
        assertEquals(listOf("shorts compilation", "prank"), l.keywords())
    }

    @Test fun everyEditChangesTheRevisionSoListsRefilter() {
        val l = list(); val r0 = l.revision.value
        l.addKeyword("x"); l.blockChannel("UC1", "One"); l.unblockChannel("UC1")
        assertEquals(r0 + 3, l.revision.value)
    }

    @Test fun theFeedStepDropsBlockedVideosAndKeepsOrder() {
        val items = listOf(v("a", "Fine", "UC1"), v("b", "Prank!", "UC2"), v("c", "Fine two", "UC3"))
        val out = BlocklistStep(BlockRules(setOf("UC1"), listOf("prank"))).apply(items, FeedContext(SettingsResolver(InMemorySettingsStore())))
        assertEquals(listOf("c"), out.map { it.id })
    }

    @Test fun anEmptyBlocklistLeavesTheFeedAlone() {
        val items = listOf(v("a", "Fine", "UC1"))
        assertEquals(items, BlocklistStep(BlockRules.None).apply(items, FeedContext(SettingsResolver(InMemorySettingsStore()))))
    }
}
