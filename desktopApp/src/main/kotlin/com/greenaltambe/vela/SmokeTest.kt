package com.greenaltambe.vela

import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.auth.desktopSecretStore
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.SqlDelightSettingsStore
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.extractor.chain.DiagnosticsLog
import com.greenaltambe.vela.extractor.ytdlp.SystemProcessRunner
import com.greenaltambe.vela.player.mpv.MpvLibrary
import java.io.File
import kotlin.concurrent.thread

/**
 * `Vela --smoke-test`: starts the real runtime (database, settings, the extractor chain, the program lookup, libmpv) with no window and no
 * network, prints one line per step, and exits 0 within 30 seconds. A missing yt-dlp or libmpv is REPORTED, not a failure (the app runs
 * without them); a crash or a timeout is a failure with a message. It uses a temporary data folder, never the user's.
 */
object SmokeTest {
    private const val LIMIT_SECONDS = 30L

    fun run(): Int {
        // A hung step must not hang CI: the watchdog ends the process.
        thread(isDaemon = true, name = "smoke-watchdog") { Thread.sleep(LIMIT_SECONDS * 1000); say("SMOKE FAILED: no result within $LIMIT_SECONDS s"); Runtime.getRuntime().halt(2) }
        val dir = java.nio.file.Files.createTempDirectory("vela-smoke").toFile()
        return try {
            step("version") { System.getProperty("java.version") + " on " + System.getProperty("os.name") + " " + System.getProperty("os.arch") }
            val database = step("database") { createDatabase(JvmDatabaseDriverFactory(File(dir, "vela.db").path)) }
            val settings = step("settings") { SettingsResolver(SqlDelightSettingsStore(database)).also { it.resolve(Settings.ytDlpPath); it.resolve(Settings.maxQualityUnmetered) } }
            val secrets = step("credential store") { desktopSecretStore(File(dir, "secrets")).description }
            val wiring = step("extractor chain") { buildDesktopWiring(database, settings, desktopSecretStore(File(dir, "secrets")), SystemProcessRunner(), DiagnosticsLog(), managedDir = File(dir, "bin")) }
            check(wiring.chain != null) { "no extractor chain" }
            say("SMOKE yt-dlp: " + (wiring.ytDlp.path?.let { "found $it (${wiring.ytDlpVersion ?: "version unknown"})" } ?: "not found (${wiring.ytDlp.description})"))
            say("SMOKE ffmpeg: " + (wiring.ffmpeg.path?.let { "found $it" } ?: "not found"))
            val mpv = MpvLibrary.load()
            say("SMOKE libmpv: " + mpv.fold({ "loaded" }, { "not loaded (${it.message?.lineSequence()?.firstOrNull()})" }))
            say("SMOKE OK (credential store: $secrets)")
            0
        } catch (e: Throwable) {
            say("SMOKE FAILED: ${e::class.simpleName}: ${e.message}")
            e.printStackTrace()
            1
        } finally { runCatching { dir.deleteRecursively() } }
    }

    /** One line to stdout and, when VELA_SMOKE_LOG names a file, to that file too (a packaged Windows exe has no console). */
    private fun say(line: String) {
        println(line)
        System.getenv("VELA_SMOKE_LOG")?.takeIf { it.isNotBlank() }?.let { runCatching { File(it).appendText(line + "\n") } }
    }

    private fun <T> step(name: String, block: () -> T): T = try { block().also { say("SMOKE $name: ok") } } catch (e: Throwable) { throw IllegalStateException("step '$name' failed: ${e.message}", e) }
}
