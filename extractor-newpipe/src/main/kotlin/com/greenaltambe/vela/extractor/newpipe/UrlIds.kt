package com.greenaltambe.vela.extractor.newpipe

import com.greenaltambe.vela.extractor.StreamUrls
import kotlin.time.Instant

internal fun videoUrl(videoId: String) = "https://www.youtube.com/watch?v=$videoId"

internal fun channelUrl(channelId: String) = "https://www.youtube.com/channel/$channelId"

/** The video id from a watch, shorts, live or youtu.be URL; null if none can be found. */
internal fun videoIdFromUrl(url: String): String? {
    queryParam(url, "v")?.let { return it }
    val path = url.substringBefore('?').substringAfter("://", url).substringAfter('/', "")
    val segments = path.split('/').filter { it.isNotEmpty() }
    return when {
        segments.size >= 2 && segments[0] in setOf("shorts", "live", "embed", "v") -> segments[1]
        url.contains("youtu.be/") && segments.isNotEmpty() -> segments[0]
        else -> null
    }
}

/** The channel id from a `/channel/UC...` URL, else the last path segment (`/@handle`, `/c/name`); null if empty. */
internal fun channelIdFromUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val path = url.substringBefore('?').substringAfter("://", url).substringAfter('/', "")
    val segments = path.split('/').filter { it.isNotEmpty() }
    val i = segments.indexOf("channel")
    return if (i >= 0 && i + 1 < segments.size) segments[i + 1] else segments.lastOrNull()
}

internal fun queryParam(url: String, name: String): String? = StreamUrls.queryParam(url, name)

internal fun expiryOf(url: String): Instant? = StreamUrls.expiryOf(url)
