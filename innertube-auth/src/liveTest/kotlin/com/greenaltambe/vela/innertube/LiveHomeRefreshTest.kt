package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.AccountGovernor
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.time.Duration

/**
 * Measures whether Home (FEwhat_to_watch) changes between back-to-back requests, and whether sending `visitorData` changes that.
 * It asserts nothing: it prints the overlap of video ids so the numbers can go in the report.
 *
 *  A  as Vela does it today: the bundled web client, no visitorData, repeated.
 *  B  sticky: the visitorData the first response carried, sent with every later request.
 *  C  chained: the visitorData of the previous response, sent with the next request (what a page that keeps refreshing does).
 */
class LiveHomeRefreshTest {
    private val jar = LiveCookies.jarOrSkip()
    private val session = AuthSession(jar, AccountGovernor("live-home"))
    private val patience = Duration.parse("70s")
    private val runs = 4

    private data class Answer(val ids: List<String>, val visitor: String?)

    private fun home(web: WebClient): Answer {
        val page = InnertubeClient(session, web).browse("FEwhat_to_watch", patience)
        return Answer(InnertubeParsers.videos(page).map { it.id }, visitorOf(page))
    }

    private fun visitorOf(page: JsonElement): String? =
        runCatching { page.jsonObject["responseContext"]!!.jsonObject["visitorData"]!!.jsonPrimitive.content }.getOrNull()

    private fun report(label: String, answers: List<Answer>) {
        val first = answers.first().ids.toSet()
        println("== $label")
        answers.forEachIndexed { i, a ->
            val prev = answers.getOrNull(i - 1)?.ids?.toSet()
            val vsPrev = prev?.let { p -> "${a.ids.count { it in p }}/${a.ids.size} same as previous" } ?: "first"
            val vsFirst = "${a.ids.count { it in first }}/${a.ids.size} same as the first"
            println("run ${i + 1}: ${a.ids.size} videos, $vsPrev, $vsFirst, visitorData in response=${a.visitor != null}")
        }
    }

    @Test fun measureHomeOverlap() {
        val bundled = WebClient.bundled()

        val a = List(runs) { home(bundled) }
        report("A today: no visitorData", a)

        val sticky = a.first().visitor
        val b = if (sticky != null) List(runs) { home(bundled.copy(visitorData = sticky)) } else emptyList()
        if (b.isNotEmpty()) report("B sticky visitorData", b) else println("== B skipped: the response carried no visitorData")

        var visitor: String? = null
        val c = List(runs) { home(bundled.copy(visitorData = visitor)).also { visitor = it.visitor ?: visitor } }
        report("C chained visitorData", c)
    }

    /** Which positions stay put between requests, and whether the Home page offers more pages (a continuation token). */
    @Test fun measurePositionsAndContinuation() {
        val web = WebClient.bundled()
        val pages = List(4) { InnertubeClient(session, web).browse("FEwhat_to_watch", patience) }
        val lists = pages.map { p -> InnertubeParsers.videos(p).map { it.id } }
        for (n in listOf(3, 5, 10, 20)) {
            val same = (1 until lists.size).map { i -> (0 until n).count { lists[i].getOrNull(it) == lists[i - 1].getOrNull(it) } }
            val sameSet = (1 until lists.size).map { i -> lists[i].take(n).count { it in lists[i - 1].take(n) } }
            println("top $n: same position as previous request per run=$same, same video (any position)=$sameSet")
        }
        val token = InnertubeParsers.continuationToken(pages.first())
        println("first page: ${lists.first().size} videos, continuation token present=${token != null}")
        var t = token
        var total = lists.first().size
        var n = 0
        val seen = lists.first().toMutableSet()
        while (t != null && n < 5) {
            val next = InnertubeClient(session, web).continuation(t, patience)
            val ids = InnertubeParsers.videos(next).map { it.id }
            println("continuation ${n + 1}: ${ids.size} videos, ${ids.count { it !in seen }} new vs everything before")
            seen += ids; total += ids.size; t = InnertubeParsers.continuationToken(next); n++
        }
        println("followed $n continuations, $total videos, more offered=${t != null}")
    }
}
