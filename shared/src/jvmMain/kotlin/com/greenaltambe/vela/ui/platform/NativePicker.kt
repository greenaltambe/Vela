package com.greenaltambe.vela.ui.platform

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The desktop's own file/folder chooser (GTK, the Nautilus-style dialog) through `zenity`, which GNOME ships. Java's built-in dialogs
 * (Swing, AWT) look dated, so callers try this first and fall back to them when [Result.Unavailable] (no zenity, e.g. inside a sandbox).
 */
object NativePicker {
    sealed interface Result {
        data class Chosen(val file: File) : Result
        data object Cancelled : Result
        data object Unavailable : Result
    }

    fun folder(title: String, startDir: File? = null): Result = run(title, startDir, directory = true)
    fun file(title: String, startDir: File? = null): Result = run(title, startDir, directory = false)

    private fun run(title: String, startDir: File?, directory: Boolean): Result {
        if (!System.getProperty("os.name").lowercase().contains("linux")) return Result.Unavailable
        val cmd = buildList {
            add("zenity"); add("--file-selection"); add("--title=$title")
            if (directory) add("--directory")
            startDir?.takeIf { it.isDirectory }?.let { add("--filename=${it.path}/") }
        }
        return try {
            val p = ProcessBuilder(cmd).redirectErrorStream(false).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            val out = p.inputStream.bufferedReader().readText().trim()
            p.waitFor(5, TimeUnit.SECONDS)
            when {
                p.exitValue() == 0 && out.isNotEmpty() -> Result.Chosen(File(out))
                p.exitValue() == 1 -> Result.Cancelled          // zenity: 1 = the viewer cancelled
                else -> Result.Unavailable
            }
        } catch (e: java.io.IOException) {
            Result.Unavailable
        }
    }
}
