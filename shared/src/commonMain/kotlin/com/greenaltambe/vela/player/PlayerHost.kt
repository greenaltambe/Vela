package com.greenaltambe.vela.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * What the shared UI needs from a platform that can play video: the [session] to drive and a place to draw the
 * picture. Platforms without a player (desktop, for now) pass no host and the UI says so.
 */
interface PlayerHost {
    val session: PlaybackSession
    val controllerState: kotlinx.coroutines.flow.StateFlow<PlayerState>
    val stats: kotlinx.coroutines.flow.StateFlow<PlaybackStats>

    /** The video surface; fills [modifier]. */
    @Composable
    fun Surface(modifier: Modifier)

    /** The surface with the picture either fitted inside it (bars where the shapes differ) or zoomed to fill it (the edges cropped). Platforms that cannot zoom ignore [fill]. */
    @Composable
    fun Surface(modifier: Modifier, fill: Boolean) = Surface(modifier)

    /** Called just before a video is started from the UI, e.g. to ask for the notification permission. */
    fun onBeforePlayback() {}
}
