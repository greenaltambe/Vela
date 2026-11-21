package com.greenaltambe.vela

import androidx.compose.runtime.snapshotFlow
import com.greenaltambe.vela.player.MediaInfo
import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.player.PlayerHost
import com.greenaltambe.vela.player.PlayerState
import com.greenaltambe.vela.player.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * What the desktop-wide things (media keys / MPRIS, keeping the screen awake) see of the tabs' separate players. The media widgets follow the player of the
 * tab in front (the focused window's selected tab); the screen stays awake while any tab's player is playing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TabPlayerBridge(private val workspace: Workspace, scope: CoroutineScope) {
    /** The player of the tab in front, or null while it has none yet. */
    fun frontHost(): PlayerHost? = (workspace.focused ?: workspace.windows.firstOrNull())?.selected?.player?.host

    private val front = snapshotFlow { frontHost() }

    val frontState: StateFlow<PlayerState> = front.flatMapLatest { it?.controllerState ?: flowOf(PlayerState()) }.stateIn(scope, SharingStarted.Eagerly, PlayerState())
    val frontMedia: StateFlow<MediaInfo?> = front.flatMapLatest { it?.session?.media ?: flowOf(null) }.stateIn(scope, SharingStarted.Eagerly, null)

    private val hosts = snapshotFlow { workspace.allTabs().mapNotNull { it.player?.host } }
    // The first player that is playing, else the first there is (or nothing).
    private val busiest = hosts.flatMapLatest { list ->
        if (list.isEmpty()) flowOf(PlayerState() to SessionState.Idle)
        else combine(list.map { h -> combine(h.controllerState, h.session.state) { p, s -> p to s } }) { all -> all.firstOrNull { it.first.status == PlaybackStatus.PLAYING } ?: all.first() }
    }
    /** For the screen-saver inhibitor: the state of whichever tab plays. */
    val anyState: StateFlow<PlayerState> = busiest.map { it.first }.stateIn(scope, SharingStarted.Eagerly, PlayerState())
    val anySession: StateFlow<SessionState> = busiest.map { it.second }.stateIn(scope, SharingStarted.Eagerly, SessionState.Idle)
}
