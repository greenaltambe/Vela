package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.greenaltambe.vela.ui.components.HeaderAction
import com.greenaltambe.vela.ui.components.MediaRow
import com.greenaltambe.vela.ui.components.PLAYLIST_SIDE_BY_SIDE
import com.greenaltambe.vela.ui.components.PlaylistActions
import com.greenaltambe.vela.ui.components.PlaylistHeader
import com.greenaltambe.vela.ui.components.SaveAction
import com.greenaltambe.vela.ui.components.PageAction
import com.greenaltambe.vela.ui.look.LocalUndoToasts
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * One playlist of the library: the library page frame, the playlist header (beside the list on a wide window), then the videos as [MediaRow]s. Long
 * press selects (Remove, Download act on all selected); a row's menu moves it or removes it; every removal shows Undo. A saved playlist (somebody
 * else's) can only be played, copied, shared and removed; opening it brings its copy up to date.
 */
@Composable
fun PlaylistScreen(services: AppServices, navigator: Navigator, playlistId: Long, modifier: Modifier = Modifier) {
    var localRevision by remember { mutableStateOf(0) }
    val revision = localRevision + playlistRevision(services)
    val scope = rememberCoroutineScope()
    val toasts = LocalUndoToasts.current
    val removal = remember { DeferredRemoval<String>(scope, toasts) }
    val selected = remember { mutableStateListOf<String>() }
    val summary = remember(revision) { services.playlists.all().firstOrNull { it.id == playlistId } }
    val items = remember(revision) { services.playlists.items(playlistId) }
    val synced = summary?.isSynced == true
    val readOnly = summary?.isReadOnly == true
    val saved = summary?.takeIf { it.isSaved }
    val reading = playlistId in services.playlistService.refreshing.collectAsState().value
    var removingSaved by remember { mutableStateOf(false) }
    var addFor by remember { mutableStateOf<List<VideoSummary>?>(null) }
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    LaunchedEffect(playlistId, saved != null) { if (saved != null) services.playlistService.launch { services.playlistService.refreshSavedIfStale(playlistId) } }
    addFor?.let { SaveManySheet(services, it, onDismiss = { addFor = null }) }
    var overflowOpen by remember { mutableStateOf(false) }
    var syncOpen by remember { mutableStateOf(false) }
    val notOnYouTube = remember(items) { items.filter { it.rowState == com.greenaltambe.vela.data.RowState.OVERFLOW }.map { it.videoId }.toSet() }
    val all = remember(items) { items.map { it.toSummary() } }
    val videos = all.filter { it.id !in removal.hidden }
    var downloadFor by remember { mutableStateOf<List<VideoSummary>?>(null) }
    downloadFor?.let { DownloadDialog(services, it, onDismiss = { downloadFor = null; selected.clear() }) }
    val extras = rememberCardExtras(services, all)

    fun toggle(id: String) { if (id in selected) selected.remove(id) else selected.add(id) }
    fun remove(ids: List<String>) {
        selected.clear()
        removal.remove(ids, if (ids.size == 1) "Removed from playlist" else "Removed ${ids.size} from playlist") {
            services.playlistService.launch { val r = services.playlistService.remove(playlistId, ids); if (!r.ok) toasts.show(r.message ?: "Could not remove it"); localRevision++ }
        }
    }

    overflowOpen.takeIf { it }?.let { summary?.let { s -> OverflowDialog(services, s, onDismiss = { overflowOpen = false }) { r -> overflowOpen = false; toasts.show(r.message ?: "Done"); localRevision++ } } }
    if (syncOpen && summary != null) SyncSettingsDialog(services, summary, onDismiss = { syncOpen = false }) { on ->
        syncOpen = false
        val done = if (saved != null) (if (on) "Saved to your YouTube library" else "Kept on this device") else if (on) "Syncing with YouTube" else "Stopped syncing"
        services.playlistService.launch { val r = services.playlistService.setSynced(playlistId, on); toasts.show(r.message ?: if (r.ok) done else "Could not change it"); localRevision++ }
    }

    val share = com.greenaltambe.vela.ui.platform.LocalShare.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    if (removingSaved && saved != null) RemoveSavedDialog(services, saved, onDismiss = { removingSaved = false }) { fromAccount ->
        removingSaved = false
        scope.launch {
            val r = services.playlistService.delete(playlistId, fromAccount)
            toasts.show(r.message ?: if (r.ok) "Removed from your library" else "Could not remove it")
            if (r.ok) navigator.back()
        }
    }
    // The original of a saved playlist, or your own playlist on YouTube, has a link; Watch later and Liked videos are nobody else's to open.
    val shareable = saved?.sourceId ?: summary?.remoteId?.takeIf { it != "WL" && it != "LL" }
    val header: @Composable () -> Unit = {
        PlaylistHeader(
            coverUrl = saved?.coverUrl ?: videos.firstOrNull()?.let { "https://i.ytimg.com/vi/${it.id}/hqdefault.jpg" },
            owner = saved?.ownerName,
            onOwner = saved?.ownerChannelId?.let { id -> { navigator.push(Route.Channel(id, saved.ownerName.orEmpty())) } },
            actions = PlaylistActions(
                playAll = HeaderAction(videos.isNotEmpty()) { navigator.playAll(videos) },
                shuffle = HeaderAction(videos.size > 1) { navigator.playAll(videos.shuffled()) },
                download = if (services.downloads != null) HeaderAction(videos.isNotEmpty()) { downloadFor = videos } else null,
                share = shareable?.let { remote ->
                    HeaderAction {
                        val link = com.greenaltambe.vela.library.playlistLink(remote)
                        if (share != null) share(summary?.name ?: "Playlist", link)
                        else { clipboard.setText(androidx.compose.ui.text.AnnotatedString(link)); toasts.show(if (saved != null) "Link copied" else "Link copied. A private playlist opens only for you.") }
                    }
                },
                save = saved?.let { SaveAction(true) { removingSaved = true } },
                menu = saved?.sourceId?.let { source ->
                    buildList {
                        if (videos.isNotEmpty()) add(PageAction("Add all to a playlist", "playlist.addAll") { addFor = videos })
                        add(PageAction("Open in browser", "playlist.openInBrowser") { runCatching { uri.openUri(com.greenaltambe.vela.library.playlistLink(source)) } })
                        add(PageAction("Refresh", "playlist.refresh") { services.playlistService.launch { val r = services.playlistService.refreshSaved(playlistId); toasts.show(r.message ?: "Up to date") } })
                    }
                }.orEmpty(),
            ),
            modifier = Modifier.padding(bottom = Spacing.sm),
        )
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val beside = maxWidth >= PLAYLIST_SIDE_BY_SIDE
        LibraryScaffold(
            summary?.name ?: "Playlist", Modifier, subtitle = "${videos.size} video${if (videos.size == 1) "" else "s"}" + (summary?.takeIf { it.isSynced || it.isSaved }?.let { " · " + playlistSupporting(it).substringBefore(" · ") } ?: ""), onBack = { navigator.back() }, listTag = "playlist.list",
            menu = buildList {
                if (services.downloads != null && videos.isNotEmpty()) add(PageAction("Download all", "playlist.downloadAll") { downloadFor = videos })
                if (summary != null && (!readOnly || summary.isSaved)) add(PageAction("Sync settings", "playlist.syncSettings") { syncOpen = true })
                if (synced) add(PageAction(if (summary?.syncState == com.greenaltambe.vela.data.PlaylistSync.ERROR) "Retry sync" else "Sync now", "playlist.syncNow") {
                    services.playlistService.launch { val r = if (summary?.syncState == com.greenaltambe.vela.data.PlaylistSync.ERROR) services.playlistService.retry(playlistId) else services.playlistService.syncPlaylist(playlistId); toasts.show(r.message ?: if (r.ok) "Synced" else "Could not sync"); localRevision++ }
                })
            },
            side = if (beside) header else null,
            selectionCount = selected.size, onClearSelection = { selected.clear() },
            selectionActions = {
                IconButton(onClick = { selected.clear(); selected.addAll(videos.map { it.id }) }, modifier = Modifier.testTag("playlist.selectAll")) { Icon(AppIcons.check, "Select all") }
                if (services.downloads != null) IconButton(onClick = { downloadFor = videos.filter { it.id in selected } }, modifier = Modifier.testTag("playlist.downloadSelected")) { Icon(AppIcons.download, "Download") }
                if (!readOnly) IconButton(onClick = { remove(selected.toList()) }, modifier = Modifier.testTag("playlist.removeSelected")) { Icon(AppIcons.delete, "Remove from playlist") }
            },
        ) {
            if (!beside) item(key = "actions") { header() }
            if (summary != null && synced) {
                val err = summary.syncState == com.greenaltambe.vela.data.PlaylistSync.ERROR
                if (err || summary.pendingCount > 0 || summary.syncState == com.greenaltambe.vela.data.PlaylistSync.LINKING) item(key = "syncstate") {
                    Row(Modifier.fillMaxWidth().padding(bottom = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SyncBadge(summary)
                        Text(
                            if (err) "Sync failed: ${summary.syncError ?: "try again"}" else if (summary.syncState == com.greenaltambe.vela.data.PlaylistSync.LINKING) "Syncing, ${summary.pendingCount} left" else "${summary.pendingCount} change${if (summary.pendingCount == 1) "" else "s"} waiting to reach YouTube",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).testTag("playlist.syncState"),
                        )
                        if (err) com.greenaltambe.vela.ui.components.SecondaryButton("Retry", {
                            services.playlistService.launch { val r = services.playlistService.retry(playlistId); toasts.show(r.message ?: if (r.ok) "Synced" else "Could not sync"); localRevision++ }
                        }, modifier = Modifier.testTag("playlist.retry"))
                    }
                }
                if (summary.overflowCount > 0) item(key = "overflow") {
                    Row(Modifier.fillMaxWidth().padding(bottom = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("${summary.overflowCount} video${if (summary.overflowCount == 1) "" else "s"} did not fit on YouTube (its limit is ${com.greenaltambe.vela.data.YOUTUBE_PLAYLIST_LIMIT}). They are kept here.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).testTag("playlist.overflowNote"))
                        com.greenaltambe.vela.ui.components.SecondaryButton("Add the rest to another playlist", { overflowOpen = true }, modifier = Modifier.testTag("playlist.overflowAction"))
                    }
                }
            }
            if (saved?.unavailableSince != null) item(key = "unavailable") {
            Text("This playlist is no longer available on YouTube. This is the copy kept here.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm).testTag("playlist.unavailable"))
        }
        if (videos.isEmpty()) item(key = "empty") {
            EmptyState(if (saved == null) "This playlist is empty. Add videos from the ⋮ menu on any video." else if (reading) "Reading the playlist…" else "No videos here yet. Choose Refresh to read it from YouTube.")
        }
            itemsIndexed(videos, key = { _, v -> "p-${v.id}" }, contentType = { _, _ -> "video" }) { index, v ->
                var menu by remember { mutableStateOf(false) }
                val position = all.indexOfFirst { it.id == v.id }
                MediaRow(
                    v.title, v.thumbnailUrl ?: "https://i.ytimg.com/vi/${v.id}/mqdefault.jpg",
                    onClick = { if (selected.isNotEmpty()) toggle(v.id) else navigator.push(Route.Video(v)) }, onLongClick = { toggle(v.id) },
                    duration = com.greenaltambe.vela.ui.components.formatDuration(v.durationSeconds), progress = extras(v).watched, selected = v.id in selected,
                    supporting = listOfNotNull(v.channelName.takeIf { it.isNotBlank() }, com.greenaltambe.vela.ui.components.formatViews(v.viewCount), "Not on YouTube".takeIf { v.id in notOnYouTube }),
                    modifier = Modifier.testTag("playlist.row"),
                    trailing = {
                        Box {
                            IconButton(onClick = { menu = true }, modifier = Modifier.testTag("playlist.itemMenu")) { Icon(AppIcons.more, "Options") }
                            DropdownMenu(menu, { menu = false }) {
                                ShareMenuItem(v, onDone = { menu = false })
                                if (summary?.isLocal == true && position > 1) DropdownMenuItem(text = { Text("Move to top") }, leadingIcon = { Icon(AppIcons.arrowUp, null) }, onClick = { menu = false; services.playlists.move(playlistId, position, 0); localRevision++ })
                                if (summary?.isLocal == true && position > 0) DropdownMenuItem(text = { Text("Move up") }, leadingIcon = { Icon(AppIcons.arrowUp, null) }, onClick = { menu = false; services.playlists.move(playlistId, position, position - 1); localRevision++ })
                                if (summary?.isLocal == true && position < all.lastIndex) DropdownMenuItem(text = { Text("Move down") }, leadingIcon = { Icon(AppIcons.arrowDown, null) }, onClick = { menu = false; services.playlists.move(playlistId, position, position + 1); localRevision++ })
                                if (!readOnly) DropdownMenuItem(text = { Text("Remove from playlist") }, leadingIcon = { Icon(AppIcons.delete, null) }, onClick = { menu = false; remove(listOf(v.id)) }, modifier = Modifier.testTag("playlist.remove"))
                            }
                        }
                    },
                )
            }
        }
    }
}
