package com.greenaltambe.vela.cast

import com.greenaltambe.vela.player.PlaybackMetadata
import com.greenaltambe.vela.player.PlaybackPlan
import com.greenaltambe.vela.player.PlaybackSession
import com.greenaltambe.vela.player.PlayerController
import com.greenaltambe.vela.player.PlayerEvent
import com.greenaltambe.vela.player.PlayerState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** A device on the network that can be sent a video to (a TV or speaker). [id] is stable across discoveries. */
data class CastTarget(val id: String, val name: String)

/** One way of finding and driving cast devices (DLNA/UPnP today). The controller it returns plays [PlaybackPlan]s on the device. */
interface CastBackend {
    /** Devices found so far; empty until [startDiscovery] has found some. */
    val targets: StateFlow<List<CastTarget>>

    fun startDiscovery()
    fun stopDiscovery()

    /** A controller that drives [target]. Throws if the device cannot be used. */
    fun connect(target: CastTarget): PlayerController
}

/**
 * The controller the [PlaybackSession] talks to: the phone's/PC's own player until a cast target is picked, then that device.
 * Calls go to whichever is active; [state] and [events] follow it.
 */
class SwitchingController(private val local: PlayerController) : PlayerController {
    private val remote = MutableStateFlow<PlayerController?>(null)
    private val active: PlayerController get() = remote.value ?: local

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val state: StateFlow<PlayerState> = remote.flatMapLatest { (it ?: local).state }.stateIn(scope, SharingStarted.Eagerly, local.state.value)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val events: Flow<PlayerEvent> = remote.flatMapLatest { (it ?: local).events }

    fun useRemote(controller: PlayerController?) { if (controller != null) local.stop(); remote.value = controller }
    val isRemote: Boolean get() = remote.value != null

    override fun load(plan: PlaybackPlan, startPositionMs: Long, playWhenReady: Boolean, metadata: PlaybackMetadata) = active.load(plan, startPositionMs, playWhenReady, metadata)
    override fun play() = active.play()
    override fun pause() = active.pause()
    override fun seekTo(positionMs: Long) = active.seekTo(positionMs)
    override fun goLive() = active.goLive()
    override fun setSpeed(speed: Float) = active.setSpeed(speed)
    override fun setVolume(volume: Float) = active.setVolume(volume)
    override val canBoost: Boolean get() = active.canBoost
    override fun setBoost(db: Int) = active.setBoost(db)
    override fun stop() = active.stop()
    override fun release() { remote.value?.release(); local.release(); scope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }
}

/**
 * Cast on the user's side: which devices exist, which one is connected, and the switch between playing here and there.
 * Connecting re-plans the current video for a TV (one muxed or HLS stream, see [com.greenaltambe.vela.player.PlaybackConstraints.castOnly])
 * and resumes at the same position; disconnecting does the same back to the local player.
 */
class CastManager(private val backend: CastBackend, val controller: SwitchingController) {
    val targets: StateFlow<List<CastTarget>> = backend.targets

    private val _connected = MutableStateFlow<CastTarget?>(null)
    val connected: StateFlow<CastTarget?> = _connected

    private val _error = MutableStateFlow<String?>(null)
    /** Why the last [connect] failed, or null. */
    val error: StateFlow<String?> = _error

    /** True while a device is the output; the session reads this to pick TV-playable streams. */
    val casting: Boolean get() = _connected.value != null

    fun startDiscovery() = backend.startDiscovery()
    fun stopDiscovery() = backend.stopDiscovery()

    fun connect(target: CastTarget, session: PlaybackSession) {
        _error.value = null
        val remote = try { backend.connect(target) } catch (e: Exception) { _error.value = e.message ?: "Could not connect to ${target.name}"; return }
        val position = controller.state.value.positionMs
        controller.useRemote(remote)
        _connected.value = target
        session.replan(position)
    }

    fun disconnect(session: PlaybackSession) {
        if (_connected.value == null) return
        val position = controller.state.value.positionMs
        controller.stop() // stops the TV
        controller.useRemote(null)
        _connected.update { null }
        session.replan(position)
    }
}
