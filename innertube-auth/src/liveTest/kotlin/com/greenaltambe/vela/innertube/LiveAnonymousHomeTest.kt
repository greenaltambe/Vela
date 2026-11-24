package com.greenaltambe.vela.innertube

import kotlinx.serialization.json.*
import org.junit.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** Does youtube.com's Home answer without any cookies (no account, no cookie jar)? Prints counts only. */
class LiveAnonymousHomeTest {
    private val web = WebClient.bundled()
    private val http = HttpClient.newHttpClient()

    private fun post(body: JsonObject): JsonElement {
        val payload = buildJsonObject { put("context", web.context()); body.forEach { (k, v) -> put(k, v) } }
        val req = HttpRequest.newBuilder(URI("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false"))
            .header("Content-Type", "application/json").header("User-Agent", web.userAgent).header("Origin", "https://www.youtube.com")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build()
        val r = http.send(req, HttpResponse.BodyHandlers.ofString())
        println("HTTP ${r.statusCode()}, ${r.body().length} chars")
        return Json { isLenient = true; ignoreUnknownKeys = true }.parseToJsonElement(r.body())
    }

    @Test fun anonymousHome() {
        val first = post(buildJsonObject { put("browseId", "FEwhat_to_watch") })
        val vids = InnertubeParsers.videos(first)
        val chips = InnertubeParsers.homeChips(first)
        println("anonymous Home: ${vids.size} videos, live=${vids.count { it.isLive }}, duration known=${vids.count { it.durationSeconds != null }}, chips=${chips.size}, signed-out page=${InnertubeParsers.isSignedOutPage(first)}")
        println("chip kinds: ${chips.groupingBy { it.kind }.eachCount()}; chips with token=${chips.count { it.token != null }}")
        var token = InnertubeParsers.feedContinuation(first); var pages = 1; var total = vids.size
        val seen = vids.map { it.id }.toMutableSet(); var repeats = 0
        while (token != null && pages < 4) {
            val next = post(buildJsonObject { put("continuation", token!!) })
            val v = InnertubeParsers.videos(next); repeats += v.count { !seen.add(it.id) }; total += v.size; pages++
            token = InnertubeParsers.feedContinuation(next)
        }
        println("anonymous paging: $pages pages, $total videos, $repeats repeats, more=${token != null}")
        val chip = chips.firstOrNull { it.token != null && it.kind == com.greenaltambe.vela.auth.HomeChipKind.TOPIC }
        if (chip != null) { val c = post(buildJsonObject { put("continuation", chip.token!!) }); println("first topic chip: ${InnertubeParsers.videos(c).size} videos") }
    }
}
