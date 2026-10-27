package com.greenaltambe.vela.extractor.newpipe

import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.VideoStream
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Measures how googlevideo serves one real stream three ways, over the same HTTP stack ExoPlayer uses
 * (HttpURLConnection, HTTP/1.1): open-ended, bounded `Range` header chunks, and the `&range=` URL parameter chunks.
 * It asserts nothing about speed; the numbers it prints are the result (see docs/reports/07-*.md).
 */
class LiveThroughputTest {
    private val total = 20 * 1024 * 1024
    private val chunk = 10 * 1024 * 1024

    private class Sample(val mode: String, val ttfbMs: Long, val ms: Long, val bytes: Long) {
        val mbps get() = bytes * 8.0 / 1_000_000 / (ms / 1000.0)
        override fun toString() = "%-34s ttfb=%4d ms  %5.1f MB in %5d ms = %6.1f Mbit/s".format(mode, ttfbMs, bytes / 1048576.0, ms, mbps)
    }

    private fun open(url: String, headers: Map<String, String>, range: String?): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 15_000
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            range?.let { setRequestProperty("Range", it) }
        }

    /** Reads up to [limit] bytes from [url]; returns bytes read and time to first byte. */
    private fun read(url: String, headers: Map<String, String>, range: String?, limit: Int): Pair<Long, Long> {
        val start = System.nanoTime()
        val c = open(url, headers, range)
        check(c.responseCode in listOf(200, 206)) { "HTTP ${c.responseCode} for range=$range" }
        var first = -1L
        var n = 0L
        val buf = ByteArray(64 * 1024)
        c.inputStream.use { ins ->
            while (n < limit) {
                val r = ins.read(buf, 0, minOf(buf.size.toLong(), limit - n).toInt())
                if (r < 0) break
                if (first < 0) first = (System.nanoTime() - start) / 1_000_000
                n += r
            }
        }
        c.disconnect()
        return n to first
    }

    private fun timed(mode: String, block: () -> Pair<Long, Long>): Sample {
        val t = System.nanoTime()
        val (bytes, ttfb) = block()
        return Sample(mode, ttfb, (System.nanoTime() - t) / 1_000_000, bytes)
    }

    private fun openEnded(s: VideoStream, h: Map<String, String>, withRange: Boolean) =
        timed(if (withRange) "open-ended, Range: bytes=0-" else "open-ended, no Range header") {
            read(s.url, h, if (withRange) "bytes=0-" else null, total)
        }

    private fun headerChunks(s: VideoStream, h: Map<String, String>) = timed("2 x 10 MB, Range header") {
        var got = 0L; var first = -1L
        for (i in 0 until total / chunk) {
            val (n, ttfb) = read(s.url, h, "bytes=${i * chunk}-${(i + 1) * chunk - 1}", chunk)
            got += n; if (first < 0) first = ttfb
        }
        got to first
    }

    private fun paramChunks(s: VideoStream, h: Map<String, String>) = timed("2 x 10 MB, &range= URL parameter") {
        var got = 0L; var first = -1L
        for (i in 0 until total / chunk) {
            val (n, ttfb) = read("${s.url}&range=${i * chunk}-${(i + 1) * chunk - 1}", h, null, chunk)
            got += n; if (first < 0) first = ttfb
        }
        got to first
    }

    @Test
    fun openEndedVersusChunkedRanges() = runBlocking<Unit> {
        assumeTrue("SKIPPED: www.youtube.com is unreachable", runCatching { Socket().use { it.connect(InetSocketAddress("www.youtube.com", 443), 5_000) } }.isSuccess)
        val set = when (val r = NewPipeExtractor().streams("dQw4w9WgXcQ")) {
            is ExtractorResult.Success -> r.value
            is ExtractorResult.Failure -> fail("${r.error.reason}: ${r.error.message}")
        }
        // A video-only stream big enough for 20 MB: the biggest 1080p-or-lower one.
        val stream = set.videoStreams.filter { !it.hasAudio && it.height <= 1080 && (it.contentLength ?: 0) > total * 2L }
            .maxByOrNull { it.height } ?: fail("no suitable stream")
        val headers = set.headers + stream.headers
        println("stream: itag=${stream.itag} ${stream.height}p ${stream.codec} length=${stream.contentLength} client=${set.client}")

        val rounds = 3
        val all = mutableListOf<Sample>()
        repeat(rounds) { round ->
            // Rotate the order so no mode always goes first (warm connections, caches).
            val modes = listOf<() -> Sample>(
                { openEnded(stream, headers, withRange = false) },
                { openEnded(stream, headers, withRange = true) },
                { headerChunks(stream, headers) },
                { paramChunks(stream, headers) },
            )
            (modes.drop(round % modes.size) + modes.take(round % modes.size)).forEach { run ->
                val s = runCatching(run).getOrElse { println("round $round failed: ${it.message}"); null }
                s?.let { all += it; println("round $round: $it") }
            }
        }
        println("---- mean over ${rounds} rounds ----")
        all.groupBy { it.mode }.forEach { (mode, xs) ->
            println("%-34s mean %6.1f Mbit/s   min %6.1f   max %6.1f   (n=%d)".format(mode, xs.map { it.mbps }.average(), xs.minOf { it.mbps }, xs.maxOf { it.mbps }, xs.size))
        }
        assertTrue(all.isNotEmpty())
    }
}
