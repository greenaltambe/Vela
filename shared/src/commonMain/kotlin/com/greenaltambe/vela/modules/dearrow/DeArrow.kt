package com.greenaltambe.vela.modules.dearrow

import com.greenaltambe.vela.core.Sha256
import com.greenaltambe.vela.core.http.ApiClient
import com.greenaltambe.vela.core.http.ApiResult
import com.greenaltambe.vela.core.http.ModuleEvent
import com.greenaltambe.vela.core.http.ModuleLog
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.core.settings.TitleCasing
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.feed.FeedContext
import com.greenaltambe.vela.feed.FeedStep
import com.greenaltambe.vela.modules.FeatureModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

enum class DeArrowSurface { FEED, SEARCH, VIDEO_PAGE, PLAYER_TITLE }

data class TitleVote(val title: String, val original: Boolean, val votes: Int, val locked: Boolean)
data class ThumbnailVote(val timestampSeconds: Double?, val original: Boolean, val votes: Int, val locked: Boolean)

data class Branding(
    val titles: List<TitleVote> = emptyList(),
    val thumbnails: List<ThumbnailVote> = emptyList(),
    /** A fraction (0..1) of the video's length at which DeArrow suggests grabbing a frame when nobody submitted one. */
    val randomTime: Double? = null,
    val videoDurationSeconds: Double? = null,
) {
    companion object { val NONE = Branding() }
}

/** The best-voted, locked-first order the API's own clients use. */
private fun List<TitleVote>.rankedTitles() = sortedWith(compareByDescending<TitleVote> { it.locked }.thenByDescending { it.votes })
private fun List<ThumbnailVote>.rankedThumbnails() = sortedWith(compareByDescending<ThumbnailVote> { it.locked }.thenByDescending { it.votes })

/** Null when the original should stay: no submission, the community chose the original, or the top one is not trusted. */
fun Branding.pickTitle(minVotes: Int): String? {
    val top = titles.rankedTitles().firstOrNull() ?: return null
    if (top.original || !(top.locked || top.votes >= minVotes)) return null
    return top.title.trim().takeIf { it.isNotEmpty() }
}

/** The timestamp to take a frame from, or null to keep the original thumbnail. [allowGenerated] uses [Branding.randomTime] when nobody submitted. */
fun Branding.pickThumbnailTime(minVotes: Int, allowGenerated: Boolean): Double? {
    val top = thumbnails.rankedThumbnails().firstOrNull()
    if (top != null) {
        if (top.original || !(top.locked || top.votes >= minVotes)) return null
        return top.timestampSeconds
    }
    val random = randomTime
    val duration = videoDurationSeconds
    return if (allowGenerated && random != null && duration != null && duration > 0) random * duration else null
}

fun parseBrandingResponse(body: String): Map<String, Branding> {
    val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return emptyMap()
    return root.mapNotNull { (videoId, value) ->
        val o = value as? JsonObject ?: return@mapNotNull null
        val titles = (o["titles"] as? JsonArray).orEmpty().mapNotNull { t ->
            val to = t as? JsonObject ?: return@mapNotNull null
            TitleVote(
                to["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                to["original"]?.jsonPrimitive?.booleanOrNull ?: false,
                to["votes"]?.jsonPrimitive?.intOrNull ?: 0,
                to["locked"]?.jsonPrimitive?.let { it.booleanOrNull ?: ((it.intOrNull ?: 0) != 0) } ?: false,
            )
        }
        val thumbs = (o["thumbnails"] as? JsonArray).orEmpty().mapNotNull { t ->
            val to = t as? JsonObject ?: return@mapNotNull null
            ThumbnailVote(
                to["timestamp"]?.jsonPrimitive?.doubleOrNull,
                to["original"]?.jsonPrimitive?.booleanOrNull ?: false,
                to["votes"]?.jsonPrimitive?.intOrNull ?: 0,
                to["locked"]?.jsonPrimitive?.let { it.booleanOrNull ?: ((it.intOrNull ?: 0) != 0) } ?: false,
            )
        }
        videoId to Branding(titles, thumbs, o["randomTime"]?.jsonPrimitive?.doubleOrNull, o["videoDuration"]?.jsonPrimitive?.doubleOrNull)
    }.toMap()
}

private val smallWords = setOf("a", "an", "the", "and", "but", "or", "nor", "for", "so", "yet", "on", "at", "to", "by", "of", "in", "vs", "via", "as")
private val unformat = Regex("""(^|\s)>(?=\S)""")

/**
 * DeArrow titles may mark words that must keep their casing with a leading `>` (">iPhone"). The marker is always
 * removed; the marked words are left alone by [casing]. Casing is deliberately simple (English-style rules): it
 * does not know proper nouns, which is exactly what the `>` marker is for.
 */
fun formatTitle(raw: String, casing: TitleCasing): String {
    val protected = unformat.findAll(raw).count() > 0
    val marked = if (protected) Regex("""(?:^|\s)>(\S+)""").findAll(raw).map { it.groupValues[1] }.toSet() else emptySet()
    val clean = unformat.replace(raw) { it.groupValues[1] }
    if (casing == TitleCasing.AS_SUBMITTED) return clean
    val words = clean.split(' ')
    fun allCaps(w: String) = w.length >= 2 && w.any { it.isLetter() } && w.all { it.isUpperCase() || !it.isLetter() }
    // A title that is mostly capitals is shouting, not full of acronyms: there, short capitalised words are lowered too.
    val shouting = words.count(::allCaps) * 10 >= words.size * 6
    return words.mapIndexed { i, w ->
        when {
            w.isEmpty() || w in marked -> w
            // Acronyms (NASA, FBI) and mixed-case brand words (iPhone, YouTube) are kept as they are.
            !shouting && w.length <= 5 && allCaps(w) -> w
            w.any { it.isLowerCase() } && w.drop(1).any { it.isUpperCase() } -> w
            casing == TitleCasing.TITLE_CASE ->
                if (i != 0 && i != words.lastIndex && w.lowercase() in smallWords) w.lowercase() else w.replaceFirstChar { it.uppercaseChar() }.let { cap -> cap[0] + cap.drop(1).lowercase() }
            else -> if (i == 0) w.replaceFirstChar { it.uppercaseChar() }.let { cap -> cap[0] + cap.drop(1).lowercase() } else w.lowercase()
        }
    }.joinToString(" ")
}

/**
 * DeArrow: community titles and thumbnails, looked up by hash prefix (one request covers every video sharing the
 * prefix; the answers for the others are kept too). Nothing here is awaited by UI: [prefetch] fills [cached] and
 * bumps [revision], and the feed step only reads what is already known, so a list shows its originals at once and the
 * replacements arrive when ready.
 */
class DeArrow(
    private val client: ApiClient,
    private val settings: SettingsResolver,
    private val log: ModuleLog,
    private val clock: Clock = Clock.System,
) : FeatureModule {
    override val id = "dearrow"
    override val enabledSetting = Settings.deArrowEnabled

    private val known = HashMap<String, Branding>()
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision

    @Synchronized fun cached(videoId: String): Branding? = known[videoId]

    /** Runs on [Dispatchers.Default] whatever the caller's dispatcher (screens call it from the main thread: the cache lookups, hashing and JSON parsing must not run there), at most 4 requests at once. */
    suspend fun prefetch(videoIds: List<String>) = withContext(Dispatchers.Default) {
        if (!isEnabled(settings)) return@withContext
        val todo = synchronized(this@DeArrow) { videoIds.filter { it !in known }.distinct() }
        if (todo.isEmpty()) return@withContext
        val server = settings.resolve(Settings.sponsorBlockServer).trimEnd('/')
        val byPrefix = todo.groupBy { Sha256.prefix(it, 4) }
        val gate = kotlinx.coroutines.sync.Semaphore(4)
        coroutineScope {
            byPrefix.map { (prefix, ids) ->
                async {
                  gate.withPermit {
                    when (val r = client.get("$server/api/branding/$prefix", ttl = 12.hours, cacheNegative = true)) {
                        is ApiResult.Ok -> {
                            val parsed = parseBrandingResponse(r.body)
                            synchronized(this@DeArrow) {
                                known.putAll(parsed)
                                ids.forEach { if (it !in known) known[it] = Branding.NONE }
                            }
                            _revision.update { it + 1 }
                            log.record(ModuleEvent(id, true, "prefix $prefix: ${ids.size} video(s), ${ids.count { parsed[it]?.titles?.isNotEmpty() == true }} with titles${if (r.stale) " (stale)" else ""}", clock.now()))
                        }
                        is ApiResult.Failed -> {
                            if (r.kind == com.greenaltambe.vela.core.http.FailureKind.NOT_FOUND) {
                                synchronized(this@DeArrow) { ids.forEach { known[it] = Branding.NONE } }
                            } else {
                                log.record(ModuleEvent(id, false, "prefix $prefix: inactive, ${r.kind.name.lowercase()}: ${r.message}", clock.now()))
                            }
                        }
                    }
                  }
                }
            }.awaitAll()
        }
        Unit
    }

    fun enabledFor(surface: DeArrowSurface): Boolean = isEnabled(settings) && when (surface) {
        DeArrowSurface.FEED -> settings.resolve(Settings.deArrowFeed)
        DeArrowSurface.SEARCH -> settings.resolve(Settings.deArrowSearch)
        DeArrowSurface.VIDEO_PAGE -> settings.resolve(Settings.deArrowVideoPage)
        DeArrowSurface.PLAYER_TITLE -> settings.resolve(Settings.deArrowPlayerTitle)
    }

    /** [original] when DeArrow is off for [surface], has nothing (yet) or does not trust what it has. */
    fun title(videoId: String, original: String, surface: DeArrowSurface): String {
        if (!enabledFor(surface)) return original
        val branding = cached(videoId) ?: return original
        val picked = branding.pickTitle(settings.resolve(Settings.deArrowMinVotes).toInt()) ?: return original
        return formatTitle(picked, settings.resolve(Settings.deArrowTitleCasing))
    }

    /** The replacement thumbnail URL, or null to keep the original. */
    fun thumbnail(videoId: String, surface: DeArrowSurface): String? {
        if (!enabledFor(surface)) return null
        val branding = cached(videoId) ?: return null
        val time = branding.pickThumbnailTime(settings.resolve(Settings.deArrowMinVotes).toInt(), settings.resolve(Settings.deArrowGeneratedThumbnails)) ?: return null
        return thumbnailUrl(videoId, time)
    }

    fun thumbnailUrl(videoId: String, timeSeconds: Double): String =
        "${settings.resolve(Settings.deArrowThumbnailServer).trimEnd('/')}/api/v1/getThumbnail?videoID=$videoId&time=$timeSeconds"

    /** Suggests the frame at [timestampMs] as the thumbnail. Only called after the viewer has confirmed. */
    suspend fun submitThumbnail(videoId: String, timestampMs: Long, videoDurationMs: Long, userId: String): ApiResult {
        val body = buildJsonObject {
            put("videoID", videoId)
            put("userID", userId)
            put("userAgent", ApiClient.DEFAULT_USER_AGENT)
            put("service", "YouTube")
            put("videoDuration", videoDurationMs / 1000.0)
            put("thumbnail", buildJsonObject { put("timestamp", JsonPrimitive(timestampMs / 1000.0)) })
        }
        val result = client.post("${settings.resolve(Settings.sponsorBlockServer).trimEnd('/')}/api/branding", body.toString())
        log.record(ModuleEvent(id, result is ApiResult.Ok, "submit thumbnail $videoId @${timestampMs}ms: ${if (result is ApiResult.Ok) "sent" else (result as ApiResult.Failed).message}", clock.now()))
        return result
    }
}

/**
 * The DeArrow feed step: swaps in whatever branding is already known for [surface]. Reads only the in-memory cache, so
 * it never waits on the network; the caller calls [DeArrow.prefetch] for the visible ids and re-runs the pipeline when
 * [DeArrow.revision] changes.
 */
class DeArrowStep(private val dearrow: DeArrow, private val surface: DeArrowSurface) : FeedStep {
    override fun apply(items: List<VideoSummary>, context: FeedContext): List<VideoSummary> {
        if (!dearrow.enabledFor(surface)) return items
        return items.map { v ->
            val title = dearrow.title(v.id, v.title, surface)
            val thumb = dearrow.thumbnail(v.id, surface)
            if (title == v.title && thumb == null) v
            else v.copy(
                title = title,
                thumbnailUrl = thumb ?: v.thumbnailUrl,
                originalTitle = v.title.takeIf { title != v.title },
                originalThumbnailUrl = v.thumbnailUrl.takeIf { thumb != null },
            )
        }
    }
}
