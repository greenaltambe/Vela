package com.greenaltambe.vela.modules.sponsorblock

import com.greenaltambe.vela.core.Sha256
import com.greenaltambe.vela.core.http.ApiClient
import com.greenaltambe.vela.core.http.ApiResult
import com.greenaltambe.vela.core.http.FailureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlin.time.Duration.Companion.hours

/** Result of a lookup: segments (possibly none) or the reason there are none to show. */
sealed interface SegmentLookup {
    data class Found(val segments: List<Segment>, val fromCache: Boolean, val stale: Boolean) : SegmentLookup
    data class Unavailable(val kind: FailureKind, val message: String) : SegmentLookup
}

/**
 * The SponsorBlock server. Lookups use the privacy API: only the first 4 hex characters of the video id's SHA-256 are
 * sent, and the matching video is picked out locally from the (several) videos that share the prefix.
 */
class SponsorBlockApi(
    private val client: ApiClient,
    private val server: () -> String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun fetch(videoId: String, categories: List<SegmentCategory>): SegmentLookup {
        val prefix = Sha256.prefix(videoId, 4)
        val cats = categories.joinToString(",", "[", "]") { "\"${it.id}\"" }
        val types = "[\"skip\",\"mute\",\"poi\"]"
        val url = "${server().trimEnd('/')}/api/skipSegments/$prefix?categories=${encode(cats)}&actionTypes=${encode(types)}"
        return when (val r = client.get(url, ttl = 6.hours, cacheNegative = true)) {
            is ApiResult.Ok -> SegmentLookup.Found(parseSegments(r.body, videoId), r.fromCache, r.stale)
            is ApiResult.Failed -> if (r.kind == FailureKind.NOT_FOUND) SegmentLookup.Found(emptyList(), fromCache = false, stale = false)
            else SegmentLookup.Unavailable(r.kind, r.message)
        }
    }

    /** Sends a segment for review. Only ever called from an explicit user confirmation. */
    suspend fun submit(videoId: String, startMs: Long, endMs: Long, category: SegmentCategory, userId: String, videoDurationMs: Long): ApiResult {
        val body = buildJsonObject {
            put("videoID", videoId)
            put("userID", userId)
            put("userAgent", ApiClient.DEFAULT_USER_AGENT)
            put("service", "YouTube")
            put("videoDuration", videoDurationMs / 1000.0)
            putJsonArray("segments") {
                add(buildJsonObject {
                    put("segment", buildJsonArray { add(JsonPrimitive(startMs / 1000.0)); add(JsonPrimitive(endMs / 1000.0)) })
                    put("category", category.id)
                    put("actionType", if (category == SegmentCategory.POI_HIGHLIGHT) "poi" else "skip")
                })
            }
        }
        return client.post("${server().trimEnd('/')}/api/skipSegments", body.toString())
    }

    private fun encode(s: String) = buildString {
        for (c in s) when (c) {
            '[', ']', '"', ',' -> append('%').append(c.code.toString(16).uppercase().padStart(2, '0'))
            else -> append(c)
        }
    }

    internal fun parseSegments(body: String, videoId: String): List<Segment> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonArray ?: return emptyList()
        val entry = root.firstOrNull { (it as? JsonObject)?.get("videoID")?.jsonPrimitive?.contentOrNull == videoId } as? JsonObject ?: return emptyList()
        return (entry["segments"] as? JsonArray).orEmpty().mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val span = o["segment"] as? JsonArray ?: return@mapNotNull null
            val start = span.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val end = span.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            Segment(
                uuid = o["UUID"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                category = o["category"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                actionType = o["actionType"]?.jsonPrimitive?.contentOrNull ?: "skip",
                startMs = (start * 1000).toLong(),
                endMs = (end * 1000).toLong(),
                votes = o["votes"]?.jsonPrimitive?.intOrNull ?: 0,
                locked = (o["locked"]?.jsonPrimitive?.intOrNull ?: 0) != 0 || o["locked"]?.jsonPrimitive?.booleanOrNull == true,
                description = o["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }
    }
}
