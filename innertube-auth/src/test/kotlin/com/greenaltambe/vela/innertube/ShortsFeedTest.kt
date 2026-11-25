package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.*
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.extractor.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

/** The Shorts feed, anonymous and signed in, on hand-built answers (every id and token is made up). */
class ShortsFeedTest {
    private class FixedClock : Clock { override fun now() = Instant.fromEpochSeconds(1_700_000_000) }
    private class Sent(val url: String, val headers: Map<String, String>, val body: String) {
        val mask get() = headers["X-Goog-FieldMask"]
        val endpoint get() = url.substringAfter("/v1/").substringBefore('?')
    }
    private val sent = mutableListOf<Sent>()
    private var respond: (Sent) -> RawResponse = { ok(if (it.endpoint == "reel/reel_item_watch") start else page) }
    private val transport = FakeTransport { _, url, headers, body -> Sent(url, headers, body.orEmpty()).also { sent += it }.let(respond) }
    private fun ok(body: String) = RawResponse(200, body, emptyMap(), "")
    private fun code(c: Int) = RawResponse(c, "{}", emptyMap(), "")

    private fun reel(id: String, extra: String = "") =
        """{"command":{"reelWatchEndpoint":{"videoId":"$id","thumbnail":{"thumbnails":[{"url":"//img/small-$id"},{"url":"//img/$id"}]}$extra}}}"""
    private val start = """{"replacementEndpoint":{"reelWatchEndpoint":{"videoId":"first","thumbnail":{"thumbnails":[{"url":"https://img/first"}]}}},"sequenceContinuation":"seq-0"}"""
    private val page = """{"entries":[${reel("a")},${reel("ad", ""","adClientParams":{"isAd":true}""")},{"command":{"reelWatchEndpoint":{"adClientParams":{}}}},{"command":{}},${reel("b")},${reel("a")}],
        "continuationEndpoint":{"continuationCommand":{"token":"seq-1"}}}"""
    /** An answer that ignored the mask: everything, around the same few fields. */
    private val fullPage = """{"responseContext":{"x":1},"entries":[{"command":{"clickTrackingParams":"z","reelWatchEndpoint":{"videoId":"a","playerParams":"p",
        "unserializedPrefetchData":{"playerResponse":{"videoDetails":{"videoId":"other"}},"x":{"continuationCommand":{"token":"comments"}}},
        "thumbnail":{"thumbnails":[{"url":"https://img/a"}]}}},"trackingParams":"t"}],"continuationEndpoint":{"clickTrackingParams":"c","continuationCommand":{"token":"seq-full"}}}"""

    private fun alt(region: String = "IN") = AltClientExtractor(transport, { emptyList() }, contentRegion = { region })
    private fun <T> ExtractorResult<T>.value() = assertIs<ExtractorResult.Success<T>>(this).value
    private fun <T> ExtractorResult<T>.error() = assertIs<ExtractorResult.Failure>(this).error

    // ---- anonymous ----

    @Test fun aNewSequenceBeginsWithOneShortAndAStampedToken() = runBlocking<Unit> {
        val page = alt().shortsFeed().value()
        assertEquals(listOf("first"), page.items.map { it.id })
        assertTrue(page.items.single().isShort)
        assertEquals("https://img/first", page.items.single().thumbnailUrl)
        assertEquals("alt-shorts:seq-0", page.continuation)
        val request = sent.single()
        assertEquals("reel/reel_item_watch", request.endpoint)
        assertEquals(ShortsFeed.SEED_MASK, request.mask)
        assertTrue("\"gl\":\"IN\"" in request.body, "the chosen region")
        assertTrue("REEL_WATCH_INPUT_TYPE_SEEDLESS" in request.body)
        assertNull(request.headers.keys.firstOrNull { it.equals("Authorization", true) || it.equals("Cookie", true) }, "nothing about the viewer is sent")
    }

    @Test fun aPageDropsAdvertisementsAndRepeatsAndGoesOnFromItsOwnToken() = runBlocking<Unit> {
        val got = alt().shortsFeed(continuation = "alt-shorts:seq-0").value()
        assertEquals(listOf("a", "b"), got.items.map { it.id })
        assertEquals("https://img/a", got.items.first().thumbnailUrl)
        assertEquals("alt-shorts:seq-1", got.continuation)
        assertTrue("\"sequenceParams\":\"seq-0\"" in sent.single().body)
        assertEquals(ShortsFeed.PAGE_MASK, sent.single().mask)
    }

    @Test fun aListingsSeedIsSentAsItIs() = runBlocking<Unit> {
        alt().shortsFeed(seed = "from-a-row").value()
        assertTrue("\"sequenceParams\":\"from-a-row\"" in sent.single().body)
        assertEquals("reel/reel_watch_sequence", sent.single().endpoint)
    }

    @Test fun aFullAnswerReadsTheSame() = runBlocking<Unit> {
        respond = { ok(fullPage) }
        val got = alt().shortsFeed(seed = "s").value()
        assertEquals(listOf("a"), got.items.map { it.id })
        assertEquals("alt-shorts:seq-full", got.continuation, "the sequence's token, not one from inside an entry")
    }

    @Test fun aRefusedOrEmptyingMaskIsRetriedOnceWithoutIt() = runBlocking<Unit> {
        for (trimmed in listOf(code(400), ok("{}"))) {
            sent.clear()
            respond = { if (it.mask != null) trimmed else ok(fullPage) }
            assertEquals(listOf("a"), alt().shortsFeed(seed = "s").value().items.map { it.id })
            assertEquals(listOf(ShortsFeed.PAGE_MASK, null), sent.map { it.mask })
        }
    }

    @Test fun aRefusedTokenIsExpiredAndNothingIsTriedForever() = runBlocking<Unit> {
        respond = { code(400) }
        assertIs<ExtractorError.TokenExpired>(alt().shortsFeed(continuation = "alt-shorts:old").error())
        assertEquals(2, sent.size)
        assertIs<ExtractorError.Unplayable>(alt().shortsFeed().error())
    }

    @Test fun anotherSourcesTokenIsNeverSent() = runBlocking<Unit> {
        assertIs<ExtractorError.TokenNotMine>(alt().shortsFeed(continuation = "account-shorts:x").error())
        assertTrue(sent.isEmpty())
    }

    @Test fun failuresBecomeOurErrors() = runBlocking<Unit> {
        suspend fun error(r: RawResponse): ExtractorError { respond = { r }; return alt().shortsFeed().error() }
        assertIs<ExtractorError.RateLimited>(error(code(429)))
        assertIs<ExtractorError.Blocked>(error(code(403)))
        assertIs<ExtractorError.TierUnavailable>(error(code(503)))
        assertIs<ExtractorError.ParseFailure>(error(ok("<html>")))
        assertIs<ExtractorError.ParseFailure>(error(ok("{}")), "a start that names no Short is a failure")
        respond = { throw java.io.IOException("no network") }
        assertIs<ExtractorError.Network>(alt().shortsFeed().error())
    }

    @Test fun aSequencePageWithNothingAndNoTokenIsTheEnd() = runBlocking<Unit> {
        respond = { ok("""{"entries":[]}""") }
        val got = alt().shortsFeed(seed = "s").value()
        assertTrue(got.items.isEmpty()); assertNull(got.continuation)
    }

    // ---- signed in ----

    private val accounts = AccountManager(createDatabase(JvmDatabaseDriverFactory()), CredentialStore(InMemorySecretStore()))
    private val registry = GovernorRegistry(FixedClock(), { GovernorLimits(minSpacingMs = 0, browsePerMinute = 100) })
    private val notes = mutableListOf<String>()
    private fun signedIn(): InnertubeAccountReads {
        accounts.addGoogle(GoogleIdentity("T", "t@example.com", null, "UCx"), CookieJar(listOf(Cookie("SID", "s", ".google.com"), Cookie("SAPISID", "AbCdEfGhIjKlMnOp/12345", ".youtube.com"))))
        return InnertubeAccountReads(accounts, registry, transport, WebClient("WEB", "2.0", "en", "US", "UA"), FixedClock(), onFailure = { notes += it }, region = { "IN" })
    }

    @Test fun theAccountsFeedIsAskedWithItsCookiesTheMaskAndTheRegion() = runBlocking<Unit> {
        val reads = signedIn()
        val first = reads.shorts()!!
        assertEquals("account-shorts:seq-0", first.continuation)
        val next = reads.shorts(continuation = first.continuation)!!
        assertEquals(listOf("a", "b"), next.items.map { it.id })
        assertEquals(listOf(ShortsFeed.SEED_MASK, ShortsFeed.PAGE_MASK), sent.map { it.mask })
        assertTrue(sent.all { r -> r.headers.keys.any { it.equals("Authorization", true) } && "\"gl\":\"IN\"" in r.body })
    }

    @Test fun theAccountsFeedRetriesWithoutARefusedMaskAndGivesNullWhenItFails() = runBlocking<Unit> {
        val reads = signedIn()
        respond = { if (it.mask != null) code(400) else ok(fullPage) }
        assertEquals(listOf("a"), reads.shorts(seed = "s")!!.items.map { it.id })
        respond = { code(500) }
        assertNull(reads.shorts(seed = "s"))
        respond = { ok("{}") }
        assertNull(reads.shorts(), "a start that names no Short is a failed read")
        assertTrue(notes.isNotEmpty())
    }

    @Test fun theAccountNeverTakesAnAnonymousToken() = runBlocking<Unit> {
        val reads = signedIn()
        assertNull(reads.shorts(continuation = "alt-shorts:x"))
        assertTrue(sent.isEmpty())
    }

    @Test fun signedOutThereIsNoAccountFeed() = runBlocking<Unit> {
        assertNull(InnertubeAccountReads(accounts, registry, transport, WebClient("WEB", "2.0", "en", "US", "UA"), FixedClock()).shorts())
        assertTrue(sent.isEmpty())
    }
}
