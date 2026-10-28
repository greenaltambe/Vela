package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.extractor.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * Live chat and replay chat by polling YouTube's own continuation endpoints, anonymously (no cookies, never the account). Measured 2026-10-28
 * on a real live stream: `next` carries a `liveChatRenderer` with the first continuation; `live_chat/get_live_chat` answered ~70 messages and
 * `timeoutMs` 10000 with the next continuation. Replay uses `live_chat/get_live_chat_replay` with the same shape wrapped in `replayChatItemAction`.
 */
class LiveChatClient(
    private val transport: AuthTransport = OkHttpAuthTransport(),
    private val web: WebClient = WebClient.bundled(),
    private val base: String = "https://www.youtube.com/youtubei/v1",
) : LiveChatSource {

    private fun post(endpoint: String, body: JsonObject): JsonElement {
        val payload = buildJsonObject { put("context", web.context()); body.forEach { (k, v) -> put(k, v) } }
        val r = transport.execute("POST", "$base/$endpoint?prettyPrint=false", mapOf("Content-Type" to "application/json", "User-Agent" to web.userAgent), payload.toString())
        if (r.code !in 200..299) throw AuthFailure.Http(r.code)
        return lenientJson.parseToJsonElement(r.body)
    }

    override suspend fun open(videoId: String): ChatChannel? = withContext(Dispatchers.IO) {
        val next = runCatching { post("next", buildJsonObject { put("videoId", videoId) }) }.getOrNull() ?: return@withContext null
        val renderer = next.findAll("liveChatRenderer").firstOrNull() ?: return@withContext null
        val token = renderer.findAll("continuation").firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull } ?: return@withContext null
        val replay = (renderer["isReplay"] as? JsonPrimitive)?.booleanOrNull == true
        Channel(token, replay)
    }

    private inner class Channel(private var token: String, override val isReplay: Boolean) : ChatChannel {
        private val seen = LinkedHashSet<String>()

        override suspend fun poll(): ChatBatch = withContext(Dispatchers.IO) {
            val json = post(if (isReplay) "live_chat/get_live_chat_replay" else "live_chat/get_live_chat", buildJsonObject { put("continuation", token) })
            val parsed = LiveChatParser.parse(json)
            parsed.nextToken?.let { token = it }
            val fresh = parsed.messages.filter { seen.add(it.id) }
            while (seen.size > 2000) seen.remove(seen.first())
            ChatBatch(fresh, parsed.timeoutMs.coerceIn(MIN_POLL_MS, MAX_POLL_MS), ended = parsed.nextToken == null)
        }
    }

    companion object { const val MIN_POLL_MS = 3_000L; const val MAX_POLL_MS = 30_000L }
}

object LiveChatParser {
    class Parsed(val messages: List<ChatMessage>, val nextToken: String?, val timeoutMs: Long)

    fun parse(json: JsonElement): Parsed {
        val cont = json.findAll("liveChatContinuation").firstOrNull()
        val actions = (cont ?: json).findAll("addChatItemAction")
        val messages = actions.mapNotNull { a -> a["item"]?.let(::message) }
        val c = cont["continuations"][0]
        val data = listOf("timedContinuationData", "invalidationContinuationData", "liveChatReplayContinuationData").firstNotNullOfOrNull { c[it] }
        val next = data["continuation"].string
        val timeout = (data["timeoutMs"] as? JsonPrimitive)?.longOrNull ?: (data["timeoutMs"].string?.toLongOrNull()) ?: 5_000L
        return Parsed(messages, next, timeout)
    }

    private fun message(item: JsonElement): ChatMessage? {
        (item["liveChatTextMessageRenderer"])?.let { r -> return ChatMessage(r["id"].string ?: return null, r["authorName"].text() ?: "", runsText(r["message"]), timestampMs = micros(r)) }
        (item["liveChatPaidMessageRenderer"])?.let { r -> return ChatMessage(r["id"].string ?: return null, r["authorName"].text() ?: "", runsText(r["message"]), ChatKind.SUPERCHAT, r["purchaseAmountText"].text(), micros(r)) }
        (item["liveChatMembershipItemRenderer"])?.let { r -> return ChatMessage(r["id"].string ?: return null, r["authorName"].text() ?: "", runsText(r["headerSubtext"]).ifEmpty { "became a member" }, ChatKind.MEMBER, null, micros(r)) }
        return null
    }

    private fun micros(r: JsonElement) = (r["timestampUsec"].string?.toLongOrNull() ?: 0L) / 1000

    /** Text runs with emoji as their shortcut (":smile:") or character, links as their text. */
    private fun runsText(e: JsonElement?): String = e["runs"].array.joinToString("") { run ->
        run["text"].string ?: run["emoji"]["shortcuts"][0].string ?: run["emoji"]["emojiId"].string ?: ""
    }
}
