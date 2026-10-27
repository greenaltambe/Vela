package com.greenaltambe.vela.modules

import com.greenaltambe.vela.modules.sponsorblock.Segment
import com.greenaltambe.vela.modules.sponsorblock.SegmentAction
import com.greenaltambe.vela.modules.sponsorblock.SegmentCategory
import com.greenaltambe.vela.modules.sponsorblock.SegmentEffect
import com.greenaltambe.vela.modules.sponsorblock.SegmentEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SegmentEngineTest {
    private fun seg(id: String, cat: String, from: Long, to: Long, type: String = "skip") = Segment(id, cat, type, from, to)

    private val defaults = mapOf(
        SegmentCategory.SPONSOR to SegmentAction.SKIP, SegmentCategory.SELFPROMO to SegmentAction.SKIP,
        SegmentCategory.INTRO to SegmentAction.PROMPT, SegmentCategory.FILLER to SegmentAction.IGNORE,
        SegmentCategory.MUSIC_OFFTOPIC to SegmentAction.MUTE, SegmentCategory.POI_HIGHLIGHT to SegmentAction.PROMPT,
    )

    private fun engine(vararg segments: Segment, min: Long = 0, actions: Map<SegmentCategory, SegmentAction> = defaults) =
        SegmentEngine({ actions[it] ?: SegmentAction.IGNORE }, min).also { it.load(segments.toList()) }

    private fun SegmentEngine.skips(p: Long) = onPosition(p).filterIsInstance<SegmentEffect.Skip>()

    @Test fun enteringASkipSegmentSeeksToItsEnd() {
        val e = engine(seg("a", "sponsor", 10_000, 20_000))
        assertTrue(e.skips(9_999).isEmpty())
        val skip = e.skips(10_100).single()
        assertEquals(20_000, skip.toMs)
        assertEquals(10_000, skip.fromMs)
        assertEquals(listOf("a"), skip.segments.map { it.uuid })
    }

    @Test fun aSeekIntoTheMiddleOfASegmentSkipsToo() {
        val e = engine(seg("a", "sponsor", 10_000, 20_000))
        assertEquals(20_000, e.skips(15_500).single().toMs)
    }

    @Test fun theEndIsExclusiveAndTheStartInclusive() {
        val e = engine(seg("a", "sponsor", 10_000, 20_000))
        assertTrue(e.skips(20_000).isEmpty())
        assertEquals(1, e.skips(10_000).size)
    }

    @Test fun skipOnceThenItPlaysIfYouGoBack() {
        val e = engine(seg("a", "sponsor", 10_000, 20_000))
        assertEquals(1, e.skips(10_500).size)
        assertTrue(e.skips(10_700).isEmpty(), "still reporting a position inside while the seek lands")
        assertTrue(e.skips(12_000).isEmpty(), "the viewer went back to watch it")
    }

    @Test fun undoMarksTheSegmentsSoTheyAreNotSkippedAgain() {
        val e = engine(seg("a", "sponsor", 10_000, 20_000))
        val skip = e.skips(10_500).single()
        e.markHandled(skip.segments.map { it.uuid })
        assertTrue(e.skips(10_000).isEmpty())
    }

    @Test fun overlappingAndTouchingSegmentsAreOneJump() {
        val e = engine(seg("a", "sponsor", 10_000, 20_000), seg("b", "selfpromo", 18_000, 30_000), seg("c", "sponsor", 30_000, 35_000), seg("d", "sponsor", 50_000, 60_000))
        val skip = e.skips(10_200).single()
        assertEquals(35_000, skip.toMs)
        assertEquals(listOf("a", "b", "c"), skip.segments.map { it.uuid })
        assertEquals(60_000L, e.skips(50_100).single().toMs, "a later, separate segment is its own jump")
    }

    @Test fun landingInsideAnotherSkipSegmentChainsOnTheNextPosition() {
        val e = engine(seg("a", "sponsor", 10_000, 20_000), seg("b", "sponsor", 20_500, 30_000))
        assertEquals(20_000, e.skips(10_000).single().toMs)
        assertEquals(30_000, e.skips(20_600).single().toMs)
    }

    @Test fun segmentsShorterThanTheMinimumAreIgnored() {
        val e = engine(seg("short", "sponsor", 10_000, 10_800), seg("long", "sponsor", 20_000, 25_000), min = 1_000)
        assertTrue(e.skips(10_100).isEmpty())
        assertEquals(1, e.skips(20_100).size)
        assertEquals(listOf("long"), e.visible().map { it.uuid })
    }

    @Test fun ignoredCategoriesDoNothingAndAreNotMarked() {
        val e = engine(seg("f", "filler", 5_000, 9_000), seg("x", "unknown_category", 1_000, 2_000))
        assertTrue(e.onPosition(6_000).isEmpty())
        assertTrue(e.visible().isEmpty())
    }

    @Test fun fullVideoLabelsNeverSkip() {
        val e = engine(seg("full", "sponsor", 0, 100_000, type = "full"))
        assertTrue(e.skips(500).isEmpty())
    }

    @Test fun muteSegmentsMuteOnEntryAndUnmuteOnExitWithoutSeeking() {
        val e = engine(seg("m", "music_offtopic", 10_000, 20_000))
        assertTrue(e.onPosition(5_000).isEmpty())
        assertEquals(listOf(SegmentEffect.Mute(true)), e.onPosition(10_500))
        assertTrue(e.onPosition(12_000).isEmpty(), "already muted: no repeat")
        assertEquals(listOf(SegmentEffect.Mute(false)), e.onPosition(20_100))
    }

    @Test fun anApiMuteActionTypeNeverSeeksEvenIfTheCategoryIsSetToSkip() {
        val e = engine(seg("m", "sponsor", 10_000, 20_000, type = "mute"))
        assertEquals(listOf(SegmentEffect.Mute(true)), e.onPosition(10_500))
    }

    @Test fun promptAppearsInsideASegmentAndGoesAwayAfterwards() {
        val e = engine(seg("i", "intro", 0, 30_000))
        assertEquals("i", (e.onPosition(1_000).single() as SegmentEffect.Prompt).segment?.uuid)
        assertTrue(e.onPosition(2_000).isEmpty(), "unchanged: no repeat")
        val gone = e.onPosition(31_000).single()
        assertEquals(null, assertIs<SegmentEffect.Prompt>(gone).segment)
    }

    @Test fun acceptingAPromptStopsOfferingIt() {
        val e = engine(seg("i", "intro", 0, 30_000))
        e.onPosition(1_000)
        e.markHandled(listOf("i"))
        assertEquals(null, (e.onPosition(2_000).single() as SegmentEffect.Prompt).segment)
    }

    @Test fun highlightPointsArePromptedInAWindowAroundThem() {
        val e = engine(seg("h", "poi_highlight", 60_000, 60_000, type = "poi"))
        assertTrue(e.onPosition(40_000).isEmpty())
        assertEquals("h", (e.onPosition(56_000).single() as SegmentEffect.Prompt).segment?.uuid)
        assertEquals(null, (e.onPosition(70_000).single() as SegmentEffect.Prompt).segment)
    }

    @Test fun skipLandingTogetherWithAMuteSegmentEvaluatesTheLandingPoint() {
        val e = engine(seg("s", "sponsor", 10_000, 20_000), seg("m", "music_offtopic", 20_000, 25_000))
        val effects = e.onPosition(10_500)
        assertEquals(20_000, effects.filterIsInstance<SegmentEffect.Skip>().single().toMs)
        assertTrue(SegmentEffect.Mute(true) in effects, "the landing point is inside the mute segment")
    }

    @Test fun reloadingClearsWhatWasHandled() {
        val e = engine(seg("a", "sponsor", 10_000, 20_000))
        e.skips(10_500)
        e.load(listOf(seg("a", "sponsor", 10_000, 20_000)))
        assertEquals(1, e.skips(10_500).size)
    }
}
