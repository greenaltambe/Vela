package com.greenaltambe.vela.extractor.newpipe

import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.StreamSet
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Before
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Clock

/** Hits the real YouTube. Rick Astley's "Never Gonna Give You Up" and his official channel are about as stable as it gets. */
class LiveNewPipeTest {
    private val videoId = "dQw4w9WgXcQ"
    private val channelId = "UCuAXFkgsw1L7xaCfnd5JJOw"
    private val extractor = NewPipeExtractor()

    @Before
    fun requireNetwork() {
        val reachable = runCatching { Socket().use { it.connect(InetSocketAddress("www.youtube.com", 443), 5_000) } }.isSuccess
        assumeTrue("SKIPPED: www.youtube.com is unreachable, so the live tests cannot run", reachable)
    }

    private fun <T> ExtractorResult<T>.value(): T = when (this) {
        is ExtractorResult.Success -> value
        is ExtractorResult.Failure -> fail("${error.reason}: ${error.message} (cause: ${error.cause})")
    }

    @Test
    fun search() = runBlocking {
        val results = extractor.search("rick astley never gonna give you up").value().items
        assertTrue(results.isNotEmpty(), "no results")
        results.forEach { assertTrue(it.id.isNotBlank() && it.title.isNotBlank(), "incomplete result $it") }
        println("search: ${results.size} results, first = ${results.first()}")
    }

    @Test
    fun searchPaging() = runBlocking {
        val first = extractor.search("rick astley").value()
        val token = assertNotNull(first.continuation, "first page has no continuation")
        val second = extractor.search("rick astley", token).value()
        println("searchPaging: page1=${first.items.size}, page2=${second.items.size}, more=${second.continuation != null}")
        assertTrue(second.items.isNotEmpty(), "second page empty")
        assertTrue(second.items.map { it.id }.toSet().intersect(first.items.map { it.id }.toSet()).size < first.items.size, "page 2 repeats page 1")
    }

    @Test
    fun channelVideosPaging() = runBlocking {
        val first = extractor.channelVideos(channelId).value()
        val second = extractor.channelVideos(channelId, assertNotNull(first.continuation)).value()
        println("channelVideos: page1=${first.items.size}, page2=${second.items.size}")
        assertTrue(first.items.isNotEmpty() && second.items.isNotEmpty())
        assertTrue(first.items.map { it.id }.intersect(second.items.map { it.id }.toSet()).isEmpty(), "pages overlap")
    }

    @Test
    fun commentsPagingAndReplies() = runBlocking {
        val first = extractor.comments(videoId).value()
        val second = extractor.comments(videoId, continuation = assertNotNull(first.continuation)).value()
        println("comments paging: page1=${first.comments.size}, page2=${second.comments.size}")
        assertTrue(second.comments.isNotEmpty(), "second comments page empty")

        val withReplies = (first.comments + second.comments).first { it.replyCount > 0 && it.repliesContinuation != null }
        val viaToken = extractor.replies(videoId, withReplies.id, withReplies.repliesContinuation).value()
        println("replies (token) to ${withReplies.id}: ${viaToken.comments.size}, first=${viaToken.comments.firstOrNull()?.text?.take(60)}")
        assertTrue(viaToken.comments.isNotEmpty(), "no replies via token")

        val onFirstPage = first.comments.firstOrNull { it.replyCount > 0 && it.repliesContinuation != null }
        if (onFirstPage != null) {
            val viaLookup = extractor.replies(videoId, onFirstPage.id).value()
            println("replies (lookup) to ${onFirstPage.id}: ${viaLookup.comments.size}")
            assertTrue(viaLookup.comments.isNotEmpty(), "no replies via lookup")
        }
    }

    @Test
    fun foreignTokenFailsCleanly() = runBlocking {
        val result = extractor.search("rick astley", "ytdlp:21")
        println("foreign token: $result".take(200))
        assertTrue(result is ExtractorResult.Failure && result.error.message == "continuation from a different tier")
    }

    @Test
    fun videoInfo() = runBlocking {
        val info = extractor.videoInfo(videoId).value()
        println("videoInfo: $info".take(400))
        assertEquals(videoId, info.id)
        assertTrue("Never Gonna Give You Up" in info.title, info.title)
        assertEquals(channelId, info.channelId)
        assertTrue(info.durationSeconds in 200..230, "duration ${info.durationSeconds}")
        assertNotNull(info.uploadDate, "uploadDate")
        assertTrue(!info.ageRestricted && !info.isLive && !info.isShort)
    }


    @Test
    fun realStreamsAreBestFirstAndPlanAsSeparateNonDrc() = runBlocking {
        val set = extractor.streams(videoId).value()
        assertEquals(set.videoStreams.map { it.height }, set.videoStreams.map { it.height }.sortedDescending(), "video best-first by height")
        val plan = com.greenaltambe.vela.player.StreamSelector.plan(set, com.greenaltambe.vela.player.PlaybackPrefs())
        println("plan: $plan".take(300))
        val separate = assertIs<com.greenaltambe.vela.player.PlaybackPlan.Separate>(plan)
        assertTrue(!separate.audio.isDrc, "DRC avoided")
        assertTrue(separate.video.height >= set.videoStreams.filter { !it.hasAudio }.maxOf { it.height } / 2)
    }

    @Test
    fun streams() = runBlocking {
        val set = extractor.streams(videoId).value()
        println("streams: ${set.videoStreams.size} video, ${set.audioStreams.size} audio, hls=${set.hlsUrl != null}, expires=${set.expiresAt}")
        set.videoStreams.take(3).forEach { println("  video $it".take(200)) }
        set.audioStreams.take(3).forEach { println("  audio $it".take(200)) }
        assertEquals("newpipe", set.client)
        assertTrue(set.videoStreams.isNotEmpty(), "no video streams")
        assertTrue(set.audioStreams.isNotEmpty(), "no audio streams")
        assertTrue((set.videoStreams.map { it.url } + set.audioStreams.map { it.url }).all { it.startsWith("https://") })
        val expires = assertNotNull(set.expiresAt, "expiresAt should come from the expire= parameter")
        assertTrue(expires > Clock.System.now(), "streams already expired: $expires")
    }

    @Test
    fun aResolvedAudioStreamIsActuallyFetchable() = runBlocking {
        val set: StreamSet = extractor.streams(videoId).value()
        val audio = set.audioStreams.first()
        val request = Request.Builder().url(audio.url).header("Range", "bytes=0-1023").apply {
            (set.headers + audio.headers).forEach { (k, v) -> header(k, v) }
        }.build()
        OkHttpClient().newCall(request).execute().use { response ->
            println("audio fetch: HTTP ${response.code}, ${response.header("Content-Type")}, client=${Regex("[?&]c=([^&]+)").find(audio.url)?.groupValues?.get(1)}")
            assertTrue(response.code in listOf(200, 206), "HTTP ${response.code}")
        }
    }

    @Test
    fun channel() = runBlocking {
        val page = extractor.channel(channelId).value()
        println("channel: ${page.name}, subs=${page.subscriberCount}, ${page.videos.size} videos, continuation=${page.continuation != null}")
        assertTrue("Rick Astley" in page.name, page.name)
        assertTrue(page.videos.isNotEmpty(), "no videos")
        assertTrue(page.avatarUrl != null)
    }

    @Test
    fun comments() = runBlocking {
        val page = extractor.comments(videoId).value()
        println("comments: ${page.comments.size}, continuation=${page.continuation != null}, first=${page.comments.firstOrNull()}".take(300))
        assertTrue(page.comments.isNotEmpty(), "no comments")
        assertTrue(page.comments.all { it.text.isNotBlank() })
    }

    @Test
    fun unknownVideoFailsCleanly() = runBlocking {
        val result = extractor.videoInfo("aaaaaaaaaaa")
        (result as? ExtractorResult.Failure)?.error?.let { e ->
            println("unknown video: ${e.reason} \"${e.message}\" cause=${e.cause}")
        }
        assertTrue(result is ExtractorResult.Failure, "expected a failure")
    }
}
