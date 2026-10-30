package com.greenaltambe.vela.player.mpv

/** Where to look for libmpv. Tried in order; the first that loads wins. */
object MpvLibrary {
    /** `-Dvela.libmpv=/path/libmpv.so.2` or `VELA_LIBMPV`, then the soname Fedora/Debian ship, then the bare name. */
    fun candidates(
        property: String? = System.getProperty("vela.libmpv"), env: String? = System.getenv("VELA_LIBMPV"),
        windows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true),
        appDir: String? = runCatching { ProcessHandle.current().info().command().orElse(null)?.let { java.io.File(it).parent } }.getOrNull(),
        resourcesDir: String? = System.getProperty("compose.application.resources.dir"),
    ): List<String> {
        val explicit = listOfNotNull(property?.takeIf { it.isNotBlank() }, env?.takeIf { it.isNotBlank() })
        // Windows: libmpv-2.dll (or mpv-2.dll) next to the executable first, then anywhere on PATH.
        // The release build puts libmpv-2.dll into the app's resources folder (desktopApp/appResources/windows-x64, filled by CI).
        if (windows) return explicit + listOfNotNull(resourcesDir?.let { "$it\\libmpv-2.dll" }, appDir?.let { "$it\\libmpv-2.dll" }, appDir?.let { "$it\\mpv-2.dll" }) + listOf("libmpv-2", "mpv-2", "libmpv", "mpv")
        return explicit + listOf("libmpv.so.2", "mpv")
    }

    /** A JNA-backed backend, or null with the reason when libmpv cannot be loaded. */
    fun load(): Result<MpvBackend> = runCatching { JnaMpvBackend.load(candidates()) }
}
