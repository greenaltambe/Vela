package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.greenaltambe.vela.ui.components.EmptyState
import com.greenaltambe.vela.ui.look.toColor
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadsScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    val downloads = services.downloads
    if (downloads == null) { EmptyState("Downloads are not available on this platform.", modifier); return }
    val items by downloads.manager.items.collectAsState()
    val waiting by downloads.manager.waiting.collectAsState()
    val settingsRevision by services.settings.revision.collectAsState()
    var confirmDelete by remember { mutableStateOf<DownloadItem?>(null) }
    val chooseFolder = rememberFolderPicker { path -> if (path != null) services.settings.set(Settings.downloadsFolder, path) }

    val storage = remember(settingsRevision) { downloads.storage() }
    val used = remember(items) { downloads.repo.completedBytes() }
    val free = remember(items, settingsRevision) { storage.freeBytes() }
    val cap = services.settings.resolve(Settings.downloadsStorageCapGb)

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 900.dp).fillMaxSize().testTag("downloads.list"), contentPadding = Vela.contentPadding, verticalArrangement = Arrangement.spacedBy(Vela.md)) {
            item {
                val total = used + (free ?: 0L)
                val fraction = if (cap > 0) used / (cap * 1024.0 * 1024 * 1024) else if (total > 0) used.toDouble() / total else 0.0
                Column(Modifier.fillMaxWidth().clip(Vela.largeShape).background(Vela.colors.surface.toColor()).padding(Vela.md), verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
                        androidx.compose.material3.Icon(Vela.icons.folder, null, tint = Vela.colors.onSurfaceVariant.toColor())
                        Text(storage.description, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Vela.colors.onSurfaceVariant.toColor(), modifier = Modifier.weight(1f).testTag("downloads.folder"))
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(Vela.pillShape).background(Vela.colors.surfaceVariant.toColor())) {
                        Box(Modifier.fillMaxWidth(fraction.toFloat().coerceIn(0.02f, 1f)).height(6.dp).background(Vela.wake))
                    }
                    Text(
                        "${formatBytes(used)} used" + (free?.let { " · ${formatBytes(it)} free" } ?: "") +
                            if (cap > 0) " · cap ${cap.toInt()} GB" + (if (used > cap * 1024L * 1024 * 1024) " (over)" else "") else "",
                        style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("downloads.storage"),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Vela.xs)) {
                        TextButton(onClick = { navigator.push(com.greenaltambe.vela.ui.nav.Route.DownloadRules) }, modifier = Modifier.testTag("downloads.rules")) { Text("Automatic downloads", maxLines = 1) }
                        TextButton(onClick = chooseFolder, modifier = Modifier.testTag("downloads.chooseFolder")) { Text("Change folder", maxLines = 1) }
                        if (services.settings.resolve(Settings.downloadsFolder).isNotEmpty()) TextButton(onClick = { services.settings.set(Settings.downloadsFolder, "") }) { Text("Use default", maxLines = 1) }
                    }
                    waiting?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.testTag("downloads.waiting")) }
                }
            }
            if (items.isEmpty()) item { EmptyState("Nothing downloaded yet. Use Download on a video page or in its ⋮ menu.") }
            items(items, key = { it.id }) { item -> DownloadRow(services, navigator, item, onDelete = { confirmDelete = item }) }
        }
    }

    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(if (item.state == DownloadState.COMPLETED) "Delete this download?" else "Cancel and remove this download?") },
            text = { Text(item.title + if (item.state == DownloadState.COMPLETED) "\n\nThe file is deleted from the device." else "\n\nWhat was downloaded so far is deleted.", maxLines = 6, overflow = TextOverflow.Ellipsis) },
            confirmButton = { TextButton(onClick = { downloads.manager.remove(item.id); confirmDelete = null }, modifier = Modifier.testTag("downloads.confirmDelete")) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } },
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DownloadRow(services: AppServices, navigator: Navigator, item: DownloadItem, onDelete: () -> Unit) {
    val manager = services.downloads!!.manager
    // Delete is behind a swipe (asks first, then the row snaps back) or the ⋮ menu; nothing destructive sits on the row itself.
    val swipe = androidx.compose.material3.rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        if (v == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart && item.state != DownloadState.MERGING) onDelete()
        false
    })
    androidx.compose.material3.SwipeToDismissBox(
        swipe, enableDismissFromStartToEnd = false, modifier = Modifier.testTag("downloads.row"),
        backgroundContent = {
            // Drawn only while the row is being pulled, with the same corner radius as the row, so no red shows around its corners at rest.
            if (swipe.dismissDirection != androidx.compose.material3.SwipeToDismissBoxValue.Settled) Box(Modifier.fillMaxSize().clip(Vela.largeShape).background(Vela.colors.error.toColor()).padding(horizontal = Vela.lg), contentAlignment = Alignment.CenterEnd) {
                androidx.compose.material3.Icon(Vela.icons.delete, "Delete", tint = Vela.colors.onPrimary.toColor())
            }
        },
    ) {
        val stored = remember(item.id, item.state) { if (item.state == DownloadState.COMPLETED) services.downloads.repo.offlineData(item.id)?.thumbnailPath else null }
        // The saved copy when there is one (works offline), otherwise YouTube's picture for the id.
        val picture = stored?.let { if (it.startsWith("/")) "file://$it" else it } ?: "https://i.ytimg.com/vi/${item.videoId}/mqdefault.jpg"
        val playable = item.state == DownloadState.COMPLETED
        Row(
            Modifier.fillMaxWidth().clip(Vela.largeShape).background(Vela.colors.surface.toColor())
                .then(if (playable) Modifier.clickable { navigator.push(Route.Video(VideoSummary(item.videoId, item.title, item.channelId, ""))) } else Modifier)
                .padding(Vela.sm),
            horizontalArrangement = Arrangement.spacedBy(Vela.md), verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(132.dp).aspectRatio(16f / 9f).clip(Vela.thumbnailShape).background(Vela.colors.surfaceVariant.toColor())) {
                coil3.compose.AsyncImage(picture, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                if (!playable) Box(Modifier.fillMaxSize().background(Vela.colors.background.toColor().copy(alpha = 0.35f)))
                when (item.state) {
                    DownloadState.DOWNLOADING, DownloadState.QUEUED, DownloadState.MERGING, DownloadState.PAUSED -> {
                        val p = item.progress
                        if (p != null && item.state != DownloadState.MERGING) LinearProgressIndicator(progress = { p }, modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(), color = Vela.colors.primary.toColor())
                        else LinearProgressIndicator(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(), color = Vela.colors.primary.toColor())
                    }
                    else -> {}
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(statusLine(item), style = MaterialTheme.typography.bodySmall, maxLines = if (item.state == DownloadState.FAILED) 3 else 2, overflow = TextOverflow.Ellipsis,
                    color = if (item.state == DownloadState.FAILED) Vela.colors.error.toColor() else Vela.colors.onSurfaceVariant.toColor(), modifier = Modifier.testTag("downloads.status"))
            }
            // The one action that fits the state; delete is the swipe, Play is a tap on the row.
            val action: Triple<androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit>? = when (item.state) {
                DownloadState.QUEUED, DownloadState.DOWNLOADING -> Triple(Vela.icons.pause, "Pause") { manager.pause(item.id) }
                DownloadState.PAUSED -> Triple(Vela.icons.play, "Resume") { manager.resume(item.id) }
                DownloadState.FAILED -> Triple(Vela.icons.refresh, "Retry") { manager.retry(item.id) }
                else -> null
            }
            action?.let { (icon, label, run) -> androidx.compose.material3.IconButton(onClick = run, modifier = Modifier.testTag("downloads.${label.lowercase()}")) { androidx.compose.material3.Icon(icon, label) } }
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
