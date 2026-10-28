package com.greenaltambe.vela.extractor.ytdlp

import com.greenaltambe.vela.core.settings.YtDlpAutoUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.time.Duration.Companion.seconds

/** Downloads [url] into [target]; throws on any failure. A seam so tests need no network. */
fun interface Downloader { suspend fun download(url: String, target: File) }

/** HTTPS only, follows GitHub's redirect to the CDN. */
class HttpsDownloader : Downloader {
    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(java.time.Duration.ofSeconds(20)).build()
    override suspend fun download(url: String, target: File) = withContext(Dispatchers.IO) {
        require(url.startsWith("https://")) { "refusing non-https url" }
        val r = client.send(HttpRequest.newBuilder(URI(url)).header("User-Agent", "Vela/1.0 (yt-dlp updater)").build(), HttpResponse.BodyHandlers.ofFile(target.toPath()))
        check(r.statusCode() == 200) { "HTTP ${r.statusCode()} for $url" }
    }
}

sealed interface UpdateResult {
    class Updated(val version: String) : UpdateResult
    class RolledBack(val attempted: String, val why: String) : UpdateResult
    class Failed(val why: String) : UpdateResult
}

/**
 * Keeps an app-managed copy of yt-dlp in [dir] (`yt-dlp`, plus `yt-dlp.previous` for rollback). It never touches a system-installed
 * copy. An update: download the official release asset and `SHA2-256SUMS`, check the SHA-256, check that the binary reports the
 * expected version, replace atomically, then run [healthCheck] against the new binary and roll back to the previous copy if it fails.
 */
class YtDlpUpdater(
    private val dir: File,
    private val downloader: Downloader = HttpsDownloader(),
    private val runner: ProcessRunner = SystemProcessRunner(),
    /** The yt-dlp tier's canary against the binary at this path: true when it still serves a video. */
    private val healthCheck: suspend (binary: String) -> Boolean,
    private val asset: String = defaultAsset(),
    private val baseUrl: String = "https://github.com/yt-dlp/yt-dlp/releases/download",
) {
    val managed: File get() = File(dir, "yt-dlp")
    val previous: File get() = File(dir, "yt-dlp.previous")
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    /** The managed copy's path when it exists and is executable. */
    fun managedBinary(): String? = managed.takeIf { it.isFile && it.canExecute() }?.path

    suspend fun update(tag: String): UpdateResult {
        _status.value = "Updating yt-dlp to $tag…"
        val result = try { doUpdate(tag) } catch (e: CancellationException) { throw e } catch (e: Exception) { UpdateResult.Failed(e.message ?: e.toString()) }
        _status.value = when (result) {
            is UpdateResult.Updated -> "yt-dlp ${result.version} installed in the app's data folder."
            is UpdateResult.RolledBack -> "yt-dlp ${result.attempted} was installed but failed its check (${result.why}); the previous copy is back."
            is UpdateResult.Failed -> "yt-dlp update failed: ${result.why}. Nothing was changed."
        }
        return result
    }

    private suspend fun doUpdate(tag: String): UpdateResult {
        dir.mkdirs()
        val sums = File(dir, "SHA2-256SUMS.tmp"); val fresh = File(dir, "yt-dlp.new")
        try {
            downloader.download("$baseUrl/$tag/SHA2-256SUMS", sums)
            val expected = parseSums(sums.readText(), asset) ?: return UpdateResult.Failed("SHA2-256SUMS has no entry for $asset")
            downloader.download("$baseUrl/$tag/$asset", fresh)
            val actual = sha256(fresh)
            if (!actual.equals(expected, ignoreCase = true)) return UpdateResult.Failed("checksum mismatch for $asset (expected ${expected.take(12)}…, got ${actual.take(12)}…)")
            fresh.setExecutable(true, false)
            val reported = runner.run(listOf(fresh.path, "--version"), 30.seconds, 4096).takeIf { it.exitCode == 0 }?.stdout?.trim()
                ?: return UpdateResult.Failed("the downloaded binary does not run")
            if (reported != tag) return UpdateResult.Failed("the downloaded binary reports $reported, not $tag")

            // Replace atomically, keeping the old copy for rollback.
            val hadOld = managed.isFile
            if (hadOld) Files.move(managed.toPath(), previous.toPath(), StandardCopyOption.REPLACE_EXISTING)
            Files.move(fresh.toPath(), managed.toPath(), StandardCopyOption.ATOMIC_MOVE)

            val healthy = try { healthCheck(managed.path) } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
            if (healthy) return UpdateResult.Updated(reported)
            if (hadOld) Files.move(previous.toPath(), managed.toPath(), StandardCopyOption.REPLACE_EXISTING) else managed.delete()
            return UpdateResult.RolledBack(reported, "the yt-dlp source did not serve the check video")
        } finally {
            sums.delete(); fresh.delete()
        }
    }

    companion object {
        fun defaultAsset(arch: String = System.getProperty("os.arch").orEmpty()) = if (arch == "aarch64" || arch == "arm64") "yt-dlp_linux_aarch64" else "yt-dlp_linux"

        /** `<sha256>  <file>` lines (sha256sum format, optional `*` before the name). */
        fun parseSums(text: String, name: String): String? = text.lineSequence().map { it.trim().split(Regex("\\s+"), 2) }
            .firstOrNull { it.size == 2 && it[1].removePrefix("*") == name }?.get(0)

        fun sha256(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { s -> val b = ByteArray(1 shl 16); while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

/** What to do after a check finds a newer yt-dlp, per the `ytdlp.autoUpdate` setting: OFF nothing, ASK offers it, AUTO does it. */
fun shouldAutoInstall(mode: YtDlpAutoUpdate, newerAvailable: Boolean) = mode == YtDlpAutoUpdate.AUTO && newerAvailable
