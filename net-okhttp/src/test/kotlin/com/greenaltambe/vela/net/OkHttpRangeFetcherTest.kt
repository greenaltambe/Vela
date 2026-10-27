package com.greenaltambe.vela.net

import com.greenaltambe.vela.core.http.TransportException
import com.greenaltambe.vela.downloads.ByteArrayCollector
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A server that honours `Range` the way googlevideo does: 206 with Content-Range, 416 past the end. */
private class RangeServer(val body: ByteArray) : Dispatcher() {
    val ranges = mutableListOf<String?>()
    override fun dispatch(request: RecordedRequest): MockResponse {
        val range = request.getHeader("Range")
        ranges += range
        if (range == null) return MockResponse().setResponseCode(200).setBody(Buffer().write(body))
        val m = Regex("bytes=(\\d+)-(\\d*)").matchEntire(range) ?: return MockResponse().setResponseCode(400)
        val start = m.groupValues[1].toInt()
        if (start >= body.size) return MockResponse().setResponseCode(416).setHeader("Content-Range", "bytes */${body.size}")
        val end = m.groupValues[2].toIntOrNull()?.coerceAtMost(body.size - 1) ?: (body.size - 1)
        return MockResponse().setResponseCode(206)
            .setHeader("Content-Range", "bytes $start-$end/${body.size}")
            .setBody(Buffer().write(body.copyOfRange(start, end + 1)))
    }
}

class OkHttpRangeFetcherTest {
    private val body = ByteArray(250_000) { (it * 7).toByte() }

    private fun withServer(block: suspend (MockWebServer, RangeServer) -> Unit) {
        val dispatcher = RangeServer(body)
        MockWebServer().use { server ->
            server.dispatcher = dispatcher
            server.start()
            runBlocking { block(server, dispatcher) }
        }
    }

    @Test fun aRangeIsRequestedAndTheBytesArrive() = withServer { server, dispatcher ->
        val sink = ByteArrayCollector()
        val r = OkHttpRangeFetcher().fetch(server.url("/v").toString(), mapOf("X-Test" to "1"), 1000, 69_999, sink)
        assertEquals(206, r.status)
        assertEquals(69_000, r.bytes)
        assertEquals(250_000L, r.totalLength)
        assertContentEquals(body.copyOfRange(1000, 70_000), sink.toByteArray())
        assertEquals("bytes=1000-69999", dispatcher.ranges.single())
        assertEquals("1", server.takeRequest().getHeader("X-Test"), "per-stream headers are sent")
    }

    @Test fun anOpenEndedRangeAndAWholeFetch() = withServer { server, dispatcher ->
        val open = ByteArrayCollector()
        OkHttpRangeFetcher().fetch(server.url("/v").toString(), emptyMap(), 240_000, null, open)
        assertEquals("bytes=240000-", dispatcher.ranges.last())
        assertContentEquals(body.copyOfRange(240_000, 250_000), open.toByteArray())

        val whole = ByteArrayCollector()
        val r = OkHttpRangeFetcher().fetch(server.url("/v").toString(), emptyMap(), 0, null, whole)
        assertNull(dispatcher.ranges.last(), "a plain fetch sends no Range header")
        assertEquals(200, r.status)
        assertEquals(250_000L, r.totalLength, "Content-Length of a full answer")
        assertEquals(250_000, whole.size())
    }

    @Test fun pastTheEndIs416WithTheTotalAndNoBytes() = withServer { server, _ ->
        val sink = ByteArrayCollector()
        val r = OkHttpRangeFetcher().fetch(server.url("/v").toString(), emptyMap(), 250_000, 260_000, sink)
        assertEquals(416, r.status)
        assertEquals(0, r.bytes)
        assertEquals(250_000L, r.totalLength)
        assertEquals(0, sink.size())
    }

    @Test fun errorStatusesAreReturnedNotThrown() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403).setBody("expired"))
            server.start()
            val r = runBlocking { OkHttpRangeFetcher().fetch(server.url("/").toString(), emptyMap(), 0, 99, ByteArrayCollector()) }
            assertEquals(403, r.status)
            assertEquals(0, r.bytes)
        }
    }

    @Test fun aConnectionDroppedMidBodyKeepsTheBytesAlreadyWrittenAndThrows() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-99999/250000")
                    .setBody(Buffer().write(body.copyOfRange(0, 100_000))).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
            )
            server.start()
            val sink = ByteArrayCollector()
            assertFailsWith<TransportException> {
                runBlocking { OkHttpRangeFetcher().fetch(server.url("/").toString(), emptyMap(), 0, 99_999, sink) }
            }
            // Whatever arrived before the drop is already in the sink: the caller resumes from the file's size.
            assertTrue(sink.size() < 100_000)
            assertContentEquals(body.copyOfRange(0, sink.size()), sink.toByteArray(), "and it is the right prefix")
        }
    }

    @Test fun anUnreachableServerIsATransportException() {
        val server = MockWebServer().apply { start() }
        val url = server.url("/").toString()
        server.shutdown()
        assertFailsWith<TransportException> { runBlocking { OkHttpRangeFetcher().fetch(url, emptyMap(), 0, 9, ByteArrayCollector()) } }
    }
}
