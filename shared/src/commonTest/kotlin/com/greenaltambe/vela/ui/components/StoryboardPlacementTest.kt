package com.greenaltambe.vela.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The crash seen on a phone while scrubbing a long video: a sheet still loading reported a NaN size and the draw threw. */
class StoryboardPlacementTest {
    @Test fun aSheetThatReportsNoSizeDrawsNothingInsteadOfCrashing() {
        assertNull(storyboardPlacement(140f, 78f, 160, 90, 1, 1, Float.NaN, Float.NaN))
        assertNull(storyboardPlacement(140f, 78f, 160, 90, 1, 1, Float.POSITIVE_INFINITY, 450f))
        assertNull(storyboardPlacement(140f, 78f, 160, 90, 1, 1, 0f, 0f))
        assertNull(storyboardPlacement(Float.NaN, 78f, 160, 90, 1, 1, 800f, 450f))
        assertNull(storyboardPlacement(0f, 0f, 160, 90, 1, 1, 800f, 450f))
        assertNull(storyboardPlacement(140f, 78f, 0, 90, 1, 1, 800f, 450f))
        assertNull(storyboardPlacement(140f, 78f, 160, 90, -1, 0, 800f, 450f))
    }

    @Test fun theFirstCellFillsTheCanvasFromItsTopLeft() {
        val p = assertNotNull(storyboardPlacement(160f, 90f, 160, 90, 0, 0, 800f, 450f))
        assertEquals(0f, p.left); assertEquals(0f, p.top); assertEquals(800f, p.width); assertEquals(450f, p.height)
    }

    @Test fun laterCellsMoveTheSheetLeftAndUpByWholeCells() {
        val p = assertNotNull(storyboardPlacement(160f, 90f, 160, 90, 2, 1, 800f, 450f))
        assertEquals(-320f, p.left); assertEquals(-90f, p.top)
    }

    @Test fun aCanvasThatIsWiderThanTheFrameScalesUpAndCrops() {
        val p = assertNotNull(storyboardPlacement(320f, 90f, 160, 90, 0, 0, 800f, 450f))
        assertEquals(1600f, p.width, 0.01f)
        assertEquals(-45f, p.top, 0.01f)
    }
}
