package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.data.PlaylistSummary
import com.greenaltambe.vela.data.WatchRecord
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.library.groupHistoryByDay
import com.greenaltambe.vela.ui.components.PrimaryButton
import com.greenaltambe.vela.ui.components.SectionHeader
import com.greenaltambe.vela.ui.components.VideoCard
import com.greenaltambe.vela.ui.look.ComponentIds
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route

@Composable
fun LibraryScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    var revision by remember { mutableStateOf(0) }
    val playlists = remember(revision) { services.playlists.all() }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<PlaylistSummary?>(null) }
    var deleting by remember { mutableStateOf<PlaylistSummary?>(null) }

    LazyColumn(modifier.fillMaxSize().testTag("library.list"), verticalArrangement = Arrangement.spacedBy(Vela.sm), contentPadding = Vela.pagePadding) {
        item {
            Text("History", Modifier.fillMaxWidth().clickable { navigator.push(Route.History) }.padding(vertical = Vela.md).testTag("library.history"), style = MaterialTheme.typography.titleMedium)
        }
        if (services.downloads != null) item {
            Text("Downloads", Modifier.fillMaxWidth().clickable { navigator.push(Route.Downloads) }.padding(vertical = Vela.md).testTag("library.downloads"), style = MaterialTheme.typography.titleMedium)
        }
        item { SectionHeader("Playlists") { TextButton(onClick = { creating = true }, modifier = Modifier.testTag("library.newPlaylist")) { Text("New playlist") } } }
        items(playlists, key = { it.id }) { p ->
            Row(Modifier.fillMaxWidth().testTag(ComponentIds.PLAYLIST_ROW).clickable { navigator.push(Route.Playlist(p.id)) }.padding(vertical = Vela.sm), verticalAlignment = Alignment.CenterVertically) {
                Text("${p.name}  (${p.itemCount})", Modifier.weight(1f))
                if (!p.isWatchLater) {
                    TextButton(onClick = { renaming = p }) { Text("Rename") }
                    TextButton(onClick = { deleting = p }) { Text("Delete") }
                }
            }
        }
    }

    if (creating) NameDialog("New playlist", "", onDismiss = { creating = false }) { name ->
        services.playlists.create(name, services.clock.now()); revision++; creating = false
    }
    renaming?.let { p -> NameDialog("Rename playlist", p.name, onDismiss = { renaming = null }) { name ->
        services.playlists.rename(p.id, name, services.clock.now()); revision++; renaming = null
    } }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${p.name}\"?") },
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

    LazyColumn(modifier.fillMaxSize().testTag("playlist.list"), verticalArrangement = Arrangement.spacedBy(Vela.sm), contentPadding = Vela.pagePadding) {
        item {
            SectionHeader(summary?.name ?: "Playlist") {
                Row(horizontalArrangement = Arrangement.spacedBy(Vela.xs)) {
                    if (services.downloads != null) TextButton(onClick = { downloadAll = true }, enabled = videos.isNotEmpty(), modifier = Modifier.testTag("playlist.downloadAll")) { Text("Download all") }
                    PrimaryButton("Play all", onClick = { navigator.playAll(videos) }, enabled = videos.isNotEmpty(), modifier = Modifier.testTag("playlist.playAll"))
                }
            }
        }
        if (items.isEmpty()) item { Text("Empty. Add videos from the ⋮ menu on any video.") }
        items(items.size, key = { items[it].videoId }) { index ->
            val v = videos[index]
            VideoCard(
                v, onClick = { navigator.push(Route.Video(v)) },
                trailing = {
                    Column {
                        Row {
                            TextButton(onClick = { services.playlists.move(playlistId, index, index - 1); revision++ }, enabled = index > 0) { Text("↑") }
                            TextButton(onClick = { services.playlists.move(playlistId, index, index + 1); revision++ }, enabled = index < items.lastIndex) { Text("↓") }
                        }
                        TextButton(onClick = { services.playlists.remove(playlistId, v.id); revision++ }) { Text("Remove") }
                    }
                },
            )
        }
    }
}

@Composable
fun HistoryScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    var revision by remember { mutableStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    val records: List<WatchRecord> = remember(revision) { services.history.recent() }
    val days = remember(records) { groupHistoryByDay(records, services.clock.now(), services.utcOffsetMinutes()) }

    LazyColumn(modifier.fillMaxSize().testTag("history.list"), verticalArrangement = Arrangement.spacedBy(Vela.sm), contentPadding = Vela.pagePadding) {
        item {
            SectionHeader("Watch history") {
                TextButton(onClick = { confirmClear = true }, enabled = records.isNotEmpty(), modifier = Modifier.testTag("history.clear")) { Text("Clear all") }
            }
        }
        if (records.isEmpty()) item { Text("Nothing watched yet.") }
        days.forEach { day ->
            item(key = "day-${day.label}") { Text(day.label, style = MaterialTheme.typography.titleMedium) }
            items(day.records, key = { "h-${it.id}" }) { r ->
                val v = VideoSummary(r.videoId, r.title, r.channelId, "", durationSeconds = r.durationMs / 1000)
                VideoCard(
                    v, onClick = { navigator.push(Route.Video(v)) },
                    badge = progress(r),
                    trailing = { TextButton(onClick = { services.history.deleteVideo(r.videoId); revision++ }) { Text("Delete") } },
                )
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

private fun progress(r: WatchRecord): String? =
    if (r.durationMs > 0) "${(r.positionMs * 100 / r.durationMs).coerceIn(0, 100)}% watched" else null
