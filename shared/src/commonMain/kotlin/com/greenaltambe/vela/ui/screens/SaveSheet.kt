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
import com.greenaltambe.vela.auth.ActionResult
import kotlinx.coroutines.launch

/**
 * "Save to...": the account's Watch later (when signed in) and every local playlist as a row you tap to add the video or take it out (a check marks the
 * ones that hold it). The sheet stays open so one visit can change several lists; [onChanged] tells the page to refresh its Save button.
 */
@Composable
fun SaveSheet(services: AppServices, video: VideoSummary, onMessage: (note: String?, error: String?) -> Unit, onChanged: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var revision by remember { mutableStateOf(0) }
    val lists = remember(revision) { services.playlists.all() }
    val signedIn = services.actions != null && services.accounts.active.value.isSignedIn

    PlayerMenuSheet("Save to", onDismiss, subtitle = video.title) {
        var i = 0
        if (signedIn) PlayerMenuRow(
            "Watch later", selected = false, index = i++, leading = AppIcons.watchLater, supporting = "On your YouTube account",
            onClick = {
                scope.launch {
                    when (val r = services.actions!!.addToPlaylist("WL", video.id)) {
                        is ActionResult.Failed -> onMessage(null, "Could not add to Watch later: ${r.message}")
                        is ActionResult.Queued -> onMessage(r.message, null)
                        else -> onMessage("Added to Watch later", null)
                    }
                }
                onDismiss()
            },
            modifier = Modifier.testTag("video.saveWatchLater"),
        )
        if (lists.isEmpty() && !signedIn) Text("No playlists yet. Create one in Library.", Modifier.padding(horizontal = Spacing.gutter), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        lists.forEach { list ->
            val has = remember(revision, list.id) { services.playlists.contains(list.id, video.id) }
            PlayerMenuRow(
                list.name, selected = has, index = i++, leading = if (list.isWatchLater) AppIcons.watchLater else AppIcons.playlist,
                supporting = (if (list.isWatchLater) "On this device · " else "") + "${list.itemCount} video${if (list.itemCount == 1) "" else "s"}",
                onClick = {
                    if (has) services.playlists.remove(list.id, video.id) else services.playlists.add(list.id, listOf(video), services.clock.now())
                    revision++; onChanged()
                },
            )
        }
        // A new playlist, made and saved to in one step.
        var creating by remember { mutableStateOf(false) }
        var newName by remember { mutableStateOf("") }
        if (!creating) androidx.compose.material3.FilledTonalButton(
            onClick = { creating = true }, shapes = androidx.compose.material3.ButtonDefaults.shapes(),
            modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm).testTag("video.save.new"),
        ) { Text("New playlist", maxLines = 1) }
        else androidx.compose.foundation.layout.Row(
            Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sm),
        ) {
            androidx.compose.material3.OutlinedTextField(newName, { newName = it }, Modifier.weight(1f).testTag("video.save.newName"), singleLine = true, label = { Text("Playlist name") })
            androidx.compose.material3.FilledTonalButton(
                onClick = {
                    val id = services.playlists.create(newName.trim(), services.clock.now())
                    services.playlists.add(id, listOf(video), services.clock.now())
                    creating = false; newName = ""; revision++; onChanged()
                },
                enabled = newName.isNotBlank(), shapes = androidx.compose.material3.ButtonDefaults.shapes(), modifier = Modifier.testTag("video.save.create"),
            ) { Text("Create", maxLines = 1) }
        }
    }
}

/** Adds several videos (a selection) to one playlist, with a toast to say so. */
@Composable
fun SaveManySheet(services: AppServices, videos: List<VideoSummary>, onDismiss: () -> Unit) {
    val toasts = com.greenaltambe.vela.ui.look.LocalUndoToasts.current
    val lists = remember { services.playlists.all() }
    PlayerMenuSheet(if (videos.size == 1) "Save to" else "Save ${videos.size} videos to", onDismiss, subtitle = videos.firstOrNull()?.title?.takeIf { videos.size == 1 }) {
        if (lists.isEmpty()) Text("No playlists yet. Create one in Library.", Modifier.padding(horizontal = Spacing.gutter), style = MaterialTheme.typography.bodyMedium)
        lists.forEachIndexed { i, list ->
            PlayerMenuRow(
                list.name, selected = false, index = i, leading = if (list.isWatchLater) AppIcons.watchLater else AppIcons.playlist,
                supporting = "${list.itemCount} video${if (list.itemCount == 1) "" else "s"}",
                onClick = {
                    services.playlists.add(list.id, videos, services.clock.now())
                    toasts.show("Saved ${videos.size} to ${list.name}")
                    onDismiss()
                },
                modifier = Modifier.testTag("history.saveTo"),
            )
        }
    }
}
