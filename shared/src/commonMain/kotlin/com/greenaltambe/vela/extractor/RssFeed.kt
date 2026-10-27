package com.greenaltambe.vela.extractor

import kotlin.time.Instant

private val entryRegex = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
private fun tag(entry: String, name: String): String? =
    Regex("<$name(?:\\s[^>]*)?>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(entry)?.groupValues?.get(1)?.trim()
private fun attr(entry: String, element: String, attribute: String): String? =
    Regex("<$element\\s[^>]*?$attribute=\"([^\"]*)\"").find(entry)?.groupValues?.get(1)

private fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&")

/**
 * YouTube's per-channel Atom feed (`/feeds/videos.xml?channel_id=ID`), which lists the 15 newest uploads with their
 * publish time, thumbnail and view count but no duration. A Short is recognised by the `/shorts/` form of the link
 * when the feed uses it; otherwise it is not recognisable from this source.
 */
fun parseChannelFeed(xml: String, fallbackChannelId: String): List<VideoSummary> = entryRegex.findAll(xml).mapNotNull { m ->
    val e = m.groupValues[1]
    val id = tag(e, "yt:videoId") ?: return@mapNotNull null
    VideoSummary(
        id = id,
        title = unescape(tag(e, "title").orEmpty()),
        channelId = tag(e, "yt:channelId") ?: fallbackChannelId,
        channelName = unescape(Regex("<author>.*?<name>(.*?)</name>", RegexOption.DOT_MATCHES_ALL).find(e)?.groupValues?.get(1).orEmpty()),
        thumbnailUrl = attr(e, "media:thumbnail", "url"),
        viewCount = attr(e, "media:statistics", "views")?.toLongOrNull(),
        isShort = attr(e, "link", "href")?.contains("/shorts/") == true,
        uploadedAt = tag(e, "published")?.let { runCatching { Instant.parse(it) }.getOrNull() },
    )
}.toList()
