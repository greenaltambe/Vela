package com.greenaltambe.vela.ui.platform

import kotlin.test.Test
import kotlin.test.assertEquals

/** The brightness / volume swipe: up raises, down lowers, the range is covered by 80% of the height, and it stops at both ends. */
class SwipedLevelTest {
    @Test fun upRaisesAndDownLowers() {
        assertEquals(0.6f, swipedLevel(0.5f, -8f, 100f, travel = 0.8f), 0.001f)
        assertEquals(0.4f, swipedLevel(0.5f, 8f, 100f, travel = 0.8f), 0.001f)
    }
    @Test fun aSwipeOverTheTravelledHeightCoversTheWholeRange() {
        assertEquals(1f, swipedLevel(0f, -80f, 100f, travel = 0.8f), 0.001f)
        assertEquals(0f, swipedLevel(1f, 80f, 100f, travel = 0.8f), 0.001f)
    }
    @Test fun itStopsAtBothEnds() {
        assertEquals(1f, swipedLevel(0.9f, -500f, 100f))
        assertEquals(0f, swipedLevel(0.1f, 500f, 100f))
    }
    @Test fun anEmptySurfaceChangesNothing() = assertEquals(0.3f, swipedLevel(0.3f, -50f, 0f))

    @Test fun pastTheBottomEndTheBrightnessGoesBackToAuto() {
        assertEquals(null, brightnessTarget(-0.2f))
        assertEquals(0f, brightnessTarget(-0.05f), "a little past the end still holds the minimum")
        assertEquals(0.5f, brightnessTarget(0.5f))
        assertEquals(1f, brightnessTarget(3f))
    }
    @Test fun theRawLevelIsNotClamped() = assertEquals(-0.5f, swipedRaw(0f, 40f, 100f, travel = 0.8f), 0.001f)
}
