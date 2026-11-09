@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.greenaltambe.vela.ui.screens

import com.greenaltambe.vela.ui.theme.AppIcons
import androidx.compose.foundation.shape.CircleShape
import com.greenaltambe.vela.ui.theme.Spacing
import com.greenaltambe.vela.ui.theme.Accent
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.toShape
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
import com.greenaltambe.vela.ui.components.SecondaryButton
import com.greenaltambe.vela.ui.components.VideoCard
import com.greenaltambe.vela.ui.components.VideoCardStyle
import com.greenaltambe.vela.ui.look.ComponentIds
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route

/** A tappable row with a title and an optional second line; at least 48 dp tall. */
@Composable
fun ListRow(title: String, modifier: Modifier = Modifier, subtitle: String? = null, onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = Spacing.minTouch).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Column(Modifier.weight(1f).padding(vertical = Spacing.xs)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 156.dp), Modifier.widthIn(max = 900.dp).fillMaxSize().testTag("library.list"), contentPadding = Spacing.contentPadding,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    ShortcutTile("History", "On this device", AppIcons.history, { navigator.push(Route.History) }, Modifier.weight(1f).testTag("library.history"))
                    if (services.downloads != null) ShortcutTile("Downloads", "Saved offline", AppIcons.downloaded, { navigator.push(Route.Downloads) }, Modifier.weight(1f).testTag("library.downloads"))
                }
            }
            if (services.reads != null && services.accounts.active.value.isSignedIn) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                ShortcutTile("Sync account playlists", syncNote ?: "Copies Watch later, Liked videos and playlists from your YouTube account (adds only, never deletes)", AppIcons.cloud, {
                    syncNote = "Syncing…"
                    scope.launch { val r = AccountLibrarySync(services.reads, { services.playlists }, services.clock).sync(); syncNote = r.failure ?: "Added ${r.added}, already had ${r.alreadyHad}"; revision++ }
                }, Modifier.fillMaxWidth().testTag("library.syncAccount"))
            }
            if (recent.isNotEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                com.greenaltambe.vela.ui.screens.WatchRecordCarousel("Recently watched", recent, navigator, tag = "library.recent", onSeeAll = { navigator.push(Route.History) })
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
    libraryError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = Spacing.gutter).testTag("library.error")) }
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

/** A big rounded shortcut (History, Downloads, ...): an icon on the palette gradient, a title and a line under it. */
@Composable
private fun ShortcutTile(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(onClick = onClick).padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Accent.brush), contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A playlist as a large rounded card: the cover picture (or the palette gradient) on top, name and count on the card's own surface underneath, options in the corner. */
@Composable
private fun PlaylistCard(p: PlaylistSummary, coverVideoId: String?, editable: Boolean, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(surface).clickable(onClick = onOpen).testTag(ComponentIds.PLAYLIST_ROW)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            if (coverVideoId != null) coil3.compose.AsyncImage("https://i.ytimg.com/vi/$coverVideoId/mqdefault.jpg", null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            else {
                // An expressive badge: the icon on a scalloped shape.
                val badge = androidx.compose.material3.MaterialShapes.Cookie9Sided.toShape()
                Box(Modifier.align(Alignment.Center).size(72.dp).clip(badge).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(if (p.isWatchLater) AppIcons.watchLater else AppIcons.playlist, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            if (editable) {
                var menu by remember { mutableStateOf(false) }
                Box(Modifier.align(Alignment.TopEnd).padding(Spacing.xs)) {
                    IconButton(onClick = { menu = true }, modifier = Modifier.clip(CircleShape).background(surface.copy(alpha = 0.85f))) { Icon(AppIcons.more, "Playlist options", tint = MaterialTheme.colorScheme.onSurface) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(p.name, style = MaterialTheme.typography.titleSmall, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (p.isMirror) Icon(AppIcons.cloud, "On your YouTube account", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${p.itemCount} video${if (p.itemCount == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
