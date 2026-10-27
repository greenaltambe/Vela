package com.greenaltambe.vela.data

import com.greenaltambe.vela.core.settings.*
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersistentSettingsTest {
    private val dir = Files.createTempDirectory("vela-settings-test").toFile()

    @AfterTest fun cleanUp() { dir.deleteRecursively() }

    /** One "app run": open the file database, build the resolver the way app startup does. */
    private fun run(path: String = XdgPaths.databaseFile({ if (it == "XDG_DATA_HOME") dir.path else null }, "/nonexistent").path) =
        SettingsResolver(SqlDelightSettingsStore(createDatabase(JvmDatabaseDriverFactory(path))))

    @Test fun aSettingWrittenInOneRunIsReadInTheNext() {
        val first = run()
        first.set(Settings.maxQualityMetered, MaxQuality.P360)
        first.set(Settings.audioOnly, true)
        first.set(Settings.ytDlpPath, "/opt/yt-dlp")
        first.set(Settings.defaultPlaybackSpeed, 1.5f)
        first.set(Settings.preferredCodec, PreferredCodec.AVC, SettingLayer.channel("UC1"))

        val second = run() // a fresh connection to the same file, as after a restart
        assertEquals(MaxQuality.P360, second.resolve(Settings.maxQualityMetered))
        assertTrue(second.resolve(Settings.audioOnly))
        assertEquals("/opt/yt-dlp", second.resolve(Settings.ytDlpPath))
        assertEquals(1.5f, second.resolve(Settings.defaultPlaybackSpeed))
        assertEquals(PreferredCodec.AVC, second.resolve(Settings.preferredCodec, channelId = "UC1"))
        assertEquals(PreferredCodec.VP9, second.resolve(Settings.preferredCodec), "the channel override stayed a channel override")
    }

    @Test fun aResetAlsoPersists() {
        run().apply { set(Settings.audioOnly, true); resetToDefault(Settings.audioOnly) }
        val again = run()
        assertFalse(again.resolve(Settings.audioOnly))
        assertFalse(again.isOverridden(Settings.audioOnly))
    }

    @Test fun desktopDataDirFollowsXdgDataHome() {
        val file = XdgPaths.databaseFile({ if (it == "XDG_DATA_HOME") "${dir.path}/xdg" else null }, "/home/someone")
        assertEquals(java.io.File("${dir.path}/xdg/vela/vela.db"), file)
        assertTrue(file.parentFile.isDirectory, "directories are created")
    }

    @Test fun withoutXdgDataHomeItIsUnderTheHomeDirectory() {
        val file = XdgPaths.databaseFile({ null }, dir.path)
        assertEquals(java.io.File("${dir.path}/.local/share/vela/vela.db"), file)
        assertTrue(file.parentFile.isDirectory)
    }

    @Test fun anEmptyOrRelativeXdgDataHomeIsIgnoredPerTheSpec() {
        listOf("", "  ", "relative/dir").forEach { bad ->
            assertEquals(java.io.File("${dir.path}/.local/share/vela/vela.db"), XdgPaths.databaseFile({ bad }, dir.path), "'$bad'")
        }
    }
}
