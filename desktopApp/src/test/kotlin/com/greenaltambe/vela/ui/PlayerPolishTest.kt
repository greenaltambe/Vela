@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.core.settings.ProgressStyle
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.extractor.Extractor
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.VideoInfo
import com.greenaltambe.vela.ui.components.ScrubGeometry
import com.greenaltambe.vela.ui.components.ScrubMarker
import com.greenaltambe.vela.ui.components.Scrubber
import com.greenaltambe.vela.ui.components.drawProgress
import com.greenaltambe.vela.ui.components.metrics
import com.greenaltambe.vela.ui.nav.Route
import com.greenaltambe.vela.ui.player.ControlButton
import com.greenaltambe.vela.ui.player.PlayerButtonSizes
import com.greenaltambe.vela.ui.platform.LocalWindowControls
import com.greenaltambe.vela.ui.platform.WindowControls
import com.greenaltambe.vela.ui.theme.AppTheme
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Task 85: the seek bar on touch, the gesture lock, the markers on the wavy bar, the round buttons and the channel row.
 * Every rule here failed on the code before the task (item 1 is shown against a hand-written copy of the old press-to-seek handler,
 * [LegacyScrubber], that these same tests run against and must fail on).
 */
class PlayerPolishTest {
    // ------------------------------------------------------------------------------------------------------------------------------------
    // 1. The seek bar on touch
    // ------------------------------------------------------------------------------------------------------------------------------------

    /** The bar before the task: any press jumps to the finger and releasing commits it. */
    @Composable
    private fun LegacyScrubber(value: Float, onPreview: (Float?) -> Unit, onCommit: (Float) -> Unit, modifier: Modifier) {
        var dragging: Float? = null
        Box(modifier.fillMaxWidth().height(48.dp).pointerInput(Unit) {
            val g = ScrubGeometry(size.width.toFloat(), 4f)
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Main)
                    val c = e.changes.firstOrNull() ?: continue
                    when (e.type) {
                        PointerEventType.Press -> { dragging = g.fractionAt(c.position.x); onPreview(dragging) }
                        PointerEventType.Move -> if (c.pressed) { dragging = g.fractionAt(c.position.x); onPreview(dragging) }
                        PointerEventType.Release -> { dragging?.let(onCommit); dragging = null; onPreview(null) }
                        else -> {}
                    }
                }
            }
        })
    }

    private class Log { val previews = mutableListOf<Float?>(); val commits = mutableListOf<Float>() }

    /** A 400 px wide bar (density 1) in BOLD style: the track runs 4 .. 396. */
    private fun ComposeUiTest.bar(value: Float, legacy: Boolean, log: Log, tapToSeek: Boolean? = null) {
        setContent {
            AppTheme(SettingsResolver(InMemorySettingsStore())) {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    Box(Modifier.width(400.dp)) {
                        val m = Modifier.testTag("sb")
                        if (legacy) LegacyScrubber(value, { log.previews += it }, { log.commits += it }, m)
                        else Scrubber(value, { log.previews += it }, { log.commits += it }, m, style = ProgressStyle.BOLD, tapToSeek = tapToSeek)
                    }
                }
            }
        }
        waitForIdle()
    }

    private val g = ScrubGeometry(400f, ProgressStyle.BOLD.metrics().endInset.value)

    private fun both(test: ComposeUiTest.(legacy: Boolean, log: Log) -> Unit) {
        runDesktopComposeUiTest(400, 200) { test(false, Log()) }
        // The old handler must FAIL the same checks.
        assertFails("the legacy press-to-seek bar passed a rule it is meant to break") { runDesktopComposeUiTest(400, 200) { test(true, Log()) } }
    }

    @Test fun aTouchTapOnTheBarDoesNotSeekNorPreview() = both { legacy, log ->
        bar(0.5f, legacy, log)
        onNodeWithTag("sb").performTouchInput { down(Offset(100f, 24f)); up() }
        waitForIdle()
        assertTrue(log.commits.isEmpty(), "a tap committed ${log.commits}")
        assertTrue(log.previews.none { it != null }, "a tap previewed ${log.previews}")
    }

    @Test fun aTouchDragFromElsewhereOnTheBarGlidesToTheFingerAndCommitsOnceUnderTheFinalX() = runDesktopComposeUiTest(400, 200) {
        val log = Log()
        bar(0.9f, false, log)   // the handle is far right; the finger lands at x = 100 (not on it)
        onNodeWithTag("sb").performTouchInput { down(Offset(100f, 24f)); moveTo(Offset(180f, 24f)); moveTo(Offset(300f, 24f)); up() }
        waitForIdle()
        assertEquals(1, log.commits.size)
        assertEquals(g.fractionAt(300f), log.commits.single(), 1e-4f)
        assertTrue(log.previews.count { it != null } >= 2, "previews along the way: ${log.previews}")
        assertEquals(null, log.previews.last(), "the preview is cleared at the end")
    }

    @Test fun grabbingTheHandleDoesNotJump() = both { legacy, log ->
        bar(0.5f, legacy, log)   // the handle is at x = 200
        onNodeWithTag("sb").performTouchInput { down(Offset(206f, 24f)); moveTo(Offset(240f, 24f)); moveTo(Offset(280f, 24f)); up() }
        waitForIdle()
        assertEquals(0.5f, log.previews.first { it != null }!!, 1e-3f, "the first preview is the current value")
        assertEquals(1, log.commits.size)
    }

    /** The desktop harness cannot inject a pointer cancel, so the gesture is cut the way a system cancel cuts it: its coroutine is cancelled (the bar leaves the composition) mid-drag. */
    @Test fun aGestureCutMidDragCommitsNothingAndClearsThePreview() = runDesktopComposeUiTest(400, 200) {
        val log = Log()
        val shown = androidx.compose.runtime.mutableStateOf(true)
        setContent {
            AppTheme(SettingsResolver(InMemorySettingsStore())) {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    Box(Modifier.width(400.dp)) { if (shown.value) Scrubber(0.5f, { log.previews += it }, { log.commits += it }, Modifier.testTag("sb"), style = ProgressStyle.BOLD) }
                }
            }
        }
        waitForIdle()
        onNodeWithTag("sb").performTouchInput { down(Offset(206f, 24f)); moveTo(Offset(260f, 24f)); moveTo(Offset(300f, 24f)) }
        waitForIdle()
        assertTrue(log.previews.any { it != null }, "the drag never started")
        shown.value = false
        waitForIdle()
        assertTrue(log.commits.isEmpty(), "a cut gesture committed ${log.commits}")
        assertEquals(null, log.previews.lastOrNull(), "the preview was not cleared")
    }

    @Test fun verticalDriftDoesNotChangeThePosition() = runDesktopComposeUiTest(400, 200) {
        val log = Log()
        bar(0.9f, false, log)
        onNodeWithTag("sb").performTouchInput { down(Offset(100f, 24f)); moveTo(Offset(180f, 24f + 80f)); moveTo(Offset(300f, 24f + 80f)); up() }
        waitForIdle()
        // Only x counts; and on the legacy bar a tap-like start would also have previewed, so the main check is the single commit under x = 300.
        assertEquals(listOf(g.fractionAt(300f)), log.commits)
    }

    @Test fun aMouseClickStillSeeks() = runDesktopComposeUiTest(400, 200) {
        val log = Log()
        bar(0.5f, false, log)
        onNodeWithTag("sb").performMouseInput { click(Offset(100f, 24f)) }
        waitForIdle()
        assertEquals(listOf(g.fractionAt(100f)), log.commits)
    }

    // ------------------------------------------------------------------------------------------------------------------------------------
    // 2. SponsorBlock markers follow the bar's form
    // ------------------------------------------------------------------------------------------------------------------------------------

    private fun draw(style: ProgressStyle, phase: Float, markers: List<ScrubMarker>, shown: Float = 0.8f): ImageBitmap {
        val bmp = ImageBitmap(400, 48)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp), Size(400f, 48f)) {
            drawProgress(style, shown, listOf(Color(0xFF0000FF), Color(0xFF0000CC)), Color.White, Color(0xFF888888), Color(0xFFAAAAAA), Color.Black, markers = markers, wavePhase = phase)
        }
        return bmp
    }

    private val red = Color(0xFFFF0000)
    /** Rows of the marker-coloured pixels: centroid y and count. */
    private fun redCentroidY(bmp: ImageBitmap, x0: Int, x1: Int): Pair<Float, Int> {
        val px = bmp.toPixelMap()
        var sum = 0f; var n = 0
        for (x in x0 until x1) for (y in 0 until 48) { val c = px[x, y]; if (c.alpha > 0.8f && c.red > 0.8f && c.green < 0.6f && c.blue < 0.6f) { sum += y; n++ } }
        return (if (n == 0) 0f else sum / n) to n
    }

    @Test fun anExpressiveMarkerInThePlayedPartRisesAndFallsWithTheWave() {
        val mk = listOf(ScrubMarker(0.2f, 0.5f, red))   // the played part ends at 0.8, so this marker is wholly inside the wave
        // A narrow slice of columns: over whole wavelengths the centroid of a wave is the centre line whatever its phase.
        val a = redCentroidY(draw(ProgressStyle.EXPRESSIVE, 0f, mk), 100, 108)
        val b = redCentroidY(draw(ProgressStyle.EXPRESSIVE, 1.6f, mk), 100, 108)
        assertTrue(a.second > 10 && b.second > 10, "no marker-coloured pixels: ${a.second} / ${b.second}")
        assertTrue(kotlin.math.abs(a.first - b.first) > 0.4f, "the marker did not move with the wave: ${a.first} vs ${b.first}")
    }

    @Test fun theWaveStyleDrawsMarkersOnBothSidesOfTheHandle() {
        val played = redCentroidY(draw(ProgressStyle.WAVE, 0f, listOf(ScrubMarker(0.2f, 0.4f, red))), 0, 400).second
        val unplayed = redCentroidY(draw(ProgressStyle.WAVE, 0f, listOf(ScrubMarker(0.85f, 0.95f, red))), 0, 400).second
        assertTrue(played > 30, "WAVE: no played marker pixels ($played)")
        assertTrue(unplayed > 10, "WAVE: no unplayed marker pixels ($unplayed)")
    }

    @Test fun aFlatStyleStillDrawsItsMarkerOnTheCentreLine() {
        val (y, n) = redCentroidY(draw(ProgressStyle.BOLD, 0f, listOf(ScrubMarker(0.85f, 0.95f, red))), 0, 400)
        assertTrue(n > 100, "BOLD marker missing")
        assertEquals(24f, y, 1.0f)
    }

    // ------------------------------------------------------------------------------------------------------------------------------------
    // 4. One size for the round buttons
    // ------------------------------------------------------------------------------------------------------------------------------------

    @Test fun everyRoundButtonIsTheSameSizeWithOrWithoutLongPressAndWhenDisabled() = runDesktopComposeUiTest(400, 300) {
        setContent {
            AppTheme(SettingsResolver(InMemorySettingsStore())) {
                androidx.compose.foundation.layout.Row {
                    ControlButton({}, modifier = Modifier.testTag("plain")) { Box(Modifier.width(24.dp).height(24.dp)) }
                    ControlButton({}, onLongClick = {}, modifier = Modifier.testTag("long")) { Box(Modifier.width(24.dp).height(24.dp)) }
                    ControlButton({}, enabled = false, modifier = Modifier.testTag("disabled")) { Box(Modifier.width(24.dp).height(24.dp)) }
                }
            }
        }
        waitForIdle()
        val sizes = listOf("plain", "long", "disabled").map { sizeOf(it) }
        sizes.forEach { assertEquals(PlayerButtonSizes.utility.value to PlayerButtonSizes.utility.value, it, "sizes: $sizes") }
    }

    /** The disc the player buttons draw is the same diameter with and without a long-press action (the old hand-drawn path drew it at 88 %). */
    @Test fun theDrawnDiscIsTheSameDiameterWithAndWithoutLongPress() = runDesktopComposeUiTest(300, 120) {
        setContent {
            AppTheme(SettingsResolver(InMemorySettingsStore())) {
                androidx.compose.foundation.layout.Row {
                    ControlButton({}, modifier = Modifier.testTag("plain")) { Box(Modifier.width(24.dp).height(24.dp)) }
                    ControlButton({}, onLongClick = {}, modifier = Modifier.testTag("long")) { Box(Modifier.width(24.dp).height(24.dp)) }
                }
            }
        }
        waitForIdle()
        // Rows / columns holding any pixel that is not the plain page background: the extent of the drawn scrim disc.
        fun extent(tag: String): Pair<Int, Int> {
            val img = onNodeWithTag(tag).captureToImage().toPixelMap()
            val bg = img[0, 0]
            var minX = Int.MAX_VALUE; var maxX = -1; var minY = Int.MAX_VALUE; var maxY = -1
            for (x in 0 until img.width) for (y in 0 until img.height) {
                val c = img[x, y]
                if (kotlin.math.abs(c.red - bg.red) + kotlin.math.abs(c.green - bg.green) + kotlin.math.abs(c.blue - bg.blue) > 0.05f) { minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y) }
            }
            return (maxX - minX + 1) to (maxY - minY + 1)
        }
        val plain = extent("plain"); val long = extent("long")
        assertTrue(kotlin.math.abs(plain.first - long.first) <= 1 && kotlin.math.abs(plain.second - long.second) <= 1, "disc plain $plain vs long-press $long")
    }

    /** Width and height in dp of the node with [tag]. */
    private fun ComposeUiTest.sizeOf(tag: String): Pair<Float, Float> = onNodeWithTag(tag).getUnclippedBoundsInRoot().let { (it.right - it.left).value to (it.bottom - it.top).value }

    private class FullscreenWindow : WindowControls { override val isFullscreen = true; override fun toggleFullscreen() {} }

    @Test fun fullscreenControlsUseOneUtilitySizeAndTheTransportButtonsAreLarger() = runDesktopComposeUiTest(800, 360) {
        val f = buildFixture(withDownloads = false)
        f.navigator.push(Route.Video(FakeData.videos[0]))
        setContent { CompositionLocalProvider(LocalWindowControls provides FullscreenWindow()) { AppUnderTest(f) } }
        waitForIdle(); mainClock.advanceTimeBy(800); waitForIdle()
        val utility: Map<String, Pair<Float, Float>> = listOf("player.back", "player.captions", "player.mute", "player.quality", "player.speed", "player.sleep-timer", "player.fullscreen").associateWith { sizeOf(it) }
        utility.forEach { (tag, s) -> assertEquals(PlayerButtonSizes.utility.value to PlayerButtonSizes.utility.value, s, "$tag is $s; all: $utility") }
        assertEquals(sizeOf("player.previous"), sizeOf("player.next"))
        assertEquals(PlayerButtonSizes.transportFullscreen.value to PlayerButtonSizes.transportFullscreen.value, sizeOf("player.previous"))
        assertEquals(PlayerButtonSizes.playFullscreen.value to PlayerButtonSizes.playFullscreen.value, sizeOf("player.playPause"))
        f.dir.deleteRecursively()
    }

    // ------------------------------------------------------------------------------------------------------------------------------------
    // 5. No minimise / fullscreen gestures while the seek bar is being used
    // ------------------------------------------------------------------------------------------------------------------------------------

    private fun ComposeUiTest.openVideo(f: Fixture) {
        f.navigator.push(Route.Video(FakeData.videos[0]))
        setContent { AppUnderTest(f) }
        waitForIdle(); mainClock.advanceTimeBy(800); waitForIdle()
    }

    @Test fun scrubbingWithAFirmVerticalDriftOnTheBarSeeksOnceAndNeverMinimises() = runDesktopComposeUiTest(360, 800) {
        val f = buildFixture(withDownloads = false)
        openVideo(f)
        onNodeWithTag("player.seek").assertIsDisplayed()
        val before = f.controller.state.value.positionMs
        onNodeWithTag("player.seek").performTouchInput {
            down(Offset(60f, centerY)); moveTo(Offset(120f, centerY + 40f)); moveTo(Offset(200f, centerY + 120f)); moveTo(Offset(300f, centerY + 300f)); up()
        }
        waitForIdle(); mainClock.advanceTimeBy(800); waitForIdle()
        assertNotEquals(before, f.controller.state.value.positionMs, "the scrub did not seek")
        onNodeWithTag("player.seek").assertIsDisplayed()   // still the open page, not the mini player
        onNodeWithTag("mini.title", useUnmergedTree = true).assertDoesNotExist()   // not minimised to the mini player
        f.dir.deleteRecursively()
    }

    @Test fun aSwipeDownOnThePictureStillMinimises() = runDesktopComposeUiTest(360, 800) {
        val f = buildFixture(withDownloads = false)
        openVideo(f)
        onNodeWithTag("player.surface").performTouchInput { down(Offset(centerX, 30f)); moveTo(Offset(centerX, 150f)); moveTo(Offset(centerX, 330f)); moveTo(Offset(centerX, 520f)); up() }
        waitForIdle(); mainClock.advanceTimeBy(1500); waitForIdle()
        onNodeWithTag("mini.title", useUnmergedTree = true).assertExists()
        f.dir.deleteRecursively()
    }

    @Test fun doubleTapSeekStillWorksOutsideTheBar() = runDesktopComposeUiTest(360, 800) {
        val f = buildFixture(withDownloads = false)
        openVideo(f)
        val before = f.controller.state.value.positionMs
        onNodeWithTag("player.surface").performTouchInput { doubleClick(Offset(width * 0.85f, 60f)) }
        waitForIdle()
        assertTrue(f.controller.state.value.positionMs > before, "double tap did not seek forward")
        f.dir.deleteRecursively()
    }

    // ------------------------------------------------------------------------------------------------------------------------------------
    // 3. The channel name is readable
    // ------------------------------------------------------------------------------------------------------------------------------------

    private class Named(private val name: String) : Extractor by FakeExtractor() {
        override suspend fun videoInfo(videoId: String) = ExtractorResult.Success(VideoInfo(videoId, "Title", "d", "UC1", name, 1_845, 1_000, 10_000))
    }

    private fun channelCase(name: String, width: Int, fontScale: Float, check: ComposeUiTest.(f: Fixture) -> Unit) = runDesktopComposeUiTest(width, 900) {
        val f = buildFixture(withDownloads = false, extractorOverride = Named(name))
        f.navigator.push(Route.Video(FakeData.videos[0].copy(channelName = name)))
        setContent { CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) { AppUnderTest(f) } }
        waitForIdle(); mainClock.advanceTimeBy(800); waitForIdle()
        check(f)
        f.dir.deleteRecursively()
    }

    private fun ComposeUiTest.nameOverflows(): Boolean {
        val results = mutableListOf<TextLayoutResult>()
        onNodeWithTag("video.channel", useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.single().hasVisualOverflow
    }

    /** The control (the subscribed pill; the fixture follows this channel) is on the same row as the name: its top is above the name's bottom. */
    private fun ComposeUiTest.controlOnTheNamesRow(): Boolean {
        val name = onNodeWithTag("video.channel", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val control = onNodeWithTag("video.subscribed", useUnmergedTree = true).getUnclippedBoundsInRoot()
        return control.top < name.bottom
    }

    private val longName = "A".repeat(10) + " Extremely Long Channel Name Of A Creator Who Never Does"

    @Test fun theSubscribedStateIsOneSmallPillNotTwoWideButtons() = channelCase("Mia", 360, 1f) {
        onNodeWithTag("video.subscribed").assertIsDisplayed()
        assertTrue(runCatching { onNodeWithTag("channel.bell", useUnmergedTree = true).assertIsDisplayed() }.isFailure, "the separate bell button is gone")
        assertTrue(sizeOf("video.subscribed").first <= 80f, "the pill is ${sizeOf("video.subscribed").first} dp wide")
        assertTrue(controlOnTheNamesRow())
    }

    @Test fun aLongChannelNameStaysOnOneRowWithTheControlAndKeepsTheFullNameForAccessibility() = channelCase(longName, 360, 1.3f) {
        assertTrue(controlOnTheNamesRow(), "the control must stay on the name's row")
        onNodeWithTag("video.subscribed").assertIsDisplayed()
        val description = onNodeWithTag("video.channelRow", useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
        assertTrue(description != null && "Extremely Long Channel Name" in description, "the full name is in the row's content description: $description")
    }

    @Test fun aNameThatFitsInTwoLinesIsNotCutOffNextToThePill() = channelCase("The Quiet Corner Of Kotlin Concurrency", 360, 1f) {
        assertTrue(!nameOverflows(), "a name that fits in two lines was ellipsized")
    }

    @Test fun thePillOpensTheLevelsAndUnsubscribe() = channelCase("Mia", 360, 1f) { f ->
        onNodeWithTag("video.subscribed").performClick(); waitForIdle()
        onNodeWithTag("channel.bell.ALL", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("video.unsubscribe", useUnmergedTree = true).performClick(); waitForIdle()
        assertTrue(!f.services.subscriptions.isSubscribed("UC1"), "Unsubscribe in the menu unsubscribed")
        onNodeWithTag("video.subscribe", useUnmergedTree = true).assertIsDisplayed()   // back to the word button
    }
}
