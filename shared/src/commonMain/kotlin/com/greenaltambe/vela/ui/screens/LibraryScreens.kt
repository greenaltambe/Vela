package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Checkbox
import com.greenaltambe.vela.auth.ActionResult
import com.greenaltambe.vela.auth.AccountLibrarySync
import kotlinx.coroutines.launch
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
    var syncNote by remember { mutableStateOf<String?>(null) }
    var libraryError by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Recently watched, for the row under the shortcuts: read off the main thread, a skeleton-less empty row until it answers.
    val recent: List<WatchRecord> = rememberAsync(revision, keepWhileLoading = true, first = { emptyList<WatchRecord>() }, compute = { services.history.recent(60).distinctBy { it.videoId }.take(14) })
    val progress = rememberAsync(revision, keepWhileLoading = true, first = { emptyMap<String, Float>() }, compute = { services.history.progress() })

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 156.dp), Modifier.widthIn(max = 900.dp).fillMaxSize().testTag("library.list"), contentPadding = Vela.contentPadding,
            horizontalArrangement = Arrangement.spacedBy(Vela.md), verticalArrangement = Arrangement.spacedBy(Vela.md),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Vela.md)) {
                    ShortcutTile("History", "On this device", Vela.icons.history, { navigator.push(Route.History) }, Modifier.weight(1f).testTag("library.history"))
                    if (services.downloads != null) ShortcutTile("Downloads", "Saved offline", Vela.icons.downloaded, { navigator.push(Route.Downloads) }, Modifier.weight(1f).testTag("library.downloads"))
                }
            }
            if (services.reads != null && services.accounts.active.value.isSignedIn) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                ShortcutTile("Sync account playlists", syncNote ?: "Copies Watch later, Liked videos and playlists from your YouTube account (adds only, never deletes)", Vela.icons.cloud, {
                    syncNote = "Syncing…"
                    scope.launch { val r = AccountLibrarySync(services.reads, { services.playlists }, services.clock).sync(); syncNote = r.failure ?: "Added ${r.added}, already had ${r.alreadyHad}"; revision++ }
                }, Modifier.fillMaxWidth().testTag("library.syncAccount"))
            }
            if (recent.isNotEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
                    SectionHeader("Recently watched") { TextButton(onClick = { navigator.push(Route.History) }, modifier = Modifier.testTag("library.historyAll")) { Text("See all", maxLines = 1) } }
                    com.greenaltambe.vela.ui.components.HScrollRow(Modifier.testTag("library.recent"), Arrangement.spacedBy(Vela.md)) {
                        items(recent, key = { it.videoId }) { r ->
                            val v = VideoSummary(r.videoId, r.title, r.channelId, "", durationSeconds = r.durationMs.takeIf { it > 0 }?.div(1000))
                            Column(Modifier.width(176.dp).clip(Vela.mediumShape).clickable { navigator.push(Route.Video(v)) }, verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
                                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(Vela.thumbnailShape).background(Vela.colors.surfaceVariant.toColor())) {
                                    coil3.compose.AsyncImage("https://i.ytimg.com/vi/${r.videoId}/mqdefault.jpg", null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                    progress[r.videoId]?.takeIf { it > 0f }?.let { p ->
                                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Vela.colors.background.toColor().copy(alpha = 0.5f))) {
                                            Box(Modifier.fillMaxWidth(p.coerceIn(0f, 1f)).height(3.dp).background(Vela.wake))
                                        }
                                    }
                                }
                                Text(r.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { SectionHeader("Playlists") { TextButton(onClick = { creating = true }, modifier = Modifier.testTag("library.newPlaylist")) { Text("New playlist", maxLines = 1) } } }
            gridItems(playlists, key = { it.id }) { p ->
                val editable = !p.isWatchLater && (!p.isMirror || (p.remoteId != null && p.remoteId != "WL" && p.remoteId != "LL" && services.actions != null))
                // The cover is the first video's picture; an empty playlist gets the palette gradient and its icon.
                val cover = remember(p.id, revision) { services.playlists.items(p.id).firstOrNull()?.videoId }
                PlaylistCard(p, cover, editable, onOpen = { navigator.push(Route.Playlist(p.id)) }, onRename = { renaming = p }, onDelete = { deleting = p })
            }
        }
    }

    val canUseAccount = services.actions != null && services.accounts.active.value.isSignedIn
    libraryError?.let { Text(it, color = Vela.colors.error.toColor(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = Vela.gutter).testTag("library.error")) }
    if (creating) NameDialog("New playlist", "", onDismiss = { creating = false }, offerAccount = canUseAccount) { name, onAccount ->
        creating = false
        if (onAccount) scope.launch {
            val (r, id) = services.actions!!.createPlaylist(name)
            if (r is ActionResult.Failed || id == null) libraryError = "Could not create it on your account: ${(r as? ActionResult.Failed)?.message ?: "no id"}"
            else { libraryError = null; services.playlists.mirror(id, name, services.clock.now()); revision++ }
        } else { services.playlists.create(name, services.clock.now()); revision++ }
    }
    renaming?.let { p -> NameDialog("Rename playlist", p.name, onDismiss = { renaming = null }) { name, _ ->
        renaming = null
        if (p.isMirror) scope.launch {
            val r = services.actions!!.renamePlaylist(p.remoteId!!, name)
            if (r is ActionResult.Failed) libraryError = "Could not rename it on your account: ${r.message}" else { libraryError = null; services.playlists.mirror(p.remoteId, name, services.clock.now()); revision++ }
        } else { services.playlists.rename(p.id, name, services.clock.now()); revision++ }
    } }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${p.name}\"?", maxLines = 2) },
            confirmButton = { TextButton(onClick = {
                deleting = null
                if (p.isMirror) scope.launch {
                    val r = services.actions!!.deletePlaylist(p.remoteId!!)
                    if (r is ActionResult.Failed) libraryError = "Could not delete it on your account: ${r.message}" else { libraryError = null; services.playlists.deleteMirror(p.id); revision++ }
                } else { services.playlists.delete(p.id); revision++ }
            }, modifier = Modifier.testTag("library.deleteConfirm")) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, offerAccount: Boolean = false, onConfirm: (String, Boolean) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    var onAccount by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") })
            if (offerAccount) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(onAccount, { onAccount = it }, modifier = Modifier.testTag("library.onAccount")); Text("Also create it on my YouTube account (private)") } } },
        confirmButton = { TextButton(onClick = { onConfirm(name, onAccount) }, enabled = name.isNotBlank(), modifier = Modifier.testTag("library.nameOk")) { Text("OK") } },
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
        LazyColumn(Modifier.widthIn(max = 900.dp).fillMaxSize().testTag("playlist.list"), contentPadding = Vela.contentPadding, verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
            item {
                // The playlist as a card: its cover, name and count, then Play all and Download all.
                Column(Modifier.fillMaxWidth().clip(Vela.largeShape).background(Vela.colors.surface.toColor()).padding(Vela.md), verticalArrangement = Arrangement.spacedBy(Vela.md)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Vela.md), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(120.dp).aspectRatio(16f / 10f).clip(Vela.mediumShape).background(Vela.wake), contentAlignment = Alignment.Center) {
                            if (videos.isNotEmpty()) coil3.compose.AsyncImage("https://i.ytimg.com/vi/${videos.first().id}/mqdefault.jpg", null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            else Icon(Vela.icons.playlist, null, tint = Vela.colors.onPrimary.toColor())
                        }
                        Column(Modifier.weight(1f)) {
                            Text(summary?.name ?: "Playlist", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${videos.size} video${if (videos.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor())
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Vela.sm), verticalAlignment = Alignment.CenterVertically) {
                        PrimaryButton("Play all", onClick = { navigator.playAll(videos) }, enabled = videos.isNotEmpty(), modifier = Modifier.testTag("playlist.playAll"))
                        if (services.downloads != null) androidx.compose.material3.OutlinedButton(onClick = { downloadAll = true }, enabled = videos.isNotEmpty(), modifier = Modifier.testTag("playlist.downloadAll")) { Text("Download all", maxLines = 1) }
                    }
                }
            }
            if (items.isEmpty()) item { EmptyState("This playlist is empty. Add videos from the ⋮ menu on any video.") }
            items(items.size, key = { items[it].videoId }) { index ->
                PlaylistVideoRow(
                    videos[index], extras = extras(videos[index]), index = index, lastIndex = items.lastIndex,
                    onOpen = { navigator.push(Route.Video(videos[index])) },
                    onMove = { to -> services.playlists.move(playlistId, index, to); revision++ },
                    onRemove = { services.playlists.remove(playlistId, videos[index].id); revision++ },
                )
            }
        }
    }
}

/** One video of a playlist. Swiping it away removes it; the ⋮ menu reorders it (and also removes). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistVideoRow(v: VideoSummary, extras: CardExtras, index: Int, lastIndex: Int, onOpen: () -> Unit, onMove: (Int) -> Unit, onRemove: () -> Unit) {
    val swipe = androidx.compose.material3.rememberSwipeToDismissBoxState(confirmValueChange = { it == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart })
    if (swipe.currentValue == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart) androidx.compose.runtime.LaunchedEffect(v.id) { onRemove() }
    androidx.compose.material3.SwipeToDismissBox(
        swipe, enableDismissFromStartToEnd = false,
        backgroundContent = {
            // Only while being pulled, with the app's corner radius.
            if (swipe.dismissDirection != androidx.compose.material3.SwipeToDismissBoxValue.Settled) Box(Modifier.fillMaxSize().clip(Vela.largeShape).background(Vela.colors.error.toColor()).padding(horizontal = Vela.lg), contentAlignment = Alignment.CenterEnd) {
                Icon(Vela.icons.delete, "Remove from playlist", tint = Vela.colors.onPrimary.toColor())
            }
        },
    ) {
        VideoCard(
            v, onClick = onOpen, style = VideoCardStyle.HORIZONTAL, extras = extras,
            modifier = Modifier.clip(Vela.largeShape).background(Vela.colors.background.toColor()),
            trailing = {
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.testTag("playlist.itemMenu")) { Icon(Vela.icons.more, "Reorder or remove") }
                    DropdownMenu(menu, { menu = false }) {
                        ShareMenuItem(v, onDone = { menu = false })
                        if (index > 1) DropdownMenuItem(text = { Text("Move to top") }, leadingIcon = { Icon(Vela.icons.arrowUp, null) }, onClick = { menu = false; onMove(0) })
                        if (index > 0) DropdownMenuItem(text = { Text("Move up") }, leadingIcon = { Icon(Vela.icons.arrowUp, null) }, onClick = { menu = false; onMove(index - 1) })
                        if (index < lastIndex) DropdownMenuItem(text = { Text("Move down") }, leadingIcon = { Icon(Vela.icons.arrowDown, null) }, onClick = { menu = false; onMove(index + 1) })
                        DropdownMenuItem(text = { Text("Remove from playlist") }, leadingIcon = { Icon(Vela.icons.delete, null) }, onClick = { menu = false; onRemove() })
                    }
                }
            },
        )
    }
}

@Composable
fun HistoryScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    var revision by remember { mutableStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    var limit by remember { mutableStateOf(500) }   // older history is loaded 500 rows at a time, never all at once
    // Read off the main thread; null until the first answer (a skeleton shows meanwhile, never a false "Nothing watched yet").
    val loaded: List<WatchRecord>? = rememberAsync(revision, limit, keepWhileLoading = true, first = { null as List<WatchRecord>? }, compute = { services.history.recent(limit.toLong()) })
    val records: List<WatchRecord> = loaded.orEmpty()
    val days = remember(records) { groupHistoryByDay(records, services.clock.now(), services.utcOffsetMinutes()) }
    val progress = rememberAsync(revision, limit, keepWhileLoading = true, first = { emptyMap<String, Float>() }, compute = { services.history.progress() })
    // The account's own history, shown below this device's: loaded on request (it costs browse budget), de-duplicated by video (entries from the account carry no time), labelled by source.
    var accountItems by remember { mutableStateOf<List<VideoSummary>?>(null) }
    var accountNote by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 900.dp).fillMaxSize().testTag("history.list"), contentPadding = Vela.contentPadding, verticalArrangement = Arrangement.spacedBy(Vela.md)) {
            item {
                SectionHeader("Watch history") {
                    TextButton(onClick = { confirmClear = true }, enabled = records.isNotEmpty(), modifier = Modifier.testTag("history.clear")) { Text("Clear all", maxLines = 1) }
                }
            }
            if (loaded == null) items(5, contentType = { "skeleton" }) { com.greenaltambe.vela.ui.components.SkeletonCard(vertical = false) }
            else if (records.isEmpty()) item { EmptyState("Nothing watched yet.") }
            if (records.size >= limit) item(key = "load-older") { TextButton(onClick = { limit += 500 }, modifier = Modifier.testTag("history.older")) { Text("Load older history (showing the latest $limit)", maxLines = 1) } }
            if (services.reads != null && services.accounts.active.value.isSignedIn) item {
                TextButton(onClick = {
                    accountNote = "Loading…"
                    scope.launch {
                        val remote = services.reads.history()
                        if (remote == null) accountNote = "Could not read your YouTube history right now."
                        else { val here = records.map { it.videoId }.toSet(); accountItems = remote.filter { it.id !in here }; accountNote = "${remote.size} on your account, ${remote.count { it.id in here }} also watched here." }
                    }
                }, modifier = Modifier.testTag("history.loadAccount")) { Text("Also show my YouTube account history", maxLines = 1) }
                accountNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("history.accountNote")) }
            }
            accountItems?.takeIf { it.isNotEmpty() }?.let { list ->
                item(key = "account-header") { Text("From your YouTube account (not on this device)", style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.testTag("history.accountHeader")) }
                items(list, key = { "a-${it.id}" }) { v -> VideoCard(v, onClick = { navigator.push(Route.Video(v)) }, style = VideoCardStyle.HORIZONTAL, extras = CardExtras()) }
            }
            days.forEach { day ->
                item(key = "day-${day.label}") { Text(day.label, style = MaterialTheme.typography.titleMedium, maxLines = 1) }
                items(day.records, key = { "h-${it.id}" }, contentType = { "history" }) { r ->
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

/** A big rounded shortcut (History, Downloads, ...): an icon on the palette gradient, a title and a line under it. */
@Composable
private fun ShortcutTile(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(Vela.largeShape).background(Vela.colors.surface.toColor()).clickable(onClick = onClick).padding(Vela.md),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.md),
    ) {
        Box(Modifier.size(44.dp).clip(Vela.pillShape).background(Vela.wake), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Vela.colors.onPrimary.toColor()) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Vela.colors.onSurfaceVariant.toColor())
        }
    }
}

/** A playlist as a large rounded card: the cover picture (or the palette gradient) on top, name and count on the card's own surface underneath, options in the corner. */
@Composable
private fun PlaylistCard(p: PlaylistSummary, coverVideoId: String?, editable: Boolean, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val surface = Vela.colors.surface.toColor()
    Column(Modifier.fillMaxWidth().clip(Vela.largeShape).background(surface).clickable(onClick = onOpen).testTag(ComponentIds.PLAYLIST_ROW)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).background(Vela.wake)) {
            if (coverVideoId != null) coil3.compose.AsyncImage("https://i.ytimg.com/vi/$coverVideoId/mqdefault.jpg", null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            else Icon(if (p.isWatchLater) Vela.icons.watchLater else Vela.icons.playlist, null, Modifier.align(Alignment.Center).size(48.dp), tint = Vela.colors.onPrimary.toColor())
            if (editable) {
                var menu by remember { mutableStateOf(false) }
                Box(Modifier.align(Alignment.TopEnd).padding(Vela.xs)) {
                    IconButton(onClick = { menu = true }, modifier = Modifier.clip(Vela.pillShape).background(surface.copy(alpha = 0.85f))) { Icon(Vela.icons.more, "Playlist options", tint = Vela.colors.onSurface.toColor()) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(Vela.md), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Vela.colors.onSurface.toColor())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.xs)) {
                if (p.isMirror) Icon(Vela.icons.cloud, null, Modifier.size(14.dp), tint = Vela.colors.onSurfaceVariant.toColor())
                Text((if (p.isMirror) "YouTube account · " else "") + "${p.itemCount} video${if (p.itemCount == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Vela.colors.onSurfaceVariant.toColor())
            }
        }
    }
}
