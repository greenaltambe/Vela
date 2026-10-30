@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.core.settings.IconStyle
import com.greenaltambe.vela.core.settings.IconWeight
import com.greenaltambe.vela.ui.look.IconTokens
import com.greenaltambe.vela.ui.look.VelaIcons
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IconPacksTest {
    private fun paths(v: ImageVector): List<VectorPath> {
        val out = mutableListOf<VectorPath>()
        fun walk(g: VectorGroup) { g.forEach { n -> if (n is VectorPath) out += n else if (n is VectorGroup) walk(n) } }
        walk(v.root); return out
    }

    private val packs = IconStyle.entries.flatMap { style -> IconWeight.entries.map { w -> IconTokens(style, w, rounded = true) } } + IconTokens(IconStyle.LINE, IconWeight.REGULAR, rounded = false)

    @Test fun everyIconExistsInEveryPackWeightAndEndStyle() {
        val names = VelaIcons.forTokens(IconTokens()).all.map { it.first }
        assertEquals(com.greenaltambe.vela.ui.look.IconNames.all, names)
        assertTrue(names.size >= 50, "the set is comprehensive (${names.size})")
        for (t in packs) assertEquals(names, VelaIcons.forTokens(t).all.map { it.first }, "pack $t")
    }

    /** Vela's own packs are drawn here, so their margins are ours to check; the ready-made ones come as designed. */
    private val velaPacks = packs.filter { it.style in listOf(IconStyle.FILLED, IconStyle.LINE, IconStyle.DUOTONE) }

    @Test fun everyIconSitsInsideTheGridWithMarginAndDrawsSomething() {
        for (t in packs) for ((name, v) in VelaIcons.forTokens(t).all) {
            val ps = paths(v); assertTrue(ps.isNotEmpty(), "$name has no paths ($t)")
            val b = ps.map { it.pathData.toPath().getBounds() }.reduce { a, c -> androidx.compose.ui.geometry.Rect(minOf(a.left, c.left), minOf(a.top, c.top), maxOf(a.right, c.right), maxOf(a.bottom, c.bottom)) }
            // The line icons keep their centre lines at least 2.5 units from the edge, so even the bold stroke (2.1) stays on the grid.
            if (t !in velaPacks) continue
            val margin = if (t.style == IconStyle.FILLED) 0.5f else 2.5f
            assertTrue(b.left >= margin - 0.01f && b.top >= margin - 0.01f && b.right <= 24f - margin + 0.01f && b.bottom <= 24f - margin + 0.01f, "$name ($t) bounds $b outside the grid margin")
            assertTrue(b.width >= 8f || b.height >= 8f, "$name ($t) is tiny: $b")
        }
    }

    @Test fun linePacksAreStrokesWithSolidFillOnlyOnDotsAndDuotoneAddsASoftFill() {
        for (w in IconWeight.entries) {
            val line = VelaIcons.forTokens(IconTokens(IconStyle.LINE, w, true)); val duo = VelaIcons.forTokens(IconTokens(IconStyle.DUOTONE, w, true))
            for ((name, v) in line.all) for (p in paths(v)) {
                if (p.fill != null) assertTrue(p.stroke == null, "$name: only dots are filled in the Line pack")
                else { assertTrue(p.stroke != null, "$name has a stroke"); assertEquals(VelaIcons.strokeWidth(w), p.strokeLineWidth, "$name stroke follows the weight") }
            }
            val soft = duo.all.sumOf { (_, v) -> paths(v).count { it.fill != null && it.stroke == null && it.fillAlpha < 1f } }
            assertTrue(soft >= 25, "Duotone has soft fills inside closed shapes ($soft)")
            for ((name, v) in duo.all) for (p in paths(v)) if (p.stroke != null) assertTrue(p.fill == null, "$name: the stroke itself is never filled")
        }
        // Thin is finer than regular is finer than bold.
        assertTrue(VelaIcons.strokeWidth(IconWeight.THIN) < VelaIcons.strokeWidth(IconWeight.REGULAR) && VelaIcons.strokeWidth(IconWeight.REGULAR) < VelaIcons.strokeWidth(IconWeight.BOLD))
    }

    @Test fun roundedEndsAreAChoiceOfTheLinePacksOnly() {
        val round = paths(VelaIcons.forTokens(IconTokens(IconStyle.LINE, IconWeight.REGULAR, true)).search).first { it.stroke != null }
        val square = paths(VelaIcons.forTokens(IconTokens(IconStyle.LINE, IconWeight.REGULAR, false)).search).first { it.stroke != null }
        assertTrue(round.strokeLineCap != square.strokeLineCap && round.strokeLineJoin != square.strokeLineJoin)
    }

    /** Contact sheets (3x) of the three packs, into docs/reports/23-screens/, for looking at the drawing. */
    @OptIn(ExperimentalLayoutApi::class)
    @Test fun contactSheets() {
        val out = screensDir("23-screens") ?: return
        for ((label, style) in listOf("filled" to IconStyle.FILLED, "line" to IconStyle.LINE, "duotone" to IconStyle.DUOTONE, "rounded" to IconStyle.ROUNDED, "outlined" to IconStyle.OUTLINED, "sharp" to IconStyle.SHARP, "twotone" to IconStyle.TWO_TONE, "lucide" to IconStyle.LUCIDE)) for (weight in listOf(IconWeight.THIN, IconWeight.REGULAR)) {
            if ((style == IconStyle.FILLED || style !in listOf(IconStyle.LINE, IconStyle.DUOTONE)) && weight == IconWeight.THIN) continue
            runDesktopComposeUiTest(900, 760) {
                val set = VelaIcons.forTokens(IconTokens(style, weight, true))
                setContent {
                    Column(Modifier.background(Color(0xFFFFFFFF)).padding(12.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            set.all.forEach { (_, v) -> Image(v, null, Modifier.size(72.dp), colorFilter = ColorFilter.tint(Color(0xFF111111))) }
                        }
                        // And at the real size next to each other, as they appear in the app.
                        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { set.all.forEach { (_, v) -> Image(v, null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(Color(0xFF111111))) } }
                    }
                }
                waitForIdle()
                val image = onRoot().captureToImage()
                val data = org.jetbrains.skia.Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)
                if (data != null) File(out, "icons-$label-${weight.name.lowercase()}.png").writeBytes(data.bytes)
                // Every icon puts ink on the page: the sheet is not blank.
                val px = image.asSkiaBitmap(); var dark = 0
                for (x in 0 until px.width step 3) for (y in 0 until px.height step 3) if ((px.getColor(x, y) and 0xFF) < 0x80) dark++
                assertTrue(dark > 600, "$label/$weight sheet is nearly blank ($dark)")
            }
        }
    }
}
