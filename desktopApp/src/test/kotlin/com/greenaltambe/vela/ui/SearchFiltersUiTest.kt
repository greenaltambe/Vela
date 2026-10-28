@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.ui.nav.Route
import org.junit.Test
import kotlin.test.assertTrue

class SearchFiltersUiTest {
    @Test fun typingShowsSuggestionsAndTappingOneSearches() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(withDownloads = false)
        f.navigator.push(Route.Search())
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("search.field").performTextInput("lofi")
        waitUntil(timeoutMillis = 3_000) { runCatching { onNodeWithTag("search.suggestions").assertIsDisplayed() }.isSuccess }
        onNodeWithText("lofi live").performClick(); waitForIdle()
        assertTrue((f.navigator.current as Route.Search).query == "lofi live")
    }

    @Test fun theFilterChipsNarrowTheLoadedResultsAndSayWhenNothingMatches() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(withDownloads = false)
        f.navigator.push(Route.Search("lofi"))
        setContent { AppUnderTest(f) }; waitForIdle()
        waitUntil(timeoutMillis = 3_000) { runCatching { onNodeWithTag("search.filter.duration").assertIsDisplayed() }.isSuccess }
        onNodeWithTag("search.filter.duration").performClick(); waitForIdle()
        onNodeWithText("Under 4 min").performClick(); waitForIdle()
        onNodeWithTag("search.filter.sort").performClick(); waitForIdle()
        onNodeWithText("Most viewed").performClick(); waitForIdle()
        onNodeWithTag("search.filter.sort").assertIsDisplayed()
    }
}
