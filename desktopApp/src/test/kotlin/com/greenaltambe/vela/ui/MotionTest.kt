@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.ui.components.SkeletonCard
import com.greenaltambe.vela.ui.look.*
import com.greenaltambe.vela.ui.look.Vela
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MotionTest {
    private fun look(reduce: Boolean = false, performance: Boolean = false) =
        LookBuilder.build(LookSnapshot(reduceMotion = reduce, performance = performance), systemDark = true)

    @Test fun skeletonShimmerRunsOnlyWhenMotionIsNotReduced() = runDesktopComposeUiTest(400, 600) {
        val l = mutableStateOf(look())
        setContent { VelaTheme(l.value, false) { Column { SkeletonCard(vertical = true); SkeletonCard(vertical = false) } } }
        mainClock.autoAdvance = false; mainClock.advanceTimeBy(500)
        assertTrue(ActiveLoops.count >= 2, "positive control: the shimmer is a looping animation (${ActiveLoops.count})")
        l.value = look(reduce = true); mainClock.advanceTimeBy(100)
        assertEquals(0, ActiveLoops.count, "Reduce motion leaves no looping animation")
        l.value = look(performance = true); mainClock.advanceTimeBy(100)
        assertEquals(0, ActiveLoops.count, "Performance mode leaves none either")
    }

    @Test fun shimmerStopsWhileTheAppIsInTheBackground() = runDesktopComposeUiTest(400, 600) {
        val active = mutableStateOf(true)
        setContent { VelaTheme(look(), false) { CompositionLocalProvider(LocalAnimationsActive provides active.value) { SkeletonCard(vertical = true) } } }
        mainClock.autoAdvance = false; mainClock.advanceTimeBy(300)
        assertTrue(ActiveLoops.count > 0)
        active.value = false; mainClock.advanceTimeBy(100)
        assertEquals(0, ActiveLoops.count, "no animation clock runs in the background")
    }

    @Test fun staggerAndDurationsComeFromTheTokensAndShrinkWhenReduced() {
        val normal = look().motion; val reduced = look(reduce = true).motion
        assertEquals(listOf(0, 28, 56, 84), (0..3).map { VelaMotion.staggerDelayMs(normal, it) })
        assertEquals(VelaMotion.MAX_STAGGERED * 28, VelaMotion.staggerDelayMs(normal, 500), "a long list never waits")
        assertEquals(listOf(0, 0, 0), (0..2).map { VelaMotion.staggerDelayMs(reduced, it) }, "no stagger when reduced")
        assertEquals(listOf(90, 160, 240, 360), VelaMotion.Level.entries.map { VelaMotion.durationMs(normal, it) })
        assertTrue(VelaMotion.Level.entries.all { VelaMotion.durationMs(reduced, it) < VelaMotion.durationMs(normal, it) })
    }

    @Test fun theUndoToastAppearsRunsUndoOnceAndGoes() = runDesktopComposeUiTest(400, 600) {
        val toasts = UndoToasts(); var undone = 0
        setContent { VelaTheme(look(), false) { UndoHost(toasts) { androidx.compose.foundation.text.BasicText("page") } } }
        toasts.show("Corners → Round") { undone++ }
        waitForIdle(); onNodeWithTag("undo.toast").assertExists()
        onNodeWithTag("undo.action").performClick(); waitForIdle()
        assertEquals(1, undone); assertEquals(null, toasts.current)
    }

    @Test fun theSwitchTogglesAndSegmentedPillsReportTheChoice() = runDesktopComposeUiTest(400, 600) {
        var on = false; var picked = 0
        setContent { VelaTheme(look(), false) { Column { VelaSwitch(on, { on = it }); SegmentedPills(listOf("Compact", "Comfortable", "Roomy"), 1, { picked = it }) } } }
        onNodeWithTag("pills.2").performClick(); waitForIdle()
        assertEquals(2, picked)
        onNodeWithTag("pills.outline").assertExists()
    }
}
