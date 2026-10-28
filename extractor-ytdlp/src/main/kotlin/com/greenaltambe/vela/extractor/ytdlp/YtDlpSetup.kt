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
                "No JavaScript runtime (deno, node, quickjs or bun) was found, and yt-dlp $version needs one for YouTube: age-restricted " +
                    "and many other videos will not play through yt-dlp. Install one (Fedora: sudo dnf install nodejs) or add its folder to PATH"
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
    path: PathLookup = SystemPathLookup,
    /** Called with the JS-runtime arguments yt-dlp needs here, so the account tier can run the same way. */
    onReady: (jsRuntimeArgs: List<String>) -> Unit = {},
): ExtractorTier? {
    fun record(outcome: AttemptOutcome, detail: String) =
        log.record(TierAttempt("yt-dlp setup", TierId.YT_DLP, outcome, null, detail, Duration.ZERO, clock.now()))

    return when (val probe = probeYtDlp(runner, binary(), path)) {
        is YtDlpProbe.NotInstalled -> {
            record(AttemptOutcome.SKIPPED, "yt-dlp not installed (${probe.reason})")
            null
        }
        is YtDlpProbe.Ready -> {
            probe.note?.let { record(AttemptOutcome.NOTE, it) }
            onReady(probe.jsRuntimeArgs)
            ExtractorTier(TierId.YT_DLP, YtDlpExtractor(runner, binary, extraArgs = { probe.jsRuntimeArgs }))
        }
    }
}

/** One yt-dlp found on this machine and the version it reported (null when `--version` failed). */
class YtDlpCandidate(val binary: FoundBinary, val version: String?)

/** The oldest yt-dlp measured to play an age-restricted video anonymously is unknown; this one was measured to work (2026-10-28) and 2026.08.19 was measured not to. */
const val YTDLP_AGE_RESTRICTED_VERIFIED = com.greenaltambe.vela.health.GeneratedVersions.YTDLP_VERIFIED

/** Orders yt-dlp versions (dates, optionally with a revision). Unparseable sorts first. */
fun compareYtDlpVersions(a: String?, b: String?): Int {
    fun key(v: String?) = v?.trim()?.split('.')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size >= 3 } ?: emptyList()
    val (x, y) = key(a) to key(b)
    for (i in 0 until maxOf(x.size, y.size)) { val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 }); if (c != 0) return c }
    return 0
}

/**
 * Asks each candidate for its version and picks the newest: a distro yt-dlp months older than a pip copy could not play
 * age-restricted videos, and which one a plain `yt-dlp` resolves to depends on how the app was launched. Ties go to the
 * earlier candidate (PATH order). Null when nothing runs.
 */
suspend fun chooseYtDlp(runner: ProcessRunner, candidates: List<FoundBinary>, timeout: Duration = 15.seconds): Pair<YtDlpCandidate?, List<YtDlpCandidate>> {
    val probed = candidates.map { c ->
        val v = try {
            runner.run(listOf(c.path!!, "--version"), timeout, maxStdoutBytes = 4096).takeIf { it.exitCode == 0 }?.stdout?.trim()?.ifEmpty { null }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        YtDlpCandidate(c, v)
    }
    val best = probed.filter { it.version != null }.fold(null as YtDlpCandidate?) { acc, c -> if (acc == null || compareYtDlpVersions(c.version, acc.version) > 0) c else acc }
    return best to probed
}
