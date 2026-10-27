package com.greenaltambe.vela.player.mpv

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Where finished frames go. The UI implements this over its own pixel memory. */
interface FrameSink {
    /** Render thread. A writable BGRA (`bgr0`) target of exactly [width] x [height], or null to drop this frame. */
    fun beginFrame(width: Int, height: Int): FrameTarget?

    /** Render thread: [target] is complete and may be shown. */
    fun endFrame(target: FrameTarget)
}

/** [address] is native memory; rows are [stride] bytes apart. [token] is the sink's own bookkeeping. */
class FrameTarget(val address: Long, val stride: Int, val token: Any? = null)

/**
 * Owns the render thread: wakes when mpv says a frame is due, asks it to draw into the sink's memory at the
 * current surface size, and counts frames. Nothing here allocates per frame.
 */
internal class SoftwareRenderLoop(client: MpvClient) : AutoCloseable {
    @Volatile private var sink: FrameSink? = null
    @Volatile private var width = 0
    @Volatile private var height = 0
    private val signalled = AtomicBoolean(false)
    private val running = AtomicBoolean(true)

    private var sizeRendered: Pair<Int, Int>? = null // render thread only

    val framesRendered = AtomicLong()
    val framesSkipped = AtomicLong()
    private val renderNanos = AtomicLong()

    private val renderer: MpvSoftwareRenderer = client.createSoftwareRenderer { signal() }
    private val thread = Thread(::loop, "vela-mpv-render").apply { isDaemon = true; start() }

    /**
     * Held for the whole of a frame (beginFrame, render, endFrame). A sink is only safe to free once [setSink] /
     * [detachSink] has returned, because it cannot return while a frame is being written into the sink's memory.
     */
    private val frameLock = ReentrantLock()

    fun setSink(sink: FrameSink?) = frameLock.withLock { this.sink = sink }

    /** Detaches [sink] only if it is still the current one, so a late detach cannot remove its replacement. */
    fun detachSink(sink: FrameSink) = frameLock.withLock { if (this.sink === sink) this.sink = null }

    fun setSize(width: Int, height: Int) {
        this.width = width; this.height = height
        signal() // redraw the current frame at the new size
    }

    val averageRenderMs: Double get() = framesRendered.get().let { if (it == 0L) 0.0 else renderNanos.get() / it / 1e6 }

    private fun signal() {
        signalled.set(true)
        LockSupport.unpark(thread)
    }

    private fun loop() {
        while (running.get()) {
            if (!signalled.getAndSet(false)) {
                LockSupport.park(this)
                continue
            }
            try {
                val due = renderer.update()
                frameLock.withLock {
                    val s = sink
                    val w = width
                    val h = height
                    // A resize needs a repaint even though no new frame is due: mpv re-renders the last one.
                    if (s == null || w <= 0 || h <= 0) {
                        if (due) framesSkipped.incrementAndGet()
                        return@withLock
                    }
                    if (!due && sizeRendered == (w to h)) return@withLock
                    val target = s.beginFrame(w, h)
                    if (target == null) { framesSkipped.incrementAndGet(); return@withLock }
                    val t0 = System.nanoTime()
                    if (renderer.render(w, h, target.stride, target.address)) {
                        renderNanos.addAndGet(System.nanoTime() - t0)
                        framesRendered.incrementAndGet()
                        sizeRendered = w to h
                        s.endFrame(target)
                    }
                }
            } catch (e: MpvClosedException) {
                return
            } catch (e: Exception) {
                System.err.println("Vela mpv render: $e")
            }
        }
    }

    override fun close() {
        if (!running.getAndSet(false)) return
        LockSupport.unpark(thread)
        thread.join(2000)
        runCatching { renderer.close() }
    }
}
