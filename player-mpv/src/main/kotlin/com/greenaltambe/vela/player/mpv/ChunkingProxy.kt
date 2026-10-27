package com.greenaltambe.vela.player.mpv

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** A failure the server (not mpv) saw: [status] is the HTTP status, or null for I/O errors. */
data class UpstreamFailure(val role: StreamRole, val status: Int?, val message: String)

/** `Range: bytes=start-end` (end inclusive, null = to the end), or null if absent or not a single simple range. */
internal fun parseRange(header: String?): Pair<Long, Long?>? {
    val m = Regex("""bytes=(\d+)-(\d*)""").matchEntire(header?.trim() ?: return null) ?: return null
    return m.groupValues[1].toLong() to m.groupValues[2].takeIf { it.isNotEmpty() }?.toLong()
}

/** `bytes a-b/total` from an upstream `Content-Range`, as (a, b, total); total is null for `*`. */
internal fun parseContentRange(header: String?): Triple<Long, Long, Long?>? {
    val m = Regex("""bytes (\d+)-(\d+)/(\d+|\*)""").matchEntire(header?.trim() ?: return null) ?: return null
    return Triple(m.groupValues[1].toLong(), m.groupValues[2].toLong(), m.groupValues[3].toLongOrNull())
}

/**
 * A loopback HTTP server that mpv reads instead of googlevideo. googlevideo throttles open-ended requests to about
 * 5 Mbit/s but serves bounded ones at line rate (measured in task 07), and ffmpeg's HTTP client has no way to bound
 * requests, so the proxy turns mpv's `Range: bytes=N-` into a series of [chunkSize] ranges upstream, the same trick
 * as the Android `ChunkedDataSource`. It also applies each stream's own headers (mpv can only set one global set),
 * relays upstream HTTP statuses unchanged so mpv's error text names the real status, and reports failures to
 * [onFailure] because a truncated stream looks like a clean EOF to mpv.
 */
class ChunkingProxy(
    private val chunkSize: Long = 10L * 1024 * 1024,
    private val onFailure: (UpstreamFailure) -> Unit = {},
) : AutoCloseable {
    private class Registered(val url: String, val headers: Map<String, String>, val role: StreamRole, val epoch: Int)

    private val streams = ConcurrentHashMap<Int, Registered>()
    private val nextId = AtomicInteger(1)
    private val epoch = AtomicInteger(0)
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 16)
    private val executor = Executors.newCachedThreadPool { r -> Thread(r, "vela-proxy").apply { isDaemon = true } }

    val bytesServed = AtomicLong()
    @Volatile var lastChunkBitsPerSecond: Long = 0
        private set
    val upstreamRequests = AtomicInteger()

    init {
        server.executor = executor
        server.createContext("/s/") { ex -> handle(ex) }
        server.start()
    }

    /** Forget every registered stream, e.g. at the start of a new load; failures of older loads are then ignored. */
    fun reset() {
        epoch.incrementAndGet()
        streams.clear()
    }

    /** The loopback URL mpv should open for [url]. */
    fun register(role: StreamRole, url: String, headers: Map<String, String>): String {
        val id = nextId.getAndIncrement()
        streams[id] = Registered(url, headers, role, epoch.get())
        return "http://127.0.0.1:${server.address.port}/s/$id"
    }

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }

    private fun handle(ex: HttpExchange) {
        val id = ex.requestURI.path.removePrefix("/s/").toIntOrNull()
        val stream = id?.let { streams[it] }
        try {
            if (stream == null) { ex.sendResponseHeaders(404, -1); return }
            serve(ex, stream)
        } catch (e: IOException) {
            // mpv hung up (seek, stop): normal.
        } catch (e: Exception) {
            report(stream, null, e.toString())
        } finally {
            ex.close()
        }
    }

    private fun report(stream: Registered?, status: Int?, message: String) {
        if (stream != null && stream.epoch == epoch.get()) onFailure(UpstreamFailure(stream.role, status, message))
    }

    private fun open(stream: Registered, from: Long, toInclusive: Long): HttpURLConnection =
        (URL(stream.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            stream.headers.forEach { (k, v) -> setRequestProperty(k, v) }
            setRequestProperty("Range", "bytes=$from-$toInclusive")
            upstreamRequests.incrementAndGet()
        }

    private fun serve(ex: HttpExchange, stream: Registered) {
        val requested = parseRange(ex.requestHeaders.getFirst("Range"))
        val start = requested?.first ?: 0L
        val head = ex.requestMethod.equals("HEAD", ignoreCase = true)

        var conn = open(stream, start, start + chunkSize - 1)
        var chunkEnd: Long
        val total: Long
        val contentType: String?
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                // Relay the status; mpv logs "HTTP error 403 Forbidden" and we tell the controller.
                report(stream, code, "HTTP error $code ${conn.responseMessage.orEmpty()}")
                ex.sendResponseHeaders(code, -1)
                return
            }
            contentType = conn.contentType
            if (code == 206) {
                val cr = parseContentRange(conn.getHeaderField("Content-Range"))
                chunkEnd = cr?.second ?: (start + conn.contentLengthLong - 1)
                total = cr?.third ?: (chunkEnd + 1)
            } else {
                // Server ignored Range: it is sending everything from byte 0.
                total = conn.contentLengthLong
                chunkEnd = total - 1
                if (start > 0) conn.inputStream.skipNBytes(start)
            }
        } catch (e: IOException) {
            report(stream, null, "connect failed: ${e.message}")
            ex.sendResponseHeaders(502, -1)
            return
        }
        if (start >= total) { ex.sendResponseHeaders(416, -1); return }

        val last = minOf(requested?.second ?: (total - 1), total - 1)
        val length = last - start + 1
        contentType?.let { ex.responseHeaders["Content-Type"] = listOf(it) }
        ex.responseHeaders["Accept-Ranges"] = listOf("bytes")
        if (head) ex.responseHeaders["Content-Length"] = listOf(length.toString()) // -1 below means "no body", not "no length"
        if (requested != null) {
            ex.responseHeaders["Content-Range"] = listOf("bytes $start-$last/$total")
            ex.sendResponseHeaders(206, if (head) -1 else length)
        } else {
            ex.sendResponseHeaders(200, if (head) -1 else length)
        }
        if (head) { conn.disconnect(); return }

        var position = start
        var retries = 0
        while (true) {
            val t0 = System.nanoTime()
            var got = 0L
            try {
                val upstream: InputStream = conn.inputStream
                val buffer = ByteArray(64 * 1024)
                while (position <= minOf(chunkEnd, last)) {
                    val n = upstream.read(buffer, 0, minOf(buffer.size.toLong(), minOf(chunkEnd, last) - position + 1).toInt())
                    if (n < 0) throw IOException("upstream ended early at $position of ${minOf(chunkEnd, last)}")
                    try { ex.responseBody.write(buffer, 0, n) } catch (e: IOException) { throw ClientGone() }
                    position += n; got += n
                }
                bytesServed.addAndGet(got)
                val ns = System.nanoTime() - t0
                if (ns > 0 && got > 100_000) lastChunkBitsPerSecond = got * 8 * 1_000_000_000L / ns
                conn.disconnect()
            } catch (e: ClientGone) {
                conn.disconnect(); return
            } catch (e: IOException) {
                conn.disconnect()
                if (retries++ >= 2) { report(stream, null, "upstream read failed: ${e.message}"); ex.close(); return }
                bytesServed.addAndGet(got)
                conn = reopen(stream, position, ex) ?: return
                chunkEnd = minOf(position + chunkSize - 1, total - 1)
                continue
            }
            if (position > last) return
            // Next chunk.
            chunkEnd = minOf(position + chunkSize - 1, total - 1)
            conn = open(stream, position, chunkEnd)
            val code = try { conn.responseCode } catch (e: IOException) { report(stream, null, "chunk request failed: ${e.message}"); ex.close(); return }
            if (code != 206 && code != 200) {
                report(stream, code, "HTTP error $code ${conn.responseMessage.orEmpty()} on chunk at $position")
                ex.close(); return
            }
        }
    }

    private fun reopen(stream: Registered, position: Long, ex: HttpExchange): HttpURLConnection? {
        val c = open(stream, position, position + chunkSize - 1)
        val code = try { c.responseCode } catch (e: IOException) { report(stream, null, "reconnect failed: ${e.message}"); ex.close(); return null }
        if (code != 206 && code != 200) { report(stream, code, "HTTP error $code on reconnect at $position"); ex.close(); return null }
        return c
    }

    /** The write to mpv failed (it seeked or stopped), as opposed to the upstream read failing. */
    private class ClientGone : IOException()
}
