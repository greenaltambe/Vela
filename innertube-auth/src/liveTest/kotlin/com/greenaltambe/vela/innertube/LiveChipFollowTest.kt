package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.AccountGovernor
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.time.Duration

/** Follows each Home chip's continuation and prints what comes back: counts, overlap with All, live ones, whether paging works. Prints no ids or tokens. */
class LiveChipFollowTest {
    private val session = AuthSession(LiveCookies.jarOrSkip(), AccountGovernor("live-chips2"))
    private val client = InnertubeClient(session)
    private val p = Duration.parse("70s")

    private fun all(e: JsonElement, key: String, out: MutableList<JsonObject> = ArrayList()): List<JsonObject> {
        when (e) {
            is JsonObject -> e.forEach { (k, v) -> if (k == key && v is JsonObject) out += v; all(v, key, out) }
            is JsonArray -> e.forEach { all(it, key, out) }
            else -> {}
        }
        return out
    }

    private fun label(c: JsonObject) = c["text"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content

    @Test fun follow() {
        val home = client.browse("FEwhat_to_watch", p)
        val parsed = InnertubeParsers.homeChips(home)
        println("parsed chips: ${parsed.size}, kinds=${parsed.groupingBy { it.kind }.eachCount()}, selected=${parsed.count { it.selected }}, tokenless=${parsed.count { it.token == null }}")
        val base = InnertubeParsers.videos(home)
        val baseIds = base.map { it.id }.toSet()
        println("Home/All: ${base.size} videos, continuation=${InnertubeParsers.feedContinuation(home) != null}")
        val every = all(home, "chipCloudChipRenderer")
        println("chip bar: " + every.joinToString(" | ") { c ->
            val l = c["text"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "?"
            "$l[${c["style"]?.jsonObject?.get("styleType")?.jsonPrimitive?.content?.removePrefix("STYLE_")}${c["uniqueId"]?.jsonPrimitive?.content?.let { ",$it" } ?: ""}${if (c["content"] != null) ",content" else ""}]"
        })
        val chips = every.filter { it["navigationEndpoint"] != null && (System.getenv("VELA_ONLY_CUSTOM") == null || Regex("CUSTOM_FEED_\\d+").containsMatchIn(it["uniqueId"]?.jsonPrimitive?.content ?: "") || label(it) == "Live") && it["uniqueId"]?.jsonPrimitive?.content?.endsWith("CREATE_FEED") != true }
        for (chip in chips) {
            val label = chip["text"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "?"
            val token = all(chip, "continuationCommand").firstOrNull()?.get("token")?.jsonPrimitive?.content ?: continue
            val started = System.currentTimeMillis()
            val r = runCatching { client.continuation(token, p) }
            val ms = System.currentTimeMillis() - started
            if (r.isFailure) { println("chip '$label': FAILED ${r.exceptionOrNull()?.message?.take(80)}"); continue }
            val page = r.getOrThrow()
            val vids = InnertubeParsers.videos(page)
            val next = InnertubeParsers.feedContinuation(page)
            var more = "no next page"
            if (next != null) {
                val second = runCatching { client.continuation(next, p) }.getOrNull()
                val ids2 = second?.let { InnertubeParsers.videos(it) }.orEmpty()
                more = "page 2: ${ids2.size} videos, ${ids2.count { v -> vids.none { it.id == v.id } }} new, more after=${second?.let { InnertubeParsers.feedContinuation(it) } != null}"
            }
            println("chip '$label': ${vids.size} videos in ${ms}ms, ${vids.count { it.id in baseIds }} also in All, live=${vids.count { it.isLive }}, shorts=${vids.count { it.isShort }}, $more")
        }
    }
}
