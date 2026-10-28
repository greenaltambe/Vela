package com.greenaltambe.vela.health

import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.NOT_SUPPORTED
import com.greenaltambe.vela.extractor.chain.ExtractorTier
import com.greenaltambe.vela.extractor.chain.TierId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** The NewPipeExtractor this build ships, and when that release came out, so Diagnostics can say when it is getting old. */
object NewPipeVersion {
    const val VERSION = GeneratedVersions.NEWPIPE
    const val RELEASED = GeneratedVersions.NEWPIPE_RELEASED
    private val released: Instant get() = Instant.parse("${RELEASED}T00:00:00Z")
    fun ageDays(now: Instant): Long = (now - released).inWholeDays

}

/** Fetches the first bytes of a stream URL (anonymously) and returns the HTTP status; 206 means it plays. */
fun interface StreamProbe { suspend fun status(url: String, headers: Map<String, String>): Int }

data class Canary(val videoId: String, val label: String)

/** One check of one tier. [check] is "search", "videoInfo(label)", "streams(label)" or "GET(label)". */
data class CanaryResult(val tier: TierId, val check: String, val ok: Boolean, val detail: String?, val at: Instant, val millis: Long)

data class TierHealth(val tier: TierId, val lastSuccess: Instant?, val lastFailure: Instant?, val lastFailureReason: String?, val history: List<CanaryResult>) {
    val healthy: Boolean get() = lastFailure == null || (lastSuccess != null && lastSuccess >= lastFailure)
}

/** Where results are kept between runs. */
interface HealthStore {
    fun load(): List<CanaryResult>
    fun save(results: List<CanaryResult>)
    /** How many runs in a row had every tier failing; kept so a restart does not forget it. */
    fun loadStreak(): Int = 0
    fun saveStreak(streak: Int) {}
}

/** Shown when every tier failed the canaries in [streak] runs in a row: which tiers, and the last time anything worked. */
data class HealthAlert(val failingTiers: List<TierId>, val lastGood: Instant?, val streak: Int) {
    fun message(): String = "Playback may be broken: ${failingTiers.joinToString(", ") { it.name.lowercase().replace('_', ' ') }} failing. " +
        "Last time anything worked: ${lastGood?.toString()?.substring(0, 16)?.replace('T', ' ') ?: "never"}."
}
class InMemoryHealthStore : HealthStore {
    private var list = emptyList<CanaryResult>()
    override fun load() = list
    override fun save(results: List<CanaryResult>) { list = results }
    private var streak = 0
    override fun loadStreak() = streak
    override fun saveStreak(streak: Int) { this.streak = streak }
}

/**
 * Canary matrix: for each ANONYMOUS-class tier (never the account tier, so health checks cannot spend or flag it) it runs
 * a search, and videoInfo, streams and one 206 GET for each reference video. Results are persisted and summarised per
 * tier. A tier that answers with an empty stream set (or "no playable streams") is reported as a possible SABR move.
 */
class ExtractorHealth(
    private val tiers: List<ExtractorTier>,
    private val probe: StreamProbe,
    private val store: HealthStore = InMemoryHealthStore(),
    private val clock: Clock = Clock.System,
    /** Read on every run, so a remote config can change them without a new build. */
    private val canaries: () -> List<Canary> = { DefaultCanaries },
    private val keep: Int = 400,
) {
    private val _results = MutableStateFlow(store.load())
    val results: StateFlow<List<CanaryResult>> = _results
    private val _running = MutableStateFlow(false)
    private val _alert = MutableStateFlow<HealthAlert?>(null)
    private var streak = store.loadStreak()
    /** Non-null only after [ALERT_AFTER] full-failure runs in a row, so one flaky run never raises it; any working run clears it. */
    val alert: StateFlow<HealthAlert?> = _alert
    init { _alert.value = currentAlert() }

    private fun currentAlert(): HealthAlert? = if (streak < ALERT_AFTER) null else
        HealthAlert(summary().map { it.tier }, _results.value.filter { it.ok && it.check.startsWith("streams") }.maxOfOrNull { it.at }, streak)
    val running: StateFlow<Boolean> = _running

    fun lastRun(): Instant? = _results.value.maxOfOrNull { it.at }
    fun isStale(maxAge: kotlin.time.Duration = 7.days): Boolean = lastRun()?.let { clock.now() - it > maxAge } ?: true

    fun summary(): List<TierHealth> = tiers.filter { it.id != TierId.AUTHENTICATED }.map { t ->
        val rs = _results.value.filter { it.tier == t.id }
        TierHealth(t.id, rs.filter { it.ok }.maxOfOrNull { it.at }, rs.filter { !it.ok }.maxByOrNull { it.at }?.at, rs.filter { !it.ok }.maxByOrNull { it.at }?.detail, rs.sortedByDescending { it.at }.take(30))
    }

    suspend fun run() {
        if (!_running.compareAndSet(false, true)) return
        try {
            val fresh = ArrayList<CanaryResult>()
            for (tier in tiers.filter { it.id != TierId.AUTHENTICATED }) {
                // A tier that does not do search at all (the alt client) is not failing it, so there is nothing to record.
                val searchStart = clock.now()
                val search = try { tier.extractor.search("music") } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { ExtractorResult.Failure(com.greenaltambe.vela.extractor.ExtractorError.Network(e.message ?: e.toString())) }
                if (!(search is ExtractorResult.Failure && search.error.message == NOT_SUPPORTED)) {
                    val problem = when (search) { is ExtractorResult.Success -> if (search.value.items.isEmpty()) "no results" else null; is ExtractorResult.Failure -> search.error.message }
                    fresh += CanaryResult(tier.id, "search", problem == null, problem, searchStart, (clock.now() - searchStart).inWholeMilliseconds)
                }
                for (c in canaries()) {
                    fresh += timed(tier.id, "videoInfo(${c.label})") { when (val r = tier.extractor.videoInfo(c.videoId)) { is ExtractorResult.Success -> null; is ExtractorResult.Failure -> r.error.message } }
                    var url: String? = null; var headers = emptyMap<String, String>()
                    fresh += timed(tier.id, "streams(${c.label})") {
                        when (val r = tier.extractor.streams(c.videoId)) {
                            is ExtractorResult.Success -> {
                                val s = r.value
                                url = s.videoStreams.firstOrNull()?.url ?: s.audioStreams.firstOrNull()?.url; headers = s.headers
                                if (url == null && s.hlsUrl == null) SABR else null
                            }
                            is ExtractorResult.Failure -> if (r.error.message.contains("no playable streams")) SABR else r.error.message
                        }
                    }
                    url?.let { u -> fresh += timed(tier.id, "GET(${c.label})") { val code = try { probe.status(u, headers) } catch (e: Exception) { return@timed e.message ?: "GET failed" }; if (code == 206 || code == 200) null else "HTTP $code" } }
                }
            }
            val merged = (_results.value + fresh).sortedBy { it.at }.takeLast(keep)
            store.save(merged); _results.update { merged }
            // A run is a full failure when no tier produced a single working stream resolve.
            val anyWorked = fresh.any { it.ok && it.check.startsWith("streams") }
            streak = if (fresh.isEmpty() || anyWorked) 0 else streak + 1
            store.saveStreak(streak)
            _alert.value = currentAlert()
        } finally { _running.value = false }
    }

    private suspend fun timed(tier: TierId, check: String, body: suspend () -> String?): CanaryResult {
        val start = clock.now()
        val problem = try { body() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { e.message ?: e.toString() }
        return CanaryResult(tier, check, problem == null, problem, start, (clock.now() - start).inWholeMilliseconds)
    }

    companion object {
        const val ALERT_AFTER = 2
        const val SABR = "streamingData has no plain URLs: YouTube may have moved this client to SABR"
        /** Three stable public videos: a normal one, a music video and a short. */
        val DefaultCanaries = listOf(Canary("dQw4w9WgXcQ", "normal"), Canary("kJQP7kiw5Fk", "music"), Canary("jNQXAC9IVRw", "short"))
    }
}

/** Results as lines `tier|check|ok|millis|epochMillis|detail`, so they fit in one Setting row without a JSON dependency. */
object HealthCodec {
    fun encode(rs: List<CanaryResult>) = rs.joinToString("\n") { "${it.tier.name}|${it.check}|${if (it.ok) 1 else 0}|${it.millis}|${it.at.toEpochMilliseconds()}|${it.detail.orEmpty().replace('\n', ' ').replace('|', '/')}" }
    fun decode(text: String): List<CanaryResult> = text.lineSequence().mapNotNull { l ->
        val p = l.split('|', limit = 6); if (p.size < 6) return@mapNotNull null
        CanaryResult(TierId.entries.firstOrNull { it.name == p[0] } ?: return@mapNotNull null, p[1], p[2] == "1", p[5].ifEmpty { null }, Instant.fromEpochMilliseconds(p[4].toLongOrNull() ?: return@mapNotNull null), p[3].toLongOrNull() ?: 0)
    }.toList()
}
