package com.greenaltambe.vela.net

import com.greenaltambe.vela.core.http.HttpRequest
import com.greenaltambe.vela.core.http.TransportException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OkHttpTransportTest {
    @Test
    fun getReturnsStatusAndBodyAndSendsHeaders() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("hello"))
            server.start()
            val response = OkHttpTransport().execute(HttpRequest(server.url("/x").toString(), headers = mapOf("User-Agent" to "Test/1")))
            assertEquals(200, response.status)
            assertEquals("hello", response.body)
            assertEquals("Test/1", server.takeRequest().getHeader("User-Agent"))
        }
    }

    @Test
    fun errorStatusesAreResponsesNotExceptions() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404).setBody("nope"))
            server.start()
            val response = OkHttpTransport().execute(HttpRequest(server.url("/").toString()))
            assertEquals(404, response.status)
        }
    }

    @Test
    fun postSendsTheBodyAndContentType() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200))
            server.start()
            OkHttpTransport().execute(HttpRequest(server.url("/p").toString(), "POST", body = """{"a":1}""", contentType = "application/json"))
            val seen = server.takeRequest()
            assertEquals("POST", seen.method)
            assertEquals("""{"a":1}""", seen.body.readUtf8())
            assertEquals("application/json; charset=utf-8", seen.getHeader("Content-Type"))
        }
    }

    @Test
    fun connectionFailureIsATransportException() {
        val server = MockWebServer().apply { start() }
        val url = server.url("/").toString()
        server.shutdown()
        assertFailsWith<TransportException> { runBlocking { OkHttpTransport(connectTimeoutMs = 500).execute(HttpRequest(url)) } }
    }

    @Test
    fun aStalledServerTimesOutInsteadOfHanging() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.start()
            val started = System.nanoTime()
            assertFailsWith<TransportException> { runBlocking { OkHttpTransport(readTimeoutMs = 300, callTimeoutMs = 600).execute(HttpRequest(server.url("/").toString())) } }
            val tookMs = (System.nanoTime() - started) / 1_000_000
            assert(tookMs < 3_000) { "took $tookMs ms" }
        }
    }
}
