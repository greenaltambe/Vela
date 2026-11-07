@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import com.greenaltambe.vela.ui.look.StartupSplash
import com.greenaltambe.vela.ui.theme.AppTheme
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StartupSplashTest {
    private fun run(block: ComposeUiTest.(ready: androidx.compose.runtime.MutableState<Boolean>, finished: () -> Int) -> Unit) = runDesktopComposeUiTest(360, 800) {
        val f = buildFixture(withDownloads = false)
        val ready = mutableStateOf(false)
        var done = 0
        mainClock.autoAdvance = false
        setContent { AppTheme(f.services.settings) { StartupSplash(isReady = { ready.value }, onFinished = { done++ }) } }
        block(ready) { done }
        f.dir.deleteRecursively()
    }

    @Test fun staysWhileLoadingThenSailsOffAndFinishes() = run { ready, finished ->
        mainClock.advanceTimeBy(600) // the ship has arrived
        onNodeWithTag("startup.splash").assertExists()
        mainClock.advanceTimeBy(1_500) // still loading: it waits
        assertEquals(0, finished(), "the splash waits for the app")
        onNodeWithTag("startup.splash").assertExists()
        ready.value = true
        mainClock.advanceTimeBy(1_400) // leaves to the right and the sea drains
        assertEquals(1, finished(), "the splash ends once the app is ready")
    }

    @Test fun leavesAfterTheCapEvenIfNothingEverLoads() = run { _, finished ->
        mainClock.advanceTimeBy(2_800 + 1_400)
        assertEquals(1, finished(), "a failed load must not keep the ship at sea")
    }

    @Test fun readyEarlyStillShowsTheShipForABeat() = run { ready, finished ->
        ready.value = true
        mainClock.advanceTimeBy(600)
        assertEquals(0, finished(), "ship arrives and stays a moment even when the app was already ready")
        mainClock.advanceTimeBy(2_000)
        assertTrue(finished() == 1)
    }
}
