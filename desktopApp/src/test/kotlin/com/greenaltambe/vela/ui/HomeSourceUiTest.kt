@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.auth.*
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.extractor.VideoSummary
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeSourceUiTest {
    private val jar = CookieJar(listOf(Cookie("SID", "s", ".google.com"), Cookie("SAPISID", "p", ".youtube.com")))
    private class Reads(var answer: List<VideoSummary>?) : AccountReads {
        var calls = 0
        /** When set, [home] serves these pages by their continuation ("" is the first page) instead of [answer]. */
        var pages: Map<String, HomePage>? = null
        val continuationsAsked = mutableListOf<String?>()
        /** The chips' first pages, by chip token. */
        var chipPages: Map<String, HomePage> = emptyMap()
        val chipsAsked = mutableListOf<String>()
        override suspend fun home(chip: String?, continuation: String?): HomePage? {
            val p = pages ?: return super.home(chip, continuation)
            calls++
            if (chip != null) { chipsAsked += chip; return chipPages[chip] }
            continuationsAsked += continuation
            return p[continuation ?: ""]
        }
        override suspend fun recommended(): List<VideoSummary>? { calls++; return answer }
        override suspend fun playlists(): List<AccountPlaylist>? = null
        override suspend fun playlistVideos(playlistId: String): List<VideoSummary>? = null
    }
    private val rec = listOf(VideoSummary("rec1", "A recommended one", "UCr", "Rec Channel"))
    private fun signIn(f: Fixture) = f.services.accounts.addGoogle(GoogleIdentity("P", "p@example.com", null, "UC1"), jar)

    @Test fun signedInShowsRecommendedFromTheAccount() = runDesktopComposeUiTest(800, 1000) {
        val reads = Reads(rec); val f = buildFixture(withDownloads = false, reads = reads); signIn(f)
        setContent { AppUnderTest(f) }; waitForIdle()
        waitUntil(timeoutMillis = 3_000) { runCatching { onNodeWithText("A recommended one").assertIsDisplayed() }.isSuccess }
        // (Home no longer prints a "Recommended" heading; the account's video being shown is the evidence.)
    }

    @Test fun aFailedAccountFeedSaysSoWithRetryAndExploreAndNeverShowsTrending() = runDesktopComposeUiTest(800, 1000) {
        val reads = Reads(null); val f = buildFixture(withDownloads = false, reads = reads); signIn(f)
        setContent { AppUnderTest(f) }; waitForIdle()
        waitUntil(timeoutMillis = 3_000) { runCatching { onNodeWithText("Your feed could not be loaded right now.", substring = true).assertIsDisplayed() }.isSuccess }
        onNodeWithTag("home.explore", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, onAllNodesWithText(FakeData.videos.first().title, substring = true).fetchSemanticsNodes().size, "Trending must not stand in for the feed")
        assertTrue(reads.calls >= 1)
    }

    @Test fun anAccountWithNoRecommendationsSaysSoAndOffersExplore() = runDesktopComposeUiTest(800, 1000) {
        val reads = Reads(emptyList()); val f = buildFixture(withDownloads = false, reads = reads); signIn(f)
        setContent { AppUnderTest(f) }; waitForIdle()
        waitUntil(timeoutMillis = 3_000) { runCatching { onNodeWithText("Your account has no recommendations yet.", substring = true).assertIsDisplayed() }.isSuccess }
        onNodeWithText("Browse Explore").assertExists()
        assertEquals(0, onAllNodesWithText(FakeData.videos.first().title, substring = true).fetchSemanticsNodes().size)
    }

    @Test fun theAccountFeedPagesByItsOwnContinuationAndNeverAppendsTrending() = runDesktopComposeUiTest(800, 1000) {
        val reads = Reads(null)
        reads.pages = mapOf(
            "" to HomePage(rec, "next-1"),
            "next-1" to HomePage(listOf(VideoSummary("rec2", "Second page video", "UCr", "Rec Channel")), null),
        )
        val f = buildFixture(withDownloads = false, reads = reads); signIn(f)
        setContent { AppUnderTest(f) }; waitForIdle()
        waitUntil(timeoutMillis = 5_000) { runCatching { onNodeWithText("Second page video").assertExists() }.isSuccess }
        assertEquals(listOf<String?>(null, "next-1"), reads.continuationsAsked, "the first page, then its continuation, then nothing: the feed ended")
        assertEquals(0, onAllNodesWithText(FakeData.videos.first().title, substring = true).fetchSemanticsNodes().size, "no Trending after the feed's last page")
    }

    @Test fun signedOutHomeHasNoFeedJustShelvesAndASignInCard() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(withDownloads = false)
        setContent { AppUnderTest(f) }; waitForIdle()
        waitUntil(timeoutMillis = 3_000) { runCatching { onNodeWithTag("home.signin", useUnmergedTree = true).assertExists() }.isSuccess }
        onNodeWithTag("home.signin.button", useUnmergedTree = true).assertExists()
        onNodeWithTag("home.signin.explore", useUnmergedTree = true).assertExists()
        // (Shelves may show videos of their own; what must be gone is a stand-in feed and its "Trending" note.)
        assertEquals(0, onAllNodesWithTag("home.note", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun signedOutNeverAsksTheAccount() = runDesktopComposeUiTest(800, 1000) {
        val reads = Reads(rec)
        val signedOut = buildFixture(withDownloads = false, reads = reads)
        setContent { AppUnderTest(signedOut) }; waitForIdle()
        assertEquals(0, reads.calls, "signed out = Trending, no account call")
    }

    @Test fun withYouTubesChipsSwitchedOffHomeIsJustTheFeedWithTheFilterButton() = runDesktopComposeUiTest(800, 1000) {
        val reads = chipReads(); val f = buildFixture(withDownloads = false, reads = reads); signIn(f)
        f.services.settings.set(Settings.homeYoutubeChips, false)
        setContent { AppUnderTest(f) }; waitForIdle()
        waitUntil(timeoutMillis = 3_000) { runCatching { onNodeWithText("A recommended one").assertExists() }.isSuccess }
        onNodeWithTag("home.filter", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, onAllNodesWithTag("home.chip.ALL", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, onAllNodesWithTag("home.chip.My feed", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun theChipRowStaysAboveTheListSignedOutWithOnlyTheFilterButton() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(withDownloads = false)
        setContent { AppUnderTest(f) }; waitForIdle()
        runCatching { waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("home.chips", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() } }
            .onFailure { println("TREE>> " + onRoot(useUnmergedTree = true).printToString(40)); throw it }
        onNodeWithTag("home.chips", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("home.filter", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, onAllNodesWithTag("home.chip.ALL", useUnmergedTree = true).fetchSemanticsNodes().size, "signed out has no YouTube chips")
    }

    private val chipRow = listOf(
        HomeChip("Make a feed", "tok-create", false, HomeChipKind.CREATE_CUSTOM_FEED),
        HomeChip("All", null, true, HomeChipKind.ALL),
        HomeChip("My feed", "tok-my", false, HomeChipKind.CUSTOM_FEED),
        HomeChip("Empty one", "tok-empty", false, HomeChipKind.TOPIC),
    )
    private fun chipReads(): Reads = Reads(null).also { r ->
        r.pages = mapOf("" to HomePage(rec, null, chipRow))
        r.chipPages = mapOf(
            "tok-my" to HomePage(listOf(VideoSummary("my1", "Custom feed video", "UCm", "My Channel")), null),
            "tok-empty" to HomePage(emptyList(), null),
        )
    }

    @Test fun signedInShowsYouTubesChipsAndTheCreateChipIsLeftOut() = runDesktopComposeUiTest(800, 1000) {
        val reads = chipReads(); val f = buildFixture(withDownloads = false, reads = reads); signIn(f)
        setContent { AppUnderTest(f) }; waitForIdle()
        waitUntil(timeoutMillis = 3_000) { runCatching { onNodeWithTag("home.chip.My feed", useUnmergedTree = true).assertIsDisplayed() }.isSuccess }
        onNodeWithTag("home.chip.ALL", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, onAllNodesWithTag("home.chip.Make a feed", useUnmergedTree = true).fetchSemanticsNodes().size, "creating a custom feed is not offered here")
        onNodeWithText("A recommended one").assertExists()
    }

    @Test fun aChipLoadsItsOwnFeedAndAllComesBackWithoutAskingAgain() = runDesktopComposeUiTest(800, 1000) {
        val reads = chipReads(); val f = buildFixture(withDownloads = false, reads = reads); signIn(f)
        setContent { AppUnderTest(f) }; waitForIdle()
        waitUntil(timeoutMillis = 3_000) { runCatching { onNodeWithTag("home.chip.My feed", useUnmergedTree = true).assertIsDisplayed() }.isSuccess }
        onNodeWithTag("home.chip.My feed", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3_000) { runCatching { onNodeWithText("Custom feed video").assertExists() }.isSuccess }
        assertEquals(listOf("tok-my"), reads.chipsAsked)
        assertEquals(0, onAllNodesWithText("A recommended one").fetchSemanticsNodes().size, "the chip shows its own list, not the feed")
        val callsBefore = reads.calls
        onNodeWithTag("home.chip.ALL", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3_000) { runCatching { onNodeWithText("A recommended one").assertExists() }.isSuccess }
        assertEquals(callsBefore, reads.calls, "the feed that was open is shown again from memory")
    }

    @Test fun aChipWithNothingInItIsLeftOutOfTheRow() = runDesktopComposeUiTest(800, 1000) {
        val reads = chipReads(); val f = buildFixture(withDownloads = false, reads = reads); signIn(f)
        setContent { AppUnderTest(f) }; waitForIdle()
        waitUntil(timeoutMillis = 3_000) { runCatching { onNodeWithTag("home.chip.Empty one", useUnmergedTree = true).assertIsDisplayed() }.isSuccess }
        onNodeWithTag("home.chip.Empty one", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3_000) { runCatching { onNodeWithText("Nothing in \"Empty one\"", substring = true).assertExists() }.isSuccess }
        assertEquals(0, onAllNodesWithTag("home.chip.Empty one", useUnmergedTree = true).fetchSemanticsNodes().size)
        onNodeWithText("Show all").performClick()
        waitUntil(timeoutMillis = 3_000) { runCatching { onNodeWithText("A recommended one").assertExists() }.isSuccess }
    }

    @Test fun theFilterMenuNarrowsTheListOnTopOfTheOpenChip() = runDesktopComposeUiTest(800, 1000) {
        val reads = chipReads(); reads.pages = mapOf("" to HomePage(listOf(
            VideoSummary("long", "A long video", "UCr", "Rec Channel", durationSeconds = 3_000), VideoSummary("short", "A short video", "UCr", "Rec Channel", durationSeconds = 300)), null, chipRow))
        val f = buildFixture(withDownloads = false, reads = reads); signIn(f)
        setContent { AppUnderTest(f) }; waitForIdle()
        waitUntil(timeoutMillis = 3_000) { runCatching { onNodeWithText("A long video").assertExists() }.isSuccess }
        onNodeWithTag("home.filter", useUnmergedTree = true).performClick()
        onNodeWithTag("home.filter.SHORT", useUnmergedTree = true).performClick(); waitForIdle()
        onNodeWithText("A short video").assertExists()
        assertEquals(0, onAllNodesWithText("A long video").fetchSemanticsNodes().size)
    }

    @Test fun firstLoadShowsOnlyTheListsPlaceholdersNotShelfPlaceholdersOnTopOfThem() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(withDownloads = false)
        setContent { AppUnderTest(f) }
        // At most 3 list placeholders and, while the feed has not answered, no shelf placeholder at all.
        val skeletons = onAllNodesWithTag("skeleton", useUnmergedTree = true).fetchSemanticsNodes().size
        val shelves = onAllNodesWithTag("carousel.skeleton", useUnmergedTree = true).fetchSemanticsNodes().size
        assertTrue(skeletons <= 3, "list placeholders: $skeletons")
        assertTrue(shelves == 0 || skeletons == 0, "shelf placeholders ($shelves) must not sit over list placeholders ($skeletons)")
    }
}
