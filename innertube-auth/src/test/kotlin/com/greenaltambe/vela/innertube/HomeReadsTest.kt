package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.*
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.createDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

/** The Home read through the real reads class and a fake transport (hand-built bodies; labels, ids and tokens are made up). */
class HomeReadsTest {
    private class FixedClock : Clock { override fun now() = Instant.fromEpochSeconds(1_700_000_000) }
    private val bodies = mutableListOf<String>()
    private var signedOut = false
    private var grouped = false

    private fun video(id: String) = """{"richItemRenderer":{"content":{"videoRenderer":{"videoId":"$id","title":{"simpleText":"T $id"}}}}}"""
    private val firstPage = """{"contents":{"richGridRenderer":{
        "header":{"feedFilterChipBarRenderer":{"contents":[
          {"chipCloudChipRenderer":{"text":{"runs":[{"text":"All"}]},"isSelected":true}},
          {"chipCloudChipRenderer":{"text":{"runs":[{"text":"Topic A"}]},"navigationEndpoint":{"continuationCommand":{"token":"chip-a"}}}}]}},
        "contents":[${video("a1")},${video("a2")},{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"next-1"}}}}]}}}"""
    private val nextPage = """{"onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[${video("a3")},{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"next-2"}}}}]}}]}"""
    private val chipPage = """{"onResponseReceivedActions":[{"reloadContinuationItemsCommand":{"continuationItems":[${video("c1")},{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"chip-next"}}}}]}}]}"""

    private val transport = FakeTransport { _, _, _, body ->
        bodies += body.orEmpty()
        val b = body.orEmpty()
        RawResponse(200, when {
            signedOut -> """{"contents":{"x":{"signInEndpoint":{}}}}"""
            "\"token\"" in b || "\"continuation\"" in b -> if ("chip-a" in b) chipPage else nextPage
            grouped -> """{"contents":{"richGridRenderer":{"contents":[${video("a1")},
                {"richSectionRenderer":{"content":{"richShelfRenderer":{"title":{"simpleText":"A row"},"contents":[${video("g1")}]}}}},{"surpriseRenderer":{}}]}}}"""
            else -> firstPage
        }, emptyMap(), "")
    }
    private val db = createDatabase(JvmDatabaseDriverFactory())
    private val accounts = AccountManager(db, CredentialStore(InMemorySecretStore()))
    init { accounts.addGoogle(GoogleIdentity("T", "t@example.com", null, "UCx"), CookieJar(listOf(Cookie("SID", "s", ".google.com"), Cookie("SAPISID", "AbCdEfGhIjKlMnOp/12345", ".youtube.com")))) }
    private val registry = GovernorRegistry(FixedClock(), { GovernorLimits(minSpacingMs = 0, browsePerMinute = 100, writesPerHour = 100) })
    private val reads = InnertubeAccountReads(accounts, registry, transport, WebClient("WEB", "2.0", "en", "US", "UA"), FixedClock())

    @Test fun theFirstPageHasVideosItsPagingTokenAndTheChips() = runBlocking<Unit> {
        val page = reads.home()!!
        assertEquals(listOf("a1", "a2"), page.videos.map { it.id })
        assertEquals("next-1", page.continuation)
        assertEquals(listOf("All", "Topic A"), page.chips.map { it.label })
    }

    @Test fun theNextPageUsesTheContinuationAndCarriesNoChips() = runBlocking<Unit> {
        val page = reads.home(continuation = "next-1")!!
        assertEquals(listOf("a3"), page.videos.map { it.id })
        assertEquals("next-2", page.continuation)
        assertTrue(page.chips.isEmpty())
        assertTrue(bodies.last().contains("next-1") && !bodies.last().contains("FEwhat_to_watch"))
    }

    @Test fun aChipLoadsItsOwnFeedAndItsOwnPaging() = runBlocking<Unit> {
        val page = reads.home(chip = "chip-a")!!
        assertEquals(listOf("c1"), page.videos.map { it.id })
        assertEquals("chip-next", page.continuation, "the chip's own next page, not another chip's token")
        assertTrue(page.chips.isEmpty())
    }

    private var region = "IN"
    private val localized = InnertubeAccountReads(accounts, registry, transport, WebClient("WEB", "2.0", "en", "US", "UA"), FixedClock(), region = { region })
    private fun asked(body: String) = Regex("\"gl\":\"([^\"]*)\"").find(body)!!.groupValues[1]

    @Test fun everyHomeRequestAsksForTheChosenRegionAsItIsAtThatMoment() = runBlocking<Unit> {
        localized.home(); localized.home(continuation = "next-1"); localized.home(chip = "chip-a"); localized.recommended()
        assertEquals(listOf("IN", "IN", "IN", "IN"), bodies.map(::asked))
        region = "JP"; localized.home()
        assertEquals("JP", asked(bodies.last()))
        region = " "; localized.home()
        assertEquals("US", asked(bodies.last()), "a blank region means US")
    }

    @Test fun theAccountsOwnListsAreNotAskedForARegion() = runBlocking<Unit> {
        localized.history(); localized.historyPage(null)
        assertEquals(listOf("US", "US"), bodies.map(::asked))
    }

    @Test fun withNoRegionGivenTheClientKeepsItsOwn() = runBlocking<Unit> {
        reads.home()
        assertEquals("US", asked(bodies.last()))
    }

    @Test fun groupsComeApartFromTheFeedAndAnOddResponseIsNoted() = runBlocking<Unit> {
        grouped = true
        val notes = mutableListOf<String>()
        val noting = InnertubeAccountReads(accounts, registry, transport, WebClient("WEB", "2.0", "en", "US", "UA"), FixedClock(), onNote = { notes += it })
        val page = noting.home()!!
        assertEquals(listOf("a1"), page.videos.map { it.id })
        assertEquals(listOf("A row" to listOf("g1")), page.groups.map { g -> g.title to g.videos.map { it.id } })
        assertEquals(listOf("a1"), noting.recommended()!!.map { it.id }, "the plain list has no group videos either")
        assertEquals(2, notes.size, "the unknown item is noted for each read: $notes")
        assertTrue("surpriseRenderer" in notes.first())
    }

    @Test fun theSignedOutPageIsNotAFeed() = runBlocking<Unit> {
        signedOut = true
        assertNull(reads.home())
    }
}
