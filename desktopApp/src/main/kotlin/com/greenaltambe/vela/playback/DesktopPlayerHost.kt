package com.greenaltambe.vela.playback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.greenaltambe.vela.player.mpv.cappedRenderSize
import com.greenaltambe.vela.player.mpv.parseVideoSize
import com.greenaltambe.vela.player.PlaybackSession
import com.greenaltambe.vela.player.PlaybackStats
import com.greenaltambe.vela.player.PlayerHost
import com.greenaltambe.vela.player.PlayerState
import kotlinx.coroutines.flow.StateFlow

/** The desktop [PlayerHost]: libmpv's software-rendered frames drawn on a Compose canvas. */
class DesktopPlayerHost(private val runtime: DesktopPlaybackRuntime) : PlayerHost {
    override val session: PlaybackSession get() = runtime.session
    override val controllerState: StateFlow<PlayerState> get() = runtime.controller.state
    override val stats: StateFlow<PlaybackStats> get() = runtime.controller.stats

    @Volatile private var sink: SkiaFrameSink? = null

    /** Frames drawn to the window by the current surface; for measurements. */
    val framesDrawn: Long get() = sink?.framesDrawn?.get() ?: 0

    @Composable
    override fun Surface(modifier: Modifier) {
        val controller = runtime.controller
        val sink = remember { SkiaFrameSink() }
        DisposableEffect(sink) {
            controller.attachSink(sink)
            this@DesktopPlayerHost.sink = sink
            onDispose {
                this@DesktopPlayerHost.sink = null
                controller.detachSink(sink)
                sink.close()
            }
        }
        var widget by remember { mutableStateOf(IntSize.Zero) }
        val stats by controller.stats.collectAsState()
        val videoSize = parseVideoSize(stats.videoResolution)
        // mpv renders at min(widget, video) size, aspect-correct, and Skia stretches that over the widget: asking
        // mpv's CPU scaler to upscale to a full-screen size costs far more than a GPU stretch.
        LaunchedEffect(widget, videoSize) {
            // -Dvela.noRenderCap=true turns the cap off, to measure what it saves.
            val (w, h) = if (System.getProperty("vela.noRenderCap") != null) widget.width to widget.height
            else cappedRenderSize(widget.width, widget.height, videoSize?.first ?: 0, videoSize?.second ?: 0)
            controller.setSurfaceSize(w, h)
        }
        Canvas(
            modifier
                .background(Color.Black)
                .onSizeChanged { widget = it },
        ) {
            sink.version.longValue // read in the draw phase: each finished frame redraws, nothing recomposes
            sink.draw(drawContext.canvas.nativeCanvas, size.width, size.height)
        }
    }
}
