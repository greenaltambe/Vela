package com.greenaltambe.vela.player.mpv

import com.greenaltambe.vela.player.PlaybackMetadata
import com.greenaltambe.vela.player.PlaybackPlan
import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.player.PlaybackConstraints
import com.greenaltambe.vela.player.PlaybackPrefs
import com.greenaltambe.vela.player.StreamSelector
import org.junit.Test

/**
 * Measurements for docs/reports/08-desktop-player.md. They assert nothing about speed: the printed numbers are the
 * result. Select one with `--tests '*cacheFill*'` etc.
 */
class LiveMpvMeasureTest {
    private fun fmt(d: Double) = "%.1f".format(d)

    private class Row(val label: String, val fps: Double, val cpu: Double, val renderMs: Double, val dropped: Int, val hw: String?)

    private fun playFor(backend: MpvBackend, plan: PlaybackPlan, config: MpvConfig, w: Int, h: Int, seconds: Int, label: String): Row {
        val c = MpvPlayerController(backend, config)
        try {
            val sink = CountingSink()
            c.attachSink(sink); c.setSurfaceSize(w, h)
            c.load(plan, 20_000, true, PlaybackMetadata())
            c.awaitState(30_000) { it.status == PlaybackStatus.PLAYING } ?: return Row("$label (never played)", 0.0, 0.0, 0.0, 0, null)
            Thread.sleep(3000) // settle: decoder threads, cache
            val cpu = CpuMeter(); val f0 = sink.frames.get(); val r0 = c.framesRendered
            Thread.sleep(seconds * 1000L)
            val frames = sink.frames.get() - f0
            c.refreshStats()
            return Row(label, frames / seconds.toDouble(), cpu.percentOfOneCore(), c.averageRenderMs, c.stats.value.droppedFrames, c.property("hwdec-current"))
        } finally { c.release() }
    }

    private fun print(title: String, rows: List<Row>) {
        println("== $title")
        rows.forEach { println("  %-46s %5s fps  CPU %5s%% of a core  render %5s ms/frame  dropped %d  hwdec=%s".format(it.label, fmt(it.fps), fmt(it.cpu), fmt(it.renderMs), it.dropped, it.hw)) }
    }

    @Test
    fun decodeAndRenderVariants1080p60() {
        val backend = requireLiveMpv()
        val plan = plan(streamsOf(BBB_60), 1080, fpsAtLeast = 60)
        val rows = mutableListOf<Row>()
        for ((label, extra, size) in listOf(
            Triple("sw decode, render 1280x720 (scaled)", emptyMap<String, String>(), 1280 to 720),
            Triple("sw decode, render 1920x1080 (1:1)", emptyMap(), 1920 to 1080),
            Triple("sw decode, 1280x720, sws-allow-zimg=no", mapOf("sws-allow-zimg" to "no"), 1280 to 720),
            Triple("sw decode, 1920x1080, sws-allow-zimg=no", mapOf("sws-allow-zimg" to "no"), 1920 to 1080),
            Triple("hwdec=auto-safe, 1280x720", mapOf("hwdec" to "auto-safe"), 1280 to 720),
            Triple("hwdec=vaapi-copy, 1280x720", mapOf("hwdec" to "vaapi-copy"), 1280 to 720),
            Triple("hwdec=vaapi-copy, 1920x1080, no zimg", mapOf("hwdec" to "vaapi-copy", "sws-allow-zimg" to "no"), 1920 to 1080),
        )) rows += playFor(backend, plan, MpvConfig(extraOptions = extra), size.first, size.second, 15, label)
        print("1080p60 VP9, 15 s each", rows)
    }

    @Test
    fun decode1080p24() {
        val backend = requireLiveMpv()
        val plan = plan(streamsOf(BBB_24), 1080)
        run {
            val c = MpvPlayerController(backend)
            println("constraints from this libmpv: " + constraintsFromDecoderList(c.property("decoder-list")))
            c.release()
        }
        println("plan: ${plan.video.width}x${plan.video.height}@${plan.video.fps} ${plan.video.codec}")
        val rows = listOf(
            playFor(backend, plan, MpvConfig(), 1280, 720, 15, "sw decode, 1280x720 (defaults)"),
            playFor(backend, plan, MpvConfig(), 1920, 1080, 15, "sw decode, 1920x1080 (defaults)"),
            playFor(backend, plan(streamsOf(BBB_60), 1080, fpsAtLeast = 60), MpvConfig(), 1280, 720, 15, "1080p60 sw decode, 1280x720 (defaults)"),
            playFor(backend, plan(streamsOf(BBB_60), 1080, fpsAtLeast = 60), MpvConfig(), 1920, 1080, 15, "1080p60 sw decode, 1920x1080 (defaults)"),
            playFor(backend, plan, MpvConfig(extraOptions = mapOf("hwdec" to "vaapi-copy")), 1280, 720, 15, "vaapi-copy, 1280x720"),
        )
        print("1080p24 VP9, 15 s each", rows)
    }

    @Test
    fun avcVersusVp9At1080p60() {
        val backend = requireLiveMpv()
        val streams = streamsOf(BBB_60)
        val avc = StreamSelector.plan(
            streams.copy(videoStreams = streams.videoStreams.filter { it.height <= 1080 && it.fps >= 60 }),
            PlaybackPrefs(maxHeight = 1080, preferredCodec = com.greenaltambe.vela.core.settings.PreferredCodec.AVC), PlaybackConstraints(),
        ) as PlaybackPlan.Separate
        println("AVC plan: ${avc.video.width}x${avc.video.height}@${avc.video.fps} ${avc.video.codec} ${avc.video.bitrate / 1000} kbit/s")
        print("AVC 1080p60 via libopenh264", listOf(
            playFor(backend, avc, MpvConfig(), 1280, 720, 15, "avc sw decode, 1280x720"),
            playFor(backend, avc, MpvConfig(), 1920, 1080, 15, "avc sw decode, 1920x1080"),
        ))
    }

    /** How long mpv takes to fill 30 s of read-ahead, paused, with and without the chunking proxy. */
    @Test
    fun cacheFillChunkedVersusDefault() {
        val backend = requireLiveMpv()
        val plan = plan(streamsOf(BBB_60), 1080, fpsAtLeast = 60)
        val bitrateBps = (plan.video.bitrate + plan.audio.bitrate).toDouble()
        println("== read-ahead fill (paused), 1080p60 VP9 + opus, ~${fmt(bitrateBps / 1e6)} Mbit/s declared bitrate")
        repeat(2) { round ->
            for (chunked in listOf(false, true)) {
                val c = MpvPlayerController(backend, MpvConfig(chunkedRanges = chunked))
                try {
                    c.load(plan, 0, false, PlaybackMetadata())
                    val t0 = System.nanoTime()
                    var cached = 0.0
                    while (cached < 30.0 && (System.nanoTime() - t0) / 1e9 < 60) {
                        cached = c.property("demuxer-cache-time")?.toDoubleOrNull() ?: 0.0
                        Thread.sleep(100)
                    }
                    val secs = (System.nanoTime() - t0) / 1e9
                    val inputBytes = c.property("cache-speed")
                    println("  round ${round + 1} chunked=$chunked: ${fmt(cached)} s of media in ${fmt(secs)} s -> ~${fmt(cached * bitrateBps / secs / 1e6)} Mbit/s effective  proxyBytes=${c.proxyBytesServed}")
                } finally { c.release() }
            }
        }
    }
}
