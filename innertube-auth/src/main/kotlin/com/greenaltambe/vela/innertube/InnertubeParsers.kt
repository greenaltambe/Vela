package com.greenaltambe.vela.innertube

import kotlinx.serialization.json.JsonElement

data class AccountInfo(val displayName: String, val email: String?, val avatarUrl: String?, val channelId: String?)
/** One profile (the main channel or a brand channel) of the signed-in Google account. [pageId] is what `X-Goog-PageId` carries to act as that channel; null for the main one. ASSUMPTION: read from `datasyncId` as "<pageId>||<userId>"; not verified because the test account has a single profile. */
data class ProfileInfo(val name: String, val selected: Boolean, val pageId: String?)
data class AccountPlaylistInfo(val id: String, val title: String, val videoCount: Int?, val thumbnailUrl: String?)
data class AccountPlaylistVideo(val videoId: String, val title: String, val channelName: String?, val durationSeconds: Long?, val setVideoId: String?)
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

    /** Videos in a playlist browse/continuation response. */
    fun playlistVideos(json: JsonElement): List<AccountPlaylistVideo> =
        json.findAll("playlistVideoRenderer").mapNotNull { v ->
            val id = v["videoId"].string ?: return@mapNotNull null
            AccountPlaylistVideo(id, v["title"].text() ?: return@mapNotNull null, v["shortBylineText"].text(), v["lengthSeconds"].string?.toLongOrNull(), v["setVideoId"].string)
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
    fun videos(json: JsonElement): List<com.greenaltambe.vela.extractor.VideoSummary> {
        val classic = json.findAll("videoRenderer").mapNotNull { v ->
            val id = v["videoId"].string ?: return@mapNotNull null
            val owner = v["ownerText"] ?: v["longBylineText"] ?: v["shortBylineText"]
            com.greenaltambe.vela.extractor.VideoSummary(
                id, v["title"].text() ?: return@mapNotNull null,
                owner["runs"][0]["navigationEndpoint"]["browseEndpoint"]["browseId"].string ?: "", owner.text() ?: "",
                v["thumbnail"]["thumbnails"].array.lastOrNull()["url"].string?.let { if (it.startsWith("//")) "https:$it" else it },
                clock(v["lengthText"].text()), views(v["viewCountText"].text() ?: v["shortViewCountText"].text()),
                isLive = v["badges"].toString().contains("LIVE"),
                publishedText = v["publishedTimeText"].text(),
                channelAvatarUrl = v["channelThumbnailSupportedRenderers"]["channelThumbnailWithLinkRenderer"]["thumbnail"]["thumbnails"].array.firstOrNull()["url"].string?.let { if (it.startsWith("//")) "https:$it" else it },
            )
        }
        val lockups = json.findAll("lockupViewModel").mapNotNull { l ->
            if (l["contentType"].string?.contains("VIDEO") != true) return@mapNotNull null
            val id = l["contentId"].string ?: return@mapNotNull null
            val title = l["metadata"]["lockupMetadataViewModel"]["title"]["content"].string ?: return@mapNotNull null
            val channel = l["metadata"]["lockupMetadataViewModel"]["metadata"]["contentMetadataViewModel"]["metadataRows"][0]["metadataParts"][0]["text"]["content"].string ?: ""
            // The lockup carries its picture as image "sources"; the largest is last.
            val thumb = l["contentImage"]["thumbnailViewModel"]["image"]["sources"].array.lastOrNull()["url"].string
            val avatar = l["metadata"]["lockupMetadataViewModel"]["image"]["decoratedAvatarViewModel"]["avatar"]["avatarViewModel"]["image"]["sources"].array.firstOrNull()["url"].string
            // The second metadata row is "1.2M views" then "3 days ago".
            val parts = l["metadata"]["lockupMetadataViewModel"]["metadata"]["contentMetadataViewModel"]["metadataRows"][1]["metadataParts"]
            com.greenaltambe.vela.extractor.VideoSummary(id, title, "", channel, thumb?.let { if (it.startsWith("//")) "https:$it" else it }, viewCount = views(parts[0]["text"]["content"].string), channelAvatarUrl = avatar, publishedText = parts[1]["text"]["content"].string)
        }
        return (classic + lockups).distinctBy { it.id }
    }

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
