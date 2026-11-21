package com.greenaltambe.vela.ui.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.greenaltambe.vela.modules.sponsorblock.SponsorBlockAddon
import com.greenaltambe.vela.player.PlayerHost

/**
 * The player of one desktop tab: each tab plays on its own, like a browser's, so what one tab plays never shows in another's mini player. The player
 * (a libmpv instance) is created the first time the tab needs it ([ensure]) and released with the tab ([release]). [sponsorBlock] is this player's own
 * SponsorBlock addon (it follows one session). [persistsNowPlaying]: only one tab keeps the "restore the last video" snapshot, so tabs do not overwrite it.
 * Null on Android, where the app's one player is `AppServices.player`.
 */
class TabPlayer(
    private val create: (register: (AutoCloseable) -> Unit) -> PlayerHost?,
    val sponsorBlock: SponsorBlockAddon?,
    val persistsNowPlaying: Boolean = false,
) {
    /** The player once it exists (state: a screen reading it recomposes when it appears). */
    var host: PlayerHost? by mutableStateOf(null)
        private set
    private var tried = false
    private val closers = mutableListOf<AutoCloseable>()

    /** The player, created now if this is the first use. Null when the platform cannot make one (no libmpv). */
    fun ensure(): PlayerHost? {
        if (host == null && !tried) { tried = true; host = create { closers += it } }
        return host
    }

    /** Stops and frees the player (the tab closed). Safe to call twice. */
    fun release() {
        host?.session?.stop()
        closers.reversed().forEach { runCatching { it.close() } }
        closers.clear()
        host = null
    }
}

val LocalTabPlayer = staticCompositionLocalOf<TabPlayer?> { null }
