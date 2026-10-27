package com.greenaltambe.vela.player.mpv

/** Where to look for libmpv. Tried in order; the first that loads wins. */
object MpvLibrary {
    /** `-Dvela.libmpv=/path/libmpv.so.2` or `VELA_LIBMPV`, then the soname Fedora/Debian ship, then the bare name. */
    fun candidates(property: String? = System.getProperty("vela.libmpv"), env: String? = System.getenv("VELA_LIBMPV")): List<String> =
        listOfNotNull(property?.takeIf { it.isNotBlank() }, env?.takeIf { it.isNotBlank() }) + listOf("libmpv.so.2", "mpv")

    /** A JNA-backed backend, or null with the reason when libmpv cannot be loaded. */
    fun load(): Result<MpvBackend> = runCatching { JnaMpvBackend.load(candidates()) }
}
