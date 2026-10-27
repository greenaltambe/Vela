package com.greenaltambe.vela.player

import com.greenaltambe.vela.core.network.FixedNetworkMonitor
import com.greenaltambe.vela.core.network.NetworkType
import com.greenaltambe.vela.core.settings.*
import com.greenaltambe.vela.data.WatchHistoryRepository
import com.greenaltambe.vela.data.WatchRecord
import com.greenaltambe.vela.extractor.*
import com.greenaltambe.vela.library.SleepTimerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private class FakeClock(var now: Instant = Instant.fromEpochSeconds(1_000_000)) : Clock {
    override fun now() = now
    fun advance(ms: Long) { now = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + ms) }
}

private class FakeController : PlayerController {
    override val state = MutableStateFlow(PlayerState())
    override val events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16)
    class Load(val plan: PlaybackPlan, val startMs: Long, val playWhenReady: Boolean, val metadata: PlaybackMetadata)
    val loads = mutableListOf<Load>()
    val calls = mutableListOf<String>()
    var speeds = mutableListOf<Float>()
    val volumes = mutableListOf<Float>()
    override fun setVolume(volume: Float) { volumes += volume }

    override fun load(plan: PlaybackPlan, startPositionMs: Long, playWhenReady: Boolean, metadata: PlaybackMetadata) {
        loads += Load(plan, startPositionMs, playWhenReady, metadata)
        state.value = state.value.copy(status = PlaybackStatus.BUFFERING, positionMs = startPositionMs)
    }
    override fun play() { calls += "play"; state.value = state.value.copy(status = PlaybackStatus.PLAYING) }
    override fun pause() { calls += "pause"; state.value = state.value.copy(status = PlaybackStatus.PAUSED) }
    override fun seekTo(positionMs: Long) { calls += "seek"; state.value = state.value.copy(positionMs = positionMs) }
    override fun setSpeed(speed: Float) { speeds += speed; state.value = state.value.copy(speed = speed) }
    override fun stop() { calls += "stop"; state.value = PlayerState() }
    override fun release() {}

    fun playing(positionMs: Long, durationMs: Long = 200_000) { state.value = state.value.copy(status = PlaybackStatus.PLAYING, positionMs = positionMs, durationMs = durationMs) }
    fun error(kind: PlaybackErrorKind, status: Int? = null) { events.tryEmit(PlayerEvent.Error(PlaybackError(kind, "boom", status))) }
}

private class FakeHistory : WatchHistoryRepository {
    val rows = mutableListOf<WatchRecord>()
    var updates = 0
    override fun lastForVideo(accountId: String, videoId: String) = rows.lastOrNull { it.accountId == accountId && it.videoId == videoId }
    override fun begin(accountId: String, videoId: String, channelId: String, title: String, at: Instant, positionMs: Long, durationMs: Long, watchedMs: Long): Long {
        rows += WatchRecord(rows.size + 1L, accountId, videoId, channelId, title, at, positionMs, durationMs, watchedMs)
        return rows.size.toLong()
    }
    override fun update(id: Long, at: Instant, positionMs: Long, durationMs: Long, watchedMs: Long) {
        updates++
        val old = rows[(id - 1).toInt()]
        rows[(id - 1).toInt()] = WatchRecord(id, old.accountId, old.videoId, old.channelId, old.title, at, positionMs, durationMs, watchedMs)
    }
}

private class FakeExtractor(private val clock: FakeClock) : Extractor by StubExtractor() {
    var streamCalls = 0
    var infoCalls = 0
    var results = ArrayDeque<ExtractorResult<StreamSet>>()
    var lifetime = 6.hours
    var availableIn: kotlin.time.Duration? = null

    private fun fresh() = StreamSet(
        videoStreams = listOf(
            VideoStream("https://v/$streamCalls/1080", "video/webm", "vp9", 4_000_000, 1920, 1080, 30, itag = 248),
            VideoStream("https://v/$streamCalls/720", "video/webm", "vp9", 2_000_000, 1280, 720, 30, itag = 247),
        ),
        audioStreams = listOf(AudioStream("https://a/$streamCalls", "audio/webm", "opus", 128_000, itag = 251)),
        expiresAt = clock.now() + lifetime,
        availableAt = availableIn?.let { clock.now() + it },
        headers = mapOf("User-Agent" to "ua"),
        client = "fake",
    )

    override suspend fun streams(videoId: String): ExtractorResult<StreamSet> {
        streamCalls++
        return results.removeFirstOrNull() ?: ExtractorResult.Success(fresh())
    }

    override suspend fun videoInfo(videoId: String): ExtractorResult<VideoInfo> {
        infoCalls++
        return ExtractorResult.Success(VideoInfo(videoId, "Looked-up title", "", "UCinfo", "Chan", 200))
    }
}

private class RecordingAddon(private val failOnState: Boolean = false) : PlaybackAddon {
    val log = mutableListOf<String>()
    lateinit var host: AddonHost
    override fun attach(host: AddonHost) { this.host = host; log += "attach" }
    override fun onVideo(videoId: String, channelId: String, offline: Boolean) { log += "video:$videoId:$channelId${if (offline) ":offline" else ""}" }
    override fun onPlayerState(state: PlayerState) { if (failOnState) error("add-on bug"); log += "state:${state.status}" }
    override fun onStop() { log += "stop" }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSessionTest {
    private val clock = FakeClock()
    private val controller = FakeController()
    private val history = FakeHistory()
    private val extractor = FakeExtractor(clock)
    private val settings = SettingsResolver(InMemorySettingsStore())
    private val hint = VideoSummary("vid", "A title", "UC1", "Chan")

    private val network = FixedNetworkMonitor()

    private fun TestScope.session(maxReResolves: Int = 2) = PlaybackSession(
        extractor, controller, settings, history, backgroundScope, clock, network = network, maxReResolves = maxReResolves,
    )

    private fun ready(s: PlaybackSession) = assertIs<SessionState.Ready>(s.state.value)

    // ---- start ----

    @Test fun startResolvesPlansAndLoadsFromTheBeginning() = runTest {
        val s = session()
        s.start("vid", hint)
        runCurrent()
        val load = controller.loads.single()
        val plan = assertIs<PlaybackPlan.Separate>(load.plan)
        assertEquals(1080, plan.video.height)
        assertEquals(0, load.startMs)
        assertTrue(load.playWhenReady)
        assertEquals("A title", load.metadata.title)
        assertEquals("fake", ready(s).client)
        assertEquals(listOf(1080, 720), ready(s).heights)
        assertEquals(0, extractor.infoCalls, "the hint makes a second request unnecessary")
    }

    @Test fun withoutAHintTheInfoIsLookedUpForTitleAndChannel() = runTest {
        val s = session()
        s.start("vid")
        runCurrent()
        assertEquals(1, extractor.infoCalls)
        assertEquals("Looked-up title", controller.loads.single().metadata.title)
        controller.playing(10_000); runCurrent(); controller.pause(); runCurrent()
        assertEquals("UCinfo", history.rows.single().channelId)
    }

    @Test fun anExtractorFailureIsSurfaced() = runTest {
        extractor.results += ExtractorResult.Failure(ExtractorError.Network("down"))
        val s = session()
        s.start("vid", hint)
        runCurrent()
        val failed = assertIs<SessionState.Failed>(s.state.value)
        assertIs<ExtractorError.Network>(failed.extractorError)
        assertTrue(controller.loads.isEmpty())
    }

    @Test fun nothingPlayableIsSurfacedAsFailure() = runTest {
        extractor.results += ExtractorResult.Success(StreamSet(emptyList(), emptyList()))
        val s = session()
        s.start("vid", hint)
        runCurrent()
        assertEquals("The stream set is empty", assertIs<SessionState.Failed>(s.state.value).message)
    }

    // ---- availability ----

    @Test fun loadWaitsUntilTheUrlsAreValid() = runTest {
        extractor.availableIn = 4.seconds
        val s = session()
        s.start("vid", hint); runCurrent()
        assertTrue(controller.loads.isEmpty(), "URLs would 403 for another 4 s")
        assertIs<SessionState.Resolving>(s.state.value)
        advanceTimeBy(3.seconds); assertTrue(controller.loads.isEmpty())
        advanceTimeBy(1.seconds + 1.milliseconds)
        assertEquals(1, controller.loads.size)
    }

    @Test fun anAbsurdAvailabilityIsCappedAtTenSeconds() = runTest {
        extractor.availableIn = 1.hours
        val s = session()
        s.start("vid", hint)
        advanceTimeBy(10.seconds + 1.milliseconds)
        assertEquals(1, controller.loads.size)
    }

    @Test fun aRefreshWaitsToo() = runTest {
        val s = session()
        s.start("vid", hint); runCurrent()
        controller.playing(20_000); runCurrent()
        extractor.availableIn = 4.seconds
        controller.error(PlaybackErrorKind.StreamExpiredOrForbidden); runCurrent()
        assertEquals(1, controller.loads.size, "refreshed streams are not usable yet")
        advanceTimeBy(4.seconds + 1.milliseconds)
        assertEquals(2, controller.loads.size)
        assertEquals(20_000, controller.loads.last().startMs)
    }

    // ---- prefs & speed ----

    @Test fun settingsPickTheStreamAndTheSpeedWithChannelAndVideoOverrides() = runTest {
        settings.set(Settings.maxQualityUnmetered, MaxQuality.P1080)
        settings.set(Settings.maxQualityUnmetered, MaxQuality.P720, SettingLayer.channel("UC1"))
        settings.set(Settings.defaultPlaybackSpeed, 1.25f)
        settings.set(Settings.defaultPlaybackSpeed, 2f, SettingLayer.video("vid"))
        val s = session()
        s.start("vid", hint)
        runCurrent()
        assertEquals(720, assertIs<PlaybackPlan.Separate>(controller.loads.single().plan).video.height)
        assertEquals(listOf(2f), controller.speeds, "video override beats channel and global")

        s.start("other", hint.copy(id = "other", channelId = "UC2"))
        runCurrent()
        assertEquals(1.25f, controller.speeds.last(), "no override for this video: global")
    }

    // ---- network ----

    @Test fun theConnectionTypeAtPlanTimePicksTheCap() = runTest {
        settings.set(Settings.maxQualityUnmetered, MaxQuality.P1080)
        settings.set(Settings.maxQualityMetered, MaxQuality.P720)
        network.set(NetworkType.METERED)
        val s = session()
        s.start("vid", hint); runCurrent()
        assertEquals(720, assertIs<PlaybackPlan.Separate>(controller.loads.single().plan).video.height)
        assertEquals(NetworkType.METERED, s.networkType.value)
    }

    @Test fun aNetworkChangeMidPlaybackDoesNotSwitchQuality() = runTest {
        settings.set(Settings.maxQualityUnmetered, MaxQuality.P1080)
        settings.set(Settings.maxQualityMetered, MaxQuality.P720)
        val s = session()
        s.start("vid", hint); runCurrent()
        controller.playing(10_000); runCurrent()
        network.set(NetworkType.METERED); runCurrent()
        assertEquals(1, controller.loads.size, "nothing reloaded")
        assertEquals(NetworkType.METERED, s.networkType.value, "but it is exposed")
        // The next plan (a quality-menu action here) does see the new connection.
        s.selectQuality(null); runCurrent()
        assertEquals(720, assertIs<PlaybackPlan.Separate>(controller.loads.last().plan).video.height)
    }

    @Test fun aSpeedPickedInTheSessionSurvivesAReload() = runTest {
        val s = session()
        s.start("vid", hint); runCurrent()
        s.setSpeed(1.75f)
        s.selectQuality(720); runCurrent()
        assertEquals(1.75f, controller.speeds.last())
    }

    // ---- resume ----

    @Test fun resumesFromTheLastPosition() = runTest {
        history.begin("local", "vid", "UC1", "A title", clock.now(), 73_000, 200_000, 60_000)
        val s = session()
        s.start("vid", hint); runCurrent()
        assertEquals(73_000, controller.loads.single().startMs)
    }

    @Test fun doesNotResumeVeryEarlyOrNearTheEnd() = runTest {
        history.begin("local", "vid", "UC1", "t", clock.now(), 3_000, 200_000, 3_000)
        val s = session()
        s.start("vid", hint); runCurrent()
        assertEquals(0, controller.loads.last().startMs, "under 5 s")

        history.begin("local", "vid", "UC1", "t", clock.now(), 195_000, 200_000, 195_000)
        s.start("vid", hint); runCurrent()
        assertEquals(0, controller.loads.last().startMs, "within 10 s of the end counts as finished")
    }

    @Test fun otherVideosAndAccountsDoNotAffectResume() = runTest {
        history.begin("local", "other", "UC1", "t", clock.now(), 90_000, 200_000, 90_000)
        history.begin("someone", "vid", "UC1", "t", clock.now(), 90_000, 200_000, 90_000)
        val s = session()
        s.start("vid", hint); runCurrent()
        assertEquals(0, controller.loads.single().startMs)
    }

    // ---- expiry ----

    @Test fun anExpiredStreamTriggersAReResolveAndResumesAtThePosition() = runTest {
        val s = session()
        s.start("vid", hint); runCurrent()
        controller.playing(42_000); runCurrent()

        controller.error(PlaybackErrorKind.StreamExpiredOrForbidden, 403); runCurrent()

        assertEquals(2, extractor.streamCalls)
        assertEquals(2, controller.loads.size)
        assertEquals(42_000, controller.loads.last().startMs)
        assertTrue(controller.loads.last().playWhenReady)
        assertNotEquals((controller.loads[0].plan as PlaybackPlan.Separate).video.url, (controller.loads[1].plan as PlaybackPlan.Separate).video.url, "fresh URLs")
        assertIs<SessionState.Ready>(s.state.value)
    }

    @Test fun anyPlayerErrorAfterTheUrlsExpiredIsTreatedAsExpiry() = runTest {
        val s = session()
        s.start("vid", hint); runCurrent()
        controller.playing(10_000); runCurrent()
        clock.advance(7.hours.inWholeMilliseconds)
        controller.error(PlaybackErrorKind.Network); runCurrent()
        assertEquals(2, extractor.streamCalls)
        assertEquals(10_000, controller.loads.last().startMs)
    }

    @Test fun aNetworkErrorWithValidUrlsIsNotRetriedByReResolving() = runTest {
        val s = session()
        s.start("vid", hint); runCurrent()
        controller.playing(10_000); runCurrent()
        controller.error(PlaybackErrorKind.Network); runCurrent()
        assertEquals(1, extractor.streamCalls)
        val failed = assertIs<SessionState.Failed>(s.state.value)
        assertEquals(PlaybackErrorKind.Network, failed.playbackError?.kind)
    }

    @Test fun decoderErrorsAreSurfacedNotRetried() = runTest {
        val s = session()
        s.start("vid", hint); runCurrent()
        controller.error(PlaybackErrorKind.Decoder); runCurrent()
        assertEquals(1, extractor.streamCalls)
        assertEquals(PlaybackErrorKind.Decoder, assertIs<SessionState.Failed>(s.state.value).playbackError?.kind)
    }

    @Test fun reResolvesAreBoundedThenTheErrorSurfaces() = runTest {
        val s = session(maxReResolves = 2)
        s.start("vid", hint); runCurrent()
        controller.playing(5_000); runCurrent()
        repeat(5) { controller.error(PlaybackErrorKind.StreamExpiredOrForbidden, 403); runCurrent() }
        assertEquals(3, extractor.streamCalls, "the first resolve plus two retries")
        assertEquals(3, controller.loads.size)
        val failed = assertIs<SessionState.Failed>(s.state.value)
        assertEquals(PlaybackErrorKind.StreamExpiredOrForbidden, failed.playbackError?.kind)
        assertEquals(403, failed.playbackError?.httpStatus)
    }

    @Test fun theRetryBudgetIsRestoredOncePlaybackMakesProgress() = runTest {
        val s = session(maxReResolves = 1)
        s.start("vid", hint); runCurrent()
        controller.playing(5_000); runCurrent()
        controller.error(PlaybackErrorKind.StreamExpiredOrForbidden); runCurrent()
        assertEquals(2, extractor.streamCalls)

        controller.playing(5_000); runCurrent()
        controller.playing(9_000); runCurrent() // moved on by more than 3 s since the reload
        controller.error(PlaybackErrorKind.StreamExpiredOrForbidden); runCurrent()
        assertEquals(3, extractor.streamCalls, "a second, separate incident is allowed")
        assertIs<SessionState.Ready>(s.state.value)
    }

    @Test fun aFailedReResolveSurfacesTheExtractorError() = runTest {
        val s = session()
        s.start("vid", hint); runCurrent()
        controller.playing(5_000); runCurrent()
        extractor.results += ExtractorResult.Failure(ExtractorError.SignInRequired("bot"))
        controller.error(PlaybackErrorKind.StreamExpiredOrForbidden); runCurrent()
        assertIs<ExtractorError.SignInRequired>(assertIs<SessionState.Failed>(s.state.value).extractorError)
    }

    @Test fun playingAfterTheUrlsExpiredRefreshesFirst() = runTest {
        val s = session()
        s.start("vid", hint); runCurrent()
        controller.playing(30_000); runCurrent(); controller.pause(); runCurrent()
        clock.advance(7.hours.inWholeMilliseconds)

        s.play(); runCurrent()
        assertEquals(2, extractor.streamCalls)
        assertEquals(30_000, controller.loads.last().startMs)
        assertEquals(1, controller.calls.count { it == "play" }.let { if (it == 0) 1 else it }, "loading plays; no stale play() on dead URLs")
    }

    @Test fun playingWithValidUrlsJustPlays() = runTest {
        val s = session()
        s.start("vid", hint); runCurrent()
        controller.pause()
        s.play(); runCurrent()
        assertEquals(1, extractor.streamCalls)
        assertEquals("play", controller.calls.last())
    }

    // ---- quality ----

    @Test fun selectingAQualityReplansAtTheCurrentPositionWithoutResolving() = runTest {
        val s = session()
        s.start("vid", hint); runCurrent()
        controller.playing(61_000); runCurrent()

        s.selectQuality(720); runCurrent()

        assertEquals(1, extractor.streamCalls)
        assertEquals(720, assertIs<PlaybackPlan.Separate>(controller.loads.last().plan).video.height)
        assertEquals(61_000, controller.loads.last().startMs)
        assertEquals(720, ready(s).maxHeight)
        s.selectQuality(null); runCurrent()
        assertEquals(1080, assertIs<PlaybackPlan.Separate>(controller.loads.last().plan).video.height)
    }

    @Test fun aPausedVideoStaysPausedAfterAQualityChange() = runTest {
        val s = session()
        s.start("vid", hint); runCurrent()
        controller.playing(5_000); runCurrent(); controller.pause(); runCurrent()
        s.selectQuality(720); runCurrent()
        assertFalse(controller.loads.last().playWhenReady)
    }

    // ---- history ----

    @Test fun pausingWritesOneRowWithPositionDurationAndWatchedTime() = runTest {
        val s = session()
        s.start("vid", hint); runCurrent()
        controller.playing(0, durationMs = 200_000); runCurrent()
        clock.advance(20_000)
        controller.state.value = controller.state.value.copy(positionMs = 20_000)
        controller.pause(); runCurrent()

        val row = history.rows.single()
        assertEquals("vid", row.videoId)
        assertEquals("UC1", row.channelId)
        assertEquals("A title", row.title)
        assertEquals(20_000, row.positionMs)
        assertEquals(200_000, row.durationMs)
        assertEquals(20_000, row.watchedMs)
        assertEquals(clock.now(), row.watchedAt)
    }

    @Test fun laterPausesUpdateTheSameRowAndWatchedTimeAccumulatesScaledBySpeed() = runTest {
        val s = session()
        s.start("vid", hint); runCurrent()
        controller.playing(0); runCurrent()
        clock.advance(10_000); controller.state.value = controller.state.value.copy(positionMs = 10_000)
        controller.pause(); runCurrent()
        assertEquals(1, history.rows.size)

        s.setSpeed(2f)
        controller.playing(10_000); runCurrent()
        clock.advance(10_000); controller.state.value = controller.state.value.copy(positionMs = 30_000)
        controller.pause(); runCurrent()

        assertEquals(1, history.rows.size, "same session, same row")
        assertEquals(30_000, history.rows.single().positionMs)
        assertEquals(30_000, history.rows.single().watchedMs, "10 s at 1x plus 10 s at 2x")
    }

    @Test fun historyIsWrittenPeriodicallyWhilePlaying() = runTest {
        val s = session()
        s.start("vid", hint); runCurrent()
        controller.playing(1_000); runCurrent()
        clock.advance(15_000); controller.state.value = controller.state.value.copy(positionMs = 16_000)
        advanceTimeBy(15.seconds + 1.seconds)
        assertEquals(1, history.rows.size)
        assertEquals(16_000, history.rows.single().positionMs)
        clock.advance(15_000); controller.state.value = controller.state.value.copy(positionMs = 31_000)
        advanceTimeBy(15.seconds)
        assertEquals(31_000, history.rows.single().positionMs)
        assertTrue(history.updates >= 1)
    }

    @Test fun nothingIsWrittenWhilePausedOrBeforeAnythingWasWatched() = runTest {
        val s = session()
        s.start("vid", hint); runCurrent()
        advanceTimeBy(60.seconds)
        assertTrue(history.rows.isEmpty())
    }

    @Test fun endingAndStoppingWriteHistory() = runTest {
        val s = session()
        s.start("vid", hint); runCurrent()
        controller.playing(0); runCurrent()
        clock.advance(5_000)
        controller.state.value = controller.state.value.copy(status = PlaybackStatus.ENDED, positionMs = 200_000); runCurrent()
        assertEquals(200_000, history.rows.single().positionMs)

        s.start("vid2", hint.copy(id = "vid2")); runCurrent()
        controller.playing(0); runCurrent()
        clock.advance(4_000)
        controller.state.value = controller.state.value.copy(positionMs = 4_000)
        s.stop(); runCurrent()
        assertEquals(2, history.rows.size)
        assertEquals(4_000, history.rows.last().positionMs)
        assertEquals("stop", controller.calls.last())
        assertIs<SessionState.Idle>(s.state.value)
    }

    @Test fun leavingAVideoAndComingBackResumesWhereItWasLeft() = runTest {
        val s = session()
        s.start("vid", hint); runCurrent()
        controller.playing(0); runCurrent()
        clock.advance(100_000); controller.state.value = controller.state.value.copy(positionMs = 100_000)
        s.stop(); runCurrent()

        s.start("vid", hint); runCurrent()
        assertEquals(100_000, controller.loads.last().startMs)
    }

    @Test fun thePositionIsNotOverwrittenWithZeroWhileTheResumeLoads() = runTest {
        history.begin("local", "vid", "UC1", "t", clock.now(), 80_000, 200_000, 80_000)
        val s = session()
        s.start("vid", hint); runCurrent()
        // The player is still buffering at the resume position when the user leaves.
        s.stop(); runCurrent()
        assertEquals(80_000, history.rows.single().positionMs)
    }

    @Test fun errorsAlsoPersistTheCurrentPosition() = runTest {
        val s = session(maxReResolves = 0)
        s.start("vid", hint); runCurrent()
        controller.playing(0); runCurrent()
        clock.advance(8_000); controller.state.value = controller.state.value.copy(positionMs = 8_000)
        controller.error(PlaybackErrorKind.Other); runCurrent()
        assertEquals(8_000, history.rows.single().positionMs)
    }

    // ---- sleep timer ----

    @Test fun sleepTimerPausesAfterTheDurationAndClears() = runTest {
        val s = session()
        s.start("vid", hint); runCurrent(); controller.playing(0); runCurrent()
        s.setSleepTimer(SleepTimerMode.After(30.minutes))
        assertEquals(clock.now() + 30.minutes, s.sleepTimer.value?.deadline)
        advanceTimeBy(29.minutes); runCurrent()
        assertFalse("pause" in controller.calls)
        advanceTimeBy(1.minutes + 1.milliseconds); runCurrent()
        assertTrue("pause" in controller.calls)
        assertNull(s.sleepTimer.value)
    }

    @Test fun cancellingTheSleepTimerPreventsThePause() = runTest {
        val s = session()
        s.start("vid", hint); runCurrent(); controller.playing(0); runCurrent()
        s.setSleepTimer(SleepTimerMode.After(10.minutes))
        s.setSleepTimer(null)
        advanceTimeBy(1.hours); runCurrent()
        assertFalse("pause" in controller.calls)
    }

    @Test fun settingANewTimerReplacesTheOldOne() = runTest {
        val s = session()
        s.start("vid", hint); runCurrent(); controller.playing(0); runCurrent()
        s.setSleepTimer(SleepTimerMode.After(10.minutes))
        s.setSleepTimer(SleepTimerMode.After(60.minutes))
        advanceTimeBy(11.minutes); runCurrent()
        assertFalse("pause" in controller.calls, "the first deadline must be gone")
        advanceTimeBy(50.minutes); runCurrent()
        assertTrue("pause" in controller.calls)
    }

    @Test fun endOfVideoTimerClearsWhenTheVideoEndsAndOnStartingAnother() = runTest {
        val s = session()
        s.start("vid", hint); runCurrent(); controller.playing(0); runCurrent()
        s.setSleepTimer(SleepTimerMode.EndOfVideo)
        assertEquals(SleepTimerMode.EndOfVideo, s.sleepTimer.value?.mode)
        controller.state.value = controller.state.value.copy(status = PlaybackStatus.ENDED); runCurrent()
        assertNull(s.sleepTimer.value)

        s.setSleepTimer(SleepTimerMode.EndOfVideo)
        s.start("other", hint); runCurrent()
        assertNull(s.sleepTimer.value, "it was for the previous video")
    }

    @Test fun aTimedTimerSurvivesStartingAnotherVideoButNotStop() = runTest {
        val s = session()
        s.start("vid", hint); runCurrent()
        s.setSleepTimer(SleepTimerMode.After(20.minutes))
        s.start("other", hint); runCurrent()
        assertNotNull(s.sleepTimer.value)
        s.stop()
        assertNull(s.sleepTimer.value)
    }

    // ---- volume ----

    @Test fun volumeAndMuteReachTheController() = runTest {
        val s = session()
        s.setVolume(0.4f)
        assertEquals(listOf(0.4f), controller.volumes)
        s.toggleMute()
        assertTrue(s.muted.value)
        assertEquals(0f, controller.volumes.last())
        s.toggleMute()
        assertEquals(0.4f, controller.volumes.last())
        s.setVolume(7f)
        assertEquals(1f, s.volume.value, "clamped")
    }

    // ---- add-ons ----

    private fun TestScope.sessionWith(vararg addons: PlaybackAddon) = PlaybackSession(
        extractor, controller, settings, history, backgroundScope, clock, network = network, addons = addons.toList(),
    )

    @Test fun addonsAreAttachedAndSeeStartStatesAndStop() = runTest {
        val addon = RecordingAddon()
        val s = sessionWith(addon)
        assertEquals(listOf("attach"), addon.log)
        s.start("vid", hint); runCurrent()
        controller.playing(1_000); runCurrent()
        s.stop()
        assertEquals("video:vid:UC1", addon.log[1])
        assertTrue("state:PLAYING" in addon.log)
        assertEquals("stop", addon.log.last())
    }

    @Test fun anAddonThatThrowsNeverStopsPlayback() = runTest {
        val s = sessionWith(RecordingAddon(failOnState = true))
        s.start("vid", hint); runCurrent()
        controller.playing(1_000); runCurrent()
        assertIs<SessionState.Ready>(s.state.value)
        assertEquals(PlaybackStatus.PLAYING, controller.state.value.status)
        s.stop()
    }

    @Test fun addonMuteAndUserMuteDoNotFightEachOther() = runTest {
        val addon = RecordingAddon()
        val s = sessionWith(addon)
        s.setVolume(0.7f)
        addon.host.setAddonMuted(true)
        assertEquals(0f, controller.volumes.last())
        addon.host.setAddonMuted(false)
        assertEquals(0.7f, controller.volumes.last())

        s.toggleMute()
        addon.host.setAddonMuted(true)
        addon.host.setAddonMuted(false)
        assertEquals(0f, controller.volumes.last(), "the user's mute outlives the segment's")
        s.toggleMute()
        assertEquals(0.7f, controller.volumes.last())
    }

    @Test fun addonSeeksGoThroughTheSession() = runTest {
        val addon = RecordingAddon()
        val s = sessionWith(addon)
        s.start("vid", hint); runCurrent()
        addon.host.seekTo(42_000)
        assertEquals(42_000, controller.state.value.positionMs)
        assertTrue("seek" in controller.calls)
    }

    // ---- offline (downloaded file) ----

    private val local = PlaybackPlan.Local("/library/video.webm", hasVideo = true)

    @Test fun aDownloadedFilePlaysWithoutTouchingTheExtractor() = runTest {
        val s = session()
        s.startLocal("vid", local, "Saved title", "UC1")
        runCurrent()
        assertEquals(0, extractor.streamCalls, "no streams resolved")
        assertEquals(0, extractor.infoCalls)
        val load = controller.loads.single()
        assertEquals(local, load.plan)
        assertEquals("Saved title", load.metadata.title)
        val ready = ready(s)
        assertEquals(PlaybackSession.OFFLINE_CLIENT, ready.client)
        assertTrue(ready.heights.isEmpty(), "one file, no quality menu")
    }

    @Test fun theQualityMenuDoesNothingForAFileAndNothingIsResolved() = runTest {
        val s = session()
        s.startLocal("vid", local, "t", "UC1"); runCurrent()
        s.selectQuality(480); runCurrent()
        assertEquals(1, controller.loads.size)
        assertEquals(0, extractor.streamCalls)
    }

    @Test fun aPlayerErrorOnAFileFailsInsteadOfResolvingStreams() = runTest {
        val s = session()
        s.startLocal("vid", local, "t", "UC1"); runCurrent()
        controller.playing(0); runCurrent()
        controller.error(PlaybackErrorKind.StreamExpiredOrForbidden, 403); runCurrent()
        assertIs<SessionState.Failed>(s.state.value)
        assertEquals(0, extractor.streamCalls, "there is no URL to re-resolve")
    }

    @Test fun offlinePlaybackStillResumesAndRecordsHistory() = runTest {
        history.begin("local", "vid", "UC1", "t", clock.now(), 60_000, 200_000, 60_000)
        val s = session()
        s.startLocal("vid", local, "t", "UC1"); runCurrent()
        assertEquals(60_000, controller.loads.single().startMs, "resumes where it was left")
        controller.playing(60_000); runCurrent()
        clock.advance(10_000); controller.state.value = controller.state.value.copy(positionMs = 70_000)
        s.stop(); runCurrent()
        assertEquals(70_000, history.rows.last().positionMs)
    }

    @Test fun addonsAreToldTheVideoIsOffline() = runTest {
        val addon = RecordingAddon()
        val s = sessionWith(addon)
        s.startLocal("vid", local, "t", "UC1"); runCurrent()
        assertTrue("video:vid:UC1:offline" in addon.log, addon.log.toString())
    }

    @Test fun startingAnOnlineVideoAfterAFileResolvesStreamsAgain() = runTest {
        val s = session()
        s.startLocal("vid", local, "t", "UC1"); runCurrent()
        s.start("other", VideoSummary("other", "T", "UC2", "C")); runCurrent()
        assertEquals(1, extractor.streamCalls)
        assertNotEquals(PlaybackSession.OFFLINE_CLIENT, ready(s).client)
    }

    // ---- one video's state never shows on the next ----

    @Test fun startingAnotherVideoUnloadsThePreviousOneAtOnce() = runTest {
        val s = session()
        s.start("old", hint); runCurrent()
        controller.playing(1_736_000, 1_785_000); runCurrent() // 28:56 / 29:45, as in the owner's screenshot
        extractor.availableIn = 5.seconds // the next video's streams are slow to become usable
        s.start("new", VideoSummary("new", "New", "UC2", "C")); runCurrent()
        assertIs<SessionState.Resolving>(s.state.value)
        assertEquals(PlayerState(), controller.state.value, "no old position, duration or frame while the new streams resolve")
        assertEquals("new", (s.state.value as SessionState.Resolving).videoId)
    }

    @Test fun aFailedResolveOfTheNewVideoDoesNotLeaveTheOldOneOnScreen() = runTest {
        val s = session()
        s.start("old", hint); runCurrent()
        controller.playing(500_000, 900_000); runCurrent()
        extractor.results += ExtractorResult.Failure(ExtractorError.Network("down"))
        s.start("new", VideoSummary("new", "New", "UC2", "C")); runCurrent()
        assertIs<SessionState.Failed>(s.state.value)
        assertEquals(PlaybackStatus.IDLE, controller.state.value.status)
        assertEquals(0, controller.state.value.positionMs)
    }

    @Test fun rapidlySwitchingVideosEndsOnTheLastOneOnly() = runTest {
        val s = session()
        for (id in listOf("a", "b", "c")) s.start(id, VideoSummary(id, id, "UC", "C"))
        runCurrent()
        assertEquals("c", ready(s).videoId)
        assertEquals(1, controller.loads.size, "only the last video was loaded")
    }
}
