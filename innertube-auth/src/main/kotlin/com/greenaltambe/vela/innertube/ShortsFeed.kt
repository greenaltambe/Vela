package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.extractor.Page
import com.greenaltambe.vela.extractor.VideoSummary
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * YouTube's Shorts feed, the same two requests signed in or not: `reel/reel_item_watch` with no video begins a sequence (naming its first Short and
 * a token), and `reel/reel_watch_sequence` gives the Shorts after a token, with the next one. A Short of a listing carries such a token too.
 *
 * Each request asks for the few fields used (a field mask): an answer is then a few thousand characters instead of up to two million (measured,
 * docs/reports/152). Should YouTube refuse the mask, or the trimmed answer hold nothing, the request is made once more without it, and the same
 * parser reads the full answer. The answers name no title and no channel; playback looks those up.
 */
internal object ShortsFeed {
    const val SEED_ENDPOINT = "reel/reel_item_watch"
    const val PAGE_ENDPOINT = "reel/reel_watch_sequence"
    const val SEED_MASK = "replacementEndpoint.reelWatchEndpoint.videoId,replacementEndpoint.reelWatchEndpoint.thumbnail,sequenceContinuation"
    const val PAGE_MASK = "entries.command.reelWatchEndpoint.videoId,entries.command.reelWatchEndpoint.thumbnail,entries.command.reelWatchEndpoint.adClientParams,continuationEndpoint"

    /** YouTube refused the request even without a mask: the token, most likely. */
    class Refused : Exception("the Shorts request was refused (HTTP 400)")

    /**
     * One page: [token] null begins a sequence. [ask] makes a request, with the mask when one is given, and answers null when YouTube refuses the
     * request itself (HTTP 400); every other failure it throws.
     */
    fun load(token: String?, ask: (endpoint: String, body: JsonObject, mask: String?) -> JsonElement?): Page<VideoSummary> {
        val endpoint = if (token == null) SEED_ENDPOINT else PAGE_ENDPOINT
        val body = if (token == null) {
            buildJsonObject { put("inputType", "REEL_WATCH_INPUT_TYPE_SEEDLESS"); put("params", "CA8%3D"); put("disablePlayerResponse", true) }
        } else buildJsonObject { put("sequenceParams", token) }
        fun parse(json: JsonElement) = if (token == null) parseStart(json) else parsePage(json)
        val trimmed = ask(endpoint, body, if (token == null) SEED_MASK else PAGE_MASK)?.let(::parse)
        if (trimmed != null && (trimmed.items.isNotEmpty() || trimmed.continuation != null)) return trimmed
        return parse(ask(endpoint, body, null) ?: throw Refused())
    }

    /** The answer that begins a sequence: its first Short and the token for the rest. */
    fun parseStart(json: JsonElement): Page<VideoSummary> =
        Page(listOfNotNull(short(json.at("replacementEndpoint", "reelWatchEndpoint"))), json["sequenceContinuation"].string)

    /** A page of a sequence. Advertisements and entries naming no video are left out. */
    fun parsePage(json: JsonElement): Page<VideoSummary> = Page(
        json["entries"].array.mapNotNull { short(it.at("command", "reelWatchEndpoint")) }.distinctBy { it.id },
        json.at("continuationEndpoint", "continuationCommand", "token").string,
    )

    private fun short(endpoint: JsonElement?): VideoSummary? {
        if (endpoint == null || endpoint["adClientParams"] != null) return null
        val id = endpoint["videoId"].string?.takeIf { it.isNotBlank() } ?: return null
        val picture = endpoint.at("thumbnail", "thumbnails").array.lastOrNull()["url"].string?.let { if (it.startsWith("//")) "https:$it" else it }
        return VideoSummary(id, "", "", "", thumbnailUrl = picture, isShort = true)
    }
}
