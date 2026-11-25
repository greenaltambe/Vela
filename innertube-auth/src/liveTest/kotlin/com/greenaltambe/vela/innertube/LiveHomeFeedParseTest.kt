package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.AccountGovernor
import com.greenaltambe.vela.auth.HomeChipKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration

/**
 * The Home parser against the real Home of the test account (read-only): four pages of All and every chip's first page. Fails if an item goes
 * uncounted, or if a video the old flat read finds is in neither the feed nor a group. Prints counts and YouTube's item kinds, never ids or titles.
 */
class LiveHomeFeedParseTest {
    private val client = InnertubeClient(AuthSession(LiveCookies.jarOrSkip(), AccountGovernor("live-home-parse")))
    private val p = Duration.parse("70s")
    private val problems = mutableListOf<String>()
    private val missing = mutableListOf<String>()

    private fun count(e: JsonElement, key: String): Int = when (e) {
        is JsonObject -> e.entries.sumOf { (k, v) -> (if (k == key) 1 else 0) + count(v, key) }
        is JsonArray -> e.sumOf { count(it, key) }
        else -> 0
    }

    private fun check(name: String, page: JsonElement): HomeFeed {
        val feed = HomeFeedParser.parse(page)
        val r = feed.report
        val kept = (feed.videos + feed.groups.flatMap { it.videos }).mapTo(HashSet()) { it.id }
        val lost = InnertubeParsers.videos(page).count { it.id !in kept }
        println("$name: items ${r.items} = feed ${r.feedItems}, groups ${r.groups}, ads ${r.ads}, token ${r.tokens}, skipped ${r.skipped}, unknown ${r.unknown}, broken ${r.broken}")
        println("$name: feed videos ${feed.videos.size} (shorts ${feed.videos.count { it.isShort }}), groups ${feed.groups.map { "${it.kind}:${it.videos.size}" }}, flat videos lost $lost, fallback ${r.fallback}")
        // Every continuation the response holds, to see that a token the parser does not take is one that belongs to a group.
        val anywhere = count(page, "continuationCommand")
        val flatToken = InnertubeParsers.feedContinuation(page)
        println("$name: list token ${feed.continuation != null}, old reading's token ${flatToken != null}, same ${feed.continuation == flatToken}, continuation commands anywhere $anywhere, in the list's items ${count(page, "continuationItemRenderer")}")
        if (flatToken != null && feed.continuation != flatToken) missing += "$name: next-page token differs"
        if (r.fallback == null) assertEquals(r.items, r.accounted, "$name: an item went uncounted")
        r.problem()?.let { problems += "$name: $it" }
        if (lost > 0) missing += "$name: $lost"
        return feed
    }

    @Test fun allAndEveryChip() {
        val first = client.browse("FEwhat_to_watch", p)
        var feed = check("All 1", first)
        for (n in 2..4) feed = check("All $n", client.continuation(feed.continuation ?: break, p))
        InnertubeParsers.homeChips(first).filter { it.token != null && it.kind != HomeChipKind.CREATE_CUSTOM_FEED }.forEachIndexed { i, chip ->
            check("chip $i ${chip.kind}", client.continuation(chip.token!!, p))
        }
        println("problems: ${problems.ifEmpty { listOf("none") }}")
        assertEquals(emptyList(), missing, "videos or next-page tokens the old reading finds that the parser dropped")
    }
}
