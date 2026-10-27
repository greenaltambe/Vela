package com.greenaltambe.vela.player.mpv

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

/** Runs everything on the caller's thread, so controller tests are deterministic. */
class DirectExecutorService : AbstractExecutorService() {
    private var shut = false
    override fun execute(command: Runnable) { command.run() }
    override fun shutdown() { shut = true }
    override fun shutdownNow(): MutableList<Runnable> { shut = true; return mutableListOf() }
    override fun isShutdown() = shut
    override fun isTerminated() = shut
    override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
}

class FakeMpvClient : MpvClient {
    val options = mutableListOf<Pair<String, String>>()
    val commands = mutableListOf<List<String>>()
    val properties = mutableMapOf<String, String>()
    val observed = mutableListOf<String>()
    var initialized = false
    var closeCount = 0
    val rendererClosed get() = renderer.closed
    val renderer = FakeRenderer()
    var failNextCommand: MpvException? = null

    override val isClosed get() = closeCount > 0
    override fun setOption(name: String, value: String) { options += name to value }
    override fun initialize() { initialized = true }
    override fun setProperty(name: String, value: String) { if (isClosed) throw MpvClosedException(); properties[name] = value }
    override fun getProperty(name: String): String? = if (isClosed) null else properties[name]
    override fun command(vararg args: String) {
        if (isClosed) throw MpvClosedException()
        failNextCommand?.let { failNextCommand = null; throw it }
        commands += args.toList()
    }
    override fun observe(name: String, format: MpvFormat) { observed += name }
    override fun requestLog(level: String) {}
    override fun waitEvent(timeoutSeconds: Double): MpvEvent = if (isClosed) MpvEvent.Shutdown else MpvEvent.None
    override fun wakeup() {}
    /** A test may supply its own renderer (e.g. one that blocks mid-frame). */
    var rendererOverride: MpvSoftwareRenderer? = null
    override fun createSoftwareRenderer(onUpdate: () -> Unit): MpvSoftwareRenderer = rendererOverride ?: renderer
    override fun close() { closeCount++ }
}

class FakeRenderer : MpvSoftwareRenderer {
    var closed = false
    override fun update() = false
    override fun render(width: Int, height: Int, stride: Int, address: Long) = !closed
    override fun close() { closed = true }
}
