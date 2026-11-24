package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.AccountGovernor
import org.junit.Test
import kotlin.time.Duration

/** Pages the unfiltered Home and the first non-empty topic chip up to 10 pages; prints counts and repeats (no ids, labels or tokens). */
class LiveHomePagingTest {
    private val client = InnertubeClient(AuthSession(LiveCookies.jarOrSkip(), AccountGovernor("live-paging")))
    private val p = Duration.parse("70s")

    private fun soak(name: String, first: kotlinx.serialization.json.JsonElement) {
        val seen = HashSet<String>(); var repeats = 0; var pages = 0; var total = 0
        var page = first
        while (pages < 10) {
            val ids = InnertubeParsers.videos(page).map { it.id }
            repeats += ids.count { !seen.add(it) }; total += ids.size; pages++
            val next = InnertubeParsers.feedContinuation(page) ?: break
            page = client.continuation(next, p)
        }
        println("$name: $pages pages, $total videos, $repeats repeats, ended early=${pages < 10}")
    }

    @Test fun soak() {
        val home = client.browse("FEwhat_to_watch", p)
        soak("All", home)
        val chip = InnertubeParsers.homeChips(home).first { it.kind == com.greenaltambe.vela.auth.HomeChipKind.TOPIC && it.label == "Music" }
        soak("chip Music", client.continuation(chip.token!!, p))
    }
}
