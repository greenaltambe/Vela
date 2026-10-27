package com.greenaltambe.vela.extractor.newpipe

import com.greenaltambe.vela.extractor.ExtractorError
import com.sun.net.httpserver.HttpServer
import org.schabi.newpipe.extractor.downloader.Request
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.zip.GZIPOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OkHttpDownloaderTest {
    private val seenHeaders = mutableMapOf<String, String?>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/gzip") { ex ->
            seenHeaders["ua"] = ex.requestHeaders.getFirst("User-Agent")
            seenHeaders["ae"] = ex.requestHeaders.getFirst("Accept-Encoding")
            val out = ByteArrayOutputStream().also { b -> GZIPOutputStream(b).use { it.write("hello gzip".toByteArray()) } }.toByteArray()
            ex.responseHeaders.add("Content-Encoding", "gzip")
            ex.sendResponseHeaders(200, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        createContext("/limited") { ex -> ex.sendResponseHeaders(429, -1); ex.close() }
        createContext("/forbidden") { ex -> ex.sendResponseHeaders(403, -1); ex.close() }
        createContext("/missing") { ex -> ex.sendResponseHeaders(404, -1); ex.close() }
        createContext("/echo") { ex ->
            val body = ex.requestBody.readBytes()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        start()
    }
    private val base = "http://127.0.0.1:${server.address.port}"
    private val downloader = OkHttpDownloader()

    @AfterTest fun stop() = server.stop(0)

    @Test fun decodesGzipAndSendsARealisticUserAgent() {
        val response = downloader.execute(
            Request.newBuilder().get("$base/gzip").addHeader("Accept-Encoding", "identity").build(),
        )
        assertEquals("hello gzip", response.responseBody())
        assertEquals(OkHttpDownloader.DEFAULT_USER_AGENT, seenHeaders["ua"])
        assertContains(seenHeaders["ae"].orEmpty(), "gzip", message = "OkHttp's own gzip negotiation must be what goes out")
    }

    @Test fun explicitUserAgentIsKept() {
        downloader.execute(Request.newBuilder().get("$base/gzip").addHeader("User-Agent", "custom/1").build())
        assertEquals("custom/1", seenHeaders["ua"])
    }

    @Test fun http429BecomesRateLimited() {
        val e = assertFailsWith<HttpStatusException> { downloader.execute(Request.newBuilder().get("$base/limited").build()) }
        assertEquals(429, e.code)
        assertIs<ExtractorError.RateLimited>(e.toExtractorError())
    }

    @Test fun http403OnABlockableHostBecomesBlocked() {
        val strict = OkHttpDownloader(isBlockableHost = { it == "127.0.0.1" })
        val e = assertFailsWith<HttpStatusException> { strict.execute(Request.newBuilder().get("$base/forbidden").build()) }
        assertEquals(403, e.code)
        assertIs<ExtractorError.Blocked>(e.toExtractorError())
    }

    @Test fun http403OnAStreamHostIsReturnedForNewPipeToInterpret() {
        // The default predicate only covers youtube.com, so a local "stream" host passes the 403 through.
        assertEquals(403, downloader.execute(Request.newBuilder().get("$base/forbidden").build()).responseCode())
    }

    @Test fun blockableHostsAreYoutubeButNotStreamHosts() {
        listOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtubei.googleapis.com", "WWW.YouTube.com").forEach {
            assertTrue(OkHttpDownloader.isYoutubeApiHost(it), it)
        }
        listOf("rr3---sn-abc.googlevideo.com", "i.ytimg.com", "notyoutube.com", "youtube.com.evil.example", "127.0.0.1").forEach {
            assertFalse(OkHttpDownloader.isYoutubeApiHost(it), it)
        }
    }

    @Test fun otherStatusCodesAreReturnedForNewPipeToInterpret() {
        assertEquals(404, downloader.execute(Request.newBuilder().get("$base/missing").build()).responseCode())
    }

    @Test fun postBodyIsSent() {
        val response = downloader.execute(Request.newBuilder().post("$base/echo", "payload".toByteArray()).build())
        assertEquals("payload", response.responseBody())
    }

    @Test fun connectionFailureIsANetworkError() {
        val dead = OkHttpDownloader()
        server.stop(0)
        val e = assertFailsWith<java.io.IOException> { dead.execute(Request.newBuilder().get("$base/gzip").build()) }
        assertIs<ExtractorError.Network>(e.toExtractorError())
    }
}
