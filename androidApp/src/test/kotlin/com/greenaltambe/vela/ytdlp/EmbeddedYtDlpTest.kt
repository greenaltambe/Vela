package com.greenaltambe.vela.ytdlp

import com.greenaltambe.vela.extractor.ytdlp.Downloader
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Plain JVM tests: the updater and cookie sweep need no Android classes or native libraries. */
class EmbeddedYtDlpTest {
    private val dir = File.createTempFile("ytdlp-test", "").let { it.delete(); it.mkdirs(); it }
    private val managed = File(dir, "yt-dlp")
    @AfterTest fun clean() { dir.deleteRecursively() }

    /** A yt-dlp-shaped zipapp: `#!` line first (as in the real release), then a zip with yt_dlp/version.py. */
    private fun zipapp(version: String): ByteArray {
        val out = ByteArrayOutputStream(); out.write("#!/usr/bin/env python3\n".toByteArray())
        ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry("yt_dlp/__init__.py")); z.write("x".toByteArray()); z.closeEntry(); z.putNextEntry(ZipEntry("yt_dlp/version.py")); z.write("__version__ = '$version'\n".toByteArray()); z.closeEntry() }
        return out.toByteArray()
    }
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    private fun release(tagVersion: String, sums: ((ByteArray) -> String)? = null, content: ByteArray = zipapp(tagVersion)) = Downloader { url, target ->
        if (url.endsWith("SHA2-256SUMS")) target.writeText("${(sums ?: ::sha)(content)}  yt-dlp\n") else target.writeBytes(content)
    }
    private fun updater(dl: Downloader, healthy: Boolean = true) = EmbeddedYtDlpUpdater(managed, {}, { healthy }, dl)
    private fun installOld() { managed.writeBytes(zipapp("2025.11.12")) }

    @Test fun theVersionIsReadFromAZipappWithAShebangInFront() {
        installOld(); assertEquals("2025.11.12", readYtDlpVersion(managed))
        File(dir, "junk").writeText("not a zip"); assertNull(readYtDlpVersion(File(dir, "junk")))
    }

    @Test fun aGoodUpdateReplacesTheFileAndKeepsThePrevious() = runBlocking<Unit> {
        installOld(); assertTrue(updater(release("2026.10.26")).install("2026.10.26"))
        assertEquals("2026.10.26", readYtDlpVersion(managed)); assertEquals("2025.11.12", readYtDlpVersion(File(dir, "yt-dlp.previous")))
        assertEquals(setOf("yt-dlp", "yt-dlp.previous"), dir.list()!!.toSet(), "no temp files left")
    }

    @Test fun aWrongChecksumChangesNothing() = runBlocking<Unit> {
        installOld(); val u = updater(release("2026.10.26", sums = { "0".repeat(64) }))
        assertFalse(u.install("2026.10.26")); assertEquals("2025.11.12", readYtDlpVersion(managed)); assertTrue("checksum mismatch" in u.status.value)
        assertEquals(setOf("yt-dlp"), dir.list()!!.toSet())
    }

    @Test fun anArchiveThatReportsAnotherVersionThanTheTagIsRefused() = runBlocking<Unit> {
        installOld(); val u = updater(release("2026.10.26", content = zipapp("2026.01.01")))
        assertFalse(u.install("2026.10.26")); assertEquals("2025.11.12", readYtDlpVersion(managed)); assertTrue("reports 2026.01.01" in u.status.value)
    }

    @Test fun aFailedHealthCheckPutsThePreviousCopyBack() = runBlocking<Unit> {
        installOld(); val u = updater(release("2026.10.26"), healthy = false)
        assertFalse(u.install("2026.10.26")); assertEquals("2025.11.12", readYtDlpVersion(managed)); assertTrue("previous copy is back" in u.status.value)
    }

    @Test fun aThrowingHealthCheckOrDownloadAlsoChangesNothing() = runBlocking<Unit> {
        installOld()
        assertFalse(EmbeddedYtDlpUpdater(managed, {}, { error("canary crashed") }, release("2026.10.26")).install("2026.10.26"))
        assertEquals("2025.11.12", readYtDlpVersion(managed))
        assertFalse(updater(Downloader { _, _ -> throw java.io.IOException("offline") }).install("2026.10.26"))
        assertEquals("2025.11.12", readYtDlpVersion(managed)); assertEquals(setOf("yt-dlp", "yt-dlp.previous").intersect(dir.list()!!.toSet()).contains("yt-dlp"), true)
        assertFalse(File(dir, "yt-dlp.new").exists() || File(dir, "SHA2-256SUMS.tmp").exists())
    }

    @Test fun cookieFilesLeftByAKilledProcessAreSweptAtStartup() {
        val a = File(dir, "vela-1.cookies.txt").apply { writeText("SID=MARKER") }; val b = File(dir, "vela-2.cookies.txt").apply { writeText("x") }; val keep = File(dir, "other.txt").apply { writeText("x") }
        assertEquals(2, EmbeddedYtDlp.sweepCookies(dir)); assertFalse(a.exists() || b.exists()); assertTrue(keep.exists())
    }

    @Test fun theAndroidYtDlpCodeNeverLogsOrPrintsAnything() {
        // Leak guard: the runner sees cookie file paths and yt-dlp's stderr; nothing there may reach logcat or stdout.
        val src = File("src/main/kotlin/com/greenaltambe/vela/ytdlp").listFiles { f -> f.extension == "kt" }!!
        assertTrue(src.isNotEmpty())
        for (f in src) assertTrue(Regex("""\b(Log\.[a-z]\(|println\(|print\(|Timber\.)""").find(f.readText()) == null, "${f.name} must not log")
    }
}
