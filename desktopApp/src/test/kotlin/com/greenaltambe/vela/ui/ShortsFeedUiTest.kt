@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.greenaltambe.vela.auth.*
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.ShortsMode
import com.greenaltambe.vela.extractor.*
import com.greenaltambe.vela.ui.nav.Route
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Shorts page with YouTube's feeds: which one it shows, how it goes on, and what it passes over. */
class ShortsFeedUiTest {
    private val jar = CookieJar(listOf(Cookie("SID", "s", ".google.com"), Cookie("SAPISID", "p", ".youtube.com")))
    private fun bare(id: String) = VideoSummary(id, "", "", "", isShort = true)
    private fun titled(id: String) = VideoSummary(id, "Title $id", "", "", isShort = true)

    private class Account(var answer: (seed: String?, token: String?) -> Page<VideoSummary>?) : AccountReads {
        val asked = mutableListOf<Pair<String?, String?>>()
        override suspend fun shorts(seed: String?, continuation: String?): Page<VideoSummary>? { asked += seed to continuation; return answer(seed, continuation) }
        override suspend fun recommended(): List<VideoSummary>? = null
        override suspend fun playlists(): List<AccountPlaylist>? = null
        override suspend fun playlistVideos(playlistId: String): List<VideoSummary>? = null
    }

    /** Anonymous Shorts and lookups: each video's channel is "Channel <id>" with id "UC<id>". */
    private inner class Tube(var pages: (token: String?) -> Page<VideoSummary>? = { null }) : Extractor by FakeExtractor() {
        val askedShorts = mutableListOf<Pair<String?, String?>>()
        val lookups = mutableListOf<String>()
        override suspend fun shortsFeed(seed: String?, continuation: String?): ExtractorResult<Page<VideoSummary>> {
            askedShorts += seed to continuation
            return pages(continuation)?.let { ExtractorResult.Success(it) } ?: ExtractorResult.Failure(ExtractorError.Network("down"))
        }
        override suspend fun videoInfo(videoId: String): ExtractorResult<VideoInfo> {
            synchronized(lookups) { lookups += videoId }
            return ExtractorResult.Success(VideoInfo(videoId, "Looked $videoId", "", "UC$videoId", "Channel $videoId", 20))
        }
        override suspend fun channelShorts(channelId: String, continuation: String?) = ExtractorResult.Success(Page(listOf(titled("mix-$channelId")), null))
        override suspend fun trending(continuation: String?) = ExtractorResult.Success(Page(emptyList<VideoSummary>(), null))
    }

    private fun ten(prefix: String) = Page((1..10).map { bare("$prefix$it") }, null)

    private fun DesktopComposeUiTest.open(tube: Tube, account: Account? = null, route: Route = Route.Shorts, prepare: (Fixture) -> Unit = {}): Fixture {
        val f = buildFixture(withDownloads = false, reads = account, extractorOverride = tube)
        if (account != null) f.services.accounts.addGoogle(GoogleIdentity("P", "p@example.com", null, "UC1"), jar)
        prepare(f)
        if (route == Route.Shorts) f.navigator.selectTab(Route.Shorts) else f.navigator.push(route)
        setContent { AppUnderTest(f) }; waitForIdle()
        return f
    }
    private fun Fixture.playing() = services.player!!.session.media.value?.videoId
    private fun DesktopComposeUiTest.waitPlaying(f: Fixture, id: String) = waitUntil("playing $id (is ${f.playing()})", 5_000) { f.playing() == id }
    private fun DesktopComposeUiTest.next() { onNodeWithTag("shorts.pager").performTouchInput { swipeUp() }; waitForIdle() }
    private fun DesktopComposeUiTest.previous() { onNodeWithTag("shorts.pager").performTouchInput { swipeDown() }; waitForIdle() }

    @Test fun signedInTheTabShowsTheAccountsFeed() = runDesktopComposeUiTest(412, 900) {
        val account = Account { _, token -> if (token == null) Page(listOf(bare("acc1")), "t1") else ten("acc-more") }
        val tube = Tube()
        val f = open(tube, account)
        waitPlaying(f, "acc1")
        waitUntil(timeoutMillis = 5_000) { account.asked.size >= 2 }
        assertEquals(listOf(null to null, null to "t1"), account.asked.take(2), "the first Short at once, then the rest of its sequence")
        assertTrue(tube.askedShorts.isEmpty())
    }

    @Test fun signedOutItShowsTheAnonymousFeed() = runDesktopComposeUiTest(412, 900) {
        val tube = Tube { token -> if (token == null) Page(listOf(bare("an1")), "u1") else ten("an-more") }
        val f = open(tube)
        waitPlaying(f, "an1")
    }

    @Test fun whenTheAccountCannotBeReadTheAnonymousFeedTakesOver() = runDesktopComposeUiTest(412, 900) {
        val tube = Tube { ten("an") }
        val f = open(tube, Account { _, _ -> null })
        waitPlaying(f, "an1")
        assertTrue(f.services.log.attempts.value.any { it.subject == "shorts feed" })
    }

    @Test fun whenNeitherCanBeReadTheChannelsShortsAreShown() = runDesktopComposeUiTest(412, 900) {
        val f = open(Tube())
        waitUntil(timeoutMillis = 5_000) { f.playing()?.startsWith("mix-") == true }
    }

    @Test fun theNextShortsDetailsAreFetchedAheadAndOnlyOnce() = runDesktopComposeUiTest(412, 900) {
        val tube = Tube { ten("an") }
        val f = open(tube)
        waitPlaying(f, "an1")
        waitUntil(timeoutMillis = 5_000) { "an2" in tube.lookups }; waitForIdle()
        next(); waitPlaying(f, "an2")
        waitUntil(timeoutMillis = 5_000) { "an3" in tube.lookups }; waitForIdle()
        assertEquals(1, tube.lookups.count { it == "an2" }, "it started with its details: ${tube.lookups}")
        assertEquals("UCan2", f.services.player!!.session.media.value?.channelId)
    }

    @Test fun aBlockedChannelsShortIsPassedOverBothWaysAndStaysInPlace() = runDesktopComposeUiTest(412, 900) {
        val tube = Tube { ten("an") }
        val f = open(tube) { it.services.blocklist.blockChannel("UCan2", "Channel an2") }
        waitPlaying(f, "an1")
        waitUntil(timeoutMillis = 5_000) { "an2" in tube.lookups }; waitForIdle()
        next(); waitPlaying(f, "an3")
        previous(); waitPlaying(f, "an1")
        next(); waitPlaying(f, "an3")
    }

    @Test fun aShortFoundToBeBlockedWhilePlayingIsLeft() = runDesktopComposeUiTest(412, 900) {
        val tube = Tube { ten("an") }
        val f = open(tube) { it.services.blocklist.blockChannel("UCan1", "Channel an1") }
        waitPlaying(f, "an2")
    }

    @Test fun aShortFromAYouTubeRowPlaysTheRowThenGoesOnFromItsSeed() = runDesktopComposeUiTest(412, 900) {
        val account = Account { seed, _ -> if (seed == "row-seed") Page(listOf(titled("r1"), titled("after1"), titled("after2")), "t9") else null }
        val tapped = titled("r1").copy(shortsSequence = "row-seed")
        val f = open(Tube(), account, Route.ShortsPlayer(tapped, following = listOf(titled("r2")), fromYouTubeRow = true))
        waitPlaying(f, "r1")
        waitUntil(timeoutMillis = 5_000) { account.asked.isNotEmpty() }
        assertEquals("row-seed" to null, account.asked.first())
        next(); waitPlaying(f, "r2")
        next(); waitPlaying(f, "after1")
    }

    @Test fun aShortOpenedElsewhereDoesNotAskYouTubesFeed() = runDesktopComposeUiTest(412, 900) {
        val account = Account { _, _ -> ten("acc") }
        val tube = Tube()
        val f = open(tube, account, Route.ShortsPlayer(VideoSummary("c1", "From a channel", "UCx", "X", isShort = true), stayOnChannel = true))
        waitPlaying(f, "c1"); waitForIdle()
        assertTrue(account.asked.isEmpty() && tube.askedShorts.isEmpty())
    }

    @Test fun changingTheRegionStartsTheFeedAfresh() = runDesktopComposeUiTest(412, 900) {
        lateinit var f: Fixture
        val tube = Tube()
        tube.pages = { ten(f.services.settings.resolve(Settings.contentRegion)) }
        f = buildFixture(withDownloads = false, extractorOverride = tube)
        f.navigator.selectTab(Route.Shorts)
        setContent { AppUnderTest(f) }; waitForIdle()
        waitPlaying(f, "US1")
        f.services.settings.set(Settings.contentRegion, "IN"); waitForIdle()
        waitPlaying(f, "IN1")
    }

    @Test fun anAnswerForTheOldRegionThatArrivesLateIsNotShown() = runDesktopComposeUiTest(412, 900) {
        lateinit var f: Fixture
        val late = kotlinx.coroutines.CompletableDeferred<Unit>()
        var oldAsked = false
        val tube = object : Extractor by FakeExtractor() {
            override suspend fun shortsFeed(seed: String?, continuation: String?): ExtractorResult<Page<VideoSummary>> {
                val region = f.services.settings.resolve(Settings.contentRegion)
                if (region == "US") { oldAsked = true; late.await() }
                return ExtractorResult.Success(ten(region))
            }
        }
        f = buildFixture(withDownloads = false, extractorOverride = tube)
        f.navigator.selectTab(Route.Shorts)
        setContent { AppUnderTest(f) }; waitForIdle()
        waitUntil(timeoutMillis = 5_000) { oldAsked }
        f.services.settings.set(Settings.contentRegion, "IN"); waitForIdle()
        waitPlaying(f, "IN1")
        late.complete(Unit); waitForIdle()
        assertEquals("IN1", f.playing())
        assertEquals(0, onAllNodesWithText("US1", substring = true).fetchSemanticsNodes().size)
    }

    @Test fun signingInWhileOnTheTabTurnsToTheAccountsFeed() = runDesktopComposeUiTest(412, 900) {
        val account = Account { _, _ -> ten("acc") }
        val tube = Tube { ten("an") }
        val f = buildFixture(withDownloads = false, reads = account, extractorOverride = tube)
        f.navigator.selectTab(Route.Shorts)
        setContent { AppUnderTest(f) }; waitForIdle()
        waitPlaying(f, "an1")
        f.services.accounts.addGoogle(GoogleIdentity("P", "p@example.com", null, "UC1"), jar); waitForIdle()
        waitPlaying(f, "acc1")
    }

    @Test fun withShortsHiddenNothingIsFetched() = runDesktopComposeUiTest(412, 900) {
        val tube = Tube { ten("an") }
        val f = open(tube, route = Route.ShortsPlayer(titled("x"), fromYouTubeRow = true)) { it.services.settings.set(Settings.shortsMode, ShortsMode.HIDE) }
        waitForIdle()
        assertTrue(onAllNodesWithText("Shorts are hidden in your settings.").fetchSemanticsNodes().isNotEmpty())
        assertTrue(tube.askedShorts.isEmpty() && f.playing() == null)
    }

    @Test fun whenNothingCanBeFetchedItSaysSoAndTryAgainStartsOver() = runDesktopComposeUiTest(412, 900) {
        var up = false
        val tube = object : Extractor by FakeExtractor() {
            override suspend fun shortsFeed(seed: String?, continuation: String?): ExtractorResult<Page<VideoSummary>> =
                if (up) ExtractorResult.Success(Page(listOf(titled("back")), null)) else ExtractorResult.Failure(ExtractorError.Network("down"))
            override suspend fun channelShorts(channelId: String, continuation: String?) = ExtractorResult.Success(Page(emptyList<VideoSummary>(), null))
            override suspend fun trending(continuation: String?) = ExtractorResult.Success(Page(emptyList<VideoSummary>(), null))
        }
        val f = buildFixture(withDownloads = false, extractorOverride = tube)
        f.navigator.selectTab(Route.Shorts)
        setContent { AppUnderTest(f) }; waitForIdle()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Shorts could not load", substring = true).fetchSemanticsNodes().isNotEmpty() }
        up = true
        onAllNodesWithText("Try again")[0].performClick()
        waitPlaying(f, "back")
    }
}
