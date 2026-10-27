package com.greenaltambe.vela.player.mpv

import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.StreamSet
import com.greenaltambe.vela.extractor.newpipe.NewPipeExtractor
import com.greenaltambe.vela.player.PlaybackConstraints
import com.greenaltambe.vela.player.PlaybackPlan
import com.greenaltambe.vela.player.PlaybackPrefs
import com.greenaltambe.vela.player.PlayerEvent
import com.greenaltambe.vela.player.PlayerState
import com.greenaltambe.vela.player.StreamSelector
import com.greenaltambe.vela.player.VideoCodec
import com.sun.jna.Memory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.Assume.assumeTrue
import java.lang.management.ManagementFactory
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/** Big Buck Bunny, 4K60 upload: has 1080p60 VP9 and 720p60 VP9. */
const val BBB_60 = "aqz-KE-bpKQ"

/** Same film at 24 fps; the closest 1080p "30" the catalogue has without searching. */
const val BBB_24 = "YE7VzlLtp-4"

/** Skips (never fails) when libmpv or the network is missing. */
fun requireLiveMpv(): MpvBackend {
    val backend = MpvLibrary.load()
    assumeTrue("SKIPPED: libmpv not loadable: ${backend.exceptionOrNull()?.message}", backend.isSuccess)
    val reachable = runCatching { Socket().use { it.connect(InetSocketAddress("www.youtube.com", 443), 5_000) } }.isSuccess
    assumeTrue("SKIPPED: www.youtube.com is unreachable", reachable)
    return backend.getOrThrow()
}

fun streamsOf(videoId: String): StreamSet = runBlocking {
    when (val r = NewPipeExtractor().streams(videoId)) {
        is ExtractorResult.Success -> r.value
        is ExtractorResult.Failure -> error("${r.error.reason}: ${r.error.message}")
    }
}

/** What this desktop can decode with Fedora's ffmpeg-free: VP9 and AV1, no H.264. */
val softwareCodecs = PlaybackConstraints(supportedCodecs = setOf(VideoCodec.VP9, VideoCodec.AV1))

fun plan(streams: StreamSet, maxHeight: Int, fpsAtLeast: Int = 0): PlaybackPlan.Separate {
    // Pin the height and (if asked) the frame rate by filtering the set, then let the real selector choose.
    val filtered = streams.copy(videoStreams = streams.videoStreams.filter { it.fps >= fpsAtLeast && it.height <= maxHeight })
    return StreamSelector.plan(filtered, PlaybackPrefs(maxHeight = maxHeight), softwareCodecs) as PlaybackPlan.Separate
}

/** A sink that gives mpv scratch memory and counts what comes out. */
class CountingSink : FrameSink {
    val frames = AtomicLong()
    private var memory: Memory? = null
    private var size = 0 to 0
    override fun beginFrame(width: Int, height: Int): FrameTarget? {
        if (size != width to height || memory == null) { memory = Memory(width.toLong() * height * 4); size = width to height }
        return FrameTarget(com.sun.jna.Pointer.nativeValue(memory!!), width * 4)
    }
    override fun endFrame(target: FrameTarget) { frames.incrementAndGet() }
}

/** Process CPU time (JVM + in-process libmpv threads) as a percentage of one core over a window. */
class CpuMeter {
    private val os = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
    private var cpu0 = os.processCpuTime
    private var t0 = System.nanoTime()
    fun reset() { cpu0 = os.processCpuTime; t0 = System.nanoTime() }
    fun percentOfOneCore(): Double = (os.processCpuTime - cpu0).toDouble() / (System.nanoTime() - t0) * 100
}

fun MpvPlayerController.awaitState(timeoutMs: Long, predicate: (PlayerState) -> Boolean): PlayerState? {
    val end = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < end) {
        state.value.takeIf(predicate)?.let { return it }
        Thread.sleep(50)
    }
    return null
}

/** Collects events for later assertions. */
class EventCollector(controller: MpvPlayerController) {
    val events = ConcurrentLinkedQueue<PlayerEvent>()
    private val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).launchCollect(controller)
    private fun kotlinx.coroutines.CoroutineScope.launchCollect(c: MpvPlayerController) =
        launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { c.events.collect { events += it } }
    fun stop() = job.cancel()
}
