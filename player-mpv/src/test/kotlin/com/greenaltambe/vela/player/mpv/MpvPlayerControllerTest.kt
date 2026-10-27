package com.greenaltambe.vela.player.mpv

import com.greenaltambe.vela.extractor.AudioStream
import com.greenaltambe.vela.extractor.VideoStream
import com.greenaltambe.vela.player.PlaybackErrorKind
import com.greenaltambe.vela.player.PlaybackMetadata
import com.greenaltambe.vela.player.PlaybackPlan
import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.player.PlayerEvent
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MpvPlayerControllerTest {
    private val video = VideoStream("https://v/1", "video/webm", "vp9", 2_000_000, 1920, 1080, 60)
    private val audio = AudioStream("https://a/1", "audio/webm", "opus", 128_000)
    private val plan = PlaybackPlan.Separate(video, audio, mapOf("User-Agent" to "UA"), mapOf("User-Agent" to "UA"))

    private val client = FakeMpvClient()
    private val controller = MpvPlayerController(
        { client }, MpvConfig(chunkedRanges = false), control = DirectExecutorService(), runEventLoop = false, proxyFactory = null,
    )

    private fun prop(name: String, value: Any?) = controller.dispatch(MpvEvent.Property(name, value))

    @Test
    fun initialisesMpvInertAndObservesTheStateProperties() {
        assertTrue(client.initialized)
        assertEquals("libmpv", client.options.toMap()["vo"])
        assertTrue(client.observed.containsAll(listOf("time-pos", "duration", "pause", "paused-for-cache", "core-idle", "eof-reached", "speed")))
    }

    @Test
    fun loadIssuesLoadfileWithAudioFileStartAndHeaders() {
        controller.load(plan, 5_000, true, PlaybackMetadata(title = "T"))
        val loadfile = client.commands.single { it.first() == "loadfile" }
        assertEquals("https://v/1", loadfile[1])
        assertEquals("replace", loadfile[2])
        assertTrue(loadfile[4].contains("audio-file=%11%https://a/1"), loadfile[4])
        assertTrue(loadfile[4].contains("start=%5%5.000"), loadfile[4])
        assertEquals("UA", client.properties["user-agent"])
        assertEquals(PlaybackStatus.BUFFERING, controller.state.value.status)
        assertEquals(5_000L, controller.state.value.positionMs) // the requested start, until playback moves it
    }

    @Test
    fun propertyEventsDriveTheState() {
        controller.load(plan, 0, true, PlaybackMetadata())
        prop("duration", 600.0); prop("core-idle", false); prop("time-pos", 1.0)
        controller.dispatch(MpvEvent.PlaybackRestart)
        assertEquals(PlaybackStatus.PLAYING, controller.state.value.status)
        assertEquals(600_000L, controller.state.value.durationMs)
        assertEquals(1_000L, controller.state.value.positionMs)

        prop("pause", true)
        assertEquals(PlaybackStatus.PAUSED, controller.state.value.status)
        prop("pause", false); prop("paused-for-cache", true)
        assertEquals(PlaybackStatus.BUFFERING, controller.state.value.status)
        prop("paused-for-cache", false); prop("eof-reached", true)
        assertEquals(PlaybackStatus.ENDED, controller.state.value.status)
    }

    @Test
    fun seekShowsTheTargetImmediatelyAndCommandsMpv() {
        controller.load(plan, 0, true, PlaybackMetadata())
        prop("core-idle", false); prop("time-pos", 3.0); controller.dispatch(MpvEvent.PlaybackRestart)
        controller.seekTo(90_000)
        assertEquals(90_000L, controller.state.value.positionMs)
        assertEquals(listOf("seek", "90.000", "absolute"), client.commands.last())
        prop("time-pos", 3.2) // mpv still reports the old position until the seek lands
        assertEquals(90_000L, controller.state.value.positionMs)
        controller.dispatch(MpvEvent.PlaybackRestart); prop("time-pos", 90.1)
        assertEquals(90_100L, controller.state.value.positionMs)
    }

    @Test
    fun speedAndPauseAndPlayGoThroughProperties() {
        controller.load(plan, 0, true, PlaybackMetadata())
        controller.setSpeed(1.5f)
        assertEquals("1.5", client.properties["speed"]); assertEquals(1.5f, controller.state.value.speed)
        controller.pause(); assertEquals("yes", client.properties["pause"])
        controller.play(); assertEquals("no", client.properties["pause"])
    }

    @Test
    fun volumeIsClampedAndExposed() {
        controller.setVolume(40); assertEquals("40", client.properties["volume"]); assertEquals(40, controller.volume.value)
        controller.setVolume(500); assertEquals("130", client.properties["volume"])
        controller.setVolume(-5); assertEquals("0", client.properties["volume"])
    }

    @Test
    fun playAfterTheEndRestartsFromTheBeginning() {
        controller.load(plan, 0, true, PlaybackMetadata())
        prop("eof-reached", true)
        controller.play()
        assertEquals(listOf("seek", "0", "absolute"), client.commands.last { it.first() == "seek" })
    }

    @Test
    fun endFileErrorBecomesOneMappedErrorEventAndErrorState() = runTest {
        val events = mutableListOf<PlayerEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) { controller.events.collect { events += it } }
        controller.load(plan, 0, true, PlaybackMetadata())
        controller.dispatch(MpvEvent.Log("ffmpeg", "error", "https: HTTP error 403 Forbidden"))
        controller.dispatch(MpvEvent.EndFile(EndReason.Error, MpvErrorCode.LOADING_FAILED))
        controller.dispatch(MpvEvent.EndFile(EndReason.Error, MpvErrorCode.LOADING_FAILED)) // duplicate: ignored
        val error = (events.single() as PlayerEvent.Error).error
        assertEquals(PlaybackErrorKind.StreamExpiredOrForbidden, error.kind)
        assertEquals(403, error.httpStatus)
        assertEquals(PlaybackStatus.ERROR, controller.state.value.status)
        job.cancel()
    }

    @Test
    fun aNewLoadClearsTheErrorAndEmitsAgainOnItsOwnFailure() = runTest {
        val events = mutableListOf<PlayerEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) { controller.events.collect { events += it } }
        controller.load(plan, 0, true, PlaybackMetadata())
        controller.dispatch(MpvEvent.EndFile(EndReason.Error, MpvErrorCode.UNKNOWN_FORMAT))
        controller.load(plan, 0, true, PlaybackMetadata())
        assertEquals(PlaybackStatus.BUFFERING, controller.state.value.status)
        controller.dispatch(MpvEvent.EndFile(EndReason.Error, MpvErrorCode.LOADING_FAILED))
        assertEquals(2, events.size)
        assertEquals(PlaybackErrorKind.Decoder, (events[0] as PlayerEvent.Error).error.kind)
        job.cancel()
    }

    @Test
    fun stopAndEofEndFilesAreNotErrors() = runTest {
        val events = mutableListOf<PlayerEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) { controller.events.collect { events += it } }
        controller.load(plan, 0, true, PlaybackMetadata())
        controller.dispatch(MpvEvent.EndFile(EndReason.Stop, 0))
        controller.dispatch(MpvEvent.EndFile(EndReason.Eof, 0))
        assertTrue(events.isEmpty())
        job.cancel()
    }

    @Test
    fun upstreamFailuresFromTheProxyMapByStatus() = runTest {
        val events = mutableListOf<PlayerEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) { controller.events.collect { events += it } }
        controller.load(plan, 0, true, PlaybackMetadata())
        val m = MpvPlayerController::class.java.getDeclaredMethod("onUpstreamFailure", UpstreamFailure::class.java).apply { isAccessible = true }
        m.invoke(controller, UpstreamFailure(StreamRole.Audio, 410, "HTTP error 410 Gone"))
        val e = (events.single() as PlayerEvent.Error).error
        assertEquals(PlaybackErrorKind.StreamExpiredOrForbidden, e.kind); assertEquals(410, e.httpStatus)
        job.cancel()
    }

    @Test
    fun nothingPlayableFailsWithoutTouchingMpv() = runTest {
        val events = mutableListOf<PlayerEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) { controller.events.collect { events += it } }
        controller.load(PlaybackPlan.NothingPlayable("none"), 0, true, PlaybackMetadata())
        assertEquals(1, events.size)
        assertTrue(client.commands.none { it.first() == "loadfile" })
        job.cancel()
    }

    @Test
    fun stopGoesIdleAndKeepsSpeed() {
        controller.load(plan, 0, true, PlaybackMetadata())
        controller.setSpeed(2f)
        controller.stop()
        assertEquals(PlaybackStatus.IDLE, controller.state.value.status)
        assertEquals(2f, controller.state.value.speed)
        assertEquals(listOf("stop"), client.commands.last())
    }

    @Test
    fun releaseIsIdempotentClosesTheRendererAndTheCoreOnceAndLaterCallsAreHarmless() {
        controller.load(plan, 0, true, PlaybackMetadata())
        controller.release(); controller.release()
        assertEquals(1, client.closeCount)
        assertTrue(client.rendererClosed)
        controller.play(); controller.seekTo(1); controller.setSpeed(2f); controller.stop() // no exceptions, nothing sent
        controller.load(plan, 0, true, PlaybackMetadata())
        assertNull(client.commands.lastOrNull { it.first() == "loadfile" && it !== client.commands.first { c -> c.first() == "loadfile" } })
    }

    @Test
    fun commandFailureSurfacesAsAnErrorNotACrash() = runTest {
        val events = mutableListOf<PlayerEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) { controller.events.collect { events += it } }
        client.failNextCommand = MpvException(-12, "command failed")
        controller.load(plan, 0, true, PlaybackMetadata())
        assertEquals(1, events.size)
        assertEquals(PlaybackStatus.ERROR, controller.state.value.status)
        job.cancel()
    }

    @Test
    fun closedClientRejectsCallsWithoutTouchingNative() {
        client.close()
        assertFailsWith<MpvClosedException> { client.command("x") }
        assertNull(client.getProperty("pause"))
    }
}
