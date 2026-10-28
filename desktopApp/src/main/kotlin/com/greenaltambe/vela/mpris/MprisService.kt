package com.greenaltambe.vela.mpris

import com.greenaltambe.vela.player.MediaInfo
import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.player.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant

/** `org.mpris.MediaPlayer2`: the root object (name, raise, quit). */
@DBusInterfaceName("org.mpris.MediaPlayer2")
interface MprisRoot : DBusInterface {
    fun Raise()
    fun Quit()
}

/** `org.mpris.MediaPlayer2.Player`: what media keys and the GNOME/KDE media widgets call. */
@DBusInterfaceName("org.mpris.MediaPlayer2.Player")
interface MprisPlayer : DBusInterface {
    fun Next()
    fun Previous()
    fun Pause()
    fun PlayPause()
    fun Stop()
    fun Play()
    fun Seek(offsetMicros: Long)
    fun SetPosition(trackId: DBusPath, positionMicros: Long)
    fun OpenUri(uri: String)
}

/** What the OS controls can make the app do. */
class MprisCommands(
    val play: () -> Unit, val pause: () -> Unit, val toggle: () -> Unit, val stop: () -> Unit,
    val seekTo: (Long) -> Unit, val next: () -> Unit, val previous: () -> Unit, val raise: () -> Unit, val quit: () -> Unit,
)

/**
 * Publishes the player on the session bus as `org.mpris.MediaPlayer2.vela`, so GNOME/KDE media widgets, the lock screen and the
 * keyboard's media keys can show and control it: play/pause/stop/next/previous/seek, position, volume-less, with title, channel and artwork.
 * Changes are announced with PropertiesChanged. If there is no session bus (a headless run), [start] returns null and nothing else changes.
 */
class MprisService private constructor(
    private val connection: DBusConnection,
    private val state: StateFlow<PlayerState>,
    private val media: StateFlow<MediaInfo?>,
    private val commands: MprisCommands,
) : MprisRoot, MprisPlayer, Properties, AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var last = snapshot()

    override fun getObjectPath() = PATH
    override fun isRemote() = false

    // ---- methods ----
    override fun Raise() = commands.raise()
    override fun Quit() = commands.quit()
    override fun Next() = commands.next()
    override fun Previous() = commands.previous()
    override fun Pause() = commands.pause()
    override fun PlayPause() = commands.toggle()
    override fun Stop() = commands.stop()
    override fun Play() = commands.play()
    override fun Seek(offsetMicros: Long) = commands.seekTo((state.value.positionMs + offsetMicros / 1000).coerceAtLeast(0))
    override fun SetPosition(trackId: DBusPath, positionMicros: Long) = commands.seekTo((positionMicros / 1000).coerceAtLeast(0))
    override fun OpenUri(uri: String) {}

    // ---- properties ----
    private fun status() = when (state.value.status) {
        PlaybackStatus.PLAYING, PlaybackStatus.BUFFERING -> "Playing"
        PlaybackStatus.PAUSED -> "Paused"
        else -> "Stopped"
    }

    private fun metadata(): Map<String, Variant<*>> {
        val m = media.value ?: return mapOf("mpris:trackid" to Variant(DBusPath("/org/mpris/MediaPlayer2/TrackList/NoTrack")))
        val out = linkedMapOf<String, Variant<*>>(
            "mpris:trackid" to Variant(DBusPath("/com/greenaltambe/vela/track/${m.videoId.filter { it.isLetterOrDigit() || it == '_' }}")),
            "xesam:title" to Variant(m.title),
            "xesam:artist" to Variant(arrayOf(m.channelName), "as"),
            "xesam:url" to Variant("https://www.youtube.com/watch?v=${m.videoId}"),
        )
        if (state.value.durationMs > 0) out["mpris:length"] = Variant(state.value.durationMs * 1000, "x")
        m.artworkUrl?.let { out["mpris:artUrl"] = Variant(it) }
        return out
    }

    private fun playerProps(): Map<String, Variant<*>> = linkedMapOf(
        "PlaybackStatus" to Variant(status()), "Rate" to Variant(state.value.speed.toDouble()), "Metadata" to Variant(metadata(), "a{sv}"),
        "Volume" to Variant(1.0), "Position" to Variant(state.value.positionMs * 1000, "x"),
        "MinimumRate" to Variant(0.25), "MaximumRate" to Variant(4.0),
        "CanGoNext" to Variant(true), "CanGoPrevious" to Variant(true), "CanPlay" to Variant(media.value != null),
        "CanPause" to Variant(media.value != null), "CanSeek" to Variant(media.value != null && state.value.durationMs > 0), "CanControl" to Variant(true),
    )

    private fun rootProps(): Map<String, Variant<*>> = linkedMapOf(
        "CanQuit" to Variant(true), "CanRaise" to Variant(true), "HasTrackList" to Variant(false), "Identity" to Variant("Vela"),
        "DesktopEntry" to Variant("vela"), "SupportedUriSchemes" to Variant(arrayOf<String>(), "as"), "SupportedMimeTypes" to Variant(arrayOf<String>(), "as"),
    )

    @Suppress("UNCHECKED_CAST")
    override fun <A : Any?> Get(interfaceName: String, propertyName: String): A =
        (GetAll(interfaceName)[propertyName] as? Variant<*>)?.value as A

    override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) {}

    override fun GetAll(interfaceName: String): Map<String, Variant<*>> = when (interfaceName) {
        "org.mpris.MediaPlayer2" -> rootProps()
        "org.mpris.MediaPlayer2.Player" -> playerProps()
        else -> emptyMap()
    }

    // ---- change announcements ----
    /** Only what GNOME needs to stay in step: status, metadata, can-flags; Position is not announced (clients poll it), a jump is announced with Seeked. */
    private fun snapshot(): Map<String, Any?> = mapOf("PlaybackStatus" to status(), "Metadata" to metadata().mapValues { it.value.value.toString() }, "Rate" to state.value.speed, "CanSeek" to (state.value.durationMs > 0))

    private fun announce() {
        val now = snapshot()
        val changed = now.filter { (k, v) -> last[k] != v }.keys
        last = now
        if (changed.isEmpty()) return
        val props = playerProps().filterKeys { it in changed || (it == "Position") }
        runCatching { connection.sendMessage(Properties.PropertiesChanged(PATH, "org.mpris.MediaPlayer2.Player", props, emptyList())) }
    }

    private fun begin() {
        scope.launch { state.collect { announce() } }
        scope.launch { media.collect { announce() } }
    }

    override fun close() {
        scope.cancel()
        runCatching { connection.releaseBusName(BUS_NAME) }
        runCatching { connection.disconnect() }
    }

    companion object {
        const val PATH = "/org/mpris/MediaPlayer2"
        const val BUS_NAME = "org.mpris.MediaPlayer2.vela"

        /** Null when there is no session bus or the name is taken (a second Vela): the app runs without media-key support. */
        fun start(state: StateFlow<PlayerState>, media: StateFlow<MediaInfo?>, commands: MprisCommands, busName: String = BUS_NAME): MprisService? = try {
            val c = DBusConnectionBuilder.forSessionBus().build()
            c.requestBusName(busName)
            MprisService(c, state, media, commands).also { c.exportObject(PATH, it); it.begin() }
        } catch (e: Exception) { System.err.println("Vela: no media-key support (${e.message})"); null }
    }
}
