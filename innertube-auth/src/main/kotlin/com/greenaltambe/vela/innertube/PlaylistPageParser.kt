package com.greenaltambe.vela.innertube

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** One page of a playlist: the videos of its list, and what is needed to know whether the read can be trusted. */
data class PlaylistPage(
    val videos: List<AccountPlaylistVideo>,
    /** The token of the next page of this same list; null when there is none. */
    val next: String?,
    /** Entries of the list that could not be read: an unknown shape, or a next-page entry without a token. */
    val unread: Int,
    /** True when the answer is recognisably a playlist (first page) or holds a further part of its list (later pages). */
    val recognised: Boolean,
    /** The number of videos the page's header states; null when it states none (later pages never do). */
    val statedCount: Int? = null,
)

/**
 * Reads a playlist browse answer. YouTube serves two layouts side by side (measured 2026-11-25): `playlistVideoRenderer` entries (Watch later) and
 * `lockupViewModel` entries (Liked videos, public playlists), each with its own kind of next-page entry. Only the playlist's own list is read: the
 * page can carry further sections and a continuation of its own, which are suggestions and must never be mistaken for the playlist's videos.
 */
object PlaylistPageParser {
    /** Entries that say something about a list and are not videos. */
    private val NOTES = setOf("messageRenderer", "alertRenderer", "emptyStateRenderer", "backgroundPromoRenderer")
    private val NEXT = setOf("continuationItemRenderer", "continuationItemViewModel")
    private val COUNT = Regex("""\s*(?:(\d[\d,]*)\s+(?:videos?|episodes?|lessons?)|No videos)\s*""")

    fun first(page: JsonElement): PlaylistPage {
        val contents = page["contents"]
        val classic = contents.findAll("playlistVideoListRenderer").firstOrNull()?.get("contents") as? JsonArray
        val sections = contents.findAll("itemSectionRenderer").mapNotNull { it["contents"] as? JsonArray }
        // The first section that holds videos is the playlist; any later one is something YouTube suggests.
        val list = classic ?: sections.firstOrNull { s -> s.any(::isVideoEntry) }
        val read = if (list != null) read(list) else {
            // No video anywhere: everything the sections hold that is not a note counts as unread, so an unknown layout is not taken for an empty list.
            PlaylistPage(emptyList(), null, sections.sumOf { s -> s.count { keyOf(it) !in NOTES } }, false)
        }
        return read.copy(recognised = classic != null || page.at("metadata", "playlistMetadataRenderer") != null, statedCount = statedCount(page))
    }

    fun more(page: JsonElement): PlaylistPage {
        val lists = page["onResponseReceivedActions"].array.mapNotNull { it.at("appendContinuationItemsAction", "continuationItems") as? JsonArray }
        return read(lists.flatten()).copy(recognised = lists.isNotEmpty())
    }

    private fun read(entries: List<JsonElement>): PlaylistPage {
        val videos = ArrayList<AccountPlaylistVideo>()
        var next: String? = null
        var unread = 0
        for (entry in entries) {
            val key = keyOf(entry)
            when {
                key == "playlistVideoRenderer" -> classicVideo(entry[key])?.let(videos::add) ?: unread++
                key == "lockupViewModel" -> lockupVideo(entry[key])?.let(videos::add) ?: unread++
                key in NEXT -> {
                    val token = entry.findAll("continuationCommand").firstNotNullOfOrNull { c -> c.findAll("token").firstNotNullOfOrNull { it.string } }
                    if (token != null) next = token else unread++
                }
                key !in NOTES -> unread++
            }
        }
        return PlaylistPage(videos, next, unread, true)
    }

    private fun keyOf(entry: JsonElement): String? = (entry as? JsonObject)?.keys?.firstOrNull()
    private fun isVideoEntry(entry: JsonElement): Boolean =
        entry["playlistVideoRenderer"] != null || entry["lockupViewModel"]["contentType"].string?.contains("VIDEO") == true

    private fun classicVideo(v: JsonElement?): AccountPlaylistVideo? {
        val id = v["videoId"].string ?: return null
        val channelId = v["shortBylineText"]["runs"][0].at("navigationEndpoint", "browseEndpoint", "browseId").string
        return AccountPlaylistVideo(id, v["title"].text() ?: return null, v["shortBylineText"].text(), v["lengthSeconds"].string?.toLongOrNull(), v["setVideoId"].string, channelId.orEmpty())
    }

    private fun lockupVideo(l: JsonElement?): AccountPlaylistVideo? {
        val v = InnertubeParsers.videoLockup(l ?: return null) ?: return null
        return AccountPlaylistVideo(v.id, v.title, v.channelName.ifBlank { null }, v.durationSeconds, null, v.channelId)
    }

    private fun statedCount(page: JsonElement): Int? {
        val header = page["header"]
        val texts = header.findAll("numVideosText").mapNotNull { it.text() } +
            header.findAll("metadataParts").flatMap { parts -> parts.array.mapNotNull { it.at("text", "content").string } }
        val match = texts.firstNotNullOfOrNull { COUNT.matchEntire(it) } ?: return null
        return match.groupValues[1].replace(",", "").toIntOrNull() ?: 0
    }
}
