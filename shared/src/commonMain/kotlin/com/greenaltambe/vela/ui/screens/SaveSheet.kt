package com.greenaltambe.vela.ui.screens

import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.Spacing
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.ui.player.PlayerMenuRow
import com.greenaltambe.vela.ui.player.PlayerMenuSheet
import kotlinx.coroutines.launch
import kotlinx.coroutines.launch

/**
 * "Save to...": the account's Watch later (when signed in) and every local playlist as a row you tap to add the video or take it out (a check marks the
 * ones that hold it). The sheet stays open so one visit can change several lists; [onChanged] tells the page to refresh its Save button.
 */
@Composable
fun SaveSheet(services: AppServices, video: VideoSummary, onMessage: (note: String?, error: String?) -> Unit, onChanged: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val toasts = com.greenaltambe.vela.ui.look.LocalUndoToasts.current
    var revision by remember { mutableStateOf(0) }
    val serviceRevision = playlistRevision(services)
    val lists = remember(revision, serviceRevision) { services.playlists.all().filter { !it.isReadOnly } }
    var creating by remember { mutableStateOf(false) }

    if (creating) NewPlaylistDialog(services, onDismiss = { creating = false }) { name, synced ->
        creating = false
        scope.launch {
            val made = services.playlistService.create(name, synced)
            val id = made.id
            if (id == null || !made.ok) { onMessage(null, made.message ?: "Could not create the playlist"); return@launch }
            val r = services.playlistService.add(id, listOf(video))
            revision++; onChanged()
            if (!r.ok) onMessage(null, r.message) else toasts.show("Added to $name") { scope.launch { services.playlistService.remove(id, listOf(video.id)); onChanged() } }
        }
    }

    PlayerMenuSheet("Save to", onDismiss, subtitle = video.title) {
        // One row per playlist (Watch later included): a check marks the ones that hold the video, a tap adds or takes it out, and a synced one says so.
        if (lists.isEmpty()) Text("No playlists yet.", Modifier.padding(horizontal = Spacing.gutter), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        lists.forEachIndexed { i, list ->
            val has = remember(revision, serviceRevision, list.id) { services.playlists.contains(list.id, video.id) }
            PlayerMenuRow(
                list.name, selected = has, index = i, leading = if (list.isWatchLater) AppIcons.watchLater else AppIcons.playlist,
                supporting = playlistSupporting(list),
                onClick = { toggleInPlaylist(scope, services, toasts, list, video, has) { revision++; onChanged() } },
                modifier = Modifier.testTag(if (list.isWatchLater) "video.saveWatchLater" else "video.save.row"),
            )
        }
        androidx.compose.material3.FilledTonalButton(
            onClick = { creating = true }, shapes = androidx.compose.material3.ButtonDefaults.shapes(),
            modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm).testTag("video.save.new"),
        ) { Text("New playlist", maxLines = 1) }
    }
}

/** Adds several videos (a selection) to one playlist, with a toast to say so. */
@Composable
fun SaveManySheet(services: AppServices, videos: List<VideoSummary>, onDismiss: () -> Unit) {
    val toasts = com.greenaltambe.vela.ui.look.LocalUndoToasts.current
    val scope = rememberCoroutineScope()
    val lists = remember { services.playlists.all().filter { !it.isReadOnly } }
    PlayerMenuSheet(if (videos.size == 1) "Save to" else "Save ${videos.size} videos to", onDismiss, subtitle = videos.firstOrNull()?.title?.takeIf { videos.size == 1 }) {
        if (lists.isEmpty()) Text("No playlists yet. Create one in Library.", Modifier.padding(horizontal = Spacing.gutter), style = MaterialTheme.typography.bodyMedium)
        lists.forEachIndexed { i, list ->
            PlayerMenuRow(
                list.name, selected = false, index = i, leading = if (list.isWatchLater) AppIcons.watchLater else AppIcons.playlist,
                supporting = playlistSupporting(list),
                onClick = {
                    services.playlistService.launch {
                        val r = services.playlistService.add(list.id, videos)
                        toasts.show(if (!r.ok) r.message ?: "Could not save to ${list.name}" else "Saved ${videos.size} to ${list.name}" + (if (r.pending) ". It will reach YouTube later." else ""))
                    }
                    onDismiss()
                },
                modifier = Modifier.testTag("history.saveTo"),
            )
        }
    }
}
