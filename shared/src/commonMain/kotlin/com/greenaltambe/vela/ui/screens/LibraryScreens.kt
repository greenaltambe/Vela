package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.data.PlaylistSummary
import com.greenaltambe.vela.data.WatchRecord
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.library.groupHistoryByDay
import com.greenaltambe.vela.ui.components.CardExtras
import com.greenaltambe.vela.ui.components.EmptyState
import com.greenaltambe.vela.ui.components.PrimaryButton
import com.greenaltambe.vela.ui.components.SectionHeader
import com.greenaltambe.vela.ui.components.VideoCard
import com.greenaltambe.vela.ui.components.VideoCardStyle
import com.greenaltambe.vela.ui.look.ComponentIds
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.toColor
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route

/** A tappable row with a title and an optional second line; at least 48 dp tall. */
@Composable
fun ListRow(title: String, modifier: Modifier = Modifier, subtitle: String? = null, onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = Vela.minTouch).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
        Column(Modifier.weight(1f).padding(vertical = Vela.xs)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Vela.colors.onSurfaceVariant.toColor())
        }
        trailing?.invoke()
    }
}

@Composable
fun LibraryScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    var revision by remember { mutableStateOf(0) }
    val playlists = remember(revision) { services.playlists.all() }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<PlaylistSummary?>(null) }
    var deleting by remember { mutableStateOf<PlaylistSummary?>(null) }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 900.dp).fillMaxSize().testTag("library.list"), contentPadding = Vela.contentPadding) {
            item { ListRow("History", subtitle = "What you watched on this device", onClick = { navigator.push(Route.History) }, modifier = Modifier.testTag("library.history")) }
            if (services.downloads != null) item { ListRow("Downloads", subtitle = "Saved for offline", onClick = { navigator.push(Route.Downloads) }, modifier = Modifier.testTag("library.downloads")) }
            item { SectionHeader("Playlists") { TextButton(onClick = { creating = true }, modifier = Modifier.testTag("library.newPlaylist")) { Text("New playlist", maxLines = 1) } } }
            items(playlists, key = { it.id }) { p ->
                ListRow(
                    p.name, subtitle = "${p.itemCount} video${if (p.itemCount == 1) "" else "s"}", onClick = { navigator.push(Route.Playlist(p.id)) },
                    modifier = Modifier.testTag(ComponentIds.PLAYLIST_ROW),
                    trailing = if (!p.isWatchLater) ({
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Vela.icons.more, "Playlist options") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = p })
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; deleting = p })
                            }
                        }
                    }) else null,
                )
            }
        }
    }

    if (creating) NameDialog("New playlist", "", onDismiss = { creating = false }) { name -> services.playlists.create(name, services.clock.now()); revision++; creating = false }
    renaming?.let { p -> NameDialog("Rename playlist", p.name, onDismiss = { renaming = null }) { name -> services.playlists.rename(p.id, name, services.clock.now()); revision++; renaming = null } }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${p.name}\"?", maxLines = 2) },
            confirmButton = { TextButton(onClick = { services.playlists.delete(p.id); revision++; deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun PlaylistScreen(services: AppServices, navigator: Navigator, playlistId: Long, modifier: Modifier = Modifier) {
    var revision by remember { mutableStateOf(0) }
    val summary = remember(revision) { services.playlists.all().firstOrNull { it.id == playlistId } }
    val items = remember(revision) { services.playlists.items(playlistId) }
    val videos = remember(items) { items.map { it.toSummary() } }
    var downloadAll by remember { mutableStateOf(false) }
    if (downloadAll) DownloadDialog(services, videos, onDismiss = { downloadAll = false })
    val extras = rememberCardExtras(services, videos)

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 900.dp).fillMaxSize().testTag("playlist.list"), contentPadding = Vela.contentPadding, verticalArrangement = Arrangement.spacedBy(Vela.md)) {
            item {
                SectionHeader(summary?.name ?: "Playlist") {
                    Row(horizontalArrangement = Arrangement.spacedBy(Vela.xs)) {
                        if (services.downloads != null) TextButton(onClick = { downloadAll = true }, enabled = videos.isNotEmpty(), modifier = Modifier.testTag("playlist.downloadAll")) { Text("Download all", maxLines = 1) }
                        PrimaryButton("Play all", onClick = { navigator.playAll(videos) }, enabled = videos.isNotEmpty(), modifier = Modifier.testTag("playlist.playAll"))
                    }
                }
            }
            if (items.isEmpty()) item { EmptyState("This playlist is empty. Add videos from the ⋮ menu on any video.") }
            items(items.size, key = { items[it].videoId }) { index ->
                val v = videos[index]
                VideoCard(
                    v, onClick = { navigator.push(Route.Video(v)) }, style = VideoCardStyle.HORIZONTAL, extras = extras(v),
                    trailing = {
                        Column {
                            IconButton(onClick = { services.playlists.move(playlistId, index, index - 1); revision++ }, enabled = index > 0) { Text("↑") }
                            IconButton(onClick = { services.playlists.move(playlistId, index, index + 1); revision++ }, enabled = index < items.lastIndex) { Text("↓") }
                            IconButton(onClick = { services.playlists.remove(playlistId, v.id); revision++ }) { Icon(com.greenaltambe.vela.ui.look.Vela.icons.close, "Remove from playlist") }
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun HistoryScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    var revision by remember { mutableStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    val records: List<WatchRecord> = remember(revision) { services.history.recent() }
    val days = remember(records) { groupHistoryByDay(records, services.clock.now(), services.utcOffsetMinutes()) }
    val progress = remember(records) { services.history.progress() }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 900.dp).fillMaxSize().testTag("history.list"), contentPadding = Vela.contentPadding, verticalArrangement = Arrangement.spacedBy(Vela.md)) {
            item {
                SectionHeader("Watch history") {
                    TextButton(onClick = { confirmClear = true }, enabled = records.isNotEmpty(), modifier = Modifier.testTag("history.clear")) { Text("Clear all", maxLines = 1) }
                }
            }
            if (records.isEmpty()) item { EmptyState("Nothing watched yet.") }
            days.forEach { day ->
                item(key = "day-${day.label}") { Text(day.label, style = MaterialTheme.typography.titleMedium, maxLines = 1) }
                items(day.records, key = { "h-${it.id}" }) { r ->
                    val v = VideoSummary(r.videoId, r.title, r.channelId, "", durationSeconds = r.durationMs / 1000)
                    VideoCard(
                        v, onClick = { navigator.push(Route.Video(v)) }, style = VideoCardStyle.HORIZONTAL, extras = CardExtras(watched = progress[r.videoId]),
                        trailing = { IconButton(onClick = { services.history.deleteVideo(r.videoId); revision++ }) { Icon(Vela.icons.close, "Remove from history") } },
                    )
                }
            }
        }
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear all watch history?") },
        text = { Text("This only removes the history on this device.") },
        confirmButton = { TextButton(onClick = { services.history.clear(); revision++; confirmClear = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
}
