package com.greenaltambe.vela.ui

import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.ui.components.ScrubGeometry
import com.greenaltambe.vela.ui.components.formatAge
import com.greenaltambe.vela.ui.layout.WindowSizeClass
import com.greenaltambe.vela.ui.layout.gridColumns
import com.greenaltambe.vela.ui.layout.gutterFor
import com.greenaltambe.vela.ui.layout.windowSizeClassFor
import com.greenaltambe.vela.ui.look.SpacingTokens
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class LayoutLogicTest {
    @Test fun windowSizeClassesFollowTheBreakpoints() {
        assertEquals(WindowSizeClass.COMPACT, windowSizeClassFor(320f))
        assertEquals(WindowSizeClass.COMPACT, windowSizeClassFor(599.9f))
        assertEquals(WindowSizeClass.MEDIUM, windowSizeClassFor(600f))
        assertEquals(WindowSizeClass.MEDIUM, windowSizeClassFor(839.9f))
        assertEquals(WindowSizeClass.EXPANDED, windowSizeClassFor(840f))
        assertEquals(WindowSizeClass.EXPANDED, windowSizeClassFor(1920f))
    }

    @Test fun phonesInLandscapeAreMediumNotCompact() = assertEquals(WindowSizeClass.MEDIUM, windowSizeClassFor(800f))

    @Test fun gutterGrowsWithTheWindowAndComesFromTokens() {
        val t = SpacingTokens()
        assertEquals(16f, t.gutterFor(WindowSizeClass.COMPACT))
        assertEquals(24f, t.gutterFor(WindowSizeClass.MEDIUM))
        assertEquals(32f, t.gutterFor(WindowSizeClass.EXPANDED))
        assertEquals(48f, t.minTouchDp)
        assertEquals(12f, t.minTextSp)
        assertEquals(7f, SpacingTokens(gutterCompactDp = 7f).gutterFor(WindowSizeClass.COMPACT))
    }

    @Test fun gridColumnsNeverGoBelowTheMinimumCardWidth() {
        assertEquals(1, gridColumns(288f, 280f, 16f))
        assertEquals(1, gridColumns(100f, 280f, 16f), "never zero")
        assertEquals(2, gridColumns(600f, 280f, 16f))
        assertEquals(3, gridColumns(900f, 280f, 16f))
        assertEquals(4, gridColumns(1200f, 280f, 16f))
        for (w in 300..2000 step 37) {
            val cols = gridColumns(w.toFloat(), 280f, 16f)
            if (cols > 1) assertTrue((w - 16f * (cols - 1)) / cols >= 280f - 0.01f, "$cols columns at ${w}dp make cards narrower than 280")
        }
    }

    @Test fun scrubThumbAndTrackStayInsideTheBox() {
        val g = ScrubGeometry(widthPx = 300f, thumbRadiusPx = 8f)
        assertEquals(8f, g.xFor(0f))
        assertEquals(292f, g.xFor(1f))
        assertTrue(g.xFor(0f) - 8f >= 0f && g.xFor(1f) + 8f <= 300f, "the thumb at either end is wholly inside")
        assertEquals(8f, g.xFor(-5f), "clamped")
        assertEquals(292f, g.xFor(9f), "clamped")
        assertEquals(0.5f, g.fractionAt(150f))
        assertEquals(0f, g.fractionAt(-40f))
        assertEquals(1f, g.fractionAt(999f))
        // Tapping where the thumb is drawn gives back the same fraction: markers and thumb share one mapping.
        for (f in listOf(0f, 0.1f, 0.37f, 0.5f, 0.99f, 1f)) assertEquals(f, g.fractionAt(g.xFor(f)), 1e-5f)
    }

    @Test fun aTrackTooNarrowForItsThumbDegradesInsteadOfCrashing() {
        val g = ScrubGeometry(widthPx = 10f, thumbRadiusPx = 8f)
        assertEquals(0f, g.trackWidth)
        assertEquals(0f, g.fractionAt(5f))
    }

    @Test fun ageIsHumanAndNullWhenUnknown() {
        val now = Instant.fromEpochSeconds(1_800_000_000)
        assertNull(formatAge(null, now))
        assertEquals("just now", formatAge(now - 20.seconds, now))
        assertEquals("5 minutes ago", formatAge(now - 5.minutes, now))
        assertEquals("1 hour ago", formatAge(now - 61.minutes, now))
        assertEquals("3 days ago", formatAge(now - 3.days, now))
        assertEquals("2 months ago", formatAge(now - 65.days, now))
        assertEquals("1 year ago", formatAge(now - 400.days, now))
        assertNull(formatAge(now + 1.hours, now), "a future date is not shown")
    }

    @Test fun replacingTheSearchViewKeepsBackShort() {
        val nav = Navigator()
        nav.push(Route.Search())
        nav.replace(Route.Search("kotlin"))
        nav.replace(Route.Search("kotlin coroutines"))
        assertEquals(Route.Search("kotlin coroutines"), nav.current)
        assertTrue(nav.back())
        assertEquals(Route.Home, nav.current, "one Back from the last query, not one per query typed")
        assertFalse(nav.back())
    }

    @Test fun anEmptySearchRouteHasNoStaleQuery() = assertEquals("", (Route.Search() as Route.Search).query)

    @Test fun videoSummaryCarriesLiveAndShortFlags() {
        val v = VideoSummary("id", "t", "c", "n", isLive = true)
        assertTrue(v.isLive && !v.isShort)
    }
}
