@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.auth.*
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.ShortsMode
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.ui.nav.Route
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** YouTube's groups on Home: shown apart from the feed, hidden by their settings, in chips too. */
class HomeGroupsUiTest {
    private val jar = CookieJar(listOf(Cookie("SID", "s", ".google.com"), Cookie("SAPISID", "p", ".youtube.com")))

    private class Reads(private val pages: Map<String, HomePage>, private val chipPages: Map<String, HomePage> = emptyMap()) : AccountReads {
        val asked = mutableListOf<String>()
        override suspend fun home(chip: String?, continuation: String?): HomePage? {
            asked += chip ?: continuation ?: ""
            return if (chip != null) chipPages[chip] else pages[continuation ?: ""]
        }
        override suspend fun recommended(): List<VideoSummary>? = null
        override suspend fun playlists(): List<AccountPlaylist>? = null
        override suspend fun playlistVideos(playlistId: String): List<VideoSummary>? = null
    }

    private fun video(id: String, short: Boolean = false) = VideoSummary(id, "Title $id", if (short) "" else "UCc", if (short) "" else "A Channel", isShort = short)
    private fun feed(vararg ids: String) = ids.map { video(it) }
    private val games = FeedGroup(FeedGroupKind.SUGGESTED, "A game row", "More from this game", listOf(video("g1"), video("g2"), video("g3")), "f1")
    private val shorts = FeedGroup(FeedGroupKind.SHORTS, "Shorts row", null, listOf(video("s1", true), video("s2", true), video("s3", true)), "f2")
    private val standard = Reads(mapOf("" to HomePage(feed("f1", "f2", "f3"), null, groups = listOf(games, shorts))))

    private fun DesktopComposeUiTest.start(reads: Reads, prepare: (Fixture) -> Unit = {}): Fixture {
        val f = buildFixture(withDownloads = false, reads = reads)
        f.services.accounts.addGoogle(GoogleIdentity("P", "p@example.com", null, "UC1"), jar)
        prepare(f)
        setContent { AppUnderTest(f) }; waitForIdle()
        return f
    }
    private fun DesktopComposeUiTest.count(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().size
    private fun DesktopComposeUiTest.waitFor(text: String) = waitUntil(timeoutMillis = 5_000) { count(text) > 0 }
    /** Everything here fits a tall window, so what is not found is not drawn. */
    private fun tall(test: DesktopComposeUiTest.() -> Unit) = runDesktopComposeUiTest(500, 3200) { test() }

    @Test fun groupsShowUnderTheirOwnTitlesWhereYouTubeHadThem() = tall {
        start(standard); waitFor("A game row")
        waitFor("Shorts row"); waitFor("More from this game"); waitFor("Title g1"); waitFor("Title s1")
        fun top(text: String) = onNodeWithText(text).fetchSemanticsNode().positionInRoot.y
        assertTrue(top("Title f1") < top("A game row") && top("A game row") < top("Title f2"), "the game row follows the first video")
        assertTrue(top("Title f2") < top("Shorts row") && top("Shorts row") < top("Title f3"), "the Shorts row follows the second")
    }

    @Test fun theSwitchHidesTheOtherRowsAtOnceAndLeavesShortsAndTheFeed() = tall {
        val f = start(standard); waitFor("A game row")
        f.services.settings.set(Settings.homeYoutubeGroups, false); waitForIdle()
        assertEquals(0, count("A game row")); assertEquals(0, count("Title g1"), "its videos do not fall into the feed")
        waitFor("Shorts row"); waitFor("Title f1")
        f.services.settings.set(Settings.homeYoutubeGroups, true); waitFor("A game row")
        assertEquals(1, standard.asked.size, "switching needs no reload")
    }

    @Test fun hidingShortsHidesTheirRowsAndOnlyThose() = tall {
        start(standard) { it.services.settings.set(Settings.shortsMode, ShortsMode.HIDE) }
        waitFor("A game row")
        assertEquals(0, count("Shorts row")); assertEquals(0, count("Title s1"))
    }

    @Test fun signedInTheAppsOwnShortsShelfIsLeftOut() = tall {
        start(standard); waitFor("Shorts row")
        assertEquals(0, onAllNodesWithText("Shorts").fetchSemanticsNodes().count { it.config.toString().contains("home.shelf.SHORTS") })
        assertEquals(0, androidx.compose.ui.test.hasTestTag("home.shelf.SHORTS").let { onAllNodes(it).fetchSemanticsNodes().size })
    }

    @Test fun aChipShowsItsOwnRows() = tall {
        val reads = Reads(
            mapOf("" to HomePage(feed("f1"), null, listOf(HomeChip("All", null, true, HomeChipKind.ALL), HomeChip("Gaming", "tok", false, HomeChipKind.TOPIC)))),
            mapOf("tok" to HomePage(feed("c1", "c2"), null, groups = listOf(games.copy(afterVideoId = "c1")))),
        )
        val f = start(reads); waitFor("Gaming")
        onNodeWithText("Gaming").performClick(); waitFor("A game row"); waitFor("Title c1")
        f.services.settings.set(Settings.homeYoutubeGroups, false); waitForIdle()
        assertEquals(0, count("A game row")); waitFor("Title c2")
    }

    @Test fun aChipThatHoldsOnlyHiddenRowsSaysSoAndStays() = tall {
        val reads = Reads(
            mapOf("" to HomePage(feed("f1"), null, listOf(HomeChip("All", null, true, HomeChipKind.ALL), HomeChip("Gaming", "tok", false, HomeChipKind.TOPIC)))),
            mapOf("tok" to HomePage(emptyList(), null, groups = listOf(games.copy(afterVideoId = null)))),
        )
        start(reads) { it.services.settings.set(Settings.homeYoutubeGroups, false) }; waitFor("Gaming")
        onNodeWithText("Gaming").performClick(); waitForIdle()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("rows your settings hide", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, count("Gaming"), "the chip is still there")
    }

    @Test fun rowsOfALaterPageArriveWithItAndRowsOfAPageWithNoVideosAreKept() = tall {
        val reads = Reads(mapOf(
            "" to HomePage(emptyList(), "more", groups = listOf(games.copy(afterVideoId = null))),
            "more" to HomePage(feed("f1", "f2"), null, groups = listOf(shorts.copy(afterVideoId = "f1"))),
        ))
        start(reads)
        waitFor("A game row"); waitFor("Title f1"); waitFor("Shorts row")
        assertEquals(listOf("", "more"), reads.asked)
    }

    @Test fun aRowWithTooFewVideosIsNotDrawn() = tall {
        start(Reads(mapOf("" to HomePage(feed("f1", "f2"), null, groups = listOf(games.copy(videos = games.videos.take(2)))))))
        waitFor("Title f2"); waitForIdle()
        assertEquals(0, count("A game row"))
    }

    @Test fun openingAShortCarriesTheRestOfItsRow() = tall {
        val f = start(standard); waitFor("Title s2")
        onNodeWithText("Title s2").performClick(); waitForIdle()
        val route = f.navigator.current as Route.ShortsPlayer
        assertEquals("s2", route.video.id); assertEquals(listOf("s3"), route.following.map { it.id })
    }
}
