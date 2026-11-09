@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.ui.components.DeferredRemoval
import com.greenaltambe.vela.ui.components.EmptyState
import com.greenaltambe.vela.ui.components.LibraryScaffold
import com.greenaltambe.vela.ui.components.MediaRow
import com.greenaltambe.vela.ui.components.PageAction
import com.greenaltambe.vela.ui.components.PrimaryButton
import com.greenaltambe.vela.ui.look.LocalUndoToasts
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.Spacing

/**
 * One playlist: the library page frame, a compact row of Play all and Download all, then the videos as [MediaRow]s. Long press selects (Remove,
 * Download act on all selected); a row's menu moves it or removes it; every removal shows Undo.
 */
@Composable
fun PlaylistScreen(services: AppServices, navigator: Navigator, playlistId: Long, modifier: Modifier = Modifier) {
    var revision by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val toasts = LocalUndoToasts.current
    val removal = remember { DeferredRemoval<String>(scope, toasts) }
    val selected = remember { mutableStateListOf<String>() }
    val summary = remember(revision) { services.playlists.all().firstOrNull { it.id == playlistId } }
    val items = remember(revision) { services.playlists.items(playlistId) }
    val all = remember(items) { items.map { it.toSummary() } }
    val videos = all.filter { it.id !in removal.hidden }
    var downloadFor by remember { mutableStateOf<List<VideoSummary>?>(null) }
    downloadFor?.let { DownloadDialog(services, it, onDismiss = { downloadFor = null; selected.clear() }) }
    val extras = rememberCardExtras(services, all)

    fun toggle(id: String) { if (id in selected) selected.remove(id) else selected.add(id) }
    fun remove(ids: List<String>) {
        selected.clear()
        removal.remove(ids, if (ids.size == 1) "Removed from playlist" else "Removed ${ids.size} from playlist") { ids.forEach { services.playlists.remove(playlistId, it) }; revision++ }
    }

    LibraryScaffold(
        summary?.name ?: "Playlist", modifier, subtitle = "${videos.size} video${if (videos.size == 1) "" else "s"}", onBack = { navigator.back() }, listTag = "playlist.list",
        menu = if (services.downloads != null && videos.isNotEmpty()) listOf(PageAction("Download all", "playlist.downloadAll") { downloadFor = videos }) else emptyList(),
        selectionCount = selected.size, onClearSelection = { selected.clear() },
        selectionActions = {
            IconButton(onClick = { selected.clear(); selected.addAll(videos.map { it.id }) }, modifier = Modifier.testTag("playlist.selectAll")) { Icon(AppIcons.check, "Select all") }
            if (services.downloads != null) IconButton(onClick = { downloadFor = videos.filter { it.id in selected } }, modifier = Modifier.testTag("playlist.downloadSelected")) { Icon(AppIcons.download, "Download") }
            IconButton(onClick = { remove(selected.toList()) }, modifier = Modifier.testTag("playlist.removeSelected")) { Icon(AppIcons.delete, "Remove from playlist") }
        },
    ) {
        item(key = "actions") {
            Row(Modifier.fillMaxWidth().padding(bottom = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PrimaryButton("Play all", onClick = { navigator.playAll(videos) }, enabled = videos.isNotEmpty(), modifier = Modifier.weight(1f).testTag("playlist.playAll"))
                com.greenaltambe.vela.ui.components.SecondaryButton("Shuffle", { navigator.playAll(videos.shuffled()) }, enabled = videos.size > 1, modifier = Modifier.testTag("playlist.shuffle"))
                if (services.downloads != null) FilledTonalIconButton(onClick = { downloadFor = videos }, enabled = videos.isNotEmpty(), shapes = IconButtonDefaults.shapes(), modifier = Modifier.testTag("playlist.downloadAllButton")) { Icon(AppIcons.download, "Download all") }
            }
        }
        if (videos.isEmpty()) item(key = "empty") { EmptyState("This playlist is empty. Add videos from the ⋮ menu on any video.") }
        itemsIndexed(videos, key = { _, v -> "p-${v.id}" }, contentType = { _, _ -> "video" }) { index, v ->
            var menu by remember { mutableStateOf(false) }
            val position = all.indexOfFirst { it.id == v.id }
            MediaRow(
                v.title, v.thumbnailUrl ?: "https://i.ytimg.com/vi/${v.id}/mqdefault.jpg",
                onClick = { if (selected.isNotEmpty()) toggle(v.id) else navigator.push(Route.Video(v)) }, onLongClick = { toggle(v.id) },
                duration = com.greenaltambe.vela.ui.components.formatDuration(v.durationSeconds), progress = extras(v).watched, selected = v.id in selected,
                supporting = listOfNotNull(v.channelName.takeIf { it.isNotBlank() }, com.greenaltambe.vela.ui.components.formatViews(v.viewCount)),
                modifier = Modifier.testTag("playlist.row"),
                trailing = {
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.testTag("playlist.itemMenu")) { Icon(AppIcons.more, "Options") }
                        DropdownMenu(menu, { menu = false }) {
                            ShareMenuItem(v, onDone = { menu = false })
                            if (position > 1) DropdownMenuItem(text = { Text("Move to top") }, leadingIcon = { Icon(AppIcons.arrowUp, null) }, onClick = { menu = false; services.playlists.move(playlistId, position, 0); revision++ })
                            if (position > 0) DropdownMenuItem(text = { Text("Move up") }, leadingIcon = { Icon(AppIcons.arrowUp, null) }, onClick = { menu = false; services.playlists.move(playlistId, position, position - 1); revision++ })
                            if (position < all.lastIndex) DropdownMenuItem(text = { Text("Move down") }, leadingIcon = { Icon(AppIcons.arrowDown, null) }, onClick = { menu = false; services.playlists.move(playlistId, position, position + 1); revision++ })
                            DropdownMenuItem(text = { Text("Remove from playlist") }, leadingIcon = { Icon(AppIcons.delete, null) }, onClick = { menu = false; remove(listOf(v.id)) }, modifier = Modifier.testTag("playlist.remove"))
                        }
                    }
                },
            )
        }
    }
}
