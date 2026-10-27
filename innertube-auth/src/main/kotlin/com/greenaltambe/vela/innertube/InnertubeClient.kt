package com.greenaltambe.vela.innertube

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
    fun call(endpoint: String, body: JsonObject = JsonObject(emptyMap())): JsonElement {
        val payload = buildJsonObject { put("context", web.context()); body.forEach { (k, v) -> put(k, v) } }
        val r = session.send("POST", "$base/$endpoint?prettyPrint=false", payload.toString(), extra = mapOf("Content-Type" to "application/json", "User-Agent" to web.userAgent))
        val json = try { lenientJson.parseToJsonElement(r.body) } catch (e: Exception) { throw AuthFailure.Unparseable("not JSON (${r.body.length} chars)") }
        return json
    }

    fun browse(browseId: String): JsonElement = call("browse", buildJsonObject { put("browseId", browseId) })
    fun continuation(token: String): JsonElement = call("browse", buildJsonObject { put("continuation", token) })

    /** Who the cookies belong to. Throws [AuthFailure.LoggedOut] when the response is the signed-out menu. */
    fun accountInfo(): AccountInfo = InnertubeParsers.accountInfo(call("account/account_menu"))
        ?: throw AuthFailure.LoggedOut("account menu has no active account")

    /** Every channel the account subscribes to, following continuations. */
    fun subscriptions(maxPages: Int = 20): List<SubscribedChannelInfo> {
        val out = LinkedHashMap<String, SubscribedChannelInfo>()
        var page = browse("FEsubscriptions")
        repeat(maxPages) {
            InnertubeParsers.channels(page).forEach { out.putIfAbsent(it.channelId, it) }
            val token = InnertubeParsers.continuationToken(page) ?: return out.values.toList()
            page = continuation(token)
        }
        return out.values.toList()
    }
}
