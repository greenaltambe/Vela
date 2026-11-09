package com.greenaltambe.vela.ui

import androidx.compose.ui.graphics.Color
import com.greenaltambe.vela.ui.components.MarkerSpan
import com.greenaltambe.vela.ui.components.ScrubGeometry
import com.greenaltambe.vela.ui.components.ScrubMarker
import com.greenaltambe.vela.ui.components.markerSpans
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Task 85, item 2: where SponsorBlock markers lie relative to the handle. The bar is 400 px wide with a 0 px inset, so fraction f is at x = 400 f. */
class MarkerSpansTest {
    private val g = ScrubGeometry(400f, 0f)
    private val c = Color.Red
    private fun m(a: Float, b: Float) = ScrubMarker(a, b, c)
    private fun span(a: Float, b: Float) = MarkerSpan(a, b, c)

    /** Spans equal within a tenth of a pixel (the geometry is float arithmetic). */
    private fun assertSpans(expected: List<MarkerSpan>, actual: List<MarkerSpan>) {
        assertEquals(expected.size, actual.size, "$actual")
        expected.zip(actual).forEach { (e, a) -> assertEquals(e.x0, a.x0, 0.1f); assertEquals(e.x1, a.x1, 0.1f); assertEquals(e.color, a.color) }
    }

    @Test fun aMarkerWhollyPlayedIsOnlyInThePlayedPart() {
        val s = markerSpans(listOf(m(0.1f, 0.3f)), g, xNow = 300f, handleGap = 6f)
        assertSpans(listOf(span(40f, 120f)), s.played)
        assertTrue(s.unplayed.isEmpty())
    }

    @Test fun aMarkerWhollyUnplayedIsOnlyInTheFlatPart() {
        val s = markerSpans(listOf(m(0.8f, 0.9f)), g, xNow = 100f, handleGap = 6f)
        assertTrue(s.played.isEmpty())
        assertSpans(listOf(span(320f, 360f)), s.unplayed)
    }

    @Test fun aMarkerThatStraddlesTheHandleIsSplitAroundTheHandleGap() {
        val s = markerSpans(listOf(m(0.4f, 0.6f)), g, xNow = 200f, handleGap = 6f)   // marker x 160..240; handle gap 194..206
        assertSpans(listOf(span(160f, 194f)), s.played)
        assertSpans(listOf(span(206f, 240f)), s.unplayed)
    }

    @Test fun aTinyPlayedMarkerKeepsItsTrueWidthAndATinyUnplayedOneGetsTheMinimum() {
        val played = markerSpans(listOf(m(0.1f, 0.1025f)), g, xNow = 300f, handleGap = 0f, minFlatWidth = 9f)
        assertEquals(1f, played.played.single().let { it.x1 - it.x0 }, 1e-3f, "no minimum inside the wave")
        val flat = markerSpans(listOf(m(0.8f, 0.8025f)), g, xNow = 100f, handleGap = 0f, minFlatWidth = 9f)
        assertEquals(9f, flat.unplayed.single().let { it.x1 - it.x0 }, 1e-3f, "the minimum applies on the flat part")
    }

    @Test fun aMarkerAcrossAChapterGapIsCutAtTheGap() {
        val segments = listOf(0f..199f, 201f..400f)
        val s = markerSpans(listOf(m(0.4f, 0.6f)), g, xNow = 400f, handleGap = 0f, segments = segments)   // marker x 160..240, wholly played
        assertSpans(listOf(span(160f, 199f), span(201f, 240f)), s.played)
    }

    @Test fun markersAtTheVeryStartAndEndStayInsideTheTrack() {
        val s = markerSpans(listOf(m(0f, 0.05f), m(0.95f, 1f)), g, xNow = 200f, handleGap = 6f, minFlatWidth = 3f)
        assertSpans(listOf(span(0f, 20f)), s.played)
        assertSpans(listOf(span(380f, 400f)), s.unplayed)
    }
}
