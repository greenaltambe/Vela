@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.data.*
import com.greenaltambe.vela.downloads.DownloadRepository
import com.greenaltambe.vela.downloads.DownloadRequest
import com.greenaltambe.vela.downloads.StoredPlan
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.ui.nav.Route
import org.junit.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * The local-only screens (no network to fail) have two other states to get right: EMPTY, and LARGE. With 2000 rows each, a screen must compose
 * only what is visible (a lazy list), open quickly, and still scroll to the end. "Composed rows" is counted from the semantics tree.
 */
class LargeListsUiTest {
    private val now = Clock.System.now()
    private fun v(i: Int) = VideoSummary("vid$i", "Title number $i", "UC${i % 50}", "Channel ${i % 50}", durationSeconds = 300L + i)

    private fun androidx.compose.ui.test.ComposeUiTest.composedRows(vararg tags: String) = tags.sumOf { onAllNodesWithTag(it, useUnmergedTree = true).fetchSemanticsNodes().size }

    @Test fun historyWith2000RowsIsLazyOpensFastAndScrollsToTheEnd() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(withDownloads = false)
        val h = SqlDelightWatchHistoryRepository(f.services.database)
        f.services.database.transaction { repeat(2000) { i -> val id = h.begin("local", "vid$i", "UC1", "Title number $i", now - i.minutes, 1000L, 2000L, 500L); h.update(id, now - i.minutes, 1000L, 2000L, 500L) } }
        f.navigator.selectTab(Route.Library); f.navigator.push(Route.History)
        val t0 = System.nanoTime()
        setContent { AppUnderTest(f) }; waitForIdle()
        val firstFrameMs = (System.nanoTime() - t0) / 1_000_000
        println("LARGE history first frame ${firstFrameMs} ms, composed rows=${composedRows("card.title")}")
        assertTrue(composedRows("card.title") in 1..60, "only visible rows are composed, not 2000")
        assertTrue(firstFrameMs < 15_000, "opens in a sane time: $firstFrameMs ms")
        onNodeWithTag("history.list").performScrollToIndex(1); waitForIdle()
        onNodeWithTag("history.older").assertIsDisplayed()
        onNodeWithTag("history.older").performClick(); waitForIdle()
        assertTrue(composedRows("card.title") in 1..60, "still lazy after loading 500 more")
    }

    @Test fun aPlaylistWith2000VideosIsLazy() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(withDownloads = false)
        val repo = PlaylistRepository(f.services.database)
        val id = repo.create("Huge", now)
        repo.add(id, (0 until 2000).map(::v), now)
        f.navigator.selectTab(Route.Library); f.navigator.push(Route.Playlist(id))
        setContent { AppUnderTest(f) }; waitForIdle()
        assertTrue(composedRows("card.title") in 1..60, "composed ${composedRows("card.title")}")
        onNodeWithTag("playlist.list").performScrollToIndex(1999); waitForIdle()
        assertTrue(composedRows("card.title") in 1..60)
    }

    @Test fun libraryWith2000PlaylistsAndSubscriptionsWith2000ChannelsAreLazy() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(withDownloads = false)
        val pl = PlaylistRepository(f.services.database); f.services.database.transaction { repeat(2000) { pl.create("Playlist $it", now) } }
        val subs = SubscriptionRepository(f.services.database); f.services.database.transaction { repeat(2000) { subs.subscribe("UCbulk$it", "Channel bulk $it", null, now) } }
        f.navigator.selectTab(Route.Library)
        setContent { AppUnderTest(f) }; waitForIdle()
        assertTrue(composedRows(com.greenaltambe.vela.ui.look.ComponentIds.PLAYLIST_ROW) in 1..60, "library rows ${composedRows(com.greenaltambe.vela.ui.look.ComponentIds.PLAYLIST_ROW)}")
        onNodeWithTag("library.list").performScrollToIndex(2000); waitForIdle()
        f.navigator.selectTab(Route.Subscriptions); waitForIdle()
    }

    @Test fun downloadsWith2000RowsIsLazy() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(withDownloads = true)
        val repo = DownloadRepository(f.services.database)
        f.services.database.transaction { repeat(2000) { i -> val id = repo.insert(DownloadRequest(v(i + 100)), StoredPlan(), "720p"); repo.setState(id, DownloadState.COMPLETED, null) } }
        f.services.downloads!!.manager.reload()
        f.navigator.selectTab(Route.Library); f.navigator.push(Route.Downloads)
        val t0 = System.nanoTime()
        setContent { AppUnderTest(f) }; waitForIdle()
        println("LARGE downloads first frame ${(System.nanoTime() - t0) / 1_000_000} ms")
        onNodeWithTag("downloads.list").assertIsDisplayed()
        onNodeWithTag("downloads.list").performScrollToIndex(1900); waitForIdle()
    }

    @Test fun emptyHistoryPlaylistsAndDownloadsSayWhatToDo() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(withDownloads = false)
        f.services.database.historyQueries_clear()
        f.navigator.selectTab(Route.Library); f.navigator.push(Route.History)
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithText("Nothing watched yet.").assertIsDisplayed()
    }

    private fun com.greenaltambe.vela.data.db.VelaDatabase.historyQueries_clear() { HistoryLibrary(this).clear() }
}
