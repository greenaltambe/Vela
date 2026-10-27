package com.greenaltambe.vela.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.greenaltambe.vela.player.PlaybackSession
import com.greenaltambe.vela.player.PlaybackStats
import com.greenaltambe.vela.player.PlayerHost
import com.greenaltambe.vela.player.PlayerState
import kotlinx.coroutines.flow.StateFlow

/** The Android [PlayerHost]: Media3's [PlayerView] as the surface, [PlaybackRuntime]'s session as the driver. */
class AndroidPlayerHost(
    private val runtime: PlaybackRuntime,
    private val beforePlayback: () -> Unit,
) : PlayerHost {
    override val session: PlaybackSession get() = runtime.session
    override val controllerState: StateFlow<PlayerState> get() = runtime.controller.state
    override val stats: StateFlow<PlaybackStats> get() = runtime.controller.stats

    @Composable
    override fun Surface(modifier: Modifier) {
        var view: PlayerView? = null
        AndroidView(
            modifier = modifier,
            factory = { context ->
                PlayerView(context).apply {
                    useController = false // our own controls
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = runtime.player
                    view = this
                }
            },
        )
        // Detach when the screen goes away so a backgrounded, audio-only player does not keep decoding video.
        DisposableEffect(Unit) { onDispose { view?.player = null } }
    }

    override fun onBeforePlayback() = beforePlayback()
}
