package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.FeedGroup
import com.greenaltambe.vela.auth.FeedGroupKind
import com.greenaltambe.vela.extractor.VideoSummary
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** One page of Home: the feed's own videos, the groups YouTube set among them, the next page's token and what the parser made of the response. */
data class HomeFeed(val videos: List<VideoSummary>, val groups: List<FeedGroup>, val continuation: String?, val report: HomeParseReport)

/**
 * The accounting of one page: every item of the top-level list falls in exactly one of the counts, so nothing can disappear without showing here.
 * [skipped] holds kinds Vela leaves out on purpose (playlists, games); [unknown] kinds it has never seen; [broken] items of a known kind that could
 * not be read; [fallback] says why the whole page was read the old flat way. Names are YouTube's own item kinds, never ids or titles.
 */
data class HomeParseReport(
    val items: Int = 0,
    val feedItems: Int = 0,
    val groups: Int = 0,
    val ads: Int = 0,
    val tokens: Int = 0,
    val skipped: Map<String, Int> = emptyMap(),
    val unknown: Map<String, Int> = emptyMap(),
    val broken: Int = 0,
    val fallback: String? = null,
) {
    val accounted: Int get() = feedItems + groups + ads + tokens + broken + skipped.values.sum() + unknown.values.sum()

    /** A line for Diagnostics when something was not as expected; null when all of it was. */
    fun problem(): String? = listOfNotNull(
        fallback?.let { "Home read the old way: $it" },
        unknown.takeIf { it.isNotEmpty() }?.let { u -> "unknown Home items: " + u.entries.joinToString { "${it.key} x${it.value}" } },
        broken.takeIf { it > 0 }?.let { "$it Home items could not be read" },
    ).takeIf { it.isNotEmpty() }?.joinToString("; ")
}

/**
 * Reads a Home response by walking its top-level list once: the first page's grid, or the items a later page appends or a chip reloads. A video of
 * that list is a feed video; a section holding videos is a group, whatever kind of section it is, so a group YouTube invents tomorrow is still kept
 * out of the feed. An item that cannot be read costs that item only. If the list cannot be found at all, or nothing can be read from it while the
 * response plainly holds videos, the page is read the old flat way ([InnertubeParsers.videos]): worse than grouped, much better than empty.
 */
object HomeFeedParser {
    private const val GENERIC_TITLE = "More from YouTube"
    private val VIDEO_KEYS = setOf("videoRenderer", "gridVideoRenderer", "lockupViewModel", "shortsLockupViewModel", "reelItemRenderer")

    /** Item kinds that are not videos and are left out knowingly. */
    private val NOT_VIDEOS = setOf(
        "radioRenderer", "playlistRenderer", "gridPlaylistRenderer", "channelRenderer", "backstagePostThreadRenderer", "backstagePostRenderer",
        "postRenderer", "miniGameCardViewModel", "gameCardRenderer", "feedNudgeRenderer", "statementBannerRenderer", "inlineSurveyRenderer",
        "feedFilterChipBarRenderer", "messageRenderer", "brandVideoShelfRenderer", "brandVideoSingletonRenderer", "primetimePromoRenderer",
        "feedCustomizationFormViewModel", // the form for editing a custom feed (seen on a custom feed's chip)
    )

    fun parse(json: JsonElement): HomeFeed {
        val list = runCatching { topLevel(json) }.getOrNull()
        if (list == null) return flat(json, "the list was not where expected", HomeParseReport())
        val walk = Walk()
        list.forEach { item -> runCatching { walk.read(item) }.onFailure { walk.broken++ } }
        val report = walk.report(list.size)
        // Nothing from the feed itself and no section of a known kind: the layout has changed if the response holds videos all the same.
        if (walk.videos.isEmpty() && walk.knownGroups == 0 && InnertubeParsers.videos(json).isNotEmpty()) return flat(json, "nothing could be read from the list", report)
        return HomeFeed(walk.videos.values.toList(), walk.found, walk.token, report)
    }

    private fun flat(json: JsonElement, why: String, report: HomeParseReport) =
        HomeFeed(InnertubeParsers.videos(json), emptyList(), InnertubeParsers.feedContinuation(json), report.copy(fallback = why))

    /** The items of the page's own list; null when the response has no such list (as opposed to an empty one). */
    private fun topLevel(json: JsonElement): List<JsonElement>? {
        val commands = (json["onResponseReceivedActions"].array + json["onResponseReceivedEndpoints"].array).mapNotNull { action ->
            action["appendContinuationItemsAction"] ?: action["reloadContinuationItemsCommand"]?.takeIf { it["slot"].string != "RELOAD_CONTINUATION_SLOT_HEADER" }
        }
        if (commands.isNotEmpty()) return commands.flatMap { it["continuationItems"].array }
        return json.findAll("richGridRenderer").firstOrNull()?.let { it["contents"].array }
    }

    private class Walk {
        val videos = LinkedHashMap<String, VideoSummary>()
        val found = ArrayList<FeedGroup>()
        var token: String? = null
        var knownGroups = 0
        var broken = 0
        private var feedItems = 0
        private var groupItems = 0
        private var ads = 0
        private var tokens = 0
        private val skipped = LinkedHashMap<String, Int>()
        private val unknown = LinkedHashMap<String, Int>()

        fun report(items: Int) = HomeParseReport(items, feedItems, groupItems, ads, tokens, skipped, unknown, broken)

        fun read(item: JsonElement) {
            when (val kind = kindOf(item)) {
                "continuationItemRenderer" -> {
                    // The list's own continuation item. One inside a group belongs to the group, and the walk never looks there.
                    val next = item.at(kind, "continuationEndpoint", "continuationCommand", "token").string
                    if (next == null) broken++ else { token = next; tokens++ }
                }
                "adSlotRenderer" -> ads++
                "richSectionRenderer" -> section(item.at(kind, "content"))
                "richItemRenderer" -> feedItem(item.at(kind, "content"))
                in VIDEO_KEYS -> feedItem(item)
                in NOT_VIDEOS -> count(skipped, kind)
                else -> {
                    // Never seen. If it holds videos they are kept as a group (hidden with the other groups), never poured into the feed.
                    val inside = playable(item)
                    if (inside.videos.isNotEmpty()) group(item[kind], inside.videos)
                    count(unknown, kind)
                }
            }
        }

        private fun feedItem(content: JsonElement?) {
            val kind = kindOf(content)
            if (kind == "adSlotRenderer") { ads++; return }
            if (kind in NOT_VIDEOS) { count(skipped, kind); return }
            val inside = playable(content)
            val type = content.at("lockupViewModel", "contentType").string
            when {
                inside.videos.isNotEmpty() -> { inside.videos.forEach { videos.putIfAbsent(it.id, it) }; feedItems++ }
                // A playlist or a mix in the shape of a video card.
                type != null && !isVideoLockup(type) -> count(skipped, "lockupViewModel:$type")
                kind in VIDEO_KEYS -> broken++
                else -> count(unknown, kind)
            }
        }

        private fun section(content: JsonElement?) {
            val kind = kindOf(content)
            val inside = playable(content)
            when {
                inside.videos.isNotEmpty() -> { group(content[kind], inside.videos); knownGroups++; groupItems++ }
                inside.unreadable > 0 -> broken++
                else -> count(skipped, "section:$kind")
            }
        }

        private fun group(renderer: JsonElement?, inside: List<VideoSummary>) {
            found += FeedGroup(
                kind = if (inside.all { it.isShort }) FeedGroupKind.SHORTS else FeedGroupKind.SUGGESTED,
                title = titleOf(renderer), subtitle = textOf(renderer["subtitle"]),
                videos = inside, afterVideoId = videos.keys.lastOrNull(),
            )
        }

        private fun count(into: MutableMap<String, Int>, kind: String) { into[kind] = (into[kind] ?: 0) + 1 }
    }

    private class Inside(val videos: List<VideoSummary>, val unreadable: Int)

    /** The videos anywhere below [node], in order, leaving advertisements out. */
    private fun playable(node: JsonElement?): Inside {
        val out = LinkedHashMap<String, VideoSummary>()
        var unreadable = 0
        fun walk(e: JsonElement?) {
            when (e) {
                is JsonObject -> e.forEach { (key, value) ->
                    when {
                        key == "adSlotRenderer" -> {}
                        key == "lockupViewModel" && !isVideoLockup(value["contentType"].string.orEmpty()) -> {}
                        key in VIDEO_KEYS -> {
                            val video = runCatching { video(key, value) }.getOrNull()
                            if (video == null) unreadable++ else out.putIfAbsent(video.id, video)
                        }
                        else -> walk(value)
                    }
                }
                is JsonArray -> e.forEach(::walk)
                else -> {}
            }
        }
        // [node] is a wrapper such as {"lockupViewModel": {...}}: its own key is looked at like any other.
        walk(node)
        return Inside(out.values.toList(), unreadable)
    }

    private fun isVideoLockup(type: String) = type.contains("VIDEO") || type.contains("SHORT") || type.contains("REEL")

    private fun video(key: String, v: JsonElement): VideoSummary? = when (key) {
        "videoRenderer", "gridVideoRenderer" -> InnertubeParsers.videoRenderer(v)?.shortIf(v.at("navigationEndpoint", "reelWatchEndpoint"))
        "lockupViewModel" -> {
            val type = v["contentType"].string.orEmpty()
            val read = if (type.contains("VIDEO")) InnertubeParsers.videoLockup(v) else InnertubeParsers.videoLockup(JsonObject((v as JsonObject) + ("contentType" to kotlinx.serialization.json.JsonPrimitive("LOCKUP_CONTENT_TYPE_VIDEO"))))?.copy(isShort = true)
            // Only the card's own tap decides: a Short is a card that opens the Shorts player.
            read?.shortIf(v.at("rendererContext", "commandContext", "onTap", "innertubeCommand", "reelWatchEndpoint"))
        }
        else -> InnertubeParsers.shortItem(v)?.takeIf { it.id.isNotBlank() }
    }

    private fun VideoSummary.shortIf(reelEndpoint: JsonElement?): VideoSummary =
        if (reelEndpoint == null) this else copy(isShort = true, shortsSequence = reelEndpoint["sequenceParams"].string)

    /** The name of the renderer or view model an item is. */
    private fun kindOf(item: JsonElement?): String {
        val keys = item.obj?.keys.orEmpty()
        return keys.firstOrNull { it.endsWith("Renderer") || it.endsWith("ViewModel") } ?: keys.firstOrNull() ?: "empty"
    }

    private fun textOf(node: JsonElement?): String? = (node.text() ?: node["content"].string)?.takeIf { it.isNotBlank() }

    private fun titleOf(renderer: JsonElement?): String =
        textOf(renderer["title"]) ?: renderer["header"].findAll("title").firstNotNullOfOrNull(::textOf) ?: GENERIC_TITLE
}
