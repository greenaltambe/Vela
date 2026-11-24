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

    @Test fun theSignedOutPageIsNotAFeed() = runBlocking<Unit> {
        signedOut = true
        assertNull(reads.home())
    }
}
