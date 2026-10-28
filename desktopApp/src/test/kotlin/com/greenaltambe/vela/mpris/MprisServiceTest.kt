package com.greenaltambe.vela.mpris

import com.greenaltambe.vela.player.MediaInfo
import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.player.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.Properties
import org.junit.Assume
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Talks to the service over the real session bus (skipped when there is none): a second connection plays the part of GNOME's media widget. */
class MprisServiceTest {
    private val calls = mutableListOf<String>()
    private val state = MutableStateFlow(PlayerState(PlaybackStatus.PLAYING, 12_000, 200_000, 1.25f))
    private val media = MutableStateFlow<MediaInfo?>(MediaInfo("abc_123", "A title", "A channel", "https://i.ytimg.com/vi/abc/hq.jpg"))
    private val commands = MprisCommands({ calls += "play" }, { calls += "pause" }, { calls += "toggle" }, { calls += "stop" }, { calls += "seek $it" }, { calls += "next" }, { calls += "previous" }, { calls += "raise" }, { calls += "quit" })

    @Test fun theBusExposesStatusMetadataAndPositionAndExecutesMediaKeyCommands() {
        val name = "org.mpris.MediaPlayer2.vela_test${ProcessHandle.current().pid()}"
        val service = MprisService.start(state, media, commands, name)
        Assume.assumeTrue("no session bus", service != null)
        try {
            DBusConnectionBuilder.forSessionBus().build().use { client ->
                val props = client.getRemoteObject(name, MprisService.PATH, Properties::class.java)
                val player = props.GetAll("org.mpris.MediaPlayer2.Player")
                assertEquals("Playing", player["PlaybackStatus"]!!.value)
                assertEquals(12_000_000L, player["Position"]!!.value)
                assertEquals(1.25, player["Rate"]!!.value)
                @Suppress("UNCHECKED_CAST") val md = player["Metadata"]!!.value as Map<String, org.freedesktop.dbus.types.Variant<*>>
                assertEquals("A title", md["xesam:title"]!!.value); assertEquals(200_000_000L, md["mpris:length"]!!.value)
                assertEquals("https://i.ytimg.com/vi/abc/hq.jpg", md["mpris:artUrl"]!!.value)
                assertEquals("Vela", props.GetAll("org.mpris.MediaPlayer2")["Identity"]!!.value)

                val remote = client.getRemoteObject(name, MprisService.PATH, MprisPlayer::class.java)
                remote.PlayPause(); remote.Next(); remote.Previous(); remote.Stop()
                remote.Seek(5_000_000); remote.SetPosition(org.freedesktop.dbus.DBusPath("/x"), 30_000_000)
                Thread.sleep(300)
                assertEquals(listOf("toggle", "next", "previous", "stop", "seek 17000", "seek 30000"), calls)

                state.value = PlayerState(PlaybackStatus.PAUSED, 1, 200_000, 1f)
                assertEquals("Paused", props.GetAll("org.mpris.MediaPlayer2.Player")["PlaybackStatus"]!!.value)
                media.value = null
                @Suppress("UNCHECKED_CAST") val none = props.GetAll("org.mpris.MediaPlayer2.Player")["Metadata"]!!.value as Map<String, *>
                assertTrue("xesam:title" !in none, "nothing loaded = no track metadata")
            }
        } finally { service?.close() }
    }

    /** What GNOME's media widget receives: the signals on the real session bus, captured with the system's own dbus-monitor. */
    @Test fun statusMetadataAndSeekChangesAreAnnouncedAsPropertiesChangedSignals() {
        val name = "org.mpris.MediaPlayer2.vela_sig${ProcessHandle.current().pid()}"
        val mon = runCatching { ProcessBuilder("dbus-monitor", "--session", "type='signal',path='/org/mpris/MediaPlayer2',interface='org.freedesktop.DBus.Properties'").redirectErrorStream(true).start() }.getOrNull()
        Assume.assumeTrue("no dbus-monitor", mon != null)
        val captured = StringBuffer()
        Thread { mon!!.inputStream.bufferedReader().forEachLine { captured.appendLine(it) } }.apply { isDaemon = true; start() }
        val service = MprisService.start(state, media, commands, name)
        Assume.assumeTrue("no session bus", service != null)
        try {
            Thread.sleep(800)
            state.value = PlayerState(PlaybackStatus.PAUSED, 20_000, 200_000, 1f); Thread.sleep(500)
            state.value = PlayerState(PlaybackStatus.PLAYING, 20_000, 200_000, 1f); Thread.sleep(500)
            media.value = MediaInfo("zzz999", "Next title", "Other channel", null); Thread.sleep(800)
            state.value = PlayerState(PlaybackStatus.PLAYING, 100_000, 200_000, 2f); Thread.sleep(800)
            val log = captured.toString()
            println("MPRIS signals captured: ${Regex("member=PropertiesChanged").findAll(log).count()}")
            assertTrue("member=PropertiesChanged" in log, "no PropertiesChanged signal at all:\n$log")
            assertTrue("org.mpris.MediaPlayer2.Player" in log && "PlaybackStatus" in log && "Paused" in log && "Playing" in log, "status changes are announced:\n${log.take(1500)}")
            assertTrue("xesam:title" in log && "Next title" in log, "a new video's metadata is announced")
            assertTrue("Rate" in log, "a speed change is announced")
        } finally { service?.close(); mon?.destroyForcibly() }
    }
}
