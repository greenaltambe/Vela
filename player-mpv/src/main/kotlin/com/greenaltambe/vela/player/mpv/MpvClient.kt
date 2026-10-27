package com.greenaltambe.vela.player.mpv

/** libmpv is not on this machine, or loading it failed. */
class MpvUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** A libmpv call returned a negative error code. [code] is the `mpv_error` value. */
class MpvException(val code: Int, message: String) : RuntimeException(message)

/** The handle was already shut down; a call that cannot meaningfully be a no-op throws this. */
class MpvClosedException : IllegalStateException("mpv has been shut down")

enum class MpvFormat { Flag, Int64, Double, String }

enum class EndReason { Eof, Stop, Quit, Error, Redirect, Unknown }

/** What the event loop hands over. Property values are Boolean, Long, Double, String, or null when unavailable. */
sealed interface MpvEvent {
    data object None : MpvEvent
    data object Shutdown : MpvEvent
    data object StartFile : MpvEvent
    data object FileLoaded : MpvEvent
    data object PlaybackRestart : MpvEvent
    data class EndFile(val reason: EndReason, val errorCode: Int) : MpvEvent
    data class Property(val name: String, val value: Any?) : MpvEvent
    data class Log(val prefix: String, val level: String, val text: String) : MpvEvent
}

/** Creates handles; the seam where tests plug in a fake native layer. */
fun interface MpvBackend {
    /** @throws MpvUnavailableException when libmpv cannot be loaded. */
    fun create(): MpvClient
}

/**
 * A thin typed view of one `mpv_handle`. Every method is safe to call from any thread and safe after [close]:
 * calls on a closed handle fail with [MpvClosedException] (or return the neutral value, where documented) and never
 * touch freed memory. [close] is idempotent.
 */
interface MpvClient : AutoCloseable {
    val isClosed: Boolean

    /** Only before [initialize]. */
    fun setOption(name: String, value: String)
    fun initialize()

    fun setProperty(name: String, value: String)

    /** The property as mpv's string form, or null when it is unavailable or the handle is closed. */
    fun getProperty(name: String): String?

    /** A synchronous `mpv_command`. */
    fun command(vararg args: String)

    fun observe(name: String, format: MpvFormat)

    /** [level] is mpv's log level name, e.g. `warn`. */
    fun requestLog(level: String)

    /** Blocks up to [timeoutSeconds]; returns [MpvEvent.Shutdown] once the handle is closed. */
    fun waitEvent(timeoutSeconds: Double): MpvEvent

    /** Makes a blocked [waitEvent] return. */
    fun wakeup()

    /** [onUpdate] runs on an mpv thread: it must only signal, never call back into mpv. */
    fun createSoftwareRenderer(onUpdate: () -> Unit): MpvSoftwareRenderer
}

/** `MPV_RENDER_API_TYPE_SW`: mpv draws BGR0 frames into memory we own. Closed automatically with its [MpvClient]. */
interface MpvSoftwareRenderer : AutoCloseable {
    /** True when a new frame is due and [render] should be called. */
    fun update(): Boolean

    /** Renders into [address] (`width * 4` bytes per row at least, [stride] bytes apart). False if closed or mpv refused. */
    fun render(width: Int, height: Int, stride: Int, address: Long): Boolean
}
