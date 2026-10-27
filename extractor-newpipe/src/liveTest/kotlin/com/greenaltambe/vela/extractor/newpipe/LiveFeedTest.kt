package com.greenaltambe.vela.extractor.newpipe

import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.parseChannelFeed
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.schabi.newpipe.extractor.NewPipe
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/** What task 09 added to the extractor, against the real YouTube; prints the numbers the report quotes. */
class LiveFeedTest {
    private val extractor = NewPipeExtractor()
    private val channels = listOf(
        "UCuAXFkgsw1L7xaCfnd5JJOw", // Rick Astley
        "UC_x5XG1OV2P6uZZ5FSM9Ttw", // Google for Developers
        "UCBJycsmduvYEL83R_U4JriQ", // MKBHD
        "UCsXVk37bltHxD1rDPwtNM8Q", // Kurzgesagt
        "UCHnyfMqiRRG1u-2MsSQLbXA", // Veritasium
        "UCXuqSBlHAE6Xw-yeJA0Tunw", // Linus Tech Tips
    )

    @Before
    fun requireNetwork() {
        val reachable = runCatching { Socket().use { it.connect(InetSocketAddress("www.youtube.com", 443), 5_000) } }.isSuccess
        assumeTrue("SKIPPED: www.youtube.com is unreachable", reachable)
    }

    private fun <T> ExtractorResult<T>.value(): T = when (this) {
        is ExtractorResult.Success -> value
        is ExtractorResult.Failure -> fail("${error.reason}: ${error.message}")
    }

    @Test
    fun trending() = runBlocking {
        val result = extractor.trending()
        println("LIVE trending: " + when (result) {
            is ExtractorResult.Success -> "${result.value.items.size} items, next=${result.value.continuation != null}; first=${result.value.items.firstOrNull()?.title}"
            is ExtractorResult.Failure -> "FAILED ${result.error.reason}: ${result.error.message}"
        })
    }

    @Test
    fun relatedVideos() = runBlocking {
        val related = extractor.related("dQw4w9WgXcQ").value()
        println("LIVE related: ${related.size} items; first=${related.firstOrNull()?.title}")
        assertTrue(related.isNotEmpty())
    }

    @Test
    fun channelShorts() = runBlocking {
        val r = extractor.channelShorts("UCBJycsmduvYEL83R_U4JriQ")
        println("LIVE shorts: " + when (r) {
            is ExtractorResult.Success -> "${r.value.items.size} items, all isShort=${r.value.items.all { it.isShort }}, duration known=${r.value.items.count { it.durationSeconds != null }}"
            is ExtractorResult.Failure -> "FAILED ${r.error.message}"
        })
    }

    @Test
    fun rssFeedWorksWithoutBotChecksAndHowFastItIs() = runBlocking {
        val perChannel = channels.map { id ->
            var count = 0; var dated = 0; var durations = 0; var shorts = 0
            val ms = measureTimeMillis {
                val response = NewPipe.getDownloader().get("https://www.youtube.com/feeds/videos.xml?channel_id=$id")
                println("LIVE rss $id: HTTP ${response.responseCode()}")
                if (response.responseCode() == 200) {
                    val items = parseChannelFeed(response.responseBody(), id)
                    count = items.size; dated = items.count { it.uploadedAt != null }; durations = items.count { it.durationSeconds != null }; shorts = items.count { it.isShort }
                }
            }
            println("LIVE rss $id: ${ms} ms, $count entries, $dated dated, $durations with duration, $shorts flagged short")
            count
        }
        assertTrue(perChannel.any { it > 0 }, "no channel returned a feed")

        // The same six channels through the service's path, concurrently.
        val total = measureTimeMillis {
            channels.map { async { extractor.recentUploads(it) } }.awaitAll().forEach { assertTrue(it is ExtractorResult.Success, "$it") }
        }
        println("LIVE recentUploads x${channels.size} concurrently: $total ms")
    }

    @Test
    fun channelTabFallbackStillGivesVideos() = runBlocking {
        val tab = extractor.channelVideos("UCuAXFkgsw1L7xaCfnd5JJOw").value()
        println("LIVE channel tab: ${tab.items.size} items, durations known=${tab.items.count { it.durationSeconds != null }}, dated=${tab.items.count { it.uploadedAt != null }}")
    }
}
