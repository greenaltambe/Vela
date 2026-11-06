package com.greenaltambe.vela.ui.player

import com.greenaltambe.vela.library.Cue
import com.greenaltambe.vela.ui.screens.dedupeCues
import com.greenaltambe.vela.ui.screens.groupedCount
import com.greenaltambe.vela.ui.screens.publishedParts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class PlayerControlsTest {
    @Test fun speedLabelsHaveNoTrailingZeros() {
        assertEquals("1×", formatSpeed(1f))
        assertEquals("2×", formatSpeed(2f))
        assertEquals("0.9×", formatSpeed(0.9f))
        assertEquals("1.25×", formatSpeed(1.25f))
        assertEquals("0.05×", formatSpeed(0.05f))
        assertEquals("4×", formatSpeed(4f))
    }

    @Test fun everySpeedChipIsInRangeAndSorted() {
        assertTrue(SpeedChoices.all { it in SPEED_MIN..SPEED_MAX })
        assertEquals(SpeedChoices.sorted(), SpeedChoices)
        assertEquals(SpeedChoices.size, SpeedChoices.toSet().size)
        assertTrue(1f in SpeedChoices && 2f in SpeedChoices && SPEED_MAX in SpeedChoices)
    }

    @Test fun viewCountsAreGroupedByThousands() {
        assertEquals("340,925", groupedCount(340_925))
        assertEquals("23,822", groupedCount(23_822))
        assertEquals("999", groupedCount(999))
        assertEquals("1,000,000", groupedCount(1_000_000))
        assertEquals("0", groupedCount(0))
    }

    @Test fun publishedDateIsMonthDayOverYear() {
        // 2026-11-03 00:00 UTC
        assertEquals("Nov 3" to "2026", publishedParts(Instant.fromEpochSeconds(1_793_664_000)))
        assertEquals("Jan 1" to "1970", publishedParts(Instant.fromEpochSeconds(0)))
        assertEquals("Feb 29" to "2024", publishedParts(Instant.fromEpochSeconds(1_709_164_800)))
        assertEquals("Dec 31" to "1999", publishedParts(Instant.fromEpochSeconds(946_598_400)))
    }

    @Test fun repeatedCaptionLinesCollapseToTheFirst() {
        val cues = listOf(Cue(0, 1, "a"), Cue(1, 2, "a "), Cue(2, 3, "b"), Cue(3, 4, "b"), Cue(4, 5, "a"))
        assertEquals(listOf("a", "b", "a"), dedupeCues(cues).map { it.text.trim() })
        assertEquals(listOf(0L, 2L, 4L), dedupeCues(cues).map { it.startMs })
        assertEquals(emptyList(), dedupeCues(emptyList()))
    }
}
