package com.greenaltambe.vela.net

import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.modules.createModules
import com.greenaltambe.vela.modules.dearrow.DeArrowSurface
import com.greenaltambe.vela.modules.ryd.VotesResult
import com.greenaltambe.vela.modules.sponsorblock.SegmentCategory
import com.greenaltambe.vela.modules.sponsorblock.SegmentsResult
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The three modules against the real servers. The well-known sponsored video is a recent one from a sponsor-heavy
 * channel (see resources/fixtures/modules/capture_fixtures.py); counts change over time, so only shapes are asserted.
 */
class LiveModulesTest {
    private val sponsored = "DN953UW8iJ8"
    private val settings = SettingsResolver(InMemorySettingsStore())
    private val modules = createModules(OkHttpTransport(), createDatabase(JvmDatabaseDriverFactory()), settings)

    @Before
    fun requireNetwork() {
        for (host in listOf("sponsor.ajay.app", "returnyoutubedislikeapi.com")) {
            val ok = runCatching { Socket().use { it.connect(InetSocketAddress(host, 443), 5_000) } }.isSuccess
            assumeTrue("SKIPPED: $host is unreachable", ok)
        }
    }

    @Test
    fun sponsorBlockSegmentsForARealSponsoredVideo() = runBlocking {
        var result: SegmentsResult? = null
        val ms = measureTimeMillis { result = modules.segments.get(sponsored, SegmentCategory.entries) }
        val loaded = assertIs<SegmentsResult.Loaded>(result)
        println("LIVE sponsorblock: ${loaded.segments.size} segments in $ms ms (${loaded.source}); categories=${loaded.segments.map { it.category }.distinct()}")
        assertTrue(loaded.segments.any { it.category == "sponsor" }, "expected a sponsor segment")
        // A second call is served from the store with no network at all.
        val again = assertIs<SegmentsResult.Loaded>(modules.segments.get(sponsored, SegmentCategory.entries, allowNetwork = false))
        assertTrue(again.segments == loaded.segments)
    }

    @Test
    fun returnYouTubeDislikeForARealVideo() = runBlocking {
        var result: VotesResult? = null
        val ms = measureTimeMillis { result = modules.ryd.votes(sponsored) }
        val known = assertIs<VotesResult.Known>(result)
        println("LIVE ryd: ${known.votes} in $ms ms")
        assertTrue(known.votes.likes > 0)
    }

    @Test
    fun deArrowBrandingForARealVideo() = runBlocking {
        val ms = measureTimeMillis { modules.dearrow.prefetch(listOf(sponsored, "dQw4w9WgXcQ")) }
        val branding = modules.dearrow.cached(sponsored)
        println("LIVE dearrow: $ms ms; sponsored title='${modules.dearrow.title(sponsored, "ORIGINAL", DeArrowSurface.FEED)}' thumbnail=${modules.dearrow.thumbnail(sponsored, DeArrowSurface.FEED)}")
        assertTrue(branding != null)
    }

    @Test
    fun theThumbnailServiceActuallyServesAnImage() = runBlocking {
        modules.dearrow.prefetch(listOf(sponsored))
        val url = modules.dearrow.thumbnail(sponsored, DeArrowSurface.FEED) ?: return@runBlocking println("LIVE dearrow thumbnail: none submitted, skipped")
        val response = OkHttpTransport().execute(com.greenaltambe.vela.core.http.HttpRequest(url))
        println("LIVE dearrow thumbnail: HTTP ${response.status}, ${response.body.length} chars")
        assertTrue(response.status in 200..299 || response.status == 202, "thumbnail service answered ${response.status}")
    }

    @Test
    fun anUnknownVideoIsHandledEverywhere() = runBlocking {
        val id = "zzzzzzzzzzz"
        assertIs<SegmentsResult.Loaded>(modules.segments.get(id, SegmentCategory.entries))
        println("LIVE unknown video ryd: ${modules.ryd.votes(id)}")
        modules.dearrow.prefetch(listOf(id))
        assertTrue(modules.dearrow.title(id, "x", DeArrowSurface.FEED) == "x")
    }
}
