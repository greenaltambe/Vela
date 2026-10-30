@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.ui.nav.Route
import org.junit.Test
import java.io.File

/** Screenshots of the new pages (task 23) at phone and desktop size, into docs/reports/23-screens/ (nothing is written unless the property below resolves). */
class Task23ScreensTest {
    private val dir = screensDir("23-screens")
    private val sizes = listOf(Screen("360x800", 360, 800), Screen("1280x800", 1280, 800))

    private fun shot(name: String, openProfile: Boolean = false, setup: (Fixture) -> Unit) {
        val out = dir ?: return
        val previous = System.getProperty("vela.screens.dir")
        System.setProperty("vela.screens.dir", out.path)
        try {
            for (s in sizes) runDesktopComposeUiTest(s.width, s.height) {
                val f = buildFixture(false)
                setup(f)
                setContent { AppUnderTest(f) }; waitForIdle()
                if (openProfile) { onNodeWithTag("profile.button").performClick(); waitForIdle() }
                mainClock.advanceTimeBy(800); waitForIdle()
                if (openProfile) {
                    // The open menu is a second window: capture it (the page behind is the same as in the home screenshot).
                    val image = onAllNodes(androidx.compose.ui.test.isRoot())[1].captureToImage()
                    val data = org.jetbrains.skia.Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)
                    if (data != null) File(out, "desktop-$name-${s.name}.png").writeBytes(data.bytes)
                } else screenshot("desktop-$name-${s.name}")
                f.dir.deleteRecursively()
            }
        } finally { if (previous == null) System.clearProperty("vela.screens.dir") else System.setProperty("vela.screens.dir", previous) }
    }

    @Test fun shell() = shot("home-bar-and-rail") { }
    @Test fun profile() = shot("profile-menu", openProfile = true) { }
    @Test fun settingsHome() = shot("settings-home") { it.navigator.push(Route.Settings) }
    @Test fun settingsPage() = shot("settings-playback") { it.navigator.push(Route.Settings); it.navigator.push(Route.SettingsPage("playback")) }
    @Test fun customizeHub() = shot("customize-hub") { it.navigator.push(Route.Settings); it.navigator.push(Route.Customize) }
    @Test fun customizePages() = listOf("color", "text", "icons", "glass", "tabs", "looks").forEach { p -> shot("customize-$p") { it.navigator.push(Route.Settings); it.navigator.push(Route.CustomizePage(p)) } }
}
