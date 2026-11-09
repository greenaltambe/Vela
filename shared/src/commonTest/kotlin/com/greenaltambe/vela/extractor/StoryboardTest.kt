package com.greenaltambe.vela.extractor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Numbers as measured live (2026-11): a 3:36 video came with 160x90 frames, 108 of them, one every 2 s, in 5 pages of 5x5. */
class StoryboardTest {
    private val sb = Storyboard(listOf("p0", "p1", "p2", "p3", "p4"), 160, 90, totalFrames = 108, intervalMs = 2000, columns = 5, rows = 5)

    @Test fun theFirstFrameIsTheTopLeftCellOfTheFirstPage() = assertEquals(StoryboardFrame("p0", 0, 0), sb.frameAt(0))

    @Test fun framesAreReadLeftToRightThenDownThenOnToTheNextPage() {
        assertEquals(StoryboardFrame("p0", 2, 0), sb.frameAt(5_000))       // index 2
        assertEquals(StoryboardFrame("p0", 0, 1), sb.frameAt(10_000))      // index 5: second row
        assertEquals(StoryboardFrame("p1", 0, 0), sb.frameAt(50_000))      // index 25: next page
        assertEquals(StoryboardFrame("p1", 4, 4), sb.frameAt(98_000))      // index 49: last cell of page 2
    }

    @Test fun thePositionRoundsDownToTheFrameBeforeIt() = assertEquals(sb.frameAt(4_000), sb.frameAt(5_999))

    @Test fun pastTheEndAndBeforeTheStartClampToTheEnds() {
        assertEquals(StoryboardFrame("p4", 2, 1), sb.frameAt(10_000_000))  // index 107 = page 4, cell 7 = column 2, row 1
        assertEquals(sb.frameAt(0), sb.frameAt(-5_000))
    }

    @Test fun aStoryboardThatDoesNotAddUpGivesNothingInsteadOfCrashing() {
        assertNull(sb.copy(pageUrls = emptyList()).frameAt(0))
        assertNull(sb.copy(intervalMs = 0).frameAt(0))
        assertNull(sb.copy(columns = 0).frameAt(0))
        assertNull(sb.copy(totalFrames = 0).frameAt(0))
    }

    @Test fun fewerPagesThanTheCountPromisesUsesTheLastPageInsteadOfCrashing() {
        val short = sb.copy(pageUrls = listOf("p0", "p1"))
        assertEquals("p1", short.frameAt(10_000_000)?.url)
    }
}
