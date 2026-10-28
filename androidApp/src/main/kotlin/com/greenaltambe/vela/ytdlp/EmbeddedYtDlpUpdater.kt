package com.greenaltambe.vela.ytdlp

import com.greenaltambe.vela.extractor.ytdlp.Downloader
import com.greenaltambe.vela.extractor.ytdlp.YtDlpUpdater
import com.greenaltambe.vela.health.ManagedUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

/** HTTPS download with OkHttp (java.net.http does not exist on Android). */
class OkHttpFileDownloader(private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()) : Downloader {
    override suspend fun download(url: String, target: File) {
        require(url.startsWith("https://")) { "refusing non-https url" }
        withTimeout(5.minutes) {
            runInterruptible(Dispatchers.IO) {
                client.newCall(Request.Builder().url(url).header("User-Agent", "Vela/1.0 (yt-dlp updater)").build()).execute().use { r ->
                    check(r.isSuccessful) { "HTTP ${r.code} for $url" }
                    r.body.byteStream().use { i -> target.outputStream().use { i.copyTo(it) } }
                }
            }
        }
    }
}

/**
 * The same update contract as the desktop [YtDlpUpdater], for the zipapp `yt-dlp` release asset (a Python zip archive, run by the
 * bundled interpreter): download it and `SHA2-256SUMS` over HTTPS, check the SHA-256, check the archive's own `__version__` equals
 * the tag, keep the old copy as `yt-dlp.previous`, swap, then run [healthCheck] (the tier's canary through the new copy) and put the
 * previous copy back if it fails. No root is needed: everything lives under the app's private files.
 */
class EmbeddedYtDlpUpdater(
    /** The managed yt-dlp file (the library's own location), and a hook that makes sure the library has unpacked before it is touched. */
    private val managed: File,
    private val ensureInit: suspend () -> Unit,
    private val healthCheck: suspend () -> Boolean,
    private val downloader: Downloader = OkHttpFileDownloader(),
    private val baseUrl: String = "https://github.com/yt-dlp/yt-dlp/releases/download",
    private val onResult: (String, Boolean) -> Unit = { _, _ -> },
    /** The last result, remembered across restarts (Health shows when the last update happened or was rolled back). */
    initialStatus: String = "",
) : ManagedUpdater {
    private val _status = MutableStateFlow(initialStatus)
    override val status: StateFlow<String> = _status
    override val installsVerifiedMinimum: Boolean get() = true
    private val previous get() = File(managed.parentFile, "yt-dlp.previous")

    override suspend fun install(tag: String): Boolean {
        _status.value = "Updating yt-dlp to $tag…"
        val (ok, message) = try { doInstall(tag) } catch (e: CancellationException) { throw e } catch (e: Exception) { false to "yt-dlp update failed: ${e.message ?: e}. Nothing was changed." }
        _status.value = message; onResult(message, ok)
        return ok
    }

    private suspend fun doInstall(tag: String): Pair<Boolean, String> {
        ensureInit()
        val dir = managed.parentFile!!.also { it.mkdirs() }
        val sums = File(dir, "SHA2-256SUMS.tmp"); val fresh = File(dir, "yt-dlp.new")
        try {
            downloader.download("$baseUrl/$tag/SHA2-256SUMS", sums)
            val expected = YtDlpUpdater.parseSums(sums.readText(), "yt-dlp") ?: return false to "yt-dlp update failed: SHA2-256SUMS has no entry for yt-dlp. Nothing was changed."
            downloader.download("$baseUrl/$tag/yt-dlp", fresh)
            val actual = YtDlpUpdater.sha256(fresh)
            if (!actual.equals(expected, ignoreCase = true)) return false to "yt-dlp update failed: checksum mismatch (expected ${expected.take(12)}…, got ${actual.take(12)}…). Nothing was changed."
            val reported = readYtDlpVersion(fresh) ?: return false to "yt-dlp update failed: the download is not a yt-dlp archive. Nothing was changed."
            if (reported != tag) return false to "yt-dlp update failed: the download reports $reported, not $tag. Nothing was changed."
            val hadOld = managed.isFile
            if (hadOld) Files.move(managed.toPath(), previous.toPath(), StandardCopyOption.REPLACE_EXISTING)
            Files.move(fresh.toPath(), managed.toPath(), StandardCopyOption.REPLACE_EXISTING)
            val healthy = try { healthCheck() } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
            if (healthy) return true to "yt-dlp $reported installed in the app's data folder."
            if (hadOld) Files.move(previous.toPath(), managed.toPath(), StandardCopyOption.REPLACE_EXISTING) else managed.delete()
            return false to "yt-dlp $reported was installed but failed its check; the previous copy is back."
        } finally { sums.delete(); fresh.delete() }
    }
}
