@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.ui.nav.frostedArea
import com.greenaltambe.vela.ui.nav.frostedBackdrop
import com.greenaltambe.vela.ui.nav.recordsBackdrop
import com.greenaltambe.vela.ui.nav.rememberBarBackdrop
import org.junit.Test
import kotlin.test.assertTrue

/** The machinery behind the translucent bar, on its own: black and white stripes under a bar come out as grey (blurred), not as stripes, and the page itself is untouched. */
class FrostedBarUiTest {
    private fun gray(c: Color) = (c.red + c.green + c.blue) / 3f

    @Test fun stripesUnderAFrostedBarAreBlurredAndTheRestOfThePageIsNot() = runDesktopComposeUiTest(200, 200) {
        setContent {
            val backdrop = rememberBarBackdrop()
            Box(Modifier.size(200.dp)) {
                Box(Modifier.fillMaxSize().recordsBackdrop(backdrop).testTag("page")) {
                    Canvas(Modifier.fillMaxSize()) { for (i in 0 until (size.width / 6).toInt()) drawRect(if (i % 2 == 0) Color.Black else Color.White, Offset(i * 6f, 0f), Size(6f, size.height)) }
                }
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(60.dp).frostedBackdrop(backdrop, Color.Transparent, Color.Transparent, Color.Transparent, 12.dp).testTag("bar"))
            }
        }
        waitForIdle()
        val img = onNodeWithTag("page").captureToImage().toPixelMap()
        val h = img.height
        // Above the bar: pure stripes, so the pixels are near black or near white.
        val above = (0 until img.width).map { gray(img[it, h / 4]) }
        assertTrue(above.count { it < 0.15f || it > 0.85f } > above.size * 0.9, "the page above the bar still shows its stripes")
        // Right above the bar (inside where a blur would spread to): still the sharp stripes. A blurred halo there is the artifact this guards against.
        val justAbove = (0 until img.width).map { gray(img[it, h - 60 - 6]) }
        assertTrue(justAbove.count { it < 0.15f || it > 0.85f } > justAbove.size * 0.9, "no blurred halo above the bar: ${justAbove.take(12).map { "%.2f".format(it) }}")
        // Under the bar: blurred, so the same kind of row is grey (the stripes blended).
        val under = (20 until img.width - 20).map { gray(img[it, h - 30]) }
        assertTrue(under.count { it in 0.25f..0.75f } > under.size * 0.8, "behind the bar the stripes are blurred to grey: ${under.take(12).map { "%.2f".format(it) }}")
    }

    /** The strip a swiped-away mini player leaves behind fades with it: the faded part of the area shows the page sharp again, the rest stays frosted. */
    @Test fun theFadedPartOfAFrostedAreaShowsThePageAgainAndTheRestStaysBlurred() = runDesktopComposeUiTest(200, 200) {
        val fade = androidx.compose.runtime.mutableFloatStateOf(1f)   // snapshot state, like the real swipe
        setContent {
            val backdrop = rememberBarBackdrop()
            Box(Modifier.size(200.dp)) {
                Box(Modifier.fillMaxSize().recordsBackdrop(backdrop).testTag("page")) {
                    Canvas(Modifier.fillMaxSize()) { for (i in 0 until (size.width / 6).toInt()) drawRect(if (i % 2 == 0) Color.Black else Color.White, Offset(i * 6f, 0f), Size(6f, size.height)) }
                }
                // A 100 dp area at the bottom whose top half (the "mini player strip") fades with `fade`.
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(100.dp).frostedArea(backdrop, Color(0.5f, 0.5f, 0.5f, 0f), edge = null, edgeAtBottom = false, fade = { fade.floatValue }, fadeFraction = { 0.5f }))
            }
        }
        waitForIdle()
        fun rows(): Pair<List<Float>, List<Float>> {
            val img = onNodeWithTag("page").captureToImage().toPixelMap()
            val h = img.height
            return (20 until img.width - 20).map { gray(img[it, h - 90]) } to (20 until img.width - 20).map { gray(img[it, h - 20]) }
        }
        fun sharp(v: List<Float>) = v.count { it < 0.15f || it > 0.85f } > v.size * 0.9
        fun blurred(v: List<Float>) = v.count { it in 0.25f..0.75f } > v.size * 0.8
        val (topFull, bottomFull) = rows()
        assertTrue(blurred(topFull) && blurred(bottomFull), "not fading: both halves are frosted")
        fade.floatValue = 0f; waitForIdle()
        val (topGone, bottomStill) = rows()
        assertTrue(sharp(topGone), "the faded top half shows the page's stripes again: ${topGone.take(10).map { "%.2f".format(it) }}")
        assertTrue(blurred(bottomStill), "the other half stays frosted")
    }
}
