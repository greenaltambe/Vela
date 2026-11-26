package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.auth.AccountKind
import com.greenaltambe.vela.auth.PlaylistResult
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.data.PlaylistSummary
import com.greenaltambe.vela.data.PlaylistSync
import com.greenaltambe.vela.data.YOUTUBE_PLAYLIST_LIMIT
import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Bumps whenever a playlist changed (here, or by a sync), so a screen showing playlists re-reads. */
@Composable
fun playlistRevision(services: AppServices): Int = services.playlistService.revision.collectAsState().value

/** The second line shown for a playlist everywhere (Library card, Save sheet row): where it lives and what is going on. */
fun playlistSupporting(p: PlaylistSummary): String {
    val count = "${p.itemCount} video${if (p.itemCount == 1) "" else "s"}"
    val state = when {
        p.isSaved -> if (p.unavailableSince != null) "Saved, no longer on YouTube" else if (p.onAccount) "Saved, on YouTube" else "Saved"
        !p.isSynced -> "On this device"
        p.syncState == PlaylistSync.ERROR -> "Sync failed, tap to retry"
        p.syncState == PlaylistSync.LINKING -> "Syncing, ${p.pendingCount} left"
        p.pendingCount > 0 -> "${p.pendingCount} change${if (p.pendingCount == 1) "" else "s"} waiting"
        p.overflowCount > 0 -> "${p.overflowCount} not on YouTube"
        else -> "Synced with YouTube"
    }
    return "$state · $count"
}

/** The cloud mark of a synced playlist (nothing for a local one). */
@Composable
fun SyncBadge(p: PlaylistSummary, modifier: Modifier = Modifier) {
    if (!p.isSynced && !p.onAccount) return
    val desc = when {
        p.onAccount -> "In your YouTube library"
        p.syncState == PlaylistSync.ERROR -> "Sync failed"
        p.syncState == PlaylistSync.LINKING -> "Syncing with YouTube"
        p.pendingCount > 0 -> "Changes waiting to reach YouTube"
        else -> "Synced with YouTube"
    }
    val tint = if (p.syncState == PlaylistSync.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Icon(AppIcons.cloud, desc, modifier.size(14.dp).testTag("playlist.syncBadge"), tint = tint)
}

/** The "Sync with my YouTube account" box, the same wording everywhere. Hidden for the local profile; disabled with the reason when sign-in is needed. */
@Composable
fun SyncCheckbox(services: AppServices, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, note: String = "Created as a private playlist on your account.") {
    if (services.accounts.active.value.kind != AccountKind.GOOGLE) return
    val reason = services.playlistService.unavailableReason()
    Column(modifier) {
        Row(Modifier.fillMaxWidth().clickable(enabled = reason == null) { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked && reason == null, { onChange(it) }, enabled = reason == null, modifier = Modifier.testTag("playlist.syncCheckbox"))
            Text("Sync with my YouTube account", style = MaterialTheme.typography.bodyMedium)
        }
        Text(reason ?: note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 48.dp))
    }
}

/** A new playlist: a name and the sync box (which starts as the setting says). */
@Composable
fun NewPlaylistDialog(services: AppServices, onDismiss: () -> Unit, onCreate: (name: String, synced: Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var synced by remember { mutableStateOf(services.settings.resolve(Settings.libraryNewPlaylistSync) && services.playlistService.syncAvailable) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("New playlist") },
        text = { Column { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }, modifier = Modifier.testTag("library.nameField")); SyncCheckbox(services, synced, { synced = it }) } },
        confirmButton = { TextButton(onClick = { onCreate(name.trim(), synced && services.playlistService.syncAvailable) }, enabled = name.isNotBlank(), modifier = Modifier.testTag("library.nameOk")) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** An existing playlist's sync settings: the same box. Turning it off never deletes anything on YouTube. */
@Composable
fun SyncSettingsDialog(services: AppServices, p: PlaylistSummary, onDismiss: () -> Unit, onApply: (Boolean) -> Unit) {
    // For a saved playlist the box says whether it is in the account's YouTube library too.
    val now = if (p.isSaved) p.onAccount else p.isSynced
    var synced by remember { mutableStateOf(now) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Sync settings", maxLines = 1) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("\"${p.name}\"", style = MaterialTheme.typography.titleSmall, maxLines = 2)
                SyncCheckbox(services, synced, { synced = it }, note = if (p.isSaved) "Also appears in your YouTube library." else "Created as a private playlist on your account.")
                Text(
                    if (p.isSaved) (if (synced) "Saved to your YouTube library. Removing it there removes it here at the next sync." else "It stays saved here. Your YouTube library is left as it is.")
                    else if (synced) (if (p.isSynced) "Changes you make here are sent to YouTube, and changes there come here." else "A private YouTube playlist is made and the videos here are added to it.")
                    else "The videos stay here. The YouTube playlist is left as it is; it is never deleted by turning this off.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onApply(synced) }, enabled = synced != now, modifier = Modifier.testTag("playlist.syncApply")) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Saving somebody else's playlist: what is saved and what that means. */
@Composable
fun SavePlaylistDialog(services: AppServices, title: String, onDismiss: () -> Unit, onSave: (onAccount: Boolean) -> Unit) {
    // The box starts where the one for a new playlist starts.
    var onAccount by remember { mutableStateOf(services.settings.resolve(Settings.libraryNewPlaylistSync) && services.playlistService.syncAvailable) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Save to library") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("\"$title\"", style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Text(
                    "Added to your Library. It stays the owner's: their changes show up, and you cannot edit it.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SyncCheckbox(services, onAccount, { onAccount = it }, note = "Also appears in your YouTube library.")
            }
        },
        confirmButton = { TextButton(onClick = { onSave(onAccount && services.playlistService.syncAvailable) }, modifier = Modifier.testTag("playlist.saveConfirm")) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Taking a saved playlist out of the library. */
@Composable
fun RemoveSavedDialog(services: AppServices, p: PlaylistSummary, onDismiss: () -> Unit, onRemove: (fromAccount: Boolean) -> Unit) {
    val reason = if (p.onAccount) services.playlistService.unavailableReason() else null
    var fromAccount by remember { mutableStateOf(p.onAccount && reason == null) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Remove from your library?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    "\"${p.name}\" leaves your Library. " + (if (p.unavailableSince != null) "It is no longer on YouTube, so it cannot be saved again. " else "The playlist itself is not changed. ") + "Downloaded videos stay.",
                    modifier = Modifier.testTag("playlist.removeText"),
                )
                if (p.onAccount) {
                    Row(Modifier.fillMaxWidth().clickable(enabled = reason == null) { fromAccount = !fromAccount }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(fromAccount, { fromAccount = it }, enabled = reason == null, modifier = Modifier.testTag("playlist.removeFromAccount"))
                        Text("Also remove it from my YouTube account", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (reason != null) Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onRemove(fromAccount) }, modifier = Modifier.testTag("playlist.removeConfirm")) { Text("Remove") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The delete dialog's wording: plain about what also happens on YouTube. */
fun deleteWarning(p: PlaylistSummary): String =
    if (p.isSynced) "Deletes it here and on your YouTube account. To keep the YouTube copy, stop syncing it first (Sync settings)." else "Deletes it from this device."

/**
 * What to do with the videos YouTube's playlist had no room for (its limit is [YOUTUBE_PLAYLIST_LIMIT]): add them to another synced playlist that has room, or to a
 * new one made for them. [onDone] gets the outcome's message.
 */
@Composable
fun OverflowDialog(services: AppServices, p: PlaylistSummary, onDismiss: () -> Unit, onDone: (PlaylistResult) -> Unit) {
    val scope = rememberCoroutineScope()
    val targets = remember(p.id) { services.playlistService.overflowTargets(p.id) }
    var choice by remember { mutableStateOf<Long?>(null) }   // null = a new playlist
    var working by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("${p.overflowCount} video${if (p.overflowCount == 1) "" else "s"} did not fit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("YouTube holds at most $YOUTUBE_PLAYLIST_LIMIT videos in a playlist. Add the rest to:", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth().clickable { choice = null }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(choice == null, { choice = null }, modifier = Modifier.testTag("overflow.new")); Text("A new synced playlist (private)")
                }
                targets.forEach { (t, room) ->
                    Row(Modifier.fillMaxWidth().clickable { choice = t.id }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(choice == t.id, { choice = t.id }, modifier = Modifier.testTag("overflow.target.${t.id}"))
                        Column { Text(t.name, maxLines = 1); Text("Room for $room more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { working = true; scope.launch { val r = services.playlistService.moveOverflow(p.id, choice); working = false; onDone(r) } }, enabled = !working, modifier = Modifier.testTag("overflow.confirm")) { Text(if (working) "Moving…" else "Add them") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text("Not now") } },
    )
}

/**
 * Adds [video] to [list], or takes it out when [has], and says what happened in a toast with Undo (and why, when YouTube refused or the change waits).
 * One helper for the Save sheet and the video menu, so a playlist behaves the same from either.
 */
fun toggleInPlaylist(
    @Suppress("UNUSED_PARAMETER") scope: kotlinx.coroutines.CoroutineScope, services: AppServices, toasts: com.greenaltambe.vela.ui.look.UndoToasts,
    list: PlaylistSummary, video: com.greenaltambe.vela.extractor.VideoSummary, has: Boolean, onChanged: () -> Unit,
) {
    val svc = services.playlistService
    svc.launch {
        val r = if (has) svc.remove(list.id, listOf(video.id)) else svc.add(list.id, listOf(video))
        onChanged()
        if (!r.ok) { toasts.show(r.message ?: "Could not change \"${list.name}\""); return@launch }
        val base = (if (has) "Removed from " else "Added to ") + list.name
        val text = when {
            r.pending -> "$base. It will reach YouTube later."
            r.overflow > 0 -> "$base. Not on YouTube: that playlist is full."
            else -> base
        }
        toasts.show(text) {
            svc.launch { if (has) svc.add(list.id, listOf(video)) else svc.remove(list.id, listOf(video.id)); onChanged() }
        }
    }
}
