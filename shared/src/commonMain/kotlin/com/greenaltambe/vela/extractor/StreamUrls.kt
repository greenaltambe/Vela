package com.greenaltambe.vela.extractor

import kotlin.time.Instant

/** Small helpers for reading signed stream URLs, shared by every tier that produces them. */
object StreamUrls {
    /** Value of query parameter [name] in [url], undecoded (YouTube ids and numbers never need decoding). */
    fun queryParam(url: String, name: String): String? =
        url.substringAfter('?', "").substringBefore('#').split('&')
            .firstOrNull { it.substringBefore('=') == name }
            ?.substringAfter('=', "")?.takeIf { it.isNotEmpty() }

    /** When a stream URL stops working, from its `expire` query parameter (epoch seconds). */
    fun expiryOf(url: String): Instant? =
        queryParam(url, "expire")?.toLongOrNull()?.let(Instant::fromEpochSeconds)
}
