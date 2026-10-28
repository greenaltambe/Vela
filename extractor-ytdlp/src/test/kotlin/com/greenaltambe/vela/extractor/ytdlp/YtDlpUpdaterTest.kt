package com.greenaltambe.vela.extractor.ytdlp

import com.greenaltambe.vela.core.settings.YtDlpAutoUpdate
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class YtDlpUpdaterTest {
    private val dir = Files.createTempDirectory("ytdlp-upd").toFile()
    private val asset = "yt-dlp_linux"
    private var binaryText = "#!/bin/sh\necho 2026.11.01\n"
    private var sumsOverride: ((String) -> String)? = null
    private var failDownloadOf: String? = null
    private var healthy = true
    private val downloaded = mutableListOf<String>()

    private val downloader = Downloader { url, target ->
        downloaded += url
        if (failDownloadOf != null && url.endsWith(failDownloadOf!!)) error("HTTP 503")
        if (url.endsWith("SHA2-256SUMS")) target.writeText(sumsOverride?.invoke(binaryText) ?: "${sha(binaryText)}  $asset\n0000  other\n") else target.writeText(binaryText)
    }
    private fun sha(t: String) = File.createTempFile("sum", "").also { it.writeText(t) }.let { YtDlpUpdater.sha256(it).also { _ -> it.delete() } }
    // The "binary" is a shell script, run for real, so the --version check is genuine.
    private fun updater(check: suspend (String) -> Boolean = { healthy }) = YtDlpUpdater(dir, downloader, SystemProcessRunner(), check, asset)

    @Test fun installsVerifiedBinaryAndReportsIt() = runBlocking<Unit> {
        val u = updater()
        val rr = u.update("2026.11.01"); assertIs<UpdateResult.Updated>(rr, (rr as? UpdateResult.Failed)?.why)
        assertNotNull(u.managedBinary()); assertEquals(binaryText, u.managed.readText())
        assertTrue(downloaded.first().endsWith("/2026.11.01/SHA2-256SUMS") && downloaded.all { it.startsWith("https://") })
        assertEquals(listOf("yt-dlp"), dir.list()!!.toList(), "no temp files left")
    }

    @Test fun checksumMismatchChangesNothing() = runBlocking<Unit> {
        sumsOverride = { "deadbeef  $asset\n" }
        val u = updater(); u.managed.writeText("old")
        val r = assertIs<UpdateResult.Failed>(u.update("2026.11.01"))
        assertTrue("checksum" in r.why); assertEquals("old", u.managed.readText())
    }

    @Test fun missingChecksumEntryIsAFailure() = runBlocking<Unit> {
        sumsOverride = { "abc  something-else\n" }
        assertIs<UpdateResult.Failed>(updater().update("2026.11.01"))
    }

    @Test fun versionMismatchIsRefused() = runBlocking<Unit> {
        binaryText = "#!/bin/sh\necho 2025.01.01\n"
        val r = assertIs<UpdateResult.Failed>(updater().update("2026.11.01"))
        assertTrue("reports 2025.01.01" in r.why)
    }

    @Test fun failedCanaryRollsBackToThePreviousCopy() = runBlocking<Unit> {
        val u = updater(); u.managed.writeText("#!/bin/sh\necho old\n"); u.managed.setExecutable(true)
        healthy = false
        val r = assertIs<UpdateResult.RolledBack>(u.update("2026.11.01"))
        assertEquals("2026.11.01", r.attempted); assertEquals("#!/bin/sh\necho old\n", u.managed.readText())
    }

    @Test fun failedCanaryWithNoPreviousCopyRemovesTheNewOne() = runBlocking<Unit> {
        healthy = false
        assertIs<UpdateResult.RolledBack>(updater().update("2026.11.01"))
        assertNull(updater().managedBinary())
    }

    @Test fun downloadFailureLeavesTheOldCopy() = runBlocking<Unit> {
        val u = updater(); u.managed.writeText("old")
        failDownloadOf = asset
        assertIs<UpdateResult.Failed>(u.update("2026.11.01")); assertEquals("old", u.managed.readText())
    }

    @Test fun previousCopyIsKeptAfterAGoodUpdate() = runBlocking<Unit> {
        val u = updater(); u.managed.writeText("old")
        assertIs<UpdateResult.Updated>(u.update("2026.11.01")); assertEquals("old", u.previous.readText())
    }

    @Test fun parsingAndModes() {
        assertEquals("abc", YtDlpUpdater.parseSums("abc *yt-dlp_linux\n", "yt-dlp_linux"))
        assertNull(YtDlpUpdater.parseSums("abc  yt-dlp_linux_aarch64\n", "yt-dlp_linux"))
        assertEquals("yt-dlp_linux_aarch64", YtDlpUpdater.defaultAsset("aarch64")); assertEquals("yt-dlp_linux", YtDlpUpdater.defaultAsset("amd64"))
        assertTrue(shouldAutoInstall(YtDlpAutoUpdate.AUTO, true)); assertFalse(shouldAutoInstall(YtDlpAutoUpdate.ASK, true)); assertFalse(shouldAutoInstall(YtDlpAutoUpdate.AUTO, false)); assertFalse(shouldAutoInstall(YtDlpAutoUpdate.OFF, true))
    }
}
