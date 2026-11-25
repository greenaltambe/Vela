@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.core.settings.LookSettings
import org.junit.Test
import kotlin.test.assertTrue

/** The icons of the bottom bar and the rail take the theme's colours: light on the dark theme, dark on the light one. */
class NavIconColorUiTest {
    /** The brightest and darkest pixel of a tab's icon (the upper part of the item; its name is below). */
    private fun range(tag: String, width: Int, dark: Boolean, animate: Boolean, translucent: Boolean): Pair<Float, Float> {
        var result = 0f to 0f
        runDesktopComposeUiTest(width, 800) {
            val f = buildFixture(withDownloads = false, dark = dark)
            f.settings.set(LookSettings.animateNavIcons, animate)
            f.settings.set(LookSettings.translucentNavBar, translucent)
            f.settings.set(LookSettings.railCollapsed, true) // the folded rail has the icon above the name, as the bar does
            setContent { AppUnderTest(f) }; waitForIdle()
            // A tab that is not the chosen one: no pill behind it.
            val px = onNodeWithTag(tag).captureToImage().toPixelMap()
            var low = 1f; var high = 0f
            // The middle of the item, above its name: where the 24 dp icon is. (Pixels with nothing drawn on them are left out.)
            for (x in px.width / 2 - 10..px.width / 2 + 10) for (y in px.height / 8..px.height / 2) {
                if (px[x, y].alpha < 0.5f) continue
                val l = px[x, y].luminance()
                low = minOf(low, l); high = maxOf(high, l)
            }
            result = low to high
        }
        return result
    }

    private fun check(tag: String, width: Int) {
        for (animate in listOf(true, false)) for (translucent in listOf(false, true)) {
            val (darkLow, darkHigh) = range(tag, width, dark = true, animate, translucent)
            assertTrue(darkHigh > 0.35f, "$tag dark, animate=$animate translucent=$translucent: no light icon, brightest $darkHigh")
            assertTrue(darkLow > 0.003f, "$tag dark, animate=$animate translucent=$translucent: something black is drawn, darkest $darkLow")
            val (lightLow, _) = range(tag, width, dark = false, animate, translucent)
            assertTrue(lightLow < 0.2f, "$tag light, animate=$animate translucent=$translucent: no dark icon, darkest $lightLow")
        }
    }

    @Test fun theBottomBarsIcons() = check("nav.tab.SHORTS", 412)

    @Test fun theRailsIcons() = check("rail.tab.SHORTS", 900)
}
