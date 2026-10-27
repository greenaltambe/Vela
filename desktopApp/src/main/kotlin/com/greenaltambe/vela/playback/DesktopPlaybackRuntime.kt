package com.greenaltambe.vela.playback

import com.greenaltambe.vela.core.network.FixedNetworkMonitor
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.WatchHistoryRepository
import com.greenaltambe.vela.extractor.Extractor
import com.greenaltambe.vela.player.PlaybackAddon
import com.greenaltambe.vela.player.PlaybackSession
import com.greenaltambe.vela.player.mpv.MpvConfig
import com.greenaltambe.vela.player.mpv.MpvLibrary
import com.greenaltambe.vela.player.mpv.MpvPlayerController
import com.greenaltambe.vela.player.mpv.constraintsFromDecoderList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * The libmpv player and the [PlaybackSession] that drives it, as one unit with one [close]. [create] returns null
 * (with the reason) when libmpv cannot be loaded, so the app still starts, without playback.
 */
class DesktopPlaybackRuntime private constructor(
    val controller: MpvPlayerController,
    extractor: Extractor,
    settings: SettingsResolver,
    history: WatchHistoryRepository,
    addons: List<PlaybackAddon>,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // What this machine can decode (Fedora's ffmpeg-free has no H.264) decides which streams are asked for.
    private val constraints = constraintsFromDecoderList(controller.property("decoder-list"))

    val session = PlaybackSession(
        extractor, controller, settings, history, scope,
        network = FixedNetworkMonitor(), constraints = { constraints }, addons = addons,
    )

    override fun close() {
        session.stop()
        scope.cancel()
        controller.release()
    }

    companion object {
        fun create(extractor: Extractor, settings: SettingsResolver, history: WatchHistoryRepository, addons: List<PlaybackAddon> = emptyList()): Result<DesktopPlaybackRuntime> =
            MpvLibrary.load().mapCatching { backend ->
                DesktopPlaybackRuntime(MpvPlayerController(backend, MpvConfig()), extractor, settings, history, addons)
            }
    }
}
