package com.greenaltambe.vela.mpris

import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.player.PlayerState
import com.greenaltambe.vela.player.SessionState
import com.greenaltambe.vela.player.videoHeight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface

/** `org.freedesktop.ScreenSaver`: implemented by GNOME, KDE and others; a cookie holds the inhibition until UnInhibit or until this bus connection closes. */
@DBusInterfaceName("org.freedesktop.ScreenSaver")
interface FdoScreenSaver : DBusInterface {
    fun Inhibit(application: String, reason: String): Int
    fun UnInhibit(cookie: Int)
}

/**
 * Keeps the screen from blanking or locking while a video plays (not paused, not audio-only), through the freedesktop ScreenSaver
 * interface on the session bus. Without a bus or without that service, [start] returns null and nothing changes.
 */
class ScreenSaverInhibitor private constructor(private val conn: DBusConnection, private val saver: FdoScreenSaver, private val scope: CoroutineScope) : AutoCloseable {
    private var cookie: Int? = null

    private fun set(on: Boolean) {
        try {
            if (on && cookie == null) cookie = saver.Inhibit("Vela", "Playing a video")
            else if (!on) cookie?.let { saver.UnInhibit(it); cookie = null }
        } catch (_: Throwable) { /* the service went away: the screen simply may sleep */ }
    }

    override fun close() {
        scope.cancel()
        set(false)
        runCatching { conn.close() }
    }

    companion object {
        fun start(player: StateFlow<PlayerState>, session: StateFlow<SessionState>): ScreenSaverInhibitor? = try {
            val c = DBusConnectionBuilder.forSessionBus().build()
            val saver = c.getRemoteObject("org.freedesktop.ScreenSaver", "/org/freedesktop/ScreenSaver", FdoScreenSaver::class.java)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            ScreenSaverInhibitor(c, saver, scope).also { inh ->
                scope.launch {
                    combine(player, session) { p, s -> p.status == PlaybackStatus.PLAYING && (s as? SessionState.Ready)?.plan?.videoHeight != null }
                        .distinctUntilChanged().collect { inh.set(it) }
                }
            }
        } catch (_: Throwable) { null }
    }
}
