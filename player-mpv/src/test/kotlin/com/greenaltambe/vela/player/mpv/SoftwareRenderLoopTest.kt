package com.greenaltambe.vela.player.mpv

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A renderer that stays inside render() until released: stands in for mpv writing into the sink's memory. */
private class BlockingRenderer : MpvSoftwareRenderer {
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    @Volatile var writing = false
    override fun update() = true
    override fun render(width: Int, height: Int, stride: Int, address: Long): Boolean {
        writing = true
        entered.countDown()
        release.await(5, TimeUnit.SECONDS)
        writing = false
        return true
    }
    override fun close() {}
}

private class CountingSink : FrameSink {
    val begun = AtomicInteger()
    override fun beginFrame(width: Int, height: Int): FrameTarget { begun.incrementAndGet(); return FrameTarget(0, width * 4) }
    override fun endFrame(target: FrameTarget) {}
}

class SoftwareRenderLoopTest {
    private fun loopWith(renderer: MpvSoftwareRenderer): SoftwareRenderLoop {
        val client = FakeMpvClient().also { it.rendererOverride = renderer }
        return SoftwareRenderLoop(client)
    }

    @Test
    fun detachingWaitsForTheFrameInFlightSoTheSinkCanBeFreedSafely() {
        val renderer = BlockingRenderer()
        val loop = loopWith(renderer)
        try {
            val sink = CountingSink()
            loop.setSink(sink)
            loop.setSize(64, 36)
            assertTrue(renderer.entered.await(5, TimeUnit.SECONDS), "the loop never started rendering")

            val detached = AtomicBoolean(false)
            val t = Thread { loop.detachSink(sink); detached.set(true) }.apply { start() }
            Thread.sleep(200)
            assertFalse(detached.get(), "detach returned while the renderer was still writing into the sink")
            assertTrue(renderer.writing)

            renderer.release.countDown()
            t.join(5000)
            assertTrue(detached.get())
            assertFalse(renderer.writing, "the frame must have finished before detach returned")

            val framesAfter = sink.begun.get()
            loop.setSize(128, 72)
            Thread.sleep(200)
            assertTrue(sink.begun.get() == framesAfter, "a detached sink must get no more frames")
        } finally {
            renderer.release.countDown()
            loop.close()
        }
    }

    @Test
    fun aLateDetachDoesNotRemoveItsReplacement() {
        val renderer = BlockingRenderer().also { it.release.countDown() }
        val loop = loopWith(renderer)
        try {
            val old = CountingSink(); val new = CountingSink()
            loop.setSink(old)
            loop.setSink(new)
            loop.detachSink(old)
            loop.setSize(32, 18)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (new.begun.get() == 0 && System.nanoTime() < deadline) Thread.sleep(10)
            assertTrue(new.begun.get() > 0, "the replacement sink was detached by the old one's late dispose")
        } finally {
            loop.close()
        }
    }
}
