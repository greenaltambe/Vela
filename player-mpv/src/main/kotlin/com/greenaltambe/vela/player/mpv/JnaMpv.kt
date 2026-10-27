package com.greenaltambe.vela.player.mpv

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/** The C functions used, exactly as declared in `client.h` / `render.h`. */
internal interface MpvLib : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_terminate_destroy(ctx: Pointer)
    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_get_property_string(ctx: Pointer, name: String): Pointer?
    fun mpv_free(data: Pointer)
    fun mpv_command(ctx: Pointer, args: Array<String?>): Int
    fun mpv_observe_property(ctx: Pointer, replyUserdata: Long, name: String, format: Int): Int
    fun mpv_request_log_messages(ctx: Pointer, minLevel: String): Int
    fun mpv_wait_event(ctx: Pointer, timeout: Double): Pointer
    fun mpv_wakeup(ctx: Pointer)
    fun mpv_error_string(error: Int): String
    fun mpv_render_context_create(res: PointerByReference, mpv: Pointer, params: Pointer): Int
    fun mpv_render_context_set_update_callback(ctx: Pointer, callback: UpdateCallback?, data: Pointer?)
    fun mpv_render_context_update(ctx: Pointer): Long
    fun mpv_render_context_render(ctx: Pointer, params: Pointer): Int
    fun mpv_render_context_free(ctx: Pointer)
}

internal fun interface UpdateCallback : Callback {
    fun invoke(data: Pointer?)
}

private interface LibC : Library {
    fun setlocale(category: Int, locale: String): String?
}

private object Native_ {
    const val LC_NUMERIC = 1

    // mpv_event_id
    const val EV_NONE = 0
    const val EV_SHUTDOWN = 1
    const val EV_LOG_MESSAGE = 2
    const val EV_START_FILE = 6
    const val EV_END_FILE = 7
    const val EV_FILE_LOADED = 8
    const val EV_PLAYBACK_RESTART = 21
    const val EV_PROPERTY_CHANGE = 22

    // mpv_format
    const val FMT_NONE = 0
    const val FMT_STRING = 1
    const val FMT_FLAG = 3
    const val FMT_INT64 = 4
    const val FMT_DOUBLE = 5

    // mpv_render_param_type
    const val RP_INVALID = 0
    const val RP_API_TYPE = 1
    const val RP_SW_SIZE = 17
    const val RP_SW_FORMAT = 18
    const val RP_SW_STRIDE = 19
    const val RP_SW_POINTER = 20

    const val RENDER_UPDATE_FRAME = 1L
}

class JnaMpvBackend private constructor(private val lib: MpvLib) : MpvBackend {
    override fun create(): MpvClient {
        // libmpv needs LC_NUMERIC=C or number parsing breaks; the JVM inherits the user's locale.
        runCatching { Native.load("c", LibC::class.java).setlocale(Native_.LC_NUMERIC, "C") }
        val handle = lib.mpv_create() ?: throw MpvUnavailableException("mpv_create returned NULL")
        return JnaMpvClient(lib, handle)
    }

    companion object {
        /** @throws MpvUnavailableException listing every candidate tried. */
        fun load(candidates: List<String>): JnaMpvBackend {
            val errors = mutableListOf<String>()
            for (name in candidates) {
                try {
                    val lib = Native.load(name, MpvLib::class.java, mapOf(Library.OPTION_STRING_ENCODING to "UTF-8"))
                    return JnaMpvBackend(lib)
                } catch (t: Throwable) {
                    errors += "$name: ${t.message?.lineSequence()?.firstOrNull()}"
                }
            }
            throw MpvUnavailableException("libmpv could not be loaded (${errors.joinToString("; ")})")
        }
    }
}

/**
 * Lifetime rule: the native handle is only touched while holding [lock] for reading and after checking [handle] is
 * still set; [close] takes it for writing, so it waits for in-flight calls and nothing can start once it has cleared
 * [handle]. Blocking calls (`waitEvent`) use short timeouts and [wakeup] so [close] is quick.
 */
internal class JnaMpvClient(private val lib: MpvLib, handle: Pointer) : MpvClient {
    private val lock = ReentrantReadWriteLock()
    private var handle: Pointer? = handle
    private val closing = AtomicBoolean(false)
    private val renderers = mutableListOf<JnaSoftwareRenderer>()

    override val isClosed: Boolean get() = closing.get()

    private inline fun <T> withHandle(block: (Pointer) -> T): T = lock.read {
        block(handle ?: throw MpvClosedException())
    }

    private fun check(code: Int, what: String) {
        if (code < 0) throw MpvException(code, "$what failed: ${lib.mpv_error_string(code)} ($code)")
    }

    override fun setOption(name: String, value: String) = withHandle { check(lib.mpv_set_option_string(it, name, value), "option $name=$value") }

    override fun initialize() = withHandle { check(lib.mpv_initialize(it), "mpv_initialize") }

    override fun setProperty(name: String, value: String) = withHandle { check(lib.mpv_set_property_string(it, name, value), "set $name=$value") }

    override fun getProperty(name: String): String? = lock.read {
        val h = handle ?: return null
        val p = lib.mpv_get_property_string(h, name) ?: return null
        try { p.getString(0, "UTF-8") } finally { lib.mpv_free(p) }
    }

    override fun command(vararg args: String) = withHandle {
        check(lib.mpv_command(it, arrayOf(*args, null)), "command ${args.firstOrNull()}")
    }

    override fun observe(name: String, format: MpvFormat) = withHandle {
        val f = when (format) {
            MpvFormat.Flag -> Native_.FMT_FLAG
            MpvFormat.Int64 -> Native_.FMT_INT64
            MpvFormat.Double -> Native_.FMT_DOUBLE
            MpvFormat.String -> Native_.FMT_STRING
        }
        check(lib.mpv_observe_property(it, 0, name, f), "observe $name")
    }

    override fun requestLog(level: String) = withHandle { check(lib.mpv_request_log_messages(it, level), "log level $level") }

    override fun waitEvent(timeoutSeconds: Double): MpvEvent = lock.read {
        val h = handle ?: return MpvEvent.Shutdown
        decode(lib.mpv_wait_event(h, timeoutSeconds))
    }

    override fun wakeup() {
        lock.read { handle?.let(lib::mpv_wakeup) }
    }

    // struct mpv_event { int event_id; int error; uint64 reply_userdata; void *data; }
    private fun decode(event: Pointer): MpvEvent {
        val data = event.getPointer(16)
        return when (event.getInt(0)) {
            Native_.EV_NONE -> MpvEvent.None
            Native_.EV_SHUTDOWN -> MpvEvent.Shutdown
            Native_.EV_START_FILE -> MpvEvent.StartFile
            Native_.EV_FILE_LOADED -> MpvEvent.FileLoaded
            Native_.EV_PLAYBACK_RESTART -> MpvEvent.PlaybackRestart
            // struct mpv_event_end_file { int reason; int error; ... }
            Native_.EV_END_FILE -> MpvEvent.EndFile(
                reason = when (data?.getInt(0)) {
                    0 -> EndReason.Eof
                    2 -> EndReason.Stop
                    3 -> EndReason.Quit
                    4 -> EndReason.Error
                    5 -> EndReason.Redirect
                    else -> EndReason.Unknown
                },
                errorCode = data?.getInt(4) ?: 0,
            )
            // struct mpv_event_log_message { const char *prefix, *level, *text; int log_level; }
            Native_.EV_LOG_MESSAGE -> data?.let {
                MpvEvent.Log(
                    prefix = it.getPointer(0)?.getString(0, "UTF-8").orEmpty(),
                    level = it.getPointer(8)?.getString(0, "UTF-8").orEmpty(),
                    text = it.getPointer(16)?.getString(0, "UTF-8").orEmpty().trimEnd(),
                )
            } ?: MpvEvent.None
            // struct mpv_event_property { const char *name; mpv_format format; void *data; }
            Native_.EV_PROPERTY_CHANGE -> data?.let {
                val name = it.getPointer(0)?.getString(0, "UTF-8").orEmpty()
                val valuePtr = it.getPointer(16)
                val value: Any? = if (valuePtr == null) null else when (it.getInt(8)) {
                    Native_.FMT_FLAG -> valuePtr.getInt(0) != 0
                    Native_.FMT_INT64 -> valuePtr.getLong(0)
                    Native_.FMT_DOUBLE -> valuePtr.getDouble(0)
                    Native_.FMT_STRING -> valuePtr.getPointer(0)?.getString(0, "UTF-8")
                    else -> null
                }
                MpvEvent.Property(name, value)
            } ?: MpvEvent.None
            else -> MpvEvent.None
        }
    }

    override fun createSoftwareRenderer(onUpdate: () -> Unit): MpvSoftwareRenderer = lock.read {
        val h = handle ?: throw MpvClosedException()
        JnaSoftwareRenderer(lib, h, onUpdate).also { synchronized(renderers) { renderers += it } }
    }

    override fun close() {
        if (!closing.compareAndSet(false, true)) return
        wakeup() // let a blocked waitEvent return so the write lock below is quick
        // The render context must go before the core (libmpv requires it), and while the core is still valid.
        synchronized(renderers) { renderers.toList() }.forEach { it.close() }
        lock.write {
            val h = handle ?: return
            handle = null
            lib.mpv_terminate_destroy(h)
        }
    }
}

internal class JnaSoftwareRenderer(
    private val lib: MpvLib,
    mpv: Pointer,
    onUpdate: () -> Unit,
) : MpvSoftwareRenderer {
    private val lock = ReentrantReadWriteLock()
    private var ctx: Pointer? = null

    // Kept in fields: JNA hands a function pointer to C, and a collected callback object would crash the process.
    private val callback = UpdateCallback { onUpdate() }
    private val apiType = Memory(3).apply { setString(0, "sw") }

    // Reused by every render() call, which is only ever made from the render thread.
    private val size = Memory(8)
    private val format = Memory(8).apply { setString(0, "bgr0") }
    private val stride = Memory(8)
    private val params = Memory(16L * 5)

    init {
        val create = Memory(16L * 2)
        create.setInt(0, Native_.RP_API_TYPE); create.setPointer(8, apiType)
        create.setInt(16, Native_.RP_INVALID); create.setPointer(24, null)
        val out = PointerByReference()
        val code = lib.mpv_render_context_create(out, mpv, create)
        if (code < 0) throw MpvException(code, "mpv_render_context_create failed: ${lib.mpv_error_string(code)}")
        ctx = out.value
        lib.mpv_render_context_set_update_callback(out.value, callback, null)
    }

    override fun update(): Boolean = lock.read {
        val c = ctx ?: return false
        lib.mpv_render_context_update(c) and Native_.RENDER_UPDATE_FRAME != 0L
    }

    override fun render(width: Int, height: Int, stride: Int, address: Long): Boolean = lock.read {
        val c = ctx ?: return false
        size.setInt(0, width); size.setInt(4, height)
        this.stride.setLong(0, stride.toLong())
        var o = 0L
        fun param(type: Int, data: Pointer?) { params.setInt(o, type); params.setPointer(o + 8, data); o += 16 }
        param(Native_.RP_SW_SIZE, size)
        param(Native_.RP_SW_FORMAT, format)
        param(Native_.RP_SW_STRIDE, this.stride)
        param(Native_.RP_SW_POINTER, Pointer(address))
        param(Native_.RP_INVALID, null)
        lib.mpv_render_context_render(c, params) >= 0
    }

    override fun close() {
        lock.write {
            val c = ctx ?: return
            ctx = null
            lib.mpv_render_context_set_update_callback(c, null, null)
            lib.mpv_render_context_free(c)
        }
    }
}
