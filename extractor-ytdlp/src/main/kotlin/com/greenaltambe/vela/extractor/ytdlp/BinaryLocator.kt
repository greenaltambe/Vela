package com.greenaltambe.vela.extractor.ytdlp

import java.io.File

/** Where an external program was found ([source]: "setting", "PATH", "common location", or why it was not found). */
data class FoundBinary(val name: String, val path: String?, val source: String) {
    val found: Boolean get() = path != null
    /** One Diagnostics line. */
    val description: String get() = if (path != null) "$name: $path ($source)" else "$name: not found ($source)"
}

/**
 * Small, explicit lookup for yt-dlp, a JavaScript runtime and ffmpeg. A desktop launcher or an IDE does not start the app
 * from an interactive shell, so PATH is often short (no `~/.local/bin`, no version-manager shims). Order: the configured
 * path, then PATH, then the common install locations below. Nothing else is searched.
 */
class BinaryLocator(
    private val pathEnv: () -> String = { System.getenv("PATH").orEmpty() },
    private val home: String = System.getProperty("user.home").orEmpty(),
    private val isExecutable: (String) -> Boolean = { File(it).let { f -> f.isFile && f.canExecute() } },
    private val listDirs: (String) -> List<String> = { File(it).list()?.sorted()?.map { n -> "$it/$n" }.orEmpty() },
) : PathLookup {
    /** Directories checked after PATH, in order (user installs first, then system, then Flatpak and snap exports). */
    val commonDirs: List<String> get() = buildList {
        if (home.isNotEmpty()) {
            add("$home/.local/bin"); add("$home/bin"); add("$home/.deno/bin"); add("$home/.bun/bin"); add("$home/.volta/bin")
            add("$home/.local/share/pnpm"); add("$home/.local/share/flatpak/exports/bin"); add("$home/.nix-profile/bin")
            // nvm keeps one directory per node version; the newest by name is good enough for a fallback.
            listDirs("$home/.nvm/versions/node").asReversed().forEach { add("$it/bin") }
        }
        addAll(listOf("/usr/local/bin", "/usr/bin", "/bin", "/var/lib/flatpak/exports/bin", "/snap/bin", "/home/linuxbrew/.linuxbrew/bin"))
    }

    fun locate(name: String, configured: String? = null): FoundBinary {
        val setting = configured?.trim().orEmpty()
        // A configured value with a slash is a path and nothing else; a bare name that differs from the default is looked up as that name.
        if (setting.contains('/')) {
            return if (isExecutable(setting)) FoundBinary(name, setting, "setting")
            else FoundBinary(name, null, "the configured path $setting is not an executable file")
        }
        val wanted = setting.ifEmpty { name }
        pathEnv().split(File.pathSeparator).filter { it.isNotEmpty() }.map { "$it/$wanted" }.firstOrNull(isExecutable)
            ?.let { return FoundBinary(name, it, "PATH") }
        commonDirs.map { "$it/$wanted" }.firstOrNull(isExecutable)
            ?.let { return FoundBinary(name, it, "common location") }
        return FoundBinary(name, null, "not on PATH or in ${commonDirs.take(3).joinToString(", ")} and other common locations")
    }

    /**
     * Every distinct executable called [name] on PATH or in the common locations, PATH order first. Used for yt-dlp, which is
     * often installed twice (a distro package and a pip/pipx copy) and the two can be months apart in age.
     */
    fun locateAll(name: String): List<FoundBinary> {
        val seen = HashSet<String>()
        val onPath = pathEnv().split(File.pathSeparator).filter { it.isNotEmpty() }.map { "$it/$name" }
        return (onPath.map { it to "PATH" } + commonDirs.map { "$it/$name" }.map { it to "common location" })
            .filter { (path, _) -> isExecutable(path) && seen.add(runCatching { File(path).canonicalPath }.getOrDefault(path)) }
            .map { (path, source) -> FoundBinary(name, path, source) }
    }

    override fun find(name: String): String? = locate(name).path
}
