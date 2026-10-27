package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.data.DownloadState
import com.greenaltambe.vela.downloads.DownloadItem
import com.greenaltambe.vela.downloads.DownloadRequest
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.ui.components.SectionHeader
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import com.greenaltambe.vela.ui.platform.rememberFolderPicker

/** 1536 -> "1.5 KB", 3_400_000 -> "3.2 MB". */
fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble(); var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    val tenths = (v * 10).toLong()
    return (if (i == 0 || tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}") + " " + units[i]
}

/** Quality choices of the download dialog; null is "best available". */
val DownloadHeights: List<Int?> = listOf(null, 2160, 1440, 1080, 720, 480, 360)

/** Pick quality / audio-only for one or many videos, then queue them. Defaults come from the download settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadDialog(services: AppServices, videos: List<VideoSummary>, onDismiss: () -> Unit) {
    val downloads = services.downloads ?: return
    var audioOnly by remember { mutableStateOf(services.settings.resolve(Settings.downloadsAudioOnlyDefault)) }
    var preferAvc by remember { mutableStateOf(services.settings.resolve(Settings.downloadsPreferAvc)) }
    var height by remember { mutableStateOf(services.settings.resolve(Settings.downloadsDefaultQuality).height) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (videos.size == 1) "Download" else "Download ${videos.size} videos") },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
                if (videos.size == 1) Text(videos.first().title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
                    Switch(audioOnly, { audioOnly = it }, modifier = Modifier.testTag("download.audioOnly"))
                    Text("Audio only")
                }
                if (!audioOnly) {
                    Text("Highest quality", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Vela.xs)) {
                        DownloadHeights.forEach { h ->
                            FilterChip(selected = height == h, onClick = { height = h }, label = { Text(if (h == null) "Best" else "${h}p") }, modifier = Modifier.testTag("download.quality.${h ?: "best"}"))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
                        Switch(preferAvc, { preferAvc = it })
                        Text("Prefer H.264 (plays everywhere)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                services.player?.onBeforePlayback() // asks for the notification permission once on Android; no-op elsewhere
                videos.forEach { downloads.manager.enqueue(DownloadRequest(it, audioOnly = audioOnly, maxHeight = height, preferAvc = preferAvc)) }
                onDismiss()
            }, modifier = Modifier.testTag("download.confirm")) { Text("Download") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun DownloadsScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    val downloads = services.downloads
    if (downloads == null) { Text("Downloads are not available on this platform.", modifier); return }
    val items by downloads.manager.items.collectAsState()
    val waiting by downloads.manager.waiting.collectAsState()
    val settingsRevision by services.settings.revision.collectAsState()
    var confirmDelete by remember { mutableStateOf<DownloadItem?>(null) }
    val chooseFolder = rememberFolderPicker { path -> if (path != null) services.settings.set(Settings.downloadsFolder, path) }

    val storage = remember(settingsRevision) { downloads.storage() }
    val used = remember(items) { downloads.repo.completedBytes() }
    val free = remember(items, settingsRevision) { storage.freeBytes() }
    val cap = services.settings.resolve(Settings.downloadsStorageCapGb)

    LazyColumn(modifier.fillMaxSize().testTag("downloads.list"), verticalArrangement = Arrangement.spacedBy(Vela.sm), contentPadding = Vela.pagePadding) {
        item {
            SectionHeader("Downloads")
            Text("Folder: ${storage.description}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("downloads.folder"))
            Row(horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
                OutlinedButton(onClick = chooseFolder, modifier = Modifier.testTag("downloads.chooseFolder")) { Text("Change folder") }
                OutlinedButton(onClick = { services.settings.set(Settings.downloadsFolder, "") }, enabled = services.settings.resolve(Settings.downloadsFolder).isNotEmpty()) { Text("Use default") }
            }
            Text(
                "Used by downloads: ${formatBytes(used)}" + (free?.let { " · free: ${formatBytes(it)}" } ?: "") +
                    if (cap > 0) " · cap: ${cap.toInt()} GB" + (if (used > cap * 1024L * 1024 * 1024) " (over)" else "") else "",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("downloads.storage"),
            )
            waiting?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("downloads.waiting")) }
        }
        if (items.isEmpty()) item { Text("Nothing downloaded yet. Use Download on a video page or the ⋮ menu of any video.") }
        items(items, key = { it.id }) { item -> DownloadRow(services, navigator, item, onDelete = { confirmDelete = item }) }
    }

    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(if (item.state == DownloadState.COMPLETED) "Delete this download?" else "Cancel and remove this download?") },
            text = { Text(item.title + if (item.state == DownloadState.COMPLETED) "\n\nThe file is deleted from the device." else "\n\nWhat was downloaded so far is deleted.") },
            confirmButton = { TextButton(onClick = { downloads.manager.remove(item.id); confirmDelete = null }, modifier = Modifier.testTag("downloads.confirmDelete")) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun DownloadRow(services: AppServices, navigator: Navigator, item: DownloadItem, onDelete: () -> Unit) {
    val manager = services.downloads!!.manager
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().testTag("downloads.row"), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
        Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
        Text(statusLine(item), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("downloads.status"))
        if (item.state == DownloadState.DOWNLOADING || item.state == DownloadState.QUEUED || item.state == DownloadState.PAUSED || item.state == DownloadState.MERGING) {
            val p = item.progress
            if (p != null && item.state != DownloadState.MERGING) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Vela.xs)) {
            when (item.state) {
                DownloadState.QUEUED, DownloadState.DOWNLOADING -> TextButton(onClick = { manager.pause(item.id) }, modifier = Modifier.testTag("downloads.pause")) { Text("Pause") }
                DownloadState.PAUSED -> TextButton(onClick = { manager.resume(item.id) }, modifier = Modifier.testTag("downloads.resume")) { Text("Resume") }
                DownloadState.FAILED -> TextButton(onClick = { manager.retry(item.id) }, modifier = Modifier.testTag("downloads.retry")) { Text("Retry") }
                DownloadState.COMPLETED -> TextButton(
                    onClick = { navigator.push(Route.Video(VideoSummary(item.videoId, item.title, item.channelId, ""))) },
                    modifier = Modifier.testTag("downloads.play"),
                ) { Text("Play") }
                DownloadState.MERGING -> {}
            }
            if (item.state != DownloadState.MERGING) TextButton(onClick = onDelete, modifier = Modifier.testTag("downloads.delete")) { Text(if (item.state == DownloadState.COMPLETED) "Delete" else "Cancel") }
        }
    }
}

internal fun statusLine(item: DownloadItem): String = when (item.state) {
    DownloadState.QUEUED -> "Queued · ${item.qualityLabel}"
    DownloadState.DOWNLOADING -> buildString {
        append("Downloading · ${item.qualityLabel}")
        item.progress?.let { append(" · ${(it * 100).toInt()}%") }
        append(" · ${formatBytes(item.bytesDone)}${item.bytesTotal?.let { " of ${formatBytes(it)}" } ?: ""}")
        item.speedBps?.let { append(" · ${formatBytes(it)}/s") }
    }
    DownloadState.PAUSED -> "Paused · ${formatBytes(item.bytesDone)}${item.bytesTotal?.let { " of ${formatBytes(it)}" } ?: ""}"
    DownloadState.MERGING -> "Merging video and audio..."
    DownloadState.COMPLETED -> "Downloaded · ${item.qualityLabel} · ${formatBytes(item.bytesTotal ?: item.bytesDone)}" + (item.error?.let { " · $it" } ?: "")
    DownloadState.FAILED -> "Failed: ${item.error ?: "unknown error"}"
}
