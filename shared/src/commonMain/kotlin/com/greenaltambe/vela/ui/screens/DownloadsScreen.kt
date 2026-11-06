@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.greenaltambe.vela.ui.screens

import com.greenaltambe.vela.ui.theme.AppIcons
import androidx.compose.foundation.shape.CircleShape
import com.greenaltambe.vela.ui.theme.Spacing
import com.greenaltambe.vela.ui.theme.Accent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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

    // A bottom sheet, like every other menu: what to download at the top, the choices as big targets, one clear button at the bottom.
    com.greenaltambe.vela.ui.player.PlayerMenuSheet(
        title = if (videos.size == 1) "Download" else "Download ${videos.size} videos", onDismiss = onDismiss,
        subtitle = if (videos.size == 1) videos.first().title else null,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Audio only", style = MaterialTheme.typography.bodyLarge)
                    Text("Just the sound: a much smaller file", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(audioOnly, { audioOnly = it }, Modifier.testTag("download.audioOnly"))
            }
            if (!audioOnly) {
                Text("Highest quality", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                com.greenaltambe.vela.ui.player.ChoiceChipGrid(
                    options = DownloadHeights, isSelected = { it == height }, label = { if (it == null) "Best" else "${it}p" }, onSelect = { height = it },
                    columns = 4, tag = { "download.quality.${it ?: "best"}" },
                )
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Prefer H.264", style = MaterialTheme.typography.bodyLarge)
                        Text("Plays on every device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.Switch(preferAvc, { preferAvc = it }, Modifier.testTag("download.preferAvc"))
                }
            }
            com.greenaltambe.vela.ui.components.PrimaryButton(
                "Download", onClick = {
                    services.player?.onBeforePlayback() // asks for the notification permission once on Android; no-op elsewhere
                    videos.forEach { downloads.manager.enqueue(DownloadRequest(it, audioOnly = audioOnly, maxHeight = height, preferAvc = preferAvc)) }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().testTag("download.confirm"),
            )
        }
    }
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

    com.greenaltambe.vela.ui.settings.SettingsScaffold("Downloads", modifier, onBack = { navigator.back() }, listTag = "downloads.list") {
            // Why nothing is moving, with the two ways out: allow mobile data, or open the download settings.
            waiting?.let { why ->
                item(key = "waiting") {
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = Spacing.sm).clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.tertiaryContainer).padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(why, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.testTag("downloads.waiting"))
                        if (why.contains("Wi-Fi")) Text("Downloads are set to Wi-Fi only. They start by themselves when you connect.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            if (why.contains("Wi-Fi")) com.greenaltambe.vela.ui.components.PrimaryButton("Use mobile data", onClick = { services.settings.set(Settings.downloadsWifiOnly, false) }, modifier = Modifier.testTag("downloads.allowMobile"))
                            com.greenaltambe.vela.ui.components.SecondaryButton("Download settings", onClick = { navigator.push(Route.SettingsPage("downloads", highlight = Settings.downloadsWifiOnly.key)) }, modifier = Modifier.testTag("downloads.settings"))
                        }
                    }
                }
            }
            item {
                val total = used + (free ?: 0L)
                val fraction = if (cap > 0) used / (cap * 1024.0 * 1024 * 1024) else if (total > 0) used.toDouble() / total else 0.0
                val over = cap > 0 && used > cap * 1024L * 1024 * 1024
                // Storage: the number you care about first, a wavy bar for how full it is, where the files go, and the things you can do about it.
                Column(
                    Modifier.fillMaxWidth().padding(bottom = Spacing.md).clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Box(Modifier.size(48.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.Icon(AppIcons.folder, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${formatBytes(used)} used", style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("downloads.storage"))
                            Text(
                                listOfNotNull(free?.let { "${formatBytes(it)} free" }, if (cap > 0) "cap ${cap.toInt()} GB" + (if (over) " (over)" else "") else null).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium, color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    androidx.compose.material3.LinearWavyProgressIndicator(progress = { fraction.toFloat().coerceIn(0.02f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(storage.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("downloads.folder"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        com.greenaltambe.vela.ui.components.ActionChip(AppIcons.settings, "Settings", { navigator.push(Route.SettingsPage("downloads")) }, Modifier.testTag("downloads.openSettings"))
                        com.greenaltambe.vela.ui.components.ActionChip(AppIcons.refresh, "Automatic", { navigator.push(Route.DownloadRules) }, Modifier.testTag("downloads.rules"))
                        com.greenaltambe.vela.ui.components.ActionChip(AppIcons.folder, "Folder", chooseFolder, Modifier.testTag("downloads.chooseFolder"))
                        if (services.settings.resolve(Settings.downloadsFolder).isNotEmpty()) com.greenaltambe.vela.ui.components.ActionChip(AppIcons.close, "Default folder", { services.settings.set(Settings.downloadsFolder, "") })
                    }
                }
            }
            if (items.isEmpty()) item { EmptyState("Nothing downloaded yet. Use Download on a video page or in its ⋮ menu.") }
            itemsIndexed(items, key = { _, it -> it.id }) { i, item -> DownloadRow(services, navigator, item, com.greenaltambe.vela.ui.settings.segmentedShape(i, items.size), onDelete = { confirmDelete = item }) }
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
private fun DownloadRow(services: AppServices, navigator: Navigator, item: DownloadItem, shape: androidx.compose.ui.graphics.Shape, onDelete: () -> Unit) {
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
            if (swipe.dismissDirection != androidx.compose.material3.SwipeToDismissBoxValue.Settled) Box(Modifier.fillMaxSize().clip(shape).background(MaterialTheme.colorScheme.error).padding(horizontal = Spacing.lg), contentAlignment = Alignment.CenterEnd) {
                androidx.compose.material3.Icon(AppIcons.delete, "Delete", tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
    ) {
        val stored = remember(item.id, item.state) { if (item.state == DownloadState.COMPLETED) services.downloads.repo.offlineData(item.id)?.thumbnailPath else null }
        // The saved copy when there is one (works offline), otherwise YouTube's picture for the id.
        val picture = stored?.let { if (it.startsWith("/")) "file://$it" else it } ?: "https://i.ytimg.com/vi/${item.videoId}/mqdefault.jpg"
        val playable = item.state == DownloadState.COMPLETED
        Row(
            Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainerLow)
                .then(if (playable) Modifier.clickable { navigator.push(Route.Video(VideoSummary(item.videoId, item.title, item.channelId, ""))) } else Modifier)
                .padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(132.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                coil3.compose.AsyncImage(picture, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                if (!playable) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.35f)))
                when (item.state) {
                    DownloadState.DOWNLOADING, DownloadState.QUEUED, DownloadState.MERGING, DownloadState.PAUSED -> {
                        val p = item.progress
                        if (p != null && item.state != DownloadState.MERGING) androidx.compose.material3.LinearWavyProgressIndicator(progress = { p }, modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                        else androidx.compose.material3.LinearWavyProgressIndicator(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                    }
                    else -> {}
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(statusLine(item), style = MaterialTheme.typography.bodySmall, maxLines = if (item.state == DownloadState.FAILED) 3 else 2, overflow = TextOverflow.Ellipsis,
                    color = if (item.state == DownloadState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("downloads.status"))
            }
            // The one action that fits the state; delete is the swipe, Play is a tap on the row.
            val action: Triple<androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit>? = when (item.state) {
                DownloadState.QUEUED, DownloadState.DOWNLOADING -> Triple(AppIcons.pause, "Pause") { manager.pause(item.id) }
                DownloadState.PAUSED -> Triple(AppIcons.play, "Resume") { manager.resume(item.id) }
                DownloadState.FAILED -> Triple(AppIcons.refresh, "Retry") { manager.retry(item.id) }
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
