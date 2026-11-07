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
import androidx.compose.foundation.ExperimentalFoundationApi
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
    var subtitles by remember { mutableStateOf(services.settings.resolve(Settings.downloadsSubtitles)) }
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
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Subtitles", style = MaterialTheme.typography.bodyLarge)
                    Text("Keep the available subtitles for offline viewing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(subtitles, { subtitles = it }, Modifier.testTag("download.subtitles"))
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
                    videos.forEach { downloads.manager.enqueue(DownloadRequest(it, audioOnly = audioOnly, maxHeight = height, preferAvc = preferAvc, subtitles = subtitles)) }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().testTag("download.confirm"),
            )
        }
    }
}

/**
 * Downloads: the same page frame and rows as History. The storage line (used, free, the cap) is the subtitle with a slim wavy bar under the bar; the
 * folder, the automatic downloads and the download settings are in the menu. Rows are grouped (downloading, waiting, saved), one button does what
 * the state needs (pause, resume, retry), and everything else, Delete included, is in the row's menu or, for several at once, in the selection bar.
 * A delete shows Undo: the row is hidden at once and the files are removed after the toast.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    val downloads = services.downloads
    if (downloads == null) { EmptyState("Downloads are not available on this platform.", modifier); return }
    val manager = downloads.manager
    val items by manager.items.collectAsState()
    val waiting by manager.waiting.collectAsState()
    val settingsRevision by services.settings.revision.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val toasts = com.greenaltambe.vela.ui.look.LocalUndoToasts.current
    val removal = remember { com.greenaltambe.vela.ui.components.DeferredRemoval<Long>(scope, toasts) }
    val selected = remember { androidx.compose.runtime.mutableStateListOf<Long>() }
    val chooseFolder = rememberFolderPicker { path -> if (path != null) services.settings.set(Settings.downloadsFolder, path) }

    val storage = remember(settingsRevision) { downloads.storage() }
    val used = remember(items) { downloads.repo.completedBytes() }
    val free = remember(items, settingsRevision) { storage.freeBytes() }
    val cap = services.settings.resolve(Settings.downloadsStorageCapGb)
    val total = used + (free ?: 0L)
    val fraction = if (cap > 0) used / (cap * 1024.0 * 1024 * 1024) else if (total > 0) used.toDouble() / total else 0.0
    val subtitle = listOfNotNull("${formatBytes(used)} used", free?.let { "${formatBytes(it)} free" }, if (cap > 0) "cap ${cap.toInt()} GB" else null).joinToString(" · ")

    val visible = items.filter { it.id !in removal.hidden }
    val running = visible.filter { it.state == DownloadState.DOWNLOADING || it.state == DownloadState.MERGING }
    val queued = visible.filter { it.state == DownloadState.QUEUED || it.state == DownloadState.PAUSED || it.state == DownloadState.FAILED }
    val done = visible.filter { it.state == DownloadState.COMPLETED }
    fun toggle(id: Long) { if (id in selected) selected.remove(id) else selected.add(id) }
    fun delete(ids: List<Long>) {
        selected.clear()
        removal.remove(ids, if (ids.size == 1) "Download deleted" else "${ids.size} downloads deleted") { ids.forEach { manager.remove(it) } }
    }

    com.greenaltambe.vela.ui.components.LibraryScaffold(
        "Downloads", modifier, subtitle = subtitle, onBack = { navigator.back() }, listTag = "downloads.list",
        menu = buildList {
            add(com.greenaltambe.vela.ui.components.PageAction("Download settings", "downloads.openSettings") { navigator.push(Route.SettingsPage("downloads")) })
            add(com.greenaltambe.vela.ui.components.PageAction("Automatic downloads", "downloads.rules") { navigator.push(Route.DownloadRules) })
            add(com.greenaltambe.vela.ui.components.PageAction("Change folder", "downloads.chooseFolder") { chooseFolder() })
            if (services.settings.resolve(Settings.downloadsFolder).isNotEmpty()) add(com.greenaltambe.vela.ui.components.PageAction("Use the default folder", "downloads.defaultFolder") { services.settings.set(Settings.downloadsFolder, "") })
        },
        selectionCount = selected.size, onClearSelection = { selected.clear() },
        selectionActions = {
            androidx.compose.material3.IconButton(onClick = { selected.clear(); selected.addAll(visible.map { it.id }) }, modifier = Modifier.testTag("downloads.selectAll")) { androidx.compose.material3.Icon(AppIcons.check, "Select all") }
            androidx.compose.material3.IconButton(onClick = { delete(selected.toList()) }, modifier = Modifier.testTag("downloads.deleteSelected")) { androidx.compose.material3.Icon(AppIcons.delete, "Delete") }
        },
    ) {
        item(key = "storage-bar") {
            androidx.compose.material3.LinearWavyProgressIndicator(
                progress = { fraction.toFloat().coerceIn(0.02f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm).testTag("downloads.storage"),
                color = if (cap > 0 && used > cap * 1024L * 1024 * 1024) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
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
        if (visible.isEmpty()) item(key = "empty") { EmptyState("Nothing downloaded yet. Use Download on a video page or in its ⋮ menu.") }
        listOf("Downloading" to running, "Waiting" to queued, "Saved" to done).forEach { (label, rows) ->
            if (rows.isNotEmpty()) {
                stickyHeader(key = "group-$label", contentType = "day") { DayLabel(label) }
                items(rows, key = { "d-${it.id}" }, contentType = { "download" }) { item ->
                    DownloadRow(services, navigator, item, item.id in selected, selected.isNotEmpty(), { toggle(item.id) }, onDelete = { delete(listOf(item.id)) })
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(services: AppServices, navigator: Navigator, item: DownloadItem, selected: Boolean, selecting: Boolean, toggle: () -> Unit, onDelete: () -> Unit) {
    val manager = services.downloads!!.manager
    var menu by remember { mutableStateOf(false) }
    val stored = remember(item.id, item.state) { if (item.state == DownloadState.COMPLETED) services.downloads.repo.offlineData(item.id)?.thumbnailPath else null }
    // The saved copy when there is one (works offline), otherwise YouTube's picture for the id.
    val picture = stored?.let { if (it.startsWith("/")) "file://$it" else it } ?: "https://i.ytimg.com/vi/${item.videoId}/mqdefault.jpg"
    val playable = item.state == DownloadState.COMPLETED
    val failed = item.state == DownloadState.FAILED
    com.greenaltambe.vela.ui.components.MediaRow(
        item.title, picture,
        onClick = { if (selecting) toggle() else if (playable) navigator.push(Route.Video(VideoSummary(item.videoId, item.title, item.channelId, ""))) },
        onLongClick = toggle,
        progress = if (item.state == DownloadState.DOWNLOADING || item.state == DownloadState.PAUSED) item.progress else null,
        selected = selected, dimmed = !playable,
        supporting = listOf(statusLine(item)), supportingColor = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("downloads.row"),
        overlay = {
            if (item.state == DownloadState.DOWNLOADING || item.state == DownloadState.MERGING) androidx.compose.material3.LoadingIndicator(Modifier.align(Alignment.Center).size(36.dp))
        },
        trailing = {
            // The one action that fits the state; Play is a tap on the row.
            val action: Triple<androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit>? = when (item.state) {
                DownloadState.QUEUED, DownloadState.DOWNLOADING -> Triple(AppIcons.pause, "Pause") { manager.pause(item.id) }
                DownloadState.PAUSED -> Triple(AppIcons.play, "Resume") { manager.resume(item.id) }
                DownloadState.FAILED -> Triple(AppIcons.refresh, "Retry") { manager.retry(item.id) }
                else -> null
            }
            action?.let { (icon, label, run) ->
                androidx.compose.material3.FilledTonalIconButton(onClick = run, shapes = androidx.compose.material3.IconButtonDefaults.shapes(), modifier = Modifier.testTag("downloads.${label.lowercase()}")) { androidx.compose.material3.Icon(icon, label) }
            }
            Box {
                androidx.compose.material3.IconButton(onClick = { menu = true }, modifier = Modifier.testTag("downloads.rowMenu")) { androidx.compose.material3.Icon(AppIcons.more, "Options") }
                androidx.compose.material3.DropdownMenu(menu, { menu = false }) {
                    androidx.compose.material3.DropdownMenuItem(text = { Text(if (playable) "Delete download" else "Cancel and remove") }, leadingIcon = { androidx.compose.material3.Icon(AppIcons.delete, null) }, onClick = { menu = false; onDelete() }, modifier = Modifier.testTag("downloads.delete"))
                }
            }
        },
    )
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
