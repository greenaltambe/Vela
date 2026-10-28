package com.greenaltambe.vela.extractor.ytdlp

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Real yt-dlp with the throwaway account's cookies (`VELA_TEST_COOKIES`, else `~/.vela-test/cookies.txt`; skipped without):
 * which hosts does it contact, and which of them does it send the Cookie header to? Measured 2026-10-28 with yt-dlp 2026.10.26:
 * only `www.youtube.com` got cookies; `manifest.googlevideo.com` was contacted without them. The test fails when a host
 * outside the known sets appears, or when a cookie goes anywhere but a YouTube/Google account host, so a yt-dlp update that
 * starts talking to something new is noticed instead of trusted. Cookie values are never printed.
 */
class LiveTrafficHostsTest {
    private val allowedContacted = Regex("""(www\.youtube\.com|youtubei\.googleapis\.com|accounts\.google\.com|manifest\.googlevideo\.com|[a-z0-9-]+\.googlevideo\.com)""")
    private val allowedWithCookie = setOf("www.youtube.com", "youtubei.googleapis.com", "accounts.google.com")

    @Test fun cookiesOnlyGoToYouTubeAndNewHostsAreFlagged() {
        val source = File(System.getenv("VELA_TEST_COOKIES") ?: File(System.getProperty("user.home"), ".vela-test/cookies.txt").path)
        if (!source.isFile) { println("SKIPPED: no VELA_TEST_COOKIES / ~/.vela-test/cookies.txt"); return }
        val yt = BinaryLocator().locate("yt-dlp").path ?: run { println("SKIPPED: no yt-dlp"); return }
        val dir = Files.createTempDirectory("vela-traffic", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))).toFile()
        try {
            val jar = File(dir, "c.txt").also { source.copyTo(it); it.setReadable(false, false); it.setReadable(true, true) }
            val node = SystemPathLookup.find("node")
            val cmd = listOfNotNull(yt, "--print-traffic", "--no-warnings", "-s", "--no-playlist", "--cookies", jar.path) +
                (node?.let { listOf("--js-runtimes", "node:$it") } ?: emptyList()) + "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
            val out = ProcessBuilder(cmd).redirectErrorStream(true).start().let { p -> p.inputStream.bufferedReader().readText().also { p.waitFor() } }
            val requests = Regex("""send: b'((?:GET|POST|HEAD) .*?)'\n""", RegexOption.DOT_MATCHES_ALL).findAll(out).map { it.groupValues[1] }.toList()
            val byHost = linkedMapOf<String, Pair<Int, Int>>()
            for (r in requests) {
                val host = Regex("""Host: ([^\\\r\n]+)""").find(r)?.groupValues?.get(1) ?: "?"
                val (n, c) = byHost[host] ?: (0 to 0)
                byHost[host] = (n + 1) to (c + if ("Cookie:" in r) 1 else 0)
            }
            byHost.forEach { (h, v) -> println("TRAFFIC host=$h requests=${v.first} with_cookie=${v.second}") }
            assertTrue(requests.isNotEmpty(), "no requests parsed: yt-dlp's --print-traffic format may have changed")
            val unknown = byHost.keys.filterNot { allowedContacted.matches(it) }
            assertEquals(emptyList(), unknown, "yt-dlp contacted a host this test does not know")
            val leaked = byHost.filter { (h, v) -> v.second > 0 && h !in allowedWithCookie }.keys
            assertEquals(emptySet(), leaked, "cookies were sent to hosts other than YouTube/Google account hosts")
        } finally { dir.deleteRecursively() }
    }
}
