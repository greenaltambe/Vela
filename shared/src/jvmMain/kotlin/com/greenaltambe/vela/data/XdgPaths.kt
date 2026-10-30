package com.greenaltambe.vela.data

import java.io.File

/** True on Windows (the experimental desktop target). */
val isWindows: Boolean get() = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

/** Where the desktop app keeps its data: per the XDG Base Directory spec on Linux, `%LOCALAPPDATA%\vela` on Windows. */
object XdgPaths {
    /**
     * `$XDG_DATA_HOME/vela`, or `~/.local/share/vela` when that variable is unset, empty or not an absolute path
     * (the spec says relative values are invalid and must be ignored). Created if missing.
     */
    fun dataDir(env: (String) -> String? = System::getenv, home: String = System.getProperty("user.home")): File {
        val base = env("XDG_DATA_HOME")?.takeIf { it.isNotBlank() && File(it).isAbsolute }
            ?: if (isWindows) (env("LOCALAPPDATA")?.takeIf { it.isNotBlank() } ?: File(home, "AppData/Local").path) else File(home, ".local/share").path
        return File(base, "vela").also { it.mkdirs() }
    }

    fun databaseFile(env: (String) -> String? = System::getenv, home: String = System.getProperty("user.home")): File =
        File(dataDir(env, home), "vela.db")

    /** The user's videos folder: `XDG_VIDEOS_DIR` from `user-dirs.dirs` (as `$HOME/Videos` or an absolute path), else `~/Videos`. Not created. */
    fun videosDir(env: (String) -> String? = System::getenv, home: String = System.getProperty("user.home")): File {
        val config = env("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() && File(it).isAbsolute } ?: "$home/.config"
        val line = File(config, "user-dirs.dirs").takeIf { it.isFile }?.readLines()?.firstOrNull { it.trimStart().startsWith("XDG_VIDEOS_DIR=") }
        val raw = line?.substringAfter('=')?.trim()?.trim('"')?.replace("\$HOME", home)
        return File(raw?.takeIf { it.isNotBlank() && File(it).isAbsolute } ?: "$home/Videos")
    }
}
