package com.greenaltambe.vela.extractor.newpipe

import com.greenaltambe.vela.extractor.ContinuationToken
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.schabi.newpipe.extractor.Page as NpPage

/** Token family of this tier; see [ContinuationToken]. */
internal const val TOKEN_TIER = "newpipe"

/** NewPipe's pages are objects, ours are strings: a token is the page as URL-safe base64 JSON, stamped with [TOKEN_TIER]. */
@Serializable
private class PageDto(
    val url: String? = null,
    val id: String? = null,
    val ids: List<String>? = null,
    val cookies: Map<String, String>? = null,
    val body: String? = null,
)

@OptIn(ExperimentalEncodingApi::class)
internal fun NpPage.toContinuationToken(): String {
    val dto = PageDto(url, id, ids, cookies, body?.let { Base64.UrlSafe.encode(it) })
    val payload = Base64.UrlSafe.encode(Json.encodeToString(PageDto.serializer(), dto).encodeToByteArray())
    return ContinuationToken.wrap(TOKEN_TIER, payload)
}

/** Null if [token] wasn't issued by this tier or is damaged; callers report that as a foreign continuation. */
@OptIn(ExperimentalEncodingApi::class)
internal fun pageFromContinuationToken(token: String): NpPage? = runCatching {
    val payload = ContinuationToken.unwrap(TOKEN_TIER, token) ?: return null
    val dto = Json.decodeFromString(PageDto.serializer(), Base64.UrlSafe.decode(payload).decodeToString())
    NpPage(dto.url, dto.id, dto.ids, dto.cookies, dto.body?.let { Base64.UrlSafe.decode(it) })
}.getOrNull()

internal fun NpPage?.toTokenOrNull(): String? = this?.takeIf { NpPage.isValid(it) }?.toContinuationToken()
