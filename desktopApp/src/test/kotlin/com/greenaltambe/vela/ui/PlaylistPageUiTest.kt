@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.extractor.*
import com.greenaltambe.vela.ui.look.ComponentIds
import com.greenaltambe.vela.ui.nav.Route
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaylistPageUiTest {
    private fun many(from: Int, to: Int) = (from..to).map { VideoSummary("p$it", "Lesson number $it of the book", "UCowner", "Let's Get Rusty", "https://invalid.invalid/$it.jpg", 600L + it, 1000L * it, publishedText = "2 years ago") }

    private class Playlists(val inner: Extractor, val pages: MutableMap<String?, PlaylistContents>) : Extractor by inner {
        override suspend fun playlistPage(playlistId: String, continuation: String?): ExtractorResult<PlaylistContents> =
            pages[continuation]?.let { ExtractorResult.Success(it) } ?: ExtractorResult.Failure(ExtractorError.Network("offline"))
    }

    private val details = PlaylistDetails("PL1", "The Rust Lang Book", "Let's Get Rusty", "UCowner", coverUrl = "https://invalid.invalid/c.jpg", videoCount = 44, viewCount = 1_814_014, description = "Every chapter of the book, in order.")
    private fun pages() = mutableMapOf<String?, PlaylistContents>(null to PlaylistContents(details, many(1, 20), "2"), "2" to PlaylistContents(null, many(21, 44), null))

    private fun open(width: Int, pages: MutableMap<String?, PlaylistContents> = pages(), dark: Boolean = false, id: String = "PL1", body: androidx.compose.ui.test.DesktopComposeUiTest.(Fixture) -> Unit) =
        runDesktopComposeUiTest(width, 900) {
            val f = buildFixture(dark = dark, extractorOverride = Playlists(FakeExtractor(), pages))
            f.navigator.push(Route.ChannelPlaylist(id, "The Rust Lang Book"))
            setContent { AppUnderTest(f) }
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(ComponentIds.PLAYLIST_HEADER).fetchSemanticsNodes().isNotEmpty() }
            waitForIdle()
            body(f)
        }

    private fun androidx.compose.ui.test.DesktopComposeUiTest.present(tag: String) = onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    @Test fun aWideWindowShowsTheHeaderBesideTheList() = open(1280) { f ->
        assertTrue(present("playlist.side"))
        onNodeWithTag("playlist.facts").assertTextContains("Playlist · 44 videos · 1,814,014 views")
        onNodeWithTag("playlist.title").assertTextContains("The Rust Lang Book")
        screenshot("playlist-wide")
        onNodeWithTag("playlist.owner").performClick(); waitForIdle()
        assertEquals("UCowner", (f.navigator.current as Route.Channel).id)
    }

    @Test fun aNarrowWindowShowsTheHeaderAboveTheList() = open(400, dark = true) { _ ->
        assertTrue(!present("playlist.side"))
        for (tag in listOf("playlist.playAll", "playlist.shuffle", "playlist.downloadAllButton", "playlist.share", "playlist.headerMenu")) onNodeWithTag(tag).assertIsDisplayed()
        screenshot("playlist-narrow-dark")
    }

    @Test fun playAllStartsTheFirstAndQueuesTheWholeList() = open(1280) { f ->
        onNodeWithTag("playlist.playAll").performClick()
        waitUntil(timeoutMillis = 5_000) { f.navigator.queue.size == 43 }
        assertEquals("p1", f.navigator.nowPlaying?.id)
        assertEquals("p44", f.navigator.queue.last().id)
    }

    @Test fun shuffleReadsEveryPageBeforeItMixesThem() = open(1280) { f ->
        onNodeWithTag("playlist.shuffle").performClick()
        waitUntil(timeoutMillis = 5_000) { f.navigator.nowPlaying != null }
        assertEquals(44, f.navigator.queue.size + 1)
    }

    @Test fun downloadAllAsksForEveryVideo() = open(1280) { _ ->
        onNodeWithTag("playlist.downloadAllButton").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("download.audioOnly").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Download 44 videos").assertIsDisplayed()
    }

    @Test fun aListThatCannotBeReadWholeIsNeverCutWithoutAsking() {
        val short = pages().also { it.remove("2") }
        open(1280, short) { f ->
            onNodeWithTag("playlist.shuffle").performClick()
            waitUntil(timeoutMillis = 5_000) { present("playlist.readUse") }
            onNodeWithTag("playlist.reading").assertTextContains("20 of 44 videos were read.", substring = true)
            assertEquals(null, f.navigator.nowPlaying, "nothing plays until the viewer decides")
            onNodeWithTag("playlist.readUse").performClick()
            waitUntil(timeoutMillis = 5_000) { f.navigator.nowPlaying != null }
            assertEquals(20, f.navigator.queue.size + 1)
        }
    }

    @Test fun everyVideoCanBeAddedToANewPlaylistOfYourOwn() = open(1280) { f ->
        onNodeWithTag("playlist.headerMenu").performClick(); waitForIdle()
        onNodeWithTag("playlist.addAll").performClick()
        waitUntil(timeoutMillis = 5_000) { present("history.saveNew") }
        onNodeWithTag("history.saveNew").performClick(); waitForIdle()
        onNodeWithText("Name").performTextInput("My copy"); waitForIdle()
        onNodeWithTag("library.nameOk").performClick()
        waitUntil(timeoutMillis = 5_000) { f.services.playlists.all().any { it.name == "My copy" && it.itemCount == 44 } }
    }

    @Test fun aMixOffersNothingThatNeedsAllOfIt() = open(1280, id = "RDabc") { _ ->
        onNodeWithTag("playlist.playAll").assertIsDisplayed()
        assertTrue(!present("playlist.shuffle") && !present("playlist.downloadAllButton"))
    }

    @Test fun onlyAPlaylistOfYourOwnOnYouTubeCanBeShared() = runDesktopComposeUiTest(1280, 900) {
        val f = buildFixture()
        val lists = f.services.playlists
        val mine = lists.all().first { !it.isWatchLater }.id
        lists.setRemote(mine, "PLmine", com.greenaltambe.vela.data.PlaylistSync.SYNCED)
        lists.setRemote(lists.watchLaterId(), "WL", com.greenaltambe.vela.data.PlaylistSync.SYNCED)
        f.navigator.push(Route.Playlist(mine))
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("playlist.share").assertIsDisplayed()
        runOnUiThread { f.navigator.back(); f.navigator.push(Route.Playlist(lists.watchLaterId())) }; waitForIdle()
        onNodeWithTag(ComponentIds.PLAYLIST_HEADER).assertIsDisplayed()
        assertTrue(!present("playlist.share"))
    }

    @Test fun aLibraryPlaylistHasTheSameHeaderBesideOrAbove() {
        for (width in listOf(1280, 400)) runDesktopComposeUiTest(width, 900) {
            val f = buildFixture(dark = width == 1280)
            val mine = f.services.playlists.all().first { !it.isWatchLater }
            f.navigator.push(Route.Playlist(mine.id))
            setContent { AppUnderTest(f) }; waitForIdle()
            assertEquals(width == 1280, present("playlist.side"))
            onNodeWithTag(ComponentIds.PLAYLIST_HEADER).assertIsDisplayed()
            assertTrue(!present("playlist.share"), "a playlist on this device has no link")
            screenshot("playlist-library-$width")
            onNodeWithTag("playlist.playAll").performClick(); waitForIdle()
            assertEquals(mine.itemCount - 1, f.navigator.queue.size)
        }
    }
}
