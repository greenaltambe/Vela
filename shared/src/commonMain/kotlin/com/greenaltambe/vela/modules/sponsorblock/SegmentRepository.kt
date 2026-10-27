package com.greenaltambe.vela.modules.sponsorblock

import com.greenaltambe.vela.core.http.FailureKind
import com.greenaltambe.vela.data.db.VelaDatabase
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

sealed interface SegmentsResult {
    data class Loaded(val segments: List<Segment>, val source: Source) : SegmentsResult
    data class Unavailable(val kind: FailureKind, val message: String) : SegmentsResult
    enum class Source { NETWORK, STORE, STALE_STORE }
}

/**
 * Segments per video: a fresh stored copy is used as is, otherwise the server is asked and the answer stored; if the
 * server cannot be reached an older stored copy is still better than nothing. The store has no expiry of its own
 * (offline downloads depend on it); a stored copy is merely refreshed when it is older than [refreshAfter] and the
 * network is there. The downloader calls [get] (or [store] with what it already has).
 */
class SegmentRepository(
    private val api: SponsorBlockApi,
    private val db: VelaDatabase,
    private val clock: Clock = Clock.System,
    private val refreshAfter: Duration = 24.hours,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val serializer = ListSerializer(Segment.serializer())

    /** [categories] are the categories the user wants fetched; a stored copy is reused only if it was fetched for the same set. */
    suspend fun get(videoId: String, categories: List<SegmentCategory>, allowNetwork: Boolean = true): SegmentsResult {
        val stored = stored(videoId)
        val fresh = stored != null && clock.now() - stored.second < refreshAfter
        if (stored != null && (fresh || !allowNetwork)) {
            return SegmentsResult.Loaded(stored.first, if (fresh) SegmentsResult.Source.STORE else SegmentsResult.Source.STALE_STORE)
        }
        if (!allowNetwork) return SegmentsResult.Loaded(emptyList(), SegmentsResult.Source.STORE)
        return when (val r = api.fetch(videoId, categories)) {
            is SegmentLookup.Found -> if (r.stale) {
                // The HTTP layer could not reach the server and answered from its own old copy: not a refresh, so the
                // stored copy keeps its date (and is only created if there was none).
                if (stored == null) store(videoId, r.segments)
                SegmentsResult.Loaded(r.segments, SegmentsResult.Source.STALE_STORE)
            } else {
                store(videoId, r.segments)
                SegmentsResult.Loaded(r.segments, SegmentsResult.Source.NETWORK)
            }
            is SegmentLookup.Unavailable ->
                if (stored != null) SegmentsResult.Loaded(stored.first, SegmentsResult.Source.STALE_STORE)
                else SegmentsResult.Unavailable(r.kind, r.message)
        }
    }

    fun store(videoId: String, segments: List<Segment>) {
        db.apiCacheQueries.upsertSegments(videoId, json.encodeToString(serializer, segments), clock.now().toEpochMilliseconds())
    }

    fun stored(videoId: String): Pair<List<Segment>, Instant>? =
        db.apiCacheQueries.selectSegments(videoId).executeAsOneOrNull()?.let { row ->
            runCatching { json.decodeFromString(serializer, row.segmentsJson) }.getOrNull()?.let { it to Instant.fromEpochMilliseconds(row.fetchedAt) }
        }

    fun delete(videoId: String) { db.apiCacheQueries.deleteSegments(videoId) }
}
