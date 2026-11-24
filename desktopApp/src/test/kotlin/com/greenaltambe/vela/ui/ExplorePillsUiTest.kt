@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.ui.nav.Route
import com.greenaltambe.vela.ui.screens.ExploreTopics
import org.junit.Test
import kotlin.test.assertEquals

class ExplorePillsUiTest {
    @Test fun thePillsAreFixedHaveNoTrendingAndLiveComesLast() {
        assertEquals(listOf("Gaming", "Podcasts", "Music", "News", "Sports", "Movies", "Learning", "Cooking", "Tech", "Live"), ExploreTopics.map { it.label })
        assertEquals(setOf("trending_gaming", "trending_podcasts_episodes", "live"), ExploreTopics.mapNotNull { it.kiosk }.toSet())
    }

    @Test fun exploreOpensOnGamingAndAPillSwitchesTheList() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(withDownloads = false); f.navigator.selectTab(Route.Explore)
        setContent { AppUnderTest(f) }; waitForIdle()
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("explore.topic.Gaming", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, onAllNodesWithTag("explore.topic.Trending", useUnmergedTree = true).fetchSemanticsNodes().size)
        onNodeWithTag("explore.topic.Gaming", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("explore.topic.Podcasts", useUnmergedTree = true).performClick(); waitForIdle()
        onNodeWithTag("explore.topic.Podcasts", useUnmergedTree = true).assertIsDisplayed()
    }
}
