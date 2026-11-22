package com.greenaltambe.vela.ui.player

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.greenaltambe.vela.cast.CastManager
import com.greenaltambe.vela.player.PlaybackSession
import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.Spacing

/** The devices on the network this video can be sent to; searching runs while the sheet is open. */
@Composable
fun CastSheet(cast: CastManager, session: PlaybackSession, onDismiss: () -> Unit) {
    val targets by cast.targets.collectAsState()
    val connected by cast.connected.collectAsState()
    val error by cast.error.collectAsState()
    DisposableEffect(cast) { cast.startDiscovery(); onDispose { cast.stopDiscovery() } }
    PlayerMenuSheet("Cast to TV", onDismiss, subtitle = connected?.let { "Playing on ${it.name}" }) {
        if (connected != null) PlayerMenuRow("Stop casting", selected = false, onClick = { cast.disconnect(session); onDismiss() }, index = 0, leading = AppIcons.close, supporting = "Play on this device again")
        targets.forEachIndexed { i, t ->
            PlayerMenuRow(t.name, selected = t.id == connected?.id, onClick = { cast.connect(t, session); onDismiss() }, index = i + 1, leading = AppIcons.cast)
        }
        if (targets.isEmpty()) Text(
            "Looking for TVs and speakers on this Wi-Fi network (DLNA / UPnP). The device must be on the same network and have DLNA or \"media renderer\" turned on.",
            Modifier.padding(Spacing.gutter).testTag("cast.empty"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        error?.let { Text(it, Modifier.padding(Spacing.gutter).testTag("cast.error"), color = MaterialTheme.colorScheme.error) }
    }
}
