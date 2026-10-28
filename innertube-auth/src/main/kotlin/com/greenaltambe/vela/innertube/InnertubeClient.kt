package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.CallKind
import kotlinx.serialization.json.*

/** The website client identity every account call presents. Bundled in `innertube-web.json`; [override] wins when present. */
data class WebClient(val clientName: String, val clientVersion: String, val hl: String, val gl: String, val userAgent: String, val visitorData: String? = null) {
    fun context(): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", clientName); put("clientVersion", clientVersion); put("hl", hl); put("gl", gl)
            visitorData?.let { put("visitorData", it) }
        }
    }

    companion object {
        fun bundled(): WebClient = parse(WebClient::class.java.getResource("/innertube-web.json")!!.readText())
        fun parse(text: String): WebClient {
            val o = lenientJson.parseToJsonElement(text).jsonObject
            return WebClient(o["clientName"].string ?: "WEB", o["clientVersion"].string!!, o["hl"].string ?: "en", o["gl"].string ?: "US", o["userAgent"].string!!, o["visitorData"].string)
        }

        /** The client version the website currently ships, scraped from its `ytcfg` (the refresh path when the bundled one goes stale). */
        fun versionFrom(html: String): String? = Regex("\"INNERTUBE_CLIENT_VERSION\"\\s*:\\s*\"([0-9.]+)\"").find(html)?.groupValues?.get(1)
    }
}

/**
 * Account data and actions over youtubei, signed by an [AuthSession]. Returns parsed values; the JSON is navigated with
 * lenient path helpers, so a changed field loses that value rather than the call.
 */
class InnertubeClient(
    private val session: AuthSession,
    private val web: WebClient = WebClient.bundled(),
    private val base: String = "https://www.youtube.com/youtubei/v1",
) {
    fun call(endpoint: String, body: JsonObject = JsonObject(emptyMap()), kind: CallKind = CallKind.BROWSE, patience: kotlin.time.Duration = kotlin.time.Duration.ZERO): JsonElement {
        val payload = buildJsonObject { put("context", web.context()); body.forEach { (k, v) -> put(k, v) } }
        val r = session.send(kind, "POST", "$base/$endpoint?prettyPrint=false", payload.toString(), extra = mapOf("Content-Type" to "application/json", "User-Agent" to web.userAgent), patience = patience)
        val json = try { lenientJson.parseToJsonElement(r.body) } catch (e: Exception) { throw AuthFailure.Unparseable("not JSON (${r.body.length} chars)") }
        return json
    }

    /** [patience]: how long a listing may wait for the governor's browse window to reopen (see [com.greenaltambe.vela.auth.AccountGovernor.call]). */
    fun browse(browseId: String, patience: kotlin.time.Duration = kotlin.time.Duration.ZERO): JsonElement = call("browse", buildJsonObject { put("browseId", browseId) }, patience = patience)
    fun continuation(token: String, patience: kotlin.time.Duration = kotlin.time.Duration.ZERO): JsonElement = call("browse", buildJsonObject { put("continuation", token) }, patience = patience)

    /** Who the cookies belong to. Throws [AuthFailure.LoggedOut] when the response is the signed-out menu. */
    fun accountInfo(): AccountInfo = InnertubeParsers.accountInfo(call("account/account_menu", kind = CallKind.SESSION_CHECK))
        ?: throw AuthFailure.LoggedOut("account menu has no active account")

    /**
     * Every channel the account really subscribes to, from the "All subscriptions" page (`FEchannels`), following continuations until
     * pages stop adding channels. NOT `FEsubscriptions`: for an account with no subscriptions that page shows ~120 SUGGESTED channels
     * (measured 2026-10-28), which would have been imported as if they were subscriptions.
     */
    fun subscriptions(maxPages: Int = 40): List<SubscribedChannelInfo> {
        val out = LinkedHashMap<String, SubscribedChannelInfo>()
        var page = browse("FEchannels", LISTING_PATIENCE)
        var stale = 0
        repeat(maxPages) {
            val before = out.size
            InnertubeParsers.channels(page).forEach { out.putIfAbsent(it.channelId, it) }
            // An account with no subscriptions still gets a continuation token here, and following it answers HTTP 500 (measured).
            if (out.isEmpty()) return emptyList()
            // The page keeps offering a continuation after the real list has ended (it repeated the same channel 12 times): stop after two pages with nothing new.
            stale = if (out.size == before) stale + 1 else 0
            if (stale >= 2) return out.values.toList()
            val token = InnertubeParsers.continuationToken(page) ?: return out.values.toList()
            // A server error on a later page keeps what was read: the callers only ever add, so a partial list is safe.
            page = try { continuation(token, LISTING_PATIENCE) } catch (e: AuthFailure.Http) { if (e.code >= 500) return out.values.toList() else throw e }
        }
        return out.values.toList()
    }

    // ---- writes (always CallKind.WRITE: the governor's write budget, spacing, breaker and audit apply) ----

    private fun write(endpoint: String, body: JsonObject): JsonElement {
        val json = call(endpoint, body, CallKind.WRITE)
        InnertubeParsers.apiError(json)?.let { throw AuthFailure.Unparseable("the service refused: $it") }
        return json
    }

    fun subscribe(channelId: String) { write("subscription/subscribe", buildJsonObject { putJsonArray("channelIds") { add(channelId) }; put("params", "EgIIAhgA") }) }
    fun unsubscribe(channelId: String) { write("subscription/unsubscribe", buildJsonObject { putJsonArray("channelIds") { add(channelId) }; put("params", "CgIIAhgA") }) }

    /** Like, dislike, or remove the rating. */
    fun rate(videoId: String, rating: Rating) {
        write(when (rating) { Rating.LIKE -> "like/like"; Rating.DISLIKE -> "like/dislike"; Rating.NONE -> "like/removelike" },
            buildJsonObject { putJsonObject("target") { put("videoId", videoId) } })
    }

    /** [playlistId] may be "WL" (Watch Later) or "LL" (Liked videos). */
    fun addToPlaylist(playlistId: String, videoId: String) = editPlaylist(playlistId, buildJsonObject { put("action", "ACTION_ADD_VIDEO"); put("addedVideoId", videoId) })
    fun removeFromPlaylist(playlistId: String, videoId: String) = editPlaylist(playlistId, buildJsonObject { put("action", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID"); put("removedVideoId", videoId) })
    fun renamePlaylist(playlistId: String, title: String) = editPlaylist(playlistId, buildJsonObject { put("action", "ACTION_SET_PLAYLIST_NAME"); put("playlistName", title) })

    private fun editPlaylist(playlistId: String, action: JsonObject) {
        val r = write("browse/edit_playlist", buildJsonObject { put("playlistId", playlistId); putJsonArray("actions") { add(action) } })
        val status = r["status"].string
        if (status != null && status != "STATUS_SUCCEEDED") throw AuthFailure.Unparseable("playlist edit answered $status")
    }

    /** A new private playlist; returns its id. */
    fun createPlaylist(title: String, privacy: String = "PRIVATE"): String {
        val r = write("playlist/create", buildJsonObject { put("title", title); put("privacyStatus", privacy) })
        return r["playlistId"].string ?: throw AuthFailure.Unparseable("create playlist answered no playlist id")
    }

    fun deletePlaylist(playlistId: String) { write("playlist/delete", buildJsonObject { put("playlistId", playlistId) }) }

    // ---- reads ----

    /** The profiles (channels) of the signed-in account; the throwaway account answered one selected profile with a datasyncId and a token. */
    fun profiles(): List<ProfileInfo> = InnertubeParsers.profiles(call("account/accounts_list", patience = LISTING_PATIENCE))

    /** The viewer's rating of a video, from the watch page data (`likeStatus`); null when the response has none. */
    fun rating(videoId: String): Rating? = InnertubeParsers.rating(call("next", buildJsonObject { put("videoId", videoId) }))

    /** Whether the account subscribes to the channel, from the channel page's subscription state entity; null when the page has none. The subscriptions feed itself is not stable enough to answer this. */
    fun isSubscribed(channelId: String): Boolean? = InnertubeParsers.subscribed(browse(channelId))

    /** The account's playlists (the library's "playlists" shelf), including Watch Later and Liked when the page lists them. */
    fun playlists(maxPages: Int = 5): List<AccountPlaylistInfo> {
        val out = LinkedHashMap<String, AccountPlaylistInfo>()
        var page = browse("FEplaylist_aggregation", LISTING_PATIENCE)
        repeat(maxPages) {
            InnertubeParsers.playlists(page).forEach { out.putIfAbsent(it.id, it) }
            val token = InnertubeParsers.continuationToken(page) ?: return out.values.toList()
            page = continuation(token, LISTING_PATIENCE)
        }
        return out.values.toList()
    }

    /** One playlist's videos ("WL", "LL" or a playlist id), following continuations. */
    fun playlistVideos(playlistId: String, maxPages: Int = 10): List<AccountPlaylistVideo> {
        val out = LinkedHashMap<String, AccountPlaylistVideo>()
        var page = browse("VL$playlistId", LISTING_PATIENCE)
        repeat(maxPages) {
            InnertubeParsers.playlistVideos(page).forEach { out.putIfAbsent(it.videoId, it) }
            val token = InnertubeParsers.continuationToken(page) ?: return out.values.toList()
            page = continuation(token, LISTING_PATIENCE)
        }
        return out.values.toList()
    }
}

/** A sync/listing may wait this long for the browse budget (6 calls a minute by default) rather than fail halfway. */
private val LISTING_PATIENCE = kotlin.time.Duration.parse("70s")

typealias Rating = com.greenaltambe.vela.auth.VideoRating
