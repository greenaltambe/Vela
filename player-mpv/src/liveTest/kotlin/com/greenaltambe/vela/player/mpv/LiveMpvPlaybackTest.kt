package com.greenaltambe.vela.player.mpv

import com.greenaltambe.vela.player.PlaybackErrorKind
import com.greenaltambe.vela.player.PlaybackMetadata
import com.greenaltambe.vela.player.PlaybackPlan
import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.player.PlayerEvent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Real libmpv, real googlevideo. Skips cleanly without libmpv or network. */
class LiveMpvPlaybackTest {
    @Test
    fun playsSeparateStreamsFor35SecondsWithSeekSpeedAndPause() {
        val backend = requireLiveMpv()
        val plan = plan(streamsOf(BBB_60), maxHeight = 1080, fpsAtLeast = 60)
        println("plan: ${plan.video.width}x${plan.video.height}@${plan.video.fps} ${plan.video.codec} + ${plan.audio.codec}")
        val controller = MpvPlayerController(backend)
        val sink = CountingSink()
        val events = EventCollector(controller)
        try {
            controller.attachSink(sink)
            controller.setSurfaceSize(1280, 720)
            val cpu = CpuMeter()
            controller.load(plan, 0, true, PlaybackMetadata(title = "BBB"))

            val playing = controller.awaitState(30_000) { it.status == PlaybackStatus.PLAYING && it.positionMs > 500 }
            assertNotNull(playing, "never reached PLAYING; state=${controller.state.value} events=${events.events}")
            val tPlay = System.nanoTime()
            println("time to PLAYING: ${controller.state.value}")

            cpu.reset(); val f0 = sink.frames.get()
            Thread.sleep(20_000)
            val wall = (System.nanoTime() - tPlay) / 1e9
            val frames = sink.frames.get() - f0
            println("steady 20 s: ${"%.1f".format(frames / 20.0)} fps rendered, CPU ${"%.0f".format(cpu.percentOfOneCore())}% of one core, " +
                "avg render ${"%.2f".format(controller.averageRenderMs)} ms, skipped ${controller.framesSkipped}")
            val s = controller.state.value
            assertTrue(s.positionMs > 15_000, "position did not advance: $s (wall ${"%.1f".format(wall)} s)")
            println("position after ${"%.1f".format(wall)} s wall: ${s.positionMs} ms, duration ${s.durationMs}")

            controller.refreshStats()
            println("stats: ${controller.stats.value}")
            println("current-ao=${controller.property("current-ao")} audio-device=${controller.property("audio-device")} " +
                "hwdec-current=${controller.property("hwdec-current")} vo-drops=${controller.property("vo-drop-frame-count")} " +
                "audio-out=${controller.property("audio-out-params/format")}/${controller.property("audio-out-params/samplerate")}")

            // pause
            controller.pause()
            assertNotNull(controller.awaitState(5_000) { it.status == PlaybackStatus.PAUSED }, "no PAUSED")
            val pausedAt = controller.state.value.positionMs
            Thread.sleep(1500)
            assertTrue(kotlin.math.abs(controller.state.value.positionMs - pausedAt) < 300, "moved while paused")
            controller.play()
            assertNotNull(controller.awaitState(5_000) { it.status == PlaybackStatus.PLAYING }, "no PLAYING after resume")

            // seek forward and back
            val t1 = System.nanoTime()
            controller.seekTo(300_000)
            val afterSeek = controller.awaitState(20_000) { it.status == PlaybackStatus.PLAYING && it.positionMs in 300_500..310_000 }
            assertNotNull(afterSeek, "seek to 300 s did not resume: ${controller.state.value}")
            println("seek to 300 s resumed after ${(System.nanoTime() - t1) / 1_000_000} ms")
            controller.seekTo(5_000)
            assertNotNull(controller.awaitState(20_000) { it.status == PlaybackStatus.PLAYING && it.positionMs in 5_500..15_000 }, "seek back failed")

            // speed
            controller.setSpeed(2f)
            Thread.sleep(500)
            val p0 = controller.state.value.positionMs; Thread.sleep(4000)
            val adv = controller.state.value.positionMs - p0
            println("at 2x: advanced $adv ms in 4 s wall")
            assertTrue(adv in 6_000..9_500, "2x speed advanced $adv ms")
            assertEquals(2f, controller.state.value.speed)

            assertTrue(events.events.none { it is PlayerEvent.Error }, "unexpected errors: ${events.events}")
        } finally {
            events.stop()
            controller.release()
        }
    }

    @Test
    fun expiredUrlIsReportedAsStreamExpiredOrForbidden() {
        val backend = requireLiveMpv()
        val good = plan(streamsOf(BBB_60), maxHeight = 720)
        // Break the signature the way expiry does: googlevideo answers 403.
        fun bad(u: String) = u.replace(Regex("expire=\\d+"), "expire=1")
        val broken = good.copy(video = good.video.copy(url = bad(good.video.url)), audio = good.audio.copy(url = bad(good.audio.url)))
        for (chunked in listOf(true, false)) {
            val controller = MpvPlayerController(backend, MpvConfig(chunkedRanges = chunked))
            val events = EventCollector(controller)
            try {
                controller.load(broken, 0, true, PlaybackMetadata())
                val t0 = System.currentTimeMillis()
                while (events.events.isEmpty() && System.currentTimeMillis() - t0 < 20_000) Thread.sleep(50)
                val e = events.events.firstOrNull() as? PlayerEvent.Error
                println("chunked=$chunked -> ${e?.error} after ${System.currentTimeMillis() - t0} ms; state=${controller.state.value.status}")
                assertNotNull(e, "no error event (chunked=$chunked)")
                assertEquals(PlaybackErrorKind.StreamExpiredOrForbidden, e.error.kind, e.error.toString())
                assertEquals(PlaybackStatus.ERROR, controller.state.value.status)
            } finally { events.stop(); controller.release() }
        }
    }

    @Test
    fun audioOnlyPlaysWithoutASinkAndReleaseIsIdempotent() {
        val backend = requireLiveMpv()
        val streams = streamsOf(BBB_24)
        val controller = MpvPlayerController(backend)
        try {
            controller.load(PlaybackPlan.AudioOnly(streams.audioStreams.first(), streams.headers), 0, true, PlaybackMetadata())
            assertNotNull(controller.awaitState(30_000) { it.status == PlaybackStatus.PLAYING && it.positionMs > 3_000 }, "audio-only never played: ${controller.state.value}")
            println("audio-only: ao=${controller.property("current-ao")} state=${controller.state.value}")
            controller.stop()
            assertEquals(PlaybackStatus.IDLE, controller.state.value.status)
        } finally {
            controller.release(); controller.release()
        }
    }
}
