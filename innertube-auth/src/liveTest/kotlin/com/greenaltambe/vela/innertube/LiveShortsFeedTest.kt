package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.*
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.Page
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.shorts.ShortsFeed
import com.greenaltambe.vela.shorts.ShortsOrigin
import com.greenaltambe.vela.shorts.ShortsRead
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The app's own Shorts reads against YouTube (read-only, nothing is reported as watched): a new sequence and three pages, anonymous and signed in,
 * and a sequence started from a Home Short. Records each request's answer size and whether the field mask was enough. Prints counts only.
 */
class LiveShortsFeedTest {
    private class Measuring : AuthTransport {
        private val real = OkHttpAuthTransport()
        val answers = mutableListOf<String>()
        override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): RawResponse =
            real.execute(method, url, headers, body).also {
                answers += "${url.substringAfter("/v1/").substringBefore('?')} ${if ("X-Goog-FieldMask" in headers) "masked" else "full"} HTTP ${it.code} ${it.body.length} chars"
            }
    }

    private suspend fun run(name: String, transport: Measuring, read: suspend (seed: String?, continuation: String?) -> Page<VideoSummary>) {
        val seen = LinkedHashSet<String>()
        var page = read(null, null)
        println("$name start: ${page.items.size} Short, token ${page.continuation != null}")
        page.items.forEach { seen.add(it.id) }
        var repeats = 0
        for (n in 1..3) {
            page = read(null, checkNotNull(page.continuation) { "$name: no token before page $n" })
            repeats += page.items.count { !seen.add(it.id) }
            println("$name page $n: ${page.items.size} Shorts, with picture ${page.items.count { it.thumbnailUrl != null }}, token ${page.continuation != null}")
            assertTrue(page.items.isNotEmpty(), "$name: an empty page")
        }
        println("$name: ${seen.size} distinct, $repeats repeats")
        transport.answers.forEach { println("$name answer: $it") }
        assertEquals(emptyList(), transport.answers.filter { " full " in it }, "$name: the field mask was not enough somewhere")
    }

    @Test fun anonymous() = runBlocking<Unit> {
        val transport = Measuring()
        val alt = AltClientExtractor(transport, { emptyList() }, contentRegion = { "IN" })
        run("anonymous", transport) { seed, continuation ->
            when (val r = alt.shortsFeed(seed, continuation)) { is ExtractorResult.Success -> r.value; is ExtractorResult.Failure -> error(r.error.message) }
        }
    }

    /** The page's own supply over the real sources: the account first; then with the account failing, which must end on the anonymous feed. */
    @Test fun theSupplyOverTheRealSources() = runBlocking<Unit> {
        val accounts = AccountManager(createDatabase(JvmDatabaseDriverFactory()), CredentialStore(InMemorySecretStore()))
        accounts.addGoogle(GoogleIdentity("live", "live@example.com", null, "UClive"), LiveCookies.jarOrSkip())
        val reads = InnertubeAccountReads(accounts, GovernorRegistry(kotlin.time.Clock.System, { GovernorLimits(browsePerMinute = 20) }), region = { "IN" })
        val alt = AltClientExtractor(profiles = { emptyList() }, contentRegion = { "IN" })
        val anonymous = ShortsOrigin.ANONYMOUS to ShortsRead { s, c -> (alt.shortsFeed(s, c) as? ExtractorResult.Success)?.value }
        for ((name, account) in listOf("account working" to ShortsRead { s, c -> reads.shorts(s, c) }, "account failing" to ShortsRead { _, _ -> null })) {
            val switches = mutableListOf<String>()
            val feed = ShortsFeed(listOf(ShortsOrigin.ACCOUNT to account, anonymous), { emptyList() }) { from, to, why -> switches += "$from $why -> $to" }
            val all = ArrayList<String>(); val sizes = ArrayList<Int>()
            repeat(6) { val batch = feed.next(); sizes += batch.size; all += batch.map { it.id } }
            println("supply, $name: batches $sizes, ${all.size} Shorts, ${all.distinct().size} distinct, from ${feed.origin}, switches $switches")
            assertEquals(all.size, all.distinct().size, "$name: a Short twice")
            assertTrue(sizes.all { it > 0 }, "$name: an empty batch")
        }
    }

    @Test fun signedInAndFromAHomeShort() = runBlocking<Unit> {
        val transport = Measuring()
        val accounts = AccountManager(createDatabase(JvmDatabaseDriverFactory()), CredentialStore(InMemorySecretStore()))
        accounts.addGoogle(GoogleIdentity("live", "live@example.com", null, "UClive"), LiveCookies.jarOrSkip())
        val notes = mutableListOf<String>()
        val reads = InnertubeAccountReads(accounts, GovernorRegistry(kotlin.time.Clock.System, { GovernorLimits(browsePerMinute = 20) }), transport, onFailure = { notes += it }, region = { "IN" })
        run("signed in", transport) { seed, continuation -> checkNotNull(reads.shorts(seed, continuation)) { "failed: $notes" } }
        val short = checkNotNull(reads.home()) { "no Home: $notes" }.groups.flatMap { it.videos }.firstOrNull { it.isShort && it.shortsSequence != null }
        if (short == null) { println("no Home Short this time"); return@runBlocking }
        transport.answers.clear()
        val next = checkNotNull(reads.shorts(seed = short.shortsSequence)) { "seeded failed: $notes" }
        println("from a Home Short: ${next.items.size} Shorts, itself among them ${next.items.any { it.id == short.id }}, token ${next.continuation != null}")
        transport.answers.forEach { println("seeded answer: $it") }
        assertTrue(next.items.isNotEmpty())
    }
}
