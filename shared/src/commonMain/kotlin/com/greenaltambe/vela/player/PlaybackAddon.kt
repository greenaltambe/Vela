package com.greenaltambe.vela.player

import kotlinx.coroutines.CoroutineScope

/** What an add-on may ask of the session that hosts it. */
interface AddonHost {
    /** For add-on work that should live and die with the session. */
    val scope: CoroutineScope

    fun seekTo(positionMs: Long)

    /** Silences the output on top of the user's own volume/mute (SponsorBlock "mute" segments). */
    fun setAddonMuted(muted: Boolean)
}

/**
 * A feature that watches playback and may act on it (SponsorBlock). The session calls these on its own thread, never
 * blocks on them and never lets one's failure stop playback: an add-on that throws is skipped for that call.
 */
interface PlaybackAddon {
    fun attach(host: AddonHost) {}

    /** A new video was started. [channelId] is empty when the caller had no hint; [offline] when a downloaded file plays (no network may be used). */
    fun onVideo(videoId: String, channelId: String, offline: Boolean) {}

    fun onPlayerState(state: PlayerState) {}

    /** The session was stopped. */
    fun onStop() {}
}
