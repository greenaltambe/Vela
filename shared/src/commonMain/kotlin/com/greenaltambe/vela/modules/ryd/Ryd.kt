package com.greenaltambe.vela.modules.ryd

import com.greenaltambe.vela.core.http.ApiClient
import com.greenaltambe.vela.core.http.ApiResult
import com.greenaltambe.vela.core.http.FailureKind
import com.greenaltambe.vela.core.http.ModuleEvent
import com.greenaltambe.vela.core.http.ModuleLog
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.modules.FeatureModule
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

data class Votes(val likes: Long, val dislikes: Long, val rating: Double?, val viewCount: Long?, val deleted: Boolean = false) {
    companion object {
        fun parse(body: String): Votes? {
            val o = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
            val likes = o["likes"]?.jsonPrimitive?.longOrNull ?: return null
            val dislikes = o["dislikes"]?.jsonPrimitive?.longOrNull ?: return null
            return Votes(likes, dislikes, o["rating"]?.jsonPrimitive?.doubleOrNull, o["viewCount"]?.jsonPrimitive?.longOrNull, o["deleted"]?.jsonPrimitive?.booleanOrNull == true)
        }
    }
}

/** What a lookup came to. [Unknown] is a normal answer (the service has no data for the video); the others mean "inactive for now". */
sealed interface VotesResult {
    data class Known(val votes: Votes) : VotesResult
    data object Unknown : VotesResult
    data class Unavailable(val message: String) : VotesResult
}

/**
 * Return YouTube Dislike. One lookup per video, cached for [ApiClient]'s TTL (here 6 h); [prefetch] fetches many at
 * once but with at most [concurrency] in flight and skips what is already known, so a long list does not hammer the API.
 * Cards read [cached] synchronously and re-read when [revision] changes: the list never waits for the network.
 */
class ReturnYouTubeDislike(
    private val client: ApiClient,
    private val settings: SettingsResolver,
    private val log: ModuleLog,
    private val clock: Clock = Clock.System,
    private val concurrency: Int = 4,
) : FeatureModule {
    override val id = "ryd"
    override val enabledSetting = Settings.returnYouTubeDislikeEnabled

    private val known = HashMap<String, Votes>()
    private val unknown = HashSet<String>()
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision

    @Synchronized fun cached(videoId: String): Votes? = known[videoId]

    suspend fun votes(videoId: String): VotesResult {
        if (!isEnabled(settings)) return VotesResult.Unavailable("disabled")
        synchronized(this) { known[videoId]?.let { return VotesResult.Known(it) }; if (videoId in unknown) return VotesResult.Unknown }
        val url = "${settings.resolve(Settings.rydServer).trimEnd('/')}/votes?videoId=$videoId"
        return when (val r = client.get(url, ttl = 6.hours, cacheNegative = true)) {
            // An id the service has never seen comes back as 200 with every count zero (observed live), not as a 404.
            is ApiResult.Ok -> Votes.parse(r.body)?.takeIf { it.likes + it.dislikes + (it.viewCount ?: 0) > 0 }?.let { v ->
                synchronized(this) { known[videoId] = v }
                _revision.update { it + 1 }
                log.record(ModuleEvent(id, true, "$videoId: ${v.likes} likes, ${v.dislikes} dislikes${if (r.stale) " (stale)" else ""}", clock.now()))
                VotesResult.Known(v)
            } ?: if (Votes.parse(r.body) != null) {
                synchronized(this) { unknown += videoId }
                VotesResult.Unknown
            } else VotesResult.Unavailable("unreadable answer").also { log.record(ModuleEvent(id, false, "$videoId: unreadable answer", clock.now())) }
            is ApiResult.Failed -> if (r.kind == FailureKind.NOT_FOUND) {
                synchronized(this) { unknown += videoId }
                VotesResult.Unknown
            } else {
                log.record(ModuleEvent(id, false, "$videoId: inactive, ${r.kind.name.lowercase()}: ${r.message}", clock.now()))
                VotesResult.Unavailable(r.message)
            }
        }
    }

    /** Fetches what is not known yet; failures are logged and ignored. */
    suspend fun prefetch(videoIds: List<String>) {
        if (!isEnabled(settings)) return
        val todo = synchronized(this) { videoIds.filter { it !in known && it !in unknown }.distinct() }
        val gate = Semaphore(concurrency)
        coroutineScope { todo.map { id -> async { gate.withPermit { votes(id) } } }.awaitAll() }
    }
}

/** "👍 1.2M  👎 12K  ★ 4.8" — compact counts for cards and the video page. */
fun Votes.summary(): String = buildString {
    append("👍 ${compact(likes)}  👎 ${compact(dislikes)}")
    rating?.let { append("  ★ ${(it * 10).toLong() / 10.0}") }
}

fun compact(n: Long): String = when {
    n >= 1_000_000_000 -> "${oneDecimal(n / 1e9)}B"
    n >= 1_000_000 -> "${oneDecimal(n / 1e6)}M"
    n >= 1_000 -> "${oneDecimal(n / 1e3)}K"
    else -> "$n"
}

private fun oneDecimal(v: Double): String {
    val tenths = (v * 10).toLong()
    return if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}
