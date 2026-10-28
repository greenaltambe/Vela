package com.greenaltambe.vela.player.mpv

import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.newpipe.NewPipeExtractor
import com.greenaltambe.vela.player.PlaybackMetadata
import com.greenaltambe.vela.player.PlaybackPlan
import com.greenaltambe.vela.player.PlaybackStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertNotNull

/** Real libmpv on a real live stream (skips without libmpv/network/a live stream). Prints numbers; asserts only that it plays. */
class LiveMpvLiveStreamTest {
    private val candidates = listOf(System.getenv("VELA_LIVE_ID"), "4xDzrJKXOOY", "jfKfPfyJRdk", "36YnV9STBqc", "Z5aOVBO-Rs0").filterNotNull()

    @Test fun playsALiveStreamAndShowsHowPositionAndSeekBehave() {
        val backend = requireLiveMpv()
        val hls = candidates.firstNotNullOfOrNull { id ->
            val r = runBlocking { NewPipeExtractor().streams(id) }
            (r as? ExtractorResult.Success)?.value?.hlsUrl?.let { id to (it to r.value.headers) }
        }
        assumeTrue("SKIPPED: none of the candidate live streams is live right now", hls != null)
        val (id, plan) = hls!!
        println("LIVE using $id")
        val c = MpvPlayerController(backend); val sink = CountingSink()
        try {
            c.attachSink(sink); c.setSurfaceSize(1280, 720)
            c.load(PlaybackPlan.Hls(plan.first, plan.second), 0, true, PlaybackMetadata(title = "live"))
            val playing = c.awaitState(40_000) { it.status == PlaybackStatus.PLAYING }
            assertNotNull(playing, "never reached PLAYING; state=${c.state.value}")
            val sb = StringBuilder()
            repeat(15) { i -> Thread.sleep(2_000); val s = c.state.value; sb.append("${i * 2}s ${s.status.name.take(4)} pos=${s.positionMs} dur=${s.durationMs}; ") }
            println("LIVE telemetry: $sb")
            val edge = c.state.value
            c.seekTo((edge.positionMs - 60_000).coerceAtLeast(0)); Thread.sleep(6_000)
            val back = c.state.value
            println("LIVE seek -60s: edge=${edge.positionMs} -> ${back.positionMs} ${back.status}")
            c.seekTo(edge.positionMs + 600_000); Thread.sleep(6_000)
            val past = c.state.value
            println("LIVE seek +600s: -> ${past.positionMs} ${past.status} dur=${past.durationMs} frames=${sink.frames.get()}")
            val shown = c.state.value
            Thread.sleep(5_000)
            val later = c.state.value
            println("LIVE after the ignored seek: shown pos 5 s later=${later.positionMs} (real time-pos=${c.property("time-pos")}) offset=${later.liveOffsetMs}")
            c.seekTo(10_000); Thread.sleep(5_000)
            println("LIVE behind live: offset=${c.state.value.liveOffsetMs}")
            c.goLive(); c.awaitState(30_000) { it.status == PlaybackStatus.PLAYING && it.positionMs > 1500 }; Thread.sleep(3_000)
            println("LIVE after goLive: pos=${c.state.value.positionMs} offset=${c.state.value.liveOffsetMs} ${c.state.value.status}")
            println("LIVE props: duration=${c.property("duration")} seekable=${c.property("seekable")} demuxer-cache-time=${c.property("demuxer-cache-time")} time-pos=${c.property("time-pos")} playback-time=${c.property("playback-time")} idle=${c.property("core-idle")}")
        } finally { c.release() }
    }
}
