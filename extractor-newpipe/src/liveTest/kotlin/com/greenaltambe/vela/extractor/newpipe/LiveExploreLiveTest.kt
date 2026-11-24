package com.greenaltambe.vela.extractor.newpipe

import com.greenaltambe.vela.extractor.ExtractorResult
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.schabi.newpipe.extractor.ServiceList
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.test.Test

/** What can feed Explore's "Live" pill: the kiosks NewPipe has, and how many of the results of searches are really live streams. Prints counts only. */
class LiveExploreLiveTest {
    private val extractor = NewPipeExtractor()

    @Test fun measure() = runBlocking {
        assumeTrue("SKIPPED: www.youtube.com is unreachable", runCatching { Socket().use { it.connect(InetSocketAddress("www.youtube.com", 443), 5_000) } }.isSuccess)
        println("kiosks: " + ServiceList.YouTube.kioskList.availableKiosks + " default=" + ServiceList.YouTube.kioskList.defaultKioskId)
        for (q in listOf("Live", "live now", "live stream", "live 24/7")) {
            val r = extractor.search(q)
            if (r is ExtractorResult.Success) println("search '$q': ${r.value.items.size} results, live=${r.value.items.count { it.isLive }}, more=${r.value.continuation != null}")
            else println("search '$q': failed")
        }
        for (id in ServiceList.YouTube.kioskList.availableKiosks) {
            val started = System.currentTimeMillis()
            val r = runCatching {
                val k = ServiceList.YouTube.kioskList.getExtractorById(id, null).also { it.fetchPage() }
                val first = k.initialPage
                val items = first.items.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
                val live = items.count { it.streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM }
                val second = first.nextPage?.let { runCatching { k.getPage(it) }.getOrNull() }
                "${first.items.size} items, live=$live, duration known=${items.count { it.duration > 0 }}, page2=${second?.items?.size}, more after=${second?.nextPage != null}"
            }
            println("kiosk $id: ${r.getOrElse { "FAILED ${it.javaClass.simpleName}: ${it.message?.take(80)}" }} (${System.currentTimeMillis() - started} ms)")
        }
        for (id in listOf("live", "trending_gaming", "trending_podcasts_episodes")) {
            val r = extractor.kiosk(id)
            if (r is ExtractorResult.Success) {
                val more = r.value.continuation?.let { extractor.kiosk(id, it) }
                println("Vela kiosk('$id'): ${r.value.items.size} videos, live=${r.value.items.count { it.isLive }}, thumbnails=${r.value.items.count { it.thumbnailUrl != null }}, channel names=${r.value.items.count { it.channelName.isNotBlank() }}, page 2=${(more as? ExtractorResult.Success)?.value?.items?.size}")
            } else println("Vela kiosk('$id'): failed")
        }
        val t = extractor.trending()
        if (t is ExtractorResult.Success) println("trending: ${t.value.items.size} results, live=${t.value.items.count { it.isLive }}")
    }
}
