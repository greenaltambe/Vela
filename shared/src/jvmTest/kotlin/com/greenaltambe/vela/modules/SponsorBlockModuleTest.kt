package com.greenaltambe.vela.modules

import com.greenaltambe.vela.core.http.ApiClient
import com.greenaltambe.vela.core.http.FakeClock
import com.greenaltambe.vela.core.http.FailureKind
import com.greenaltambe.vela.core.http.FakeTransport
import com.greenaltambe.vela.core.http.HttpResponse
import com.greenaltambe.vela.core.http.InMemoryApiCache
import com.greenaltambe.vela.core.http.ModuleLog
import com.greenaltambe.vela.core.http.TransportException
import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.core.settings.SegmentActionSetting
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingLayer
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.modules.sponsorblock.Segment
import com.greenaltambe.vela.modules.sponsorblock.SegmentCategory
import com.greenaltambe.vela.modules.sponsorblock.SegmentLookup
import com.greenaltambe.vela.modules.sponsorblock.SegmentRepository
import com.greenaltambe.vela.modules.sponsorblock.SegmentsResult
import com.greenaltambe.vela.modules.sponsorblock.SponsorBlockAddon
import com.greenaltambe.vela.modules.sponsorblock.SponsorBlockApi
import com.greenaltambe.vela.modules.sponsorblock.SubmitOutcome
import com.greenaltambe.vela.player.AddonHost
import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.player.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

private class RecordingHost(override val scope: CoroutineScope) : AddonHost {
    val seeks = mutableListOf<Long>()
    val mutes = mutableListOf<Boolean>()
    override fun seekTo(positionMs: Long) { seeks += positionMs }
    override fun setAddonMuted(muted: Boolean) { mutes += muted }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SponsorBlockModuleTest {
    private val transport = FakeTransport(Fixtures::server)
    private val clock = FakeClock()
    private val client = ApiClient(transport, InMemoryApiCache(), clock, sleep = {})
    private val settings = SettingsResolver(InMemorySettingsStore())
    private val db = createDatabase(JvmDatabaseDriverFactory())
    private val api = SponsorBlockApi(client, { "https://sponsor.ajay.app" })
    private val repo = SegmentRepository(api, db, clock)
    private val log = ModuleLog()
    private val video = Fixtures.SPONSORED_VIDEO

    private fun playing(ms: Long) = PlayerState(PlaybackStatus.PLAYING, ms, 950_000)

    // ---- the API, against the real captured answer ----

    @Test fun onlyTheRequestedVideoIsPickedOutOfThePrefixAnswer() = runTest {
        val found = assertIs<SegmentLookup.Found>(api.fetch(video, SegmentCategory.entries))
        assertTrue(found.segments.isNotEmpty())
        assertTrue(found.segments.any { it.category == "sponsor" }, "this video has a sponsor segment")
        // Segments of the other ~100 videos sharing the prefix must not leak in.
        assertTrue(found.segments.all { it.uuid.isNotEmpty() && it.endMs >= it.startMs })
        val raw = Fixtures.text("sb_skipsegments_$video.json")
        assertTrue(raw.count { it == '[' } > 100, "the real answer is large and holds other videos")
        val sponsor = found.segments.first { it.category == "sponsor" }
        assertEquals(88_033, sponsor.startMs)
        assertEquals(109_099, sponsor.endMs)
        assertEquals(4, sponsor.votes)
    }

    @Test fun theRequestSendsOnlyTheHashPrefixNeverTheVideoId() = runTest {
        api.fetch(video, SegmentCategory.entries)
        val url = transport.requests.single().url
        assertTrue("/api/skipSegments/efd7?" in url, url)
        assertFalse(video in url)
        assertTrue("categories=" in url && "actionTypes=" in url)
        assertTrue(transport.requests.single().headers["User-Agent"]!!.startsWith("Vela/"))
    }

    @Test fun aVideoWithNothingIsAnEmptyListNotAFailure() = runTest {
        val found = assertIs<SegmentLookup.Found>(api.fetch("zzzzzzzzzzz", SegmentCategory.entries))
        assertTrue(found.segments.isEmpty())
    }

    @Test fun anOutageIsUnavailableNotAnException() = runTest {
        transport.handler = { throw TransportException("down") }
        val r = assertIs<SegmentLookup.Unavailable>(api.fetch(video, SegmentCategory.entries))
        assertEquals(FailureKind.OFFLINE, r.kind)
    }

    @Test fun garbageAnswersAreEmptyNotACrash() = runTest {
        transport.handler = { HttpResponse(200, "<html>not json</html>") }
        assertTrue(assertIs<SegmentLookup.Found>(api.fetch(video, SegmentCategory.entries)).segments.isEmpty())
    }

    // ---- the repository (the downloader's entry point) ----

    @Test fun segmentsAreStoredAndUsedOfflineAfterwards() = runTest {
        val first = assertIs<SegmentsResult.Loaded>(repo.get(video, SegmentCategory.entries))
        assertEquals(SegmentsResult.Source.NETWORK, first.source)
        transport.handler = { throw TransportException("offline") }
        val offline = assertIs<SegmentsResult.Loaded>(repo.get(video, SegmentCategory.entries, allowNetwork = false))
        assertEquals(first.segments, offline.segments)
        assertEquals(SegmentsResult.Source.STORE, offline.source)
    }

    @Test fun anOldStoredCopyIsRefreshedWhenOnlineAndKeptWhenNot() = runTest {
        repo.get(video, SegmentCategory.entries)
        clock.advance(48.hours)
        transport.handler = { throw TransportException("offline") }
        val kept = assertIs<SegmentsResult.Loaded>(repo.get(video, SegmentCategory.entries))
        assertEquals(SegmentsResult.Source.STALE_STORE, kept.source)
        assertTrue(kept.segments.isNotEmpty())
        transport.handler = Fixtures::server
        assertEquals(SegmentsResult.Source.NETWORK, assertIs<SegmentsResult.Loaded>(repo.get(video, SegmentCategory.entries)).source)
    }

    @Test fun nothingStoredAndOfflineIsUnavailable() = runTest {
        transport.handler = { throw TransportException("offline") }
        assertIs<SegmentsResult.Unavailable>(repo.get(video, SegmentCategory.entries))
        assertEquals(SegmentsResult.Loaded(emptyList(), SegmentsResult.Source.STORE), repo.get(video, SegmentCategory.entries, allowNetwork = false))
    }

    @Test fun storeAndDeleteForTheDownloader() {
        val s = Segment("u1", "sponsor", "skip", 1_000, 2_000)
        repo.store("abc", listOf(s))
        assertEquals(listOf(s), repo.stored("abc")?.first)
        repo.delete("abc")
        assertNull(repo.stored("abc"))
    }

    // ---- the add-on, driven like the session drives it ----

    private fun TestScope.addon(): Pair<SponsorBlockAddon, RecordingHost> {
        val host = RecordingHost(backgroundScope)
        val addon = SponsorBlockAddon(repo, api, settings, log, clock)
        addon.attach(host)
        return addon to host
    }

    @Test fun sponsorsAreSkippedWithANoticeAndUndoReturnsToTheStart() = runTest {
        val (addon, host) = addon()
        val notices = mutableListOf<com.greenaltambe.vela.modules.sponsorblock.SkipNotice>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { addon.notices.collect { notices += it } }
        addon.onVideo(video, "UC", false)
        runCurrent()
        assertTrue(addon.segments.value.isNotEmpty())

        addon.onPlayerState(playing(87_000))
        assertTrue(host.seeks.isEmpty())
        addon.onPlayerState(playing(88_200))
        assertEquals(listOf(109_099L), host.seeks)
        runCurrent()
        assertEquals("Sponsor", notices.single().label)

        addon.undo(notices.single())
        assertEquals(88_033L, host.seeks.last())
        addon.onPlayerState(playing(88_500))
        assertEquals(2, host.seeks.size, "after Undo the segment plays and is not skipped again")
    }

    @Test fun noNoticeWhenTheSettingIsOff() = runTest {
        settings.set(Settings.sponsorBlockNotifications, false)
        val (addon, host) = addon()
        val notices = mutableListOf<Any>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { addon.notices.collect { notices += it } }
        addon.onVideo(video, "UC", false); runCurrent()
        addon.onPlayerState(playing(88_200)); runCurrent()
        assertEquals(1, host.seeks.size)
        assertTrue(notices.isEmpty())
    }

    @Test fun categoryActionsAndPerChannelOverridesAreHonoured() = runTest {
        settings.set(Settings.sponsorBlockActions.getValue("sponsor"), SegmentActionSetting.PROMPT)
        settings.set(Settings.sponsorBlockActions.getValue("sponsor"), SegmentActionSetting.SKIP, SettingLayer.channel("UCfriend"))
        val (addon, host) = addon()
        addon.onVideo(video, "UCother", false); runCurrent()
        addon.onPlayerState(playing(88_200))
        assertTrue(host.seeks.isEmpty(), "prompt instead of skip")
        assertEquals("sponsor", addon.prompt.value?.category)
        addon.acceptPrompt()
        assertEquals(109_099L, host.seeks.single())
        assertNull(addon.prompt.value)

        val (second, host2) = addon()
        second.onVideo(video, "UCfriend", false); runCurrent()
        second.onPlayerState(playing(88_200))
        assertEquals(listOf(109_099L), host2.seeks, "the channel override skips")
    }

    @Test fun anIgnoredCategoryHasNoMarkers() = runTest {
        SegmentCategory.entries.forEach { settings.set(Settings.sponsorBlockActions.getValue(it.id), SegmentActionSetting.IGNORE) }
        val (addon, host) = addon()
        addon.onVideo(video, "UC", false); runCurrent()
        assertTrue(addon.segments.value.isEmpty())
        addon.onPlayerState(playing(88_200))
        assertTrue(host.seeks.isEmpty())
    }

    @Test fun disabledModuleDoesNothingAndAsksForNothing() = runTest {
        settings.set(Settings.sponsorBlockEnabled, false)
        val (addon, host) = addon()
        addon.onVideo(video, "UC", false); runCurrent()
        addon.onPlayerState(playing(88_200))
        assertTrue(transport.requests.isEmpty())
        assertTrue(host.seeks.isEmpty())
    }

    @Test fun anOutageNeverBlocksPlaybackAndShowsInDiagnostics() = runTest {
        transport.handler = { throw TransportException("no network") }
        val (addon, host) = addon()
        addon.onVideo(video, "UC", false); runCurrent()
        addon.onPlayerState(playing(88_200))
        assertTrue(host.seeks.isEmpty())
        val status = log.status().getValue("sponsorblock")
        assertFalse(status.ok)
        assertTrue("inactive" in status.message)
    }

    @Test fun stoppingClearsMarkersPromptAndMute() = runTest {
        val (addon, host) = addon()
        addon.onVideo(video, "UC", false); runCurrent()
        assertTrue(addon.segments.value.isNotEmpty())
        addon.onStop()
        assertTrue(addon.segments.value.isEmpty())
        assertEquals(false, host.mutes.last())
    }

    // ---- submission ----

    @Test fun submissionSendsTheSegmentOnlyWhenCalledAndNeverWithoutAVideo() = runTest {
        val (addon, _) = addon()
        assertIs<SubmitOutcome.Failed>(addon.submit(SegmentCategory.SPONSOR, 1_000, 5_000, 100_000))
        addon.onVideo(video, "UC", false); runCurrent()
        transport.requests.clear()
        assertIs<SubmitOutcome.Failed>(addon.submit(SegmentCategory.SPONSOR, 5_000, 5_000, 100_000), "end must be after start")
        assertTrue(transport.requests.isEmpty(), "no request for an invalid segment")

        assertEquals(SubmitOutcome.Sent, addon.submit(SegmentCategory.SPONSOR, 10_000, 20_000, 950_000))
        val post = transport.requests.single()
        assertEquals("POST", post.method)
        assertTrue(post.url.endsWith("/api/skipSegments"))
        val body = post.body!!
        assertTrue("\"videoID\":\"$video\"" in body && "\"category\":\"sponsor\"" in body && "[10.0,20.0]" in body, body)
        assertTrue(addon.userId().length >= 30)
        assertEquals(addon.userId(), settings.resolve(Settings.sponsorBlockUserId), "the id is created once and kept")
    }

    @Test fun aRejectedSubmissionIsReportedNotThrown() = runTest {
        val (addon, _) = addon()
        addon.onVideo(video, "UC", false); runCurrent()
        transport.handler = { HttpResponse(400, "bad segment") }
        assertIs<SubmitOutcome.Failed>(addon.submit(SegmentCategory.SPONSOR, 1_000, 2_000, 950_000))
    }

    @Test fun theUserIdIsNeverInTheLookupRequest() = runTest {
        val (addon, _) = addon()
        addon.userId()
        addon.onVideo(video, "UC", false); runCurrent()
        assertNotNull(settings.resolve(Settings.sponsorBlockUserId))
        assertTrue(transport.requests.none { settings.resolve(Settings.sponsorBlockUserId) in it.url })
    }
}
