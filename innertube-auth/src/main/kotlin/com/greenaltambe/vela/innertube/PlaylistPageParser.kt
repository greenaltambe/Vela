package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.extractor.PlaylistContents
import com.greenaltambe.vela.extractor.PlaylistDetails
import com.greenaltambe.vela.extractor.VideoSummary
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One video of a playlist's list; [setVideoId] names the entry itself (the old layout only). */
data class PlaylistEntry(val video: VideoSummary, val setVideoId: String? = null)

/** One page of a playlist: the videos of its list, and what is needed to know whether the read can be trusted. */
data class PlaylistPage(
    val entries: List<PlaylistEntry>,
    /** The token of the next page of this same list; null when there is none. */
    val next: String?,
    /** Entries of the list that could not be read: an unknown shape, or a next-page entry without a token. */
    val unread: Int,
    /** True when the answer is recognisably a playlist (first page) or holds a further part of its list (later pages). */
    val recognised: Boolean,
    /** The number of videos the page's header states; null when it states none (later pages never do). */
    val statedCount: Int? = null,
) {
    val videos: List<AccountPlaylistVideo> get() = entries.map { (v, set) -> AccountPlaylistVideo(v.id, v.title, v.channelName.ifBlank { null }, v.durationSeconds, set, v.channelId) }
}

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
    private val VIEWS = Regex("""\s*(?:(\d[\d,]*)\s+views?|No views)\s*""")

    /** Why a first page cannot be taken for the playlist, or null. An answer that shows no videos where there should be some is a failed read. */
    fun refusal(first: PlaylistPage): String? {
        val stated = first.statedCount
        if (first.entries.isNotEmpty() || stated == 0) return null
        if (stated == null && first.unread == 0 && first.recognised) return null
        return "the playlist page was not understood: no video read, ${stated ?: "no number"} stated, ${first.unread} unknown entries"
    }

    /**
     * A browse answer as a page to show; null when it cannot be trusted. [first] says whether it is the playlist's first page, which carries its details.
     * [signedIn] says whether it was asked as an account: only then is "saved" known. [token] stamps the next page's token for its source.
     */
    fun contents(page: JsonElement, playlistId: String, first: Boolean, signedIn: Boolean, token: (String) -> String): PlaylistContents? {
        val read = if (first) first(page) else more(page)
        val videos = read.entries.map { it.video }
        if (!first) {
            if (!read.recognised || (videos.isEmpty() && read.next == null && read.unread > 0)) return null
            return PlaylistContents(null, videos, read.next?.let(token), read.unread)
        }
        if (refusal(read) != null) return null
        val details = details(page, playlistId, signedIn) ?: return null
        if (videos.isEmpty() && read.statedCount == 0) return PlaylistContents(details, videos)
        return PlaylistContents(details, videos, read.next?.let(token), read.unread)
    }

    /** The header, in either layout; null when the page names no playlist. */
    fun details(page: JsonElement, playlistId: String, signedIn: Boolean): PlaylistDetails? {
        val old = page.at("header", "playlistHeaderRenderer")
        val new = page["header"].findAll("pageHeaderViewModel").firstOrNull()
        val title = old["title"].text() ?: new.at("title", "dynamicTextViewModel", "text", "content").string
            ?: page.at("metadata", "playlistMetadataRenderer", "title").string ?: return null
        val base = PlaylistDetails(playlistId, title, videoCount = statedCount(page)?.toLong())
        if (old != null) {
            // The old layout's save button; the account's own playlists have none.
            val like = old.at("likeButton", "likeButtonRenderer")
            return base.copy(
                ownerName = old["ownerText"].text(),
                ownerChannelId = old.at("ownerText", "runs", 0, "navigationEndpoint", "browseEndpoint", "browseId").string,
                coverUrl = old.at("playlistHeaderBanner", "heroPlaylistThumbnailRenderer", "thumbnail", "thumbnails").array.lastOrNull()["url"].string,
                viewCount = views(listOfNotNull(old["viewCountText"].text())),
                description = old["descriptionText"].text()?.takeIf { it.isNotBlank() },
                canBeSaved = like != null,
                savedOnAccount = if (signedIn) like["likeStatus"].string?.let { it == "LIKE" } else null,
            )
        }
        if (new == null) return base
        val owner = new.findAll("avatarStackViewModel").firstOrNull()
        val toggle = new.findAll("toggleButtonViewModel").map { it["toggleButtonViewModel"] ?: it }
            .firstOrNull { it.at("defaultButtonViewModel", "buttonViewModel", "iconName").string == "PLAYLIST_ADD" }
        return base.copy(
            ownerName = owner.at("text", "content").string?.removePrefix("by ")?.takeIf { it.isNotBlank() },
            ownerChannelId = owner.findAll("browseEndpoint").firstNotNullOfOrNull { it["browseId"].string?.takeIf { id -> id.startsWith("UC") } },
            ownerAvatarUrl = owner.findAll("avatarViewModel").firstOrNull().at("image", "sources").array.lastOrNull()["url"].string,
            coverUrl = new.findAll("contentPreviewImageViewModel").firstOrNull().at("image", "sources").array.lastOrNull()["url"].string,
            viewCount = views(metadataTexts(new)),
            description = new.at("description", "descriptionPreviewViewModel", "description", "content").string?.takeIf { it.isNotBlank() },
            // No row of buttons at all means the header was not understood, so nothing is claimed.
            canBeSaved = if (toggle != null) true else if (new.findAll("flexibleActionsViewModel").isNotEmpty()) false else null,
            savedOnAccount = if (signedIn) (toggle["isToggled"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() else null,
        )
    }

    private fun metadataTexts(node: JsonElement?): List<String> =
        node.findAll("metadataParts").flatMap { parts -> parts.array.mapNotNull { it.at("text", "content").string } }

    private fun views(texts: List<String>): Long? =
        texts.firstNotNullOfOrNull { VIEWS.matchEntire(it) }?.let { it.groupValues[1].replace(",", "").toLongOrNull() ?: 0 }

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
        val videos = ArrayList<PlaylistEntry>()
        var next: String? = null
        var unread = 0
        for (entry in entries) {
            val key = keyOf(entry)
            when {
                key == "playlistVideoRenderer" -> classicVideo(entry[key])?.let(videos::add) ?: unread++
                key == "lockupViewModel" -> entry[key]?.let(InnertubeParsers::videoLockup)?.let { videos.add(PlaylistEntry(it)) } ?: unread++
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

    private fun classicVideo(v: JsonElement?): PlaylistEntry? {
        val id = v["videoId"].string ?: return null
        // "1.2M views", " • ", "2 years ago"
        val info = v["videoInfo"]["runs"].array.mapNotNull { it["text"].string }
        return PlaylistEntry(
            VideoSummary(
                id, v["title"].text() ?: return null,
                v["shortBylineText"]["runs"][0].at("navigationEndpoint", "browseEndpoint", "browseId").string.orEmpty(),
                v["shortBylineText"].text().orEmpty(),
                v["thumbnail"]["thumbnails"].array.lastOrNull()["url"].string,
                v["lengthSeconds"].string?.toLongOrNull(),
                viewCount = info.firstOrNull { "view" in it }?.let(::parseCount),
                publishedText = info.firstOrNull { "ago" in it },
            ),
            v["setVideoId"].string,
        )
    }

    private fun statedCount(page: JsonElement): Int? {
        val header = page["header"]
        val texts = header.findAll("numVideosText").mapNotNull { it.text() } + metadataTexts(header)
        val match = texts.firstNotNullOfOrNull { COUNT.matchEntire(it) } ?: return null
        return match.groupValues[1].replace(",", "").toIntOrNull() ?: 0
    }
}
