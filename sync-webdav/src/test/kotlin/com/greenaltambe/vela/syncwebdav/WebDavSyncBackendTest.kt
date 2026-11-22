package com.greenaltambe.vela.syncwebdav

import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.io.IOException
import kotlin.test.*

class WebDavSyncBackendTest {
    /** A tiny WebDAV server: one collection, files in memory. [canMove] false mimics servers without MOVE. */
    private class Dav(val canMove: Boolean = true) : Dispatcher() {
        val files = java.util.TreeMap<String, ByteArray>()
        var folderExists = false
        val seen = mutableListOf<String>()
        override fun dispatch(request: RecordedRequest): MockResponse {
            seen += "${request.method} ${request.path}"
            if (request.getHeader("Authorization") != Credentials.basic("me", "app-pass", Charsets.UTF_8)) return MockResponse().setResponseCode(401)
            val name = request.path!!.removePrefix("/dav/Vela%20sync").trim('/').let { java.net.URLDecoder.decode(it, "UTF-8") }
            return when (request.method) {
                "MKCOL" -> { folderExists = true; MockResponse().setResponseCode(201) }
                "PROPFIND" -> if (!folderExists) MockResponse().setResponseCode(404) else {
                    if (name.isNotEmpty() || request.getHeader("Depth") == "0") MockResponse().setResponseCode(207).setBody("<d:multistatus xmlns:d=\"DAV:\"/>")
                    else MockResponse().setResponseCode(207).setBody("<?xml version=\"1.0\"?><D:multistatus xmlns:D=\"DAV:\"><D:response><D:href>/dav/Vela%20sync/</D:href></D:response>" +
                        files.entries.joinToString("") { (n, b) -> "<D:response><D:href>/dav/Vela%20sync/${java.net.URLEncoder.encode(n, "UTF-8").replace("+", "%20")}</D:href><D:propstat><D:prop><D:getetag>\"${b.size}-${b.contentHashCode()}\"</D:getetag></D:prop></D:propstat></D:response>" } + "</D:multistatus>")
                }
                "PUT" -> { files[name] = request.body.readByteArray(); MockResponse().setResponseCode(201) }
                "GET" -> files[name]?.let { MockResponse().setBody(okio.Buffer().write(it)) } ?: MockResponse().setResponseCode(404)
                "DELETE" -> { files.remove(name); MockResponse().setResponseCode(204) }
                "MOVE" -> if (!canMove) MockResponse().setResponseCode(501) else {
                    val dest = java.net.URLDecoder.decode(request.getHeader("Destination")!!.substringAfterLast("/dav/Vela%20sync/"), "UTF-8")
                    files[dest] = files.remove(name)!!; MockResponse().setResponseCode(204)
                }
                else -> MockResponse().setResponseCode(405)
            }
        }
    }

    private fun server(dav: Dav) = MockWebServer().apply { dispatcher = dav; start() }
    private fun backend(s: MockWebServer, pass: String = "app-pass") = WebDavSyncBackend(s.url("/dav/Vela sync").toString(), "me", pass)

    @Test fun createsTheFolderWritesListsReadsAndDeletes() = runBlocking {
        val dav = Dav(); val s = server(dav)
        try {
            val b = backend(s)
            assertEquals(emptyList(), b.list())                       // folder missing: nothing yet
            b.write("a1.vsync", "hello".encodeToByteArray())
            assertTrue(dav.folderExists)
            assertEquals(listOf("a1.vsync"), b.list())
            assertEquals("hello", b.read("a1.vsync")!!.decodeToString())
            b.write("a1.vsync", "newer".encodeToByteArray())         // replace
            assertEquals("newer", b.read("a1.vsync")!!.decodeToString())
            assertNull(b.read("nope.vsync"))
            assertTrue(dav.files.keys.none { it.startsWith(".") }, "no temp files left behind")
            b.delete("a1.vsync"); assertEquals(emptyList(), b.list())
        } finally { s.shutdown() }
    }

    @Test fun fallsBackToPlainPutWhenTheServerCannotMove() = runBlocking {
        val dav = Dav(canMove = false); val s = server(dav)
        try {
            val b = backend(s); b.write("a1.vsync", "x".encodeToByteArray())
            assertEquals(setOf("a1.vsync"), dav.files.keys)
        } finally { s.shutdown() }
    }

    @Test fun fingerprintChangesWhenAFileChanges() = runBlocking {
        val dav = Dav(); val s = server(dav)
        try {
            val b = backend(s); b.write("a1.vsync", "1".encodeToByteArray())
            val f1 = b.fingerprint(); assertNotNull(f1)
            assertEquals(f1, b.fingerprint())
            b.write("a1.vsync", "22".encodeToByteArray())
            assertNotEquals(f1, b.fingerprint())
        } finally { s.shutdown() }
    }

    @Test fun wrongPasswordIsExplained() = runBlocking {
        val s = server(Dav())
        try {
            val e = assertFailsWith<IOException> { backend(s, "wrong").list() }
            assertTrue("username or password" in e.message!!, e.message)
        } finally { s.shutdown() }
    }

    @Test fun unreachableServerIsExplained() = runBlocking {
        val e = assertFailsWith<IOException> { WebDavSyncBackend("http://127.0.0.1:1/dav", "me", "x").list() }
        assertTrue("Could not reach" in e.message!!)
    }
}
