package com.greenaltambe.vela.innertube

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class AccountInfo(val displayName: String, val email: String?, val avatarUrl: String?, val channelId: String?)
/** One profile (the main channel or a brand channel) of the signed-in Google account. [pageId] is what `X-Goog-PageId` carries to act as that channel; null for the main one. ASSUMPTION: read from `datasyncId` as "<pageId>||<userId>"; not verified because the test account has a single profile. */
data class ProfileInfo(val name: String, val selected: Boolean, val pageId: String?)
data class AccountPlaylistInfo(val id: String, val title: String, val videoCount: Int?, val thumbnailUrl: String?)
data class AccountPlaylistVideo(val videoId: String, val title: String, val channelName: String?, val durationSeconds: Long?, val setVideoId: String?, val channelId: String = "")
data class SubscribedChannelInfo(val channelId: String, val title: String, val avatarUrl: String?)

/** Path-based extractors over youtubei JSON. Each returns null / empty when the shape is not there. */
object InnertubeParsers {
    /** Null for the signed-out menu (it has no `activeAccountHeaderRenderer`). */
    fun accountInfo(json: JsonElement): AccountInfo? {
        val h = json.findAll("activeAccountHeaderRenderer").firstOrNull() ?: return null
        val name = h["accountName"].text() ?: return null
        val avatar = h["accountPhoto"]["thumbnails"].array.lastOrNull()["url"].string
        val channelId = json.findAll("browseEndpoint").firstNotNullOfOrNull { it["browseId"].string?.takeIf { id -> id.startsWith("UC") } }
        return AccountInfo(name, h["email"].text(), avatar, channelId)
    }

    /** Channels in a browse/continuation response: grid channel cards, channel rows and their link-card cousins. */
    fun channels(json: JsonElement): List<SubscribedChannelInfo> =
        (json.findAll("gridChannelRenderer") + json.findAll("channelRenderer")).mapNotNull { c ->
            val id = c["channelId"].string ?: return@mapNotNull null
            SubscribedChannelInfo(id, c["title"].text() ?: return@mapNotNull null, c["thumbnail"]["thumbnails"].array.lastOrNull()["url"].string?.let { if (it.startsWith("//")) "https:$it" else it })
        }

    fun continuationToken(json: JsonElement): String? =
        json.findAll("continuationCommand").firstNotNullOfOrNull { it["token"].string }

    /**
     * The token that loads the next page of a feed: the one in the list's own continuation item. Not [continuationToken], which returns the first
     * continuation command anywhere and on Home that can be a chip's token (the chip bar comes before the list).
     */
    fun feedContinuation(json: JsonElement): String? =
        json.findAll("continuationItemRenderer").firstNotNullOfOrNull { it["continuationEndpoint"]["continuationCommand"]["token"].string }

    /** The chips of the Home chip bar, in YouTube's order; empty when the response has none (signed out, or a continuation page). */
    fun homeChips(json: JsonElement): List<com.greenaltambe.vela.auth.HomeChip> {
        val bar = json.findAll("feedFilterChipBarRenderer").firstOrNull() ?: return emptyList()
        return bar.findAll("chipCloudChipRenderer").mapNotNull { c ->
            val label = c["text"].text()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val token = c["navigationEndpoint"]["continuationCommand"]["token"].string
            val unique = c["uniqueId"].string.orEmpty()
            val kind = when {
                unique.endsWith("CUSTOM_FEED_CREATE_FEED") -> com.greenaltambe.vela.auth.HomeChipKind.CREATE_CUSTOM_FEED
                Regex("CUSTOM_FEED_\\d+$").containsMatchIn(unique) -> com.greenaltambe.vela.auth.HomeChipKind.CUSTOM_FEED
                token == null && (c["isSelected"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true" -> com.greenaltambe.vela.auth.HomeChipKind.ALL
                else -> com.greenaltambe.vela.auth.HomeChipKind.TOPIC
            }
            com.greenaltambe.vela.auth.HomeChip(label, token, (c["isSelected"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true", kind)
        }
    }

    /** True when a browse response is the "sign in" prompt instead of content. */
    fun isSignedOutPage(json: JsonElement): Boolean = json.findAll("signInEndpoint").isNotEmpty() && json.findAll("activeAccountHeaderRenderer").isEmpty()

    /** Playlists in a browse response: classic grid/playlist renderers and the newer lockup view models (whose content id is the playlist id). */
    fun playlists(json: JsonElement): List<AccountPlaylistInfo> {
        val classic = (json.findAll("gridPlaylistRenderer") + json.findAll("playlistRenderer")).mapNotNull { p ->
            val id = p["playlistId"].string ?: return@mapNotNull null
            AccountPlaylistInfo(id, p["title"].text() ?: return@mapNotNull null, p["videoCountText"].text()?.filter { it.isDigit() }?.toIntOrNull() ?: p["videoCount"].string?.toIntOrNull(),
                p["thumbnail"]["thumbnails"].array.lastOrNull()["url"].string)
        }
        val lockups = json.findAll("lockupViewModel").mapNotNull { l ->
            if (l["contentType"].string?.contains("PLAYLIST") != true) return@mapNotNull null
            val id = l["contentId"].string ?: return@mapNotNull null
            val title = l["metadata"]["lockupMetadataViewModel"]["title"]["content"].string ?: return@mapNotNull null
            AccountPlaylistInfo(id, title, null, null)
        }
        return classic + lockups
    }

    /** The service's own refusal (`{"error":{"code":..,"message":..}}`), or null. */
    fun apiError(json: JsonElement): String? =
        (json as? kotlinx.serialization.json.JsonObject)?.get("error")?.let { e -> "${e["code"].string ?: ""} ${e["message"].string ?: ""}".trim().ifEmpty { "error" } }

    /** `likeStatus` is LIKE, DISLIKE or INDIFFERENT somewhere in the watch-page response. */
    fun rating(json: JsonElement): Rating? = json.findAll("likeStatus").firstNotNullOfOrNull {
        when ((it as? kotlinx.serialization.json.JsonPrimitive)?.content) { "LIKE" -> Rating.LIKE; "DISLIKE" -> Rating.DISLIKE; "INDIFFERENT" -> Rating.NONE; else -> null }
    }

    /** The channel page carries both buttons' templates (subscribed=false and =true); only `subscriptionStateEntity` says which is current. */
    fun subscribed(json: JsonElement): Boolean? = json.findAll("subscriptionStateEntity").firstNotNullOfOrNull { (it["subscribed"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull() }

    /**
     * Videos in a feed response: classic `videoRenderer`s (also inside `richItemRenderer`) and the newer video lockups. Entries without an id or title
     * are skipped. NOTE: written from the documented shapes; the throwaway account's Home feed is empty, so a real recommended feed was not seen.
     */
    /** A "Members only" badge on a renderer: the badge style on classic renderers, the badge text on lockups. */
    internal fun isMembersOnly(renderer: JsonElement): Boolean {
        val s = renderer.toString()
        return s.contains("MEMBERS_ONLY") || s.contains("\"Members only\"") || s.contains("\"Members first\"")
    }

    /**
     * Every video anywhere in a response, whatever it sits in: right for the account's own lists (history). Home uses [HomeFeedParser], which keeps
     * YouTube's groups apart from the feed.
     */
    fun videos(json: JsonElement): List<com.greenaltambe.vela.extractor.VideoSummary> =
        (json.findAll("videoRenderer").mapNotNull(::videoRenderer) + json.findAll("lockupViewModel").mapNotNull(::videoLockup)).distinctBy { it.id }

    /** One classic `videoRenderer` (or `gridVideoRenderer`); null without an id or a title. */
    internal fun videoRenderer(v: JsonElement): com.greenaltambe.vela.extractor.VideoSummary? {
        val id = v["videoId"].string ?: return null
        val owner = v["ownerText"] ?: v["longBylineText"] ?: v["shortBylineText"]
        return com.greenaltambe.vela.extractor.VideoSummary(
            id, v["title"].text() ?: return null,
            owner["runs"][0]["navigationEndpoint"]["browseEndpoint"]["browseId"].string ?: "", owner.text() ?: "",
            v["thumbnail"]["thumbnails"].array.lastOrNull()["url"].string?.let { if (it.startsWith("//")) "https:$it" else it },
            clock(v["lengthText"].text()), views(v["viewCountText"].text() ?: v["shortViewCountText"].text()),
            isLive = v["badges"].toString().contains("LIVE"),
            publishedText = v["publishedTimeText"].text() ?: v.findAll("publishedTimeText").firstOrNull().text(),
            channelAvatarUrl = v["channelThumbnailSupportedRenderers"]["channelThumbnailWithLinkRenderer"]["thumbnail"]["thumbnails"].array.firstOrNull()["url"].string?.let { if (it.startsWith("//")) "https:$it" else it },
            watchedPercent = v.findAll("thumbnailOverlayResumePlaybackRenderer").firstOrNull()?.get("percentDurationWatched").percent(),
            isMembersOnly = isMembersOnly(v),
        )
    }

    /** One video lockup; null when it is not a video (a playlist, a mix) or has no id or title. */
    internal fun videoLockup(l: JsonElement): com.greenaltambe.vela.extractor.VideoSummary? {
        val cType = l["contentType"].string ?: ""
        if (!cType.contains("VIDEO")) return null
        val id = l["contentId"].string ?: return null
        val meta = l.at("metadata", "lockupMetadataViewModel") ?: l["metadata"] ?: return null
        val title = meta.at("title", "content").string ?: meta["title"].text() ?: return null
        val thumb = l.at("contentImage", "thumbnailViewModel", "image", "sources").array.lastOrNull().at("url").string
        val avatar = meta.at("image", "decoratedAvatarViewModel", "avatar", "avatarViewModel", "image", "sources").array.firstOrNull().at("url").string

        val durationText = l.findAll("thumbnailBadgeViewModel").firstOrNull().at("text").string
            ?: l.findAll("thumbnailOverlayTimeStatusRenderer").firstOrNull().at("text").text()
        val duration = clock(durationText)
        // A live stream's thumbnail carries a LIVE badge (and no duration).
        val live = l.findAll("thumbnailBadgeViewModel").any { b ->
            b["badgeStyle"].string?.contains("LIVE") == true || b["text"].string.equals("LIVE", ignoreCase = true)
        }

        var viewCount: Long? = null
        var publishedText: String? = null
        val rows = meta.at("metadata", "contentMetadataViewModel", "metadataRows").array
        // The part that links to a channel is its name, whatever words the name holds ("... of the Day" is not a date).
        val channelPart = rows.flatMap { it.at("metadataParts").array }
            .firstOrNull { p -> p.findAll("browseEndpoint").any { it["browseId"].string?.startsWith("UC") == true } && p.at("text", "content").string != null }
        var channel = channelPart.at("text", "content").string.orEmpty()
        for (r in rows) {
            val parts = r.at("metadataParts").array
            for (p in parts) {
                if (p === channelPart) continue
                val text = p.at("text", "content").string ?: p.at("text").text() ?: continue
                val lower = text.lowercase()
                if (lower.contains("view")) {
                    viewCount = views(text)
                } else if (lower.contains("ago") || lower.contains("stream") || lower.contains("premier") ||
                    lower.contains("hour") || lower.contains("day") || lower.contains("month") ||
                    lower.contains("year") || lower.contains("minute") || lower.contains("second") ||
                    lower.contains("yesterday")) {
                    publishedText = text
                } else if (channel.isBlank()) {
                    channel = text
                }
            }
        }
        if (channel.isBlank()) {
            // Some entries (seen in Liked videos) have no channel row at all; the channel picture's label names it.
            channel = (meta.at("image", "decoratedAvatarViewModel", "a11yLabel").string
                ?: meta.at("image", "decoratedAvatarViewModel", "avatar", "avatarViewModel", "accessibility", "accessibilityData", "label").string)
                ?.removePrefix("Go to channel ")?.removePrefix("Go to ") ?: ""
        }
        return com.greenaltambe.vela.extractor.VideoSummary(
            id = id,
            title = title,
            // The avatar and the channel-name part carry a browse endpoint ("UC..."); the video's own link is a watchEndpoint, so only UC ids count.
            channelId = meta.findAll("browseEndpoint").firstNotNullOfOrNull { it["browseId"].string?.takeIf { b -> b.startsWith("UC") } } ?: "",
            channelName = channel,
            isMembersOnly = isMembersOnly(l),
            isLive = live,
            thumbnailUrl = thumb?.let { if (it.startsWith("//")) "https:$it" else it },
            durationSeconds = duration,
            viewCount = viewCount,
            publishedText = publishedText,
            channelAvatarUrl = avatar?.let { if (it.startsWith("//")) "https:$it" else it },
            // UNVERIFIED path: the progress bar overlay's `startPercent` (also seen as `percentDurationWatched`) under the thumbnail overlays.
            watchedPercent = (l.findAll("thumbnailOverlayProgressBarViewModel").firstOrNull()?.let { it["startPercent"] ?: it.findAll("startPercent").firstOrNull() }).percent()
                ?: l.findAll("thumbnailOverlayResumePlaybackRenderer").firstOrNull()?.get("percentDurationWatched").percent(),
        )
    }

    /** One Shorts item (`shortsLockupViewModel` or the older `reelItemRenderer`). It names no channel. */
    internal fun shortItem(reel: JsonElement): com.greenaltambe.vela.extractor.VideoSummary? {
        val id = reel["videoId"].string
            ?: reel["contentId"].string
            ?: reel.at("onTap", "innertubeCommand", "reelWatchEndpoint", "videoId").string
            ?: reel.at("onTap", "innertubeCommand", "commandMetadata", "webCommandMetadata", "url").string
                ?.substringAfter("/shorts/")?.substringBefore("?")?.substringBefore("&")
            ?: reel["entityId"].string?.let {
                if (it.startsWith("shorts-shelf-item-")) it.removePrefix("shorts-shelf-item-")
                else if (it.startsWith("shorts-lockup-item-")) it.removePrefix("shorts-lockup-item-")
                else it
            }
            ?: reel.findAll("videoId").firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }
            ?: return null

        val title = reel.at("overlayMetadata", "primaryText", "content").string
            ?: reel.at("headline", "simpleText").string
            ?: reel["title"].text()
            ?: reel.at("metadata", "lockupMetadataViewModel", "title", "content").string
            ?: reel["accessibilityText"].string?.substringBefore(",")
            ?: ""

        val thumb = reel.at("thumbnailViewModel", "thumbnailViewModel", "image", "sources").array.lastOrNull().at("url").string
            ?: reel.at("contentImage", "thumbnailViewModel", "image", "sources").array.lastOrNull().at("url").string
            ?: reel.findAll("thumbnails").firstOrNull().array.lastOrNull().at("url").string
            ?: reel.findAll("sources").firstOrNull().array.lastOrNull().at("url").string

        val viewText = reel.at("overlayMetadata", "secondaryText", "content").string
            ?: reel["viewCountText"].text()
            ?: reel["accessibilityText"].string?.let { acc ->
                acc.split(",").getOrNull(1)?.substringBefore("-")?.trim()
            }
        val views = parseCount(viewText)

        return com.greenaltambe.vela.extractor.VideoSummary(
            id = id,
            title = title,
            channelId = "",
            channelName = "",
            thumbnailUrl = thumb,
            durationSeconds = null,
            viewCount = views,
            publishedText = viewText,
            isShort = true,
            shortsSequence = reel.at("onTap", "innertubeCommand", "reelWatchEndpoint", "sequenceParams").string,
        )
    }

    /** A 0..100 number from a JSON number or numeric string; null when absent or not a number. */
    private fun JsonElement?.percent(): Int? =
        (this as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()?.coerceIn(0, 100)

    private fun clock(text: String?): Long? = text?.split(':')?.mapNotNull { it.trim().toLongOrNull() }?.takeIf { it.size in 2..3 }?.fold(0L) { acc, n -> acc * 60 + n }
    /** "1,234 views", "1.2K views", "3.4M views": the count; null if there is no number. */
    private fun views(text: String?): Long? {
        val m = Regex("""([\d.,]+)\s*([KMB])?""", RegexOption.IGNORE_CASE).find(text ?: return null) ?: return null
        val number = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val scale = when (m.groupValues[2].uppercase()) { "K" -> 1_000.0; "M" -> 1_000_000.0; "B" -> 1_000_000_000.0; else -> 1.0 }
        return (number * scale).toLong()
    }

    fun profiles(json: JsonElement): List<ProfileInfo> = json.findAll("accountItem").mapNotNull { a ->
        val name = a["accountName"].text() ?: return@mapNotNull null
        val sync = a.findAll("datasyncId").firstNotNullOfOrNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
        val page = sync?.substringBefore("||")?.takeIf { it.isNotBlank() && sync.substringAfter("||", "").isNotBlank() }
        ProfileInfo(name, (a["isSelected"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true", page)
    }
}

/** "1.2M views" and the like as a number; null when there is none. */
internal fun parseCount(text: String?): Long? {
    if (text == null) return null
    val m = Regex("""([\d.,]+)\s*([KMBkmb])?""").find(text) ?: return null
    val num = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
    val factor = when (m.groupValues[2].uppercase()) {
        "K" -> 1_000.0
        "M" -> 1_000_000.0
        "B" -> 1_000_000_000.0
        else -> 1.0
    }
    return (num * factor).toLong()
}
