package com.greenaltambe.vela.ui

import com.greenaltambe.vela.ui.debug.FrameStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameStatsTest {
    @Test fun countsJankAgainstTheBudgetAndIgnoresIdleGaps() {
        val s = FrameStats(budgetMs = 11.1)
        repeat(90) { s.record(8.0) }
        repeat(8) { s.record(14.0) }
        s.record(130.0); s.record(5000.0)   // the 5 s gap is idle time, not a frame
        val r = s.summary()
        assertEquals(99, r.frames); assertEquals(9, (r.jankyPercent).toInt())
        assertEquals(8.0, r.p50); assertEquals(1, r.over100ms); assertTrue(r.worst == 130.0)
    }

    @Test fun anEmptySummaryIsAllZeros() = assertEquals(0, FrameStats().summary().frames)
}
