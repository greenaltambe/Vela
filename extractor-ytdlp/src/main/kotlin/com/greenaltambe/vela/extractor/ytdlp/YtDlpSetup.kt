package com.greenaltambe.vela.extractor.ytdlp

import com.greenaltambe.vela.extractor.chain.AttemptOutcome
import com.greenaltambe.vela.extractor.chain.DiagnosticsLog
import com.greenaltambe.vela.extractor.chain.ExtractorTier
import com.greenaltambe.vela.extractor.chain.TierAttempt
import com.greenaltambe.vela.extractor.chain.TierId
import kotlinx.coroutines.CancellationException
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Finds an executable on PATH; a seam so tests don't depend on what is installed. */
fun interface PathLookup {
    fun find(name: String): String?
}

object SystemPathLookup : PathLookup {
    override fun find(name: String): String? =
        System.getenv("PATH").orEmpty().split(File.pathSeparator).filter { it.isNotEmpty() }
            .map { File(it, name) }.firstOrNull { it.isFile && it.canExecute() }?.path
}

/**
 * JavaScript runtimes yt-dlp can use to solve YouTube's player challenges, in yt-dlp's own priority order (see
 * `yt-dlp --help`, `--js-runtimes`): yt-dlp's name for the runtime to executables that provide it. Only deno is
 * enabled by default. quickjs is `qjs` (or `quickjs`) on disk but `quickjs` on the command line.
 */
private val jsRuntimes = listOf(
    "deno" to listOf("deno"),
    "node" to listOf("node"),
    "quickjs" to listOf("qjs", "quickjs"),
    "bun" to listOf("bun"),
)

/**
 * The first yt-dlp release that deprecates JavaScript-less YouTube extraction. Taken from the release notes as
 * remembered, NOT verified: the oldest yt-dlp we have run is 2026.10.26.
 */
private val jsRuntimeRequiredFrom = Triple(2025, 11, 12)

/** yt-dlp versions are dates: `2026.10.26`, optionally with a revision (`2026.10.26.232815`). Null if unparseable. */
internal fun parseYtDlpVersion(version: String): Triple<Int, Int, Int>? {
    val parts = version.trim().split('.').mapNotNull { it.toIntOrNull() }
    return if (parts.size >= 3) Triple(parts[0], parts[1], parts[2]) else null
}

fun versionRequiresJsRuntime(version: String): Boolean {
    val (y, m, d) = parseYtDlpVersion(version) ?: return false // unknown format: don't nag
    val (ry, rm, rd) = jsRuntimeRequiredFrom
    return (y * 10_000 + m * 100 + d) >= (ry * 10_000 + rm * 100 + rd)
}

/** What startup learned about yt-dlp on this machine. */
sealed interface YtDlpProbe {
    class NotInstalled(val reason: String) : YtDlpProbe

    /**
     * [jsRuntimeArgs] are flags to pass on every call (empty when deno, yt-dlp's default, is present or none is needed);
     * [note] is a warning to surface in diagnostics, or null.
     */
    class Ready(val version: String, val jsRuntimeArgs: List<String>, val note: String?) : YtDlpProbe
}

suspend fun probeYtDlp(
    runner: ProcessRunner,
    binary: String,
    path: PathLookup = SystemPathLookup,
    timeout: Duration = 15.seconds,
): YtDlpProbe {
    val version = try {
        val out = runner.run(listOf(binary, "--version"), timeout, maxStdoutBytes = 4096)
        if (out.exitCode != 0) return YtDlpProbe.NotInstalled("`$binary --version` failed (exit ${out.exitCode}): ${out.stderr.trim().take(200)}")
        out.stdout.trim().ifEmpty { return YtDlpProbe.NotInstalled("`$binary --version` printed nothing") }
    } catch (e: CancellationException) {
        throw e
    } catch (e: ProcessStartException) {
        return YtDlpProbe.NotInstalled(e.message ?: "cannot start $binary")
    } catch (e: Exception) {
        return YtDlpProbe.NotInstalled("`$binary --version` failed: ${e.message}")
    }

    val runtimes = jsRuntimes.mapNotNull { (name, executables) ->
        executables.firstNotNullOfOrNull { path.find(it) }?.let { name to it }
    }
    val needsRuntime = versionRequiresJsRuntime(version)
    return when {
        runtimes.isEmpty() -> YtDlpProbe.Ready(
            version, emptyList(),
            note = if (needsRuntime) {
                "yt-dlp $version prefers a JavaScript runtime (deno, node, quickjs or bun) for YouTube and none was " +
                    "found on PATH; some formats may be missing"
            } else null,
        )
        // deno is what yt-dlp enables by itself; anything else has to be named explicitly.
        runtimes.first().first == "deno" -> YtDlpProbe.Ready(version, emptyList(), null)
        else -> {
            val (name, location) = runtimes.first()
            YtDlpProbe.Ready(version, if (needsRuntime) listOf("--js-runtimes", "$name:$location") else emptyList(), null)
        }
    }
}

/**
 * Builds the yt-dlp tier if yt-dlp is installed, else returns null and records why once in [log]. A missing JS
 * runtime is recorded as a note but does not stop the tier from being registered.
 */
suspend fun createYtDlpTier(
    runner: ProcessRunner,
    binary: () -> String,
    log: DiagnosticsLog,
    clock: Clock = Clock.System,
): ExtractorTier? {
    fun record(outcome: AttemptOutcome, detail: String) =
        log.record(TierAttempt("yt-dlp setup", TierId.YT_DLP, outcome, null, detail, Duration.ZERO, clock.now()))

    return when (val probe = probeYtDlp(runner, binary())) {
        is YtDlpProbe.NotInstalled -> {
            record(AttemptOutcome.SKIPPED, "yt-dlp not installed (${probe.reason})")
            null
        }
        is YtDlpProbe.Ready -> {
            probe.note?.let { record(AttemptOutcome.NOTE, it) }
            ExtractorTier(TierId.YT_DLP, YtDlpExtractor(runner, binary, extraArgs = { probe.jsRuntimeArgs }))
        }
    }
}
