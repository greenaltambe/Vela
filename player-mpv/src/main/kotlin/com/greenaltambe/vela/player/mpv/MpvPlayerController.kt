package com.greenaltambe.vela.player.mpv

import com.greenaltambe.vela.player.PlaybackError
import com.greenaltambe.vela.player.PlaybackErrorKind
import com.greenaltambe.vela.player.PlaybackMetadata
import com.greenaltambe.vela.player.PlaybackPlan
import com.greenaltambe.vela.player.PlaybackStats
import com.greenaltambe.vela.player.PlayerController
import com.greenaltambe.vela.player.PlayerEvent
import com.greenaltambe.vela.player.PlayerState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [PlayerController] over libmpv with the software render API. Safe to call from any thread: commands run on one
 * control thread (so the UI thread never blocks on mpv) and mpv's events on another.
 *
 * - **State** is derived from mpv's observed properties by [MpvStateMapper]; position is published at most every 250 ms.
 * - **Errors**: from the chunking proxy (exact upstream status, immediately), and from mpv's `end-file` error with the
 *   recent log lines (non-proxied URLs). At most one error per [load].
 * - **Shutdown** ([release]) is idempotent and orders itself: stop commands, join threads, render context, core, proxy.
 *
 * @param runEventLoop false in tests, which feed events through [dispatch].
 */
class MpvPlayerController(
    backend: MpvBackend,
    private val config: MpvConfig = MpvConfig(),
    private val control: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "vela-mpv-control").apply { isDaemon = true } },
    runEventLoop: Boolean = true,
    private val proxyFactory: ((UpstreamFailureSink) -> ChunkingProxy)? = { sink -> ChunkingProxy(config.chunkSizeBytes) { sink.onFailure(it) } },
) : PlayerController {
    /** Lets the proxy report into the controller without a constructor cycle. */
    fun interface UpstreamFailureSink { fun onFailure(failure: UpstreamFailure) }

    private val client: MpvClient = backend.create()
    private val proxy: ChunkingProxy? = if (config.chunkedRanges) proxyFactory?.invoke { onUpstreamFailure(it) } else null
    private var renderLoop: SoftwareRenderLoop? = null

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state

    private val _stats = MutableStateFlow(PlaybackStats())
    val stats: StateFlow<PlaybackStats> = _stats

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 8)
    override val events = _events.asSharedFlow()

    private val _volume = MutableStateFlow(100)
    val volume: StateFlow<Int> = _volume

    private val released = AtomicBoolean(false)
    private val lock = Any()

    // ---- guarded by lock ----
    private var snapshot = MpvSnapshot()
    private var loadGeneration = 0
    private var errorEmitted = false
    private var recentLog = ArrayDeque<String>()
    private var upstreamStatus: Int? = null

    private var eventThread: Thread? = null
    private var statsTimer: ScheduledExecutorService? = null

    init {
        try {
            for ((k, v) in MpvOptions.initOptions(config)) client.setOption(k, v)
            client.initialize()
            client.requestLog("warn")
            client.observe("time-pos", MpvFormat.Double)
            client.observe("duration", MpvFormat.Double)
            client.observe("pause", MpvFormat.Flag)
            client.observe("paused-for-cache", MpvFormat.Flag)
            client.observe("core-idle", MpvFormat.Flag)
            client.observe("eof-reached", MpvFormat.Flag)
            client.observe("speed", MpvFormat.Double)
            renderLoop = SoftwareRenderLoop(client)
        } catch (t: Throwable) {
            runCatching { client.close() }
            proxy?.close()
            throw t
        }
        if (runEventLoop) {
            eventThread = Thread(::eventLoop, "vela-mpv-events").apply { isDaemon = true; start() }
            statsTimer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "vela-mpv-stats").apply { isDaemon = true } }
                .also { it.scheduleWithFixedDelay({ runCatching { refreshStats() } }, 1, 1, TimeUnit.SECONDS) }
        }
    }

    // ---- video surface -------------------------------------------------------------------------------------------

    fun attachSink(sink: FrameSink) { renderLoop?.setSink(sink) }
    /** Returns only once no frame is being written into [sink], so the caller may free its memory right after. */
    fun detachSink(sink: FrameSink) { renderLoop?.detachSink(sink) }
    fun setSurfaceSize(width: Int, height: Int) { renderLoop?.setSize(width, height) }

    val framesRendered: Long get() = renderLoop?.framesRendered?.get() ?: 0
    val framesSkipped: Long get() = renderLoop?.framesSkipped?.get() ?: 0
    val averageRenderMs: Double get() = renderLoop?.averageRenderMs ?: 0.0

    /** The proxy's counters, for measurements; null when chunking is off. */
    val proxyBytesServed: Long get() = proxy?.bytesServed?.get() ?: 0

    /** mpv's own report of what it opened, e.g. `pipewire`. Null until the first file has started. */
    fun property(name: String): String? = client.getProperty(name)

    // ---- PlayerController ----------------------------------------------------------------------------------------

    override fun load(plan: PlaybackPlan, startPositionMs: Long, playWhenReady: Boolean, metadata: PlaybackMetadata) {
        val generation = synchronized(lock) {
            loadGeneration += 1
            errorEmitted = false
            recentLog.clear()
            upstreamStatus = null
            snapshot = snapshot.copy(
                loadRequested = true, failed = false, eofReached = false, pause = !playWhenReady, pausedForCache = false,
                coreIdle = true, timePosSeconds = null, durationSeconds = null,
                positionOverrideMs = startPositionMs,
            )
            publishLocked(force = true)
            loadGeneration
        }
        submit {
            if (generation != currentGeneration()) return@submit
            proxy?.reset()
            val load = MpvOptions.build(
                plan, startPositionMs, playWhenReady, metadata,
                route = { role, url, headers -> proxy?.takeIf { plan !is PlaybackPlan.Hls && plan !is PlaybackPlan.Local }?.register(role, url, headers) ?: url },
                chunked = proxy != null,
            )
            if (load == null) {
                fail(generation, PlaybackError(PlaybackErrorKind.Other, (plan as? PlaybackPlan.NothingPlayable)?.reason ?: "nothing playable"))
                return@submit
            }
            applyHeaders(load)
            client.command(*load.loadfileArgs())
            // `speed` survives loadfile, `pause` is per file via the option above.
        }
    }

    private fun applyHeaders(load: MpvLoad) {
        client.setProperty("user-agent", load.userAgent ?: "")
        client.command("change-list", "http-header-fields", "clr", "")
        for (field in load.headerFields) client.command("change-list", "http-header-fields", "append", field)
    }

    override fun play() = submit {
        synchronized(lock) { snapshot = snapshot.copy(pause = false) }
        // At the end of a keep-open file, un-pausing does nothing: start over, as the Android player does.
        if (synchronized(lock) { snapshot.eofReached }) client.command("seek", "0", "absolute")
        client.setProperty("pause", "no")
    }

    override fun pause() = submit {
        client.setProperty("pause", "yes")
    }

    override fun seekTo(positionMs: Long) {
        synchronized(lock) {
            snapshot = snapshot.copy(positionOverrideMs = positionMs, eofReached = false)
            publishLocked(force = true)
        }
        submit { client.command("seek", "%.3f".format(java.util.Locale.ROOT, positionMs / 1000.0), "absolute") }
    }

    override fun setSpeed(speed: Float) {
        synchronized(lock) { snapshot = snapshot.copy(speed = speed.toDouble()); publishLocked(force = true) }
        submit { client.setProperty("speed", speed.toString()) }
    }

    override fun setVolume(volume: Float) = setVolume((volume * 100).toInt())

    fun setVolume(percent: Int) {
        val v = percent.coerceIn(0, 130)
        _volume.value = v
        submit { client.setProperty("volume", v.toString()) }
    }

    override fun stop() {
        synchronized(lock) {
            loadGeneration += 1
            val speed = snapshot.speed
            snapshot = MpvSnapshot(speed = speed)
            _state.value = MpvStateMapper.map(snapshot)
            _stats.value = PlaybackStats()
        }
        submit { client.command("stop"); proxy?.reset() }
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        statsTimer?.shutdownNow()
        // Queued commands may still be useful (a final `stop`); give them a moment, then force.
        runCatching { control.submit { runCatching { client.command("stop") } }.get(1, TimeUnit.SECONDS) }
        control.shutdown()
        if (!control.awaitTermination(2, TimeUnit.SECONDS)) control.shutdownNow()
        renderLoop?.close()
        client.close() // wakes the event loop, frees the render context (already done), destroys the core
        eventThread?.join(2000)
        proxy?.close()
        synchronized(lock) { _state.value = PlayerState(speed = snapshot.speed.toFloat()) }
    }

    // ---- events --------------------------------------------------------------------------------------------------

    private fun eventLoop() {
        while (!released.get()) {
            val event = try { client.waitEvent(0.25) } catch (e: MpvClosedException) { return }
            if (event == MpvEvent.Shutdown) return
            dispatch(event)
        }
    }

    /** Applies one mpv event. Internal so tests can drive the controller with scripted events. */
    internal fun dispatch(event: MpvEvent) {
        when (event) {
            is MpvEvent.Property -> synchronized(lock) {
                snapshot = when (event.name) {
                    "time-pos" -> snapshot.copy(timePosSeconds = event.value as? Double)
                    "duration" -> snapshot.copy(durationSeconds = event.value as? Double)
                    "pause" -> snapshot.copy(pause = event.value as? Boolean ?: snapshot.pause)
                    "paused-for-cache" -> snapshot.copy(pausedForCache = event.value as? Boolean ?: false)
                    "core-idle" -> snapshot.copy(coreIdle = event.value as? Boolean ?: true)
                    "eof-reached" -> snapshot.copy(eofReached = event.value as? Boolean ?: false)
                    "speed" -> snapshot.copy(speed = event.value as? Double ?: snapshot.speed)
                    else -> snapshot
                }
                publishLocked()
            }
            MpvEvent.PlaybackRestart -> synchronized(lock) {
                snapshot = snapshot.copy(positionOverrideMs = null)
                publishLocked(force = true)
            }
            is MpvEvent.Log -> synchronized(lock) {
                if (event.level in setOf("error", "fatal", "warn")) {
                    recentLog.addLast("${event.prefix}: ${event.text}")
                    while (recentLog.size > 12) recentLog.removeFirst()
                }
            }
            is MpvEvent.EndFile -> onEndFile(event)
            else -> Unit
        }
    }

    private fun onEndFile(event: MpvEvent.EndFile) {
        if (event.reason != EndReason.Error) return // Eof is shown through eof-reached (keep-open); Stop/Redirect are ours
        val (generation, error) = synchronized(lock) {
            loadGeneration to mpvPlaybackError(event.errorCode, errorName(event.errorCode), recentLog.toList(), upstreamStatus)
        }
        fail(generation, error)
    }

    private fun errorName(code: Int) = when (code) {
        MpvErrorCode.LOADING_FAILED -> "loading failed"
        MpvErrorCode.AO_INIT_FAILED -> "audio output init failed"
        MpvErrorCode.VO_INIT_FAILED -> "video output init failed"
        MpvErrorCode.NOTHING_TO_PLAY -> "nothing to play"
        MpvErrorCode.UNKNOWN_FORMAT -> "unknown format"
        MpvErrorCode.UNSUPPORTED -> "unsupported"
        else -> "mpv error $code"
    }

    private fun onUpstreamFailure(failure: UpstreamFailure) {
        val generation = synchronized(lock) {
            if (failure.status != null) upstreamStatus = failure.status
            loadGeneration
        }
        val kind = classifyMpvError(MpvErrorCode.LOADING_FAILED, failure.status, failure.message)
        fail(generation, PlaybackError(kind, "${failure.role}: ${failure.message}", failure.status))
    }

    /** Emits [error] once per load, and only if [generation] is still the current load. */
    private fun fail(generation: Int, error: PlaybackError) {
        synchronized(lock) {
            if (generation != loadGeneration || errorEmitted) return
            errorEmitted = true
            snapshot = snapshot.copy(failed = true, positionOverrideMs = snapshot.positionOverrideMs)
            publishLocked(force = true)
        }
        _events.tryEmit(PlayerEvent.Error(error))
    }

    private fun currentGeneration() = synchronized(lock) { loadGeneration }

    private fun publishLocked(force: Boolean = false) {
        val new = MpvStateMapper.map(snapshot)
        if (force || MpvStateMapper.worthPublishing(_state.value, new)) _state.value = new
    }

    // ---- stats ---------------------------------------------------------------------------------------------------

    fun refreshStats() {
        if (released.get() || !synchronized(lock) { snapshot.loadRequested }) return
        fun str(name: String) = client.getProperty(name)
        fun num(name: String) = str(name)?.toDoubleOrNull()
        val w = num("video-params/w")?.toInt()
        val h = num("video-params/h")?.toInt()
        val fps = num("container-fps") ?: num("estimated-vf-fps")
        val hwdec = str("hwdec-current")?.takeIf { it.isNotBlank() && it != "no" }
        val position = num("time-pos") ?: 0.0
        val cached = num("demuxer-cache-time")
        val proxyBps = proxy?.lastChunkBitsPerSecond?.takeIf { it > 0 }
        val ownBps = num("cache-speed")?.let { (it * 8).toLong() }?.takeIf { it > 0 }
        _stats.value = PlaybackStats(
            videoResolution = if (w != null && h != null && w > 0) "${w}x$h" + (fps?.let { "@${Math.round(it)}" } ?: "") else null,
            videoCodec = str("video-format")?.let { it + (hwdec?.let { d -> " ($d)" } ?: "") },
            videoBitrateBps = num("video-bitrate")?.toLong()?.takeIf { it > 0 },
            audioCodec = str("audio-codec-name"),
            audioBitrateBps = num("audio-bitrate")?.toLong()?.takeIf { it > 0 },
            bandwidthEstimateBps = proxyBps ?: ownBps,
            bufferedAheadMs = cached?.let { ((it - position) * 1000).toLong().coerceAtLeast(0) } ?: 0,
            droppedFrames = (num("frame-drop-count")?.toInt() ?: 0) + (num("decoder-frame-drop-count")?.toInt() ?: 0),
        )
    }

    private inline fun submit(crossinline block: () -> Unit) {
        if (released.get()) return
        try {
            control.execute {
                try { block() } catch (e: MpvClosedException) { /* shut down meanwhile */ } catch (e: MpvException) {
                    fail(currentGeneration(), PlaybackError(PlaybackErrorKind.Other, e.message ?: "mpv error", cause = e))
                }
            }
        } catch (e: RejectedExecutionException) { /* released */ }
    }
}
