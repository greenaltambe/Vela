package com.greenaltambe.vela.player.mpv

import com.greenaltambe.vela.player.PlaybackMetadata
import com.greenaltambe.vela.player.PlaybackPlan
import com.greenaltambe.vela.player.PlaybackPrefs
import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.player.StreamSelector
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** An ENDED live stream (a recording of one, found with its chat replay) in real libmpv: it must play and seek like any video. */
class LiveMpvEndedStreamTest {
    @Test fun anEndedLiveStreamPlaysAndSeeks() {
        val backend = requireLiveMpv()
        val id = System.getenv("VELA_ENDED_LIVE_ID") ?: "RTs0C5dV5cM"
        val streams = runCatching { streamsOf(id) }.getOrNull()
        assumeTrue("SKIPPED: $id cannot be resolved", streams != null)
        val p = StreamSelector.plan(streams!!, PlaybackPrefs(maxHeight = 480), softwareCodecs)
        println("ENDED using $id plan=${p::class.simpleName}")
        assumeTrue("SKIPPED: nothing playable", p !is PlaybackPlan.NothingPlayable)
        val c = MpvPlayerController(backend); val sink = CountingSink()
        try {
            c.attachSink(sink); c.setSurfaceSize(1280, 720)
            c.load(p, 0, true, PlaybackMetadata(title = "ended live"))
            assertNotNull(c.awaitState(40_000) { it.status == PlaybackStatus.PLAYING && it.positionMs > 500 }, "never played; ${c.state.value}")
            Thread.sleep(8_000)
            val s = c.state.value
            println("ENDED after 8 s: pos=${s.positionMs} dur=${s.durationMs} ${s.status} liveOffset=${s.liveOffsetMs}")
            assertTrue(s.positionMs > 6_000, "position advances: $s")
            c.seekTo(600_000); Thread.sleep(6_000)
            val real = c.property("time-pos")?.toDoubleOrNull() ?: -1.0
            println("ENDED seek to 600 s: real time-pos=$real shown=${c.state.value}")
            assertTrue(real in 598.0..640.0, "the seek really landed (mpv time-pos $real)")
            c.seekTo(30_000); assertNotNull(c.awaitState(30_000) { it.status == PlaybackStatus.PLAYING && it.positionMs in 28_000..60_000 }, "backward seek: ${c.state.value}")
        } finally { c.release() }
    }
}
