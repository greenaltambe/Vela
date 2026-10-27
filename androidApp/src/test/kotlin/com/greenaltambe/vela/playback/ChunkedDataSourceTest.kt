package com.greenaltambe.vela.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.util.Collections
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs the real [ChunkedDataSource] over the real [DefaultHttpDataSource] against a local server that honours `Range`. */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChunkedDataSourceTest {
    private val data = ByteArray(2_500) { (it * 31 + it / 7).toByte() }
    private val requests: MutableList<Map<String, String>> = Collections.synchronizedList(mutableListOf())
    private lateinit var server: HttpServer
    private var honourRange = true
    private val chunk = 1_000L

    @Before fun start() {
        honourRange = true
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/media") { ex -> handle(ex) }
        server.start()
    }

    @After fun stop() { server.stop(0) }

    private fun handle(ex: HttpExchange) {
        requests += ex.requestHeaders.entries.associate { it.key.lowercase() to it.value.first() }
        val range = ex.requestHeaders.getFirst("Range")
        if (!honourRange || range == null) {
            ex.sendResponseHeaders(200, data.size.toLong())
            ex.responseBody.use { it.write(data) }
            return
        }
        val (a, b) = range.removePrefix("bytes=").split('-').let { it[0].toLong() to it[1].takeIf(String::isNotEmpty)?.toLong() }
        if (a >= data.size) {
            ex.responseHeaders.add("Content-Range", "bytes */${data.size}")
            ex.sendResponseHeaders(416, -1)
            ex.close()
            return
        }
        val end = minOf(b ?: (data.size - 1L), data.size - 1L)
        ex.responseHeaders.add("Content-Range", "bytes $a-$end/${data.size}")
        ex.sendResponseHeaders(206, end - a + 1)
        ex.responseBody.use { it.write(data, a.toInt(), (end - a + 1).toInt()) }
    }

    private fun source() = ChunkedDataSource(
        DefaultHttpDataSource.Factory().setDefaultRequestProperties(mapOf("X-Test" to "yes")).setUserAgent("test-agent").createDataSource(),
        chunkBytes = chunk,
    )

    private fun spec(position: Long = 0, length: Long = C.LENGTH_UNSET.toLong()) =
        DataSpec.Builder().setUri(Uri.parse("http://127.0.0.1:${server.address.port}/media")).setPosition(position).setLength(length).build()

    /** Opens, reads everything the way the player does (arbitrary read sizes), closes. */
    private fun readAll(ds: ChunkedDataSource, spec: DataSpec, readSize: Int = 333): Pair<Long, ByteArray> {
        val length = ds.open(spec)
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(readSize)
        while (true) {
            val n = ds.read(buf, 0, buf.size)
            if (n == C.RESULT_END_OF_INPUT) break
            out.write(buf, 0, n)
        }
        ds.close()
        return length to out.toByteArray()
    }

    private fun ranges() = requests.map { it["range"] }

    @Test fun readsTheWholeResourceAsBoundedRangesAndReportsItsLength() {
        val (length, bytes) = readAll(source(), spec())
        assertEquals(data.size.toLong(), length)
        assertContentEquals(data, bytes)
        assertEquals(listOf("bytes=0-999", "bytes=1000-1999", "bytes=2000-2499"), ranges(), "never open-ended; the last chunk is cut to what is left")
    }

    @Test fun everyChunkRequestCarriesTheConfiguredHeaders() {
        readAll(source(), spec())
        assertEquals(3, requests.size)
        assertTrue(requests.all { it["x-test"] == "yes" && it["user-agent"] == "test-agent" }, requests.toString())
    }

    @Test fun seekingStartsAFreshChunkAtThePositionAndReportsTheBytesLeft() {
        val (length, bytes) = readAll(source(), spec(position = 1_700))
        assertEquals(800L, length)
        assertContentEquals(data.copyOfRange(1_700, data.size), bytes)
        assertEquals(listOf("bytes=1700-2699"), ranges())
    }

    @Test fun aSecondOpenOnTheSameSourceAfterCloseWorksLikeASeek() {
        val ds = source()
        readAll(ds, spec(position = 0, length = 10))
        val (_, bytes) = readAll(ds, spec(position = 2_200))
        assertContentEquals(data.copyOfRange(2_200, data.size), bytes)
    }

    @Test fun aBoundedDataSpecStopsAtItsLengthAndLastChunkIsShortened() {
        val (length, bytes) = readAll(source(), spec(position = 100, length = 1_500))
        assertEquals(1_500L, length)
        assertContentEquals(data.copyOfRange(100, 1_600), bytes)
        assertEquals(listOf("bytes=100-1099", "bytes=1100-1599"), ranges())
    }

    @Test fun aLengthBeyondTheResourceIsClampedToTheResource() {
        val (length, bytes) = readAll(source(), spec(position = 2_000, length = 9_999))
        assertEquals(500L, length)
        assertContentEquals(data.copyOfRange(2_000, 2_500), bytes)
    }

    @Test fun aResourceThatIsAnExactMultipleOfTheChunkSizeEndsWithoutAnExtraRequest() {
        val exact = ChunkedDataSource(DefaultHttpDataSource.Factory().createDataSource(), chunkBytes = 500)
        val (_, bytes) = readAll(exact, spec())
        assertContentEquals(data, bytes)
        assertEquals(5, requests.size, "2500 / 500 = 5 chunks, no empty sixth")
    }

    @Test fun readingExactlyAtTheEndGivesEndOfInput() {
        val ds = source()
        // position == size: the server answers 416; that must read as an empty resource, not an error.
        val length = ds.open(spec(position = data.size.toLong()))
        assertEquals(0L, length)
        assertEquals(C.RESULT_END_OF_INPUT, ds.read(ByteArray(10), 0, 10))
        ds.close()
    }

    @Test fun aServerThatIgnoresRangeIsStreamedThroughUnchunked() {
        honourRange = false
        val (length, bytes) = readAll(source(), spec())
        assertEquals(data.size.toLong(), length)
        assertContentEquals(data, bytes)
        assertEquals(2, requests.size, "the bounded probe, then one unbounded request")
    }

    @Test fun aServerThatIgnoresRangeAlsoHonoursABoundedDataSpecThroughPassThrough() {
        honourRange = false
        val (length, bytes) = readAll(source(), spec(position = 0, length = 1_800))
        assertEquals(1_800L, length)
        assertContentEquals(data.copyOfRange(0, 1_800), bytes)
    }

    @Test fun smallAndLargeReadSizesGiveTheSameBytes() {
        listOf(1, 7, 999, 1000, 1001, 4096).forEach { size ->
            assertContentEquals(data, readAll(source(), spec(), readSize = size).second, "read size $size")
        }
    }

    @Test fun zeroLengthReadsReturnZero() {
        val ds = source()
        ds.open(spec())
        assertEquals(0, ds.read(ByteArray(4), 0, 0))
        ds.close()
    }
}
