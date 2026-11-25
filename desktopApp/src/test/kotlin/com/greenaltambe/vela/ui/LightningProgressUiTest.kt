@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.core.settings.LookSettings
import com.greenaltambe.vela.core.settings.ProgressStyle
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.ui.components.ScrubMarker
import com.greenaltambe.vela.ui.components.drawProgress
import com.greenaltambe.vela.ui.components.rememberProgressPhase
import com.greenaltambe.vela.ui.nav.Route
import com.greenaltambe.vela.ui.theme.AppTheme
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Lightning seek bar: what it draws, how it moves and stops, and that it can be chosen. */
class LightningProgressUiTest {
    private val blue = Color(0xFF0000FF)

    private fun draw(time: Float, shown: Float = 0.6f, markers: List<ScrubMarker> = emptyList(), boldScale: Float = 1f): ImageBitmap {
        val bmp = ImageBitmap(400, 64)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp), Size(400f, 64f)) {
            drawProgress(ProgressStyle.LIGHTNING, shown, listOf(blue, blue), Color.White, Color(0xFF888888), Color(0xFFAAAAAA), Color.Black, markers = markers, wavePhase = time, boldScale = boldScale)
        }
        return bmp
    }

    /** How many pixels in the columns match. */
    private fun count(bmp: ImageBitmap, columns: IntRange, rows: IntRange = 0 until 64, match: (Color) -> Boolean): Int {
        val px = bmp.toPixelMap()
        return columns.sumOf { x -> rows.count { y -> match(px[x, y]) } }
    }

    private fun differing(a: ImageBitmap, b: ImageBitmap): Int {
        val pa = a.toPixelMap(); val pb = b.toPixelMap()
        return (0 until 400).sumOf { x -> (0 until 64).count { y -> pa[x, y] != pb[x, y] } }
    }

    @Test fun theBoltSpreadsAroundThePlayedPartAndTheRestStaysAThinTrack() {
        val bmp = draw(0.5f)
        // More than 6 px off the centre line: strands, forks and glow on the played side, nothing far ahead of the head.
        val offCentre = 0..25
        assertTrue(count(bmp, 40..200, offCentre) { it.alpha > 0.02f } > 100, "nothing strikes out from the played part")
        assertEquals(0, count(bmp, 330..399, offCentre) { it.alpha > 0.02f }, "something is drawn off the track far ahead of the head")
        assertTrue(count(bmp, 330..380) { it.alpha > 0.5f } > 50, "the unplayed track is missing")
    }

    @Test fun theSameMomentDrawsTheSamePictureAndALaterOneAnother() {
        assertEquals(0, differing(draw(1.25f), draw(1.25f)))
        assertTrue(differing(draw(1.25f), draw(1.5f)) > 300, "the bolt did not change")
    }

    @Test fun draggingOverchargesTheBolt() {
        val calm = count(draw(0.5f), 0..399, 0..22) { it.alpha > 0.02f }
        val charged = count(draw(0.5f, boldScale = 1.55f), 0..399, 0..22) { it.alpha > 0.02f }
        assertTrue(charged > calm * 1.2f, "dragging should spread the bolt wider: $calm -> $charged")
    }

    @Test fun sponsorBlockMarkersShowOnBothSidesOfTheHead() {
        val red = Color(0xFFFF0000)
        val bmp = draw(0.5f, markers = listOf(ScrubMarker(0.2f, 0.4f, red), ScrubMarker(0.8f, 0.95f, red)))
        val isRed = { c: Color -> c.alpha > 0.6f && c.red > 0.7f && c.green < 0.4f && c.blue < 0.4f }
        assertTrue(count(bmp, 85..155, match = isRed) > 60, "no marker colour on the bolt")
        assertTrue(count(bmp, 320..370, match = isRed) > 60, "no marker on the flat track")
    }

    @Test fun theEndsOfTheBarDrawWithoutFailing() {
        for (shown in listOf(0f, 0.001f, 1f)) assertTrue(count(draw(0.5f, shown = shown), 0..399) { it.alpha > 0.5f } > 20, "nothing drawn at $shown")
    }

    @Test fun theClockRunsWhilePlayingHoldsWhenPausedAndCarriesOn() = runDesktopComposeUiTest(100, 100) {
        val playing = mutableStateOf(true)
        lateinit var clock: State<Float>
        mainClock.autoAdvance = false
        setContent { AppTheme(SettingsResolver(InMemorySettingsStore())) { clock = rememberProgressPhase(ProgressStyle.LIGHTNING, playing.value) } }
        mainClock.advanceTimeBy(100)
        val first = clock.value
        mainClock.advanceTimeBy(1_000)
        val later = clock.value
        assertTrue(later > first + 0.8f, "the clock did not run: $first -> $later")
        playing.value = false; mainClock.advanceTimeBy(100)
        val held = clock.value
        mainClock.advanceTimeBy(1_000)
        assertEquals(held, clock.value, "a paused bar must not move")
        assertTrue(held >= later, "pausing jumped back: $later -> $held")
        playing.value = true; mainClock.advanceTimeBy(500)
        assertTrue(clock.value > held, "the clock did not carry on: $held -> ${clock.value}")
    }

    /** The height of the white-hot thread in each column, where there is one. */
    private fun thread(bmp: ImageBitmap): FloatArray {
        val px = bmp.toPixelMap()
        return FloatArray(400) { x ->
            var sum = 0f; var weight = 0f
            // The core alone is this close to white; the strands beside it and the filaments carry more of the accent.
            for (y in 4..60) { val c = px[x, y]; if (c.alpha > 0.4f && c.red > 0.42f && c.green > 0.42f) { sum += y * c.red * c.alpha; weight += c.red * c.alpha } }
            if (weight == 0f) Float.NaN else sum / weight
        }
    }

    /** The typical (median) difference in height between two threads, in pixels. */
    private fun apart(a: FloatArray, b: FloatArray): Float = (60..300).mapNotNull { x -> kotlin.math.abs(a[x] - b[x]).takeUnless { it.isNaN() } }.sorted().let { checkNotNull(it.getOrNull(it.size / 2)) { "no bright thread to compare" } }

    @Test fun theArcWrithesInPlaceAndNowAndThenTakesANewPath() {
        // Four seconds at the clock's thirty pictures a second, as in the footage: mostly small steps, with a jump every so often.
        val threads = (0..120).map { thread(draw(it / 30f, shown = 0.9f)) }
        val steps = threads.zipWithNext { a, b -> apart(a, b) }
        // The same ratio as measured on the footage: movement between pictures against the size of the arc's own curls.
        fun curl(t: FloatArray): Float {
            val xs = (60..300).filter { !t[it].isNaN() }
            val mx = xs.average(); val my = xs.sumOf { t[it].toDouble() } / xs.size
            val k = xs.sumOf { (it - mx) * (t[it] - my) } / xs.sumOf { (it - mx) * (it - mx) }
            return kotlin.math.sqrt(xs.sumOf { val r = t[it] - my - k * (it - mx); r * r } / xs.size).toFloat()
        }
        val ratios = threads.zipWithNext { a, b -> apart(a, b) / curl(b) }.sorted()
        assertTrue(ratios[60] in 0.08f..0.22f, "movement per picture should be about a seventh of the curl size, as filmed: ${ratios[60]}")
        val small = steps.count { it < 1.6f }
        val jumps = steps.count { it > 2.5f }
        assertTrue(small >= 80, "the arc should mostly move a little at a time: only $small of ${steps.size} steps are small")
        assertTrue(jumps in 5..30, "the arc should jump to a new path now and then: $jumps jumps in four seconds")
        assertTrue(steps.count { it > 0.15f } >= 100, "the arc should keep moving between the jumps")
    }

    @Test fun lightningCanBeChosenOnTheProgressPage() = runDesktopComposeUiTest(400, 1200) {
        val f = buildFixture(withDownloads = false)
        f.navigator.selectTab(Route.Settings); f.navigator.push(Route.CustomizePage("progress"))
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("progress.LIGHTNING").performScrollTo().performClick(); waitForIdle()
        assertEquals(ProgressStyle.LIGHTNING, f.settings.resolve(LookSettings.progressStyle))
        screenshot("lightning-progress-page")
    }
}
