package com.greenaltambe.vela.ytdlp

import android.content.Context
import com.greenaltambe.vela.extractor.ytdlp.OutputTooLargeException
import com.greenaltambe.vela.extractor.ytdlp.ProcessOutput
import com.greenaltambe.vela.extractor.ytdlp.ProcessRunner
import com.greenaltambe.vela.extractor.ytdlp.ProcessStartException
import com.greenaltambe.vela.extractor.ytdlp.ProcessTimeoutException
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.io.File
import java.util.UUID
import kotlin.time.Duration

/**
 * yt-dlp inside the app: the youtubedl-android library unpacks Python 3.12 and a QuickJS runtime from the APK's native-library
 * folder into app-private storage on first use (about 40 MB, seconds), then runs `python yt-dlp ...` as a child process.
 * The library adds `--js-runtimes quickjs:<path>` itself, so no runtime flags are needed here.
 */
class EmbeddedYtDlp(context: Context) {
    private val app = context.applicationContext
    private val initLock = Mutex()
    @Volatile private var ready = false

    /** Where the library keeps yt-dlp; [YtDlpManagedUpdater] replaces the file here, and `init` leaves an existing file alone. */
    val ytDlpFile: File get() = File(File(File(app.noBackupFilesDir, YoutubeDL.baseName), YoutubeDL.ytdlpDirName), YoutubeDL.ytdlpBin)

    /** Private folder for per-call cookie files; anything left in it by a killed process is deleted here, at startup. */
    val cookieDir: File by lazy { File(app.noBackupFilesDir, "ytdlp-cookies").also { it.mkdirs(); sweepCookies(it) } }

    suspend fun ensureInit() {
        if (ready) return
        initLock.withLock {
            if (ready) return
            withContext(Dispatchers.IO) {
                try { YoutubeDL.getInstance().init(app) } catch (e: YoutubeDLException) { throw ProcessStartException("yt-dlp could not be unpacked: ${e.message}", e) }
            }
            ready = true
        }
    }

    suspend fun version(): String? = try { ensureInit(); readYtDlpVersion(ytDlpFile) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    companion object {
        /** Cookie files are named `vela-<uuid>.cookies.txt`; none should survive a call, so any found on startup are leftovers of a kill. */
        fun sweepCookies(dir: File): Int = dir.listFiles { f -> f.name.endsWith(".cookies.txt") }.orEmpty().count { it.delete() }
    }
}

/** [ProcessRunner] over [EmbeddedYtDlp]: `command[0]` (the binary name callers pass) is ignored, the rest are yt-dlp arguments. */
class EmbeddedProcessRunner(private val yt: EmbeddedYtDlp) : ProcessRunner {
    override suspend fun run(command: List<String>, timeout: Duration, maxStdoutBytes: Int): ProcessOutput {
        yt.ensureInit()
        val id = "vela-${UUID.randomUUID()}"
        val request = YoutubeDLRequest(emptyList()).addCommands(command.drop(1))
        return try {
            withTimeout(timeout) {
                kotlinx.coroutines.runInterruptible(Dispatchers.IO) {
                    try {
                        val r = YoutubeDL.getInstance().execute(request, id, null)
                        ProcessOutput(r.exitCode, r.out, r.err)
                    } catch (e: YoutubeDLException) {
                        // The library throws on a non-zero exit with yt-dlp's stderr as the message.
                        ProcessOutput(1, "", e.message.orEmpty())
                    }
                }
            }.also { if (it.stdout.length > maxStdoutBytes) throw OutputTooLargeException(maxStdoutBytes) }
        } catch (e: TimeoutCancellationException) {
            withContext(NonCancellable) { YoutubeDL.getInstance().destroyProcessById(id) }
            throw ProcessTimeoutException(timeout)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { YoutubeDL.getInstance().destroyProcessById(id) }
            throw e
        }
    }
}

/**
 * `__version__` of a yt-dlp zipapp, read without starting Python. The release file is a zip with a `#!` line in front, which
 * Android's ZipFile rejects, so this streams the local entries from the first `PK` header. Null if it isn't one.
 */
fun readYtDlpVersion(zipapp: File): String? = runCatching {
    val bytes = zipapp.readBytes()
    val start = (0 until bytes.size - 3).firstOrNull { bytes[it] == 'P'.code.toByte() && bytes[it + 1] == 'K'.code.toByte() && bytes[it + 2].toInt() == 3 && bytes[it + 3].toInt() == 4 } ?: return null
    java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes, start, bytes.size - start)).use { z ->
        while (true) {
            val e = z.nextEntry ?: return null
            if (e.name == "yt_dlp/version.py") return Regex("""__version__\s*=\s*'([^']+)'""").find(z.bufferedReader().readText())?.groupValues?.get(1)
        }
        @Suppress("UNREACHABLE_CODE") null
    }
}.getOrNull()

/** The version in use: the managed copy once it exists, else the one bundled in the APK. */
fun EmbeddedYtDlp.installedVersion(context: Context): String? =
    ytDlpFile.takeIf { it.isFile }?.let(::readYtDlpVersion) ?: runCatching {
        val tmp = File.createTempFile("ytdlp-bundled", ".zip", context.cacheDir)
        try {
            context.resources.openRawResource(com.yausername.youtubedl_android.R.raw.ytdlp).use { i -> tmp.outputStream().use { i.copyTo(it) } }
            readYtDlpVersion(tmp)
        } finally { tmp.delete() }
    }.getOrNull()
