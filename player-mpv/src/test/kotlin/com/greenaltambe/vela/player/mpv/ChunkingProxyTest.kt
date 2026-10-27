package com.greenaltambe.vela.player.mpv

import com.sun.net.httpserver.HttpServer
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChunkingProxyTest {
    private val body = ByteArray(2_500_000) { (it * 31 + it / 7).toByte() }
    private val upstreamRanges = CopyOnWriteArrayList<String?>()
    private val upstreamHeaders = CopyOnWriteArrayList<String?>()
    @Volatile private var forbidAfter = Int.MAX_VALUE
    @Volatile private var ignoreRange = false
    private val failures = CopyOnWriteArrayList<UpstreamFailure>()

    private val upstream = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        executor = Executors.newCachedThreadPool()
        createContext("/v") { ex ->
            val range = ex.requestHeaders.getFirst("Range")
            upstreamRanges += range
            upstreamHeaders += ex.requestHeaders.getFirst("X-Test")
            if (upstreamRanges.size > forbidAfter) { ex.sendResponseHeaders(403, -1); ex.close(); return@createContext }
            val r = parseRange(range)
            if (r == null || ignoreRange) {
                ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.write(body); ex.close(); return@createContext
            }
            val start = r.first.toInt()
            if (start >= body.size) { ex.sendResponseHeaders(416, -1); ex.close(); return@createContext }
            val end = minOf(r.second ?: (body.size - 1L), body.size - 1L).toInt()
            ex.responseHeaders["Content-Range"] = listOf("bytes $start-$end/${body.size}")
            ex.sendResponseHeaders(206, (end - start + 1).toLong())
            ex.responseBody.write(body, start, end - start + 1); ex.close()
        }
        start()
    }
    private val proxy = ChunkingProxy(chunkSize = 1_000_000) { failures += it }
    private val src = "http://127.0.0.1:${upstream.address.port}/v"

    @AfterTest fun tearDown() { proxy.close(); upstream.stop(0) }

    private fun get(url: String, range: String? = null, method: String = "GET"): Triple<Int, ByteArray, Map<String, List<String>>> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        range?.let { c.setRequestProperty("Range", it) }
        val code = c.responseCode
        val data = if (code in 200..299 && method == "GET") c.inputStream.readBytes() else ByteArray(0)
        return Triple(code, data, c.headerFields.filterKeys { it != null }.mapKeys { it.key.lowercase() })
    }

    @Test
    fun anOpenEndedRequestBecomesBoundedChunksUpstreamAndTheBodyIsIntact() {
        val url = proxy.register(StreamRole.Video, src, mapOf("X-Test" to "yes"))
        val (code, data, headers) = get(url, "bytes=0-")
        assertEquals(206, code)
        assertContentEquals(body, data)
        assertEquals("bytes 0-2499999/2500000", headers["content-range"]?.single())
        assertEquals(listOf("bytes=0-999999", "bytes=1000000-1999999", "bytes=2000000-2499999"), upstreamRanges.toList())
        assertTrue(upstreamHeaders.all { it == "yes" }, "the stream's own headers go upstream")
        assertEquals(2_500_000L, proxy.bytesServed.get())
    }

    @Test
    fun aRangeStartingMidFileIsServedFromThere() {
        val url = proxy.register(StreamRole.Video, src, emptyMap())
        val (code, data, headers) = get(url, "bytes=1234567-")
        assertEquals(206, code)
        assertContentEquals(body.copyOfRange(1_234_567, body.size), data)
        assertEquals("bytes 1234567-2499999/2500000", headers["content-range"]?.single())
        assertEquals("bytes=1234567-2234566", upstreamRanges.first())
    }

    @Test
    fun aBoundedRangeIsHonoured() {
        val url = proxy.register(StreamRole.Audio, src, emptyMap())
        val (code, data, _) = get(url, "bytes=100-199")
        assertEquals(206, code); assertContentEquals(body.copyOfRange(100, 200), data)
    }

    @Test
    fun noRangeHeaderGivesAFull200() {
        val url = proxy.register(StreamRole.Muxed, src, emptyMap())
        val (code, data, _) = get(url)
        assertEquals(200, code); assertContentEquals(body, data)
    }

    @Test
    fun headReportsTheLengthWithoutABody() {
        val url = proxy.register(StreamRole.Muxed, src, emptyMap())
        val (code, data, headers) = get(url, method = "HEAD")
        assertEquals(200, code); assertEquals(0, data.size)
        assertEquals("2500000", headers["content-length"]?.single())
    }

    @Test
    fun anUpstreamRefusalIsRelayedAndReported() {
        forbidAfter = 0
        val url = proxy.register(StreamRole.Audio, src, emptyMap())
        val (code, _, _) = get(url, "bytes=0-")
        assertEquals(403, code)
        val f = failures.single()
        assertEquals(StreamRole.Audio, f.role); assertEquals(403, f.status)
    }

    @Test
    fun aRefusalOnALaterChunkIsReportedAndTheConnectionAborted() {
        forbidAfter = 1
        val url = proxy.register(StreamRole.Video, src, emptyMap())
        runCatching { get(url, "bytes=0-") } // truncated body: the client sees an IO error
        Thread.sleep(200)
        assertEquals(403, failures.single().status)
    }

    @Test
    fun serverThatIgnoresRangeStillWorksForARangeFromZero() {
        ignoreRange = true
        val url = proxy.register(StreamRole.Video, src, emptyMap())
        val (code, data, _) = get(url, "bytes=0-")
        assertEquals(206, code); assertContentEquals(body, data)
    }

    @Test
    fun failuresOfAnEarlierLoadAreIgnoredAfterReset() {
        val url = proxy.register(StreamRole.Video, src, emptyMap())
        proxy.reset()
        assertEquals(404, get(url, "bytes=0-").first) // forgotten entirely
        assertTrue(failures.isEmpty())
    }

    @Test
    fun unknownStreamIs404() {
        assertEquals(404, get(proxy.register(StreamRole.Video, src, emptyMap()).replace(Regex("/s/\\d+"), "/s/999")).first)
    }

    @Test
    fun rangeParsing() {
        assertEquals(0L to null, parseRange("bytes=0-"))
        assertEquals(5L to 9L, parseRange("bytes=5-9"))
        assertNull(parseRange("bytes=0-1,5-9")); assertNull(parseRange(null)); assertNull(parseRange("items=1-2"))
        assertEquals(Triple(0L, 9L, 100L), parseContentRange("bytes 0-9/100"))
        assertEquals(Triple(0L, 9L, null), parseContentRange("bytes 0-9/*"))
    }
}
