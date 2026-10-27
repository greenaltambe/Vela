package com.greenaltambe.vela.innertube

import kotlinx.serialization.json.JsonElement

data class AccountInfo(val displayName: String, val email: String?, val avatarUrl: String?, val channelId: String?)
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
}
