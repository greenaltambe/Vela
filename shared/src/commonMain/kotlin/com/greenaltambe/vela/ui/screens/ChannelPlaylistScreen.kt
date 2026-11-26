package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.extractor.PlaylistDetails
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.library.RemotePlaylist
import com.greenaltambe.vela.library.isMix
import com.greenaltambe.vela.library.playlistLink
import com.greenaltambe.vela.ui.components.HeaderAction
import com.greenaltambe.vela.ui.components.PLAYLIST_HEADER_WIDTH
import com.greenaltambe.vela.ui.components.PLAYLIST_SIDE_BY_SIDE
import com.greenaltambe.vela.ui.components.PageAction
import com.greenaltambe.vela.ui.components.PlaylistActions
import com.greenaltambe.vela.ui.components.PlaylistHeader
import com.greenaltambe.vela.ui.components.SaveAction
import com.greenaltambe.vela.ui.look.LocalUndoToasts
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import com.greenaltambe.vela.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/** What needs the whole list before it can be done. */
private enum class WholeList { PLAY, SHUFFLE, DOWNLOAD, ADD }

/** A YouTube playlist: its header with the actions on the whole list, and its videos, further pages loading as the end is reached. */
@Composable
fun ChannelPlaylistScreen(services: AppServices, navigator: Navigator, playlistId: String, modifier: Modifier = Modifier) {
    val kept = LocalPlaylistStore.current
    val store = remember(kept) { kept ?: PlaylistStore() }
    val entry = remember(playlistId, store) { store.entryFor(services, playlistId, Clock.System.now()) }
    val playlist = entry.playlist
    val scope = rememberCoroutineScope()
    val toasts = LocalUndoToasts.current
    val share = com.greenaltambe.vela.ui.platform.LocalShare.current
    val clipboard = LocalClipboardManager.current
    val uri = LocalUriHandler.current
    LaunchedEffect(playlist) { playlist.load() }
    val shown = rememberFiltered(services, playlist.videos, com.greenaltambe.vela.modules.dearrow.DeArrowSurface.FEED)
    // A first page the feed rules hide whole has no end to scroll to: the next one is asked for here.
    LaunchedEffect(playlist.videos.size, shown.isEmpty()) { if (shown.isEmpty() && playlist.videos.isNotEmpty() && playlist.hasMore && playlist.moreProblem == null) playlist.loadMore() }

    var whole by remember { mutableStateOf<WholeList?>(null) }
    var downloadFor by remember { mutableStateOf<List<VideoSummary>?>(null) }
    var addFor by remember { mutableStateOf<List<VideoSummary>?>(null) }
    // This playlist's place in the library, when it has one: a saved copy, or one of the viewer's own that is synced with it.
    val revision = playlistRevision(services)
    val mine = remember(revision, playlistId) { services.playlists.bySource(playlistId) ?: services.playlists.byRemote(playlistId) }
    var saving by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    if (saving) SavePlaylistDialog(services, playlist.details?.title ?: "Playlist", onDismiss = { saving = false }) { onAccount ->
        saving = false
        services.playlistService.launch { val r = services.playlistService.save(playlist, onAccount); toasts.show(r.message ?: if (r.ok) "Saved to your library" else "Could not save it") }
    }
    if (removing && mine?.isSaved == true) RemoveSavedDialog(services, mine, onDismiss = { removing = false }) { fromAccount ->
        removing = false
        services.playlistService.launch { val r = services.playlistService.delete(mine.id, fromAccount); toasts.show(r.message ?: if (r.ok) "Removed from your library" else "Could not remove it") }
    }
    // Read to its end on this page: the saved copy takes it over.
    LaunchedEffect(mine?.id, playlist.complete) { if (mine?.isSaved == true && playlist.complete) services.playlistService.launch { services.playlistService.refreshSaved(mine.id, playlist) } }
    downloadFor?.let { DownloadDialog(services, it, onDismiss = { downloadFor = null }) }
    addFor?.let { SaveManySheet(services, it, onDismiss = { addFor = null }) }
    whole?.let { wanted ->
        WholeListReader(services, playlist, onDismiss = { whole = null }) { videos ->
            whole = null
            when {
                videos.isEmpty() -> toasts.show("Your filters hide every video of this playlist.")
                wanted == WholeList.PLAY -> navigator.playAll(videos)
                wanted == WholeList.SHUFFLE -> navigator.playAll(videos.shuffled())
                wanted == WholeList.DOWNLOAD -> downloadFor = videos
                else -> addFor = videos
            }
        }
    }

    val state = when {
        !playlist.loaded -> playlist.error?.let { message -> ListState.Error("Playlist unavailable: $message") { scope.launch { playlist.load() } } } ?: ListState.Loading
        playlist.videos.isEmpty() -> ListState.Empty("This playlist is empty.")
        shown.isEmpty() && !playlist.hasMore -> ListState.Empty("Your filters hide every video of this playlist.")
        shown.isEmpty() && playlist.moreProblem == null -> ListState.Loading
        else -> ListState.Ready
    }

    val header: (@Composable () -> Unit)? = if (!playlist.loaded) null else ({
        val d = playlist.details
        val some = playlist.videos.isNotEmpty()
        // A mix has no end, so nothing that needs all of it is offered.
        val finite = !isMix(playlistId)
        val ownChannel = services.accounts.active.value.channelId
        // The viewer's own, by what the page offers or by whose channel it is (read without the account, a page cannot know).
        val own = d?.canBeSaved == false || (ownChannel != null && d?.ownerChannelId == ownChannel)
        PlaylistHeader(
            coverUrl = d?.coverUrl ?: playlist.videos.firstOrNull()?.let { it.thumbnailUrl ?: "https://i.ytimg.com/vi/${it.id}/hqdefault.jpg" },
            title = d?.title,
            owner = d?.ownerName,
            ownerAvatarUrl = d?.ownerAvatarUrl,
            onOwner = d?.ownerChannelId?.let { id -> { navigator.push(Route.Channel(id, d.ownerName.orEmpty())) } },
            facts = playlistFacts(d, playlist),
            description = d?.description,
            actions = PlaylistActions(
                playAll = HeaderAction(some) { if (!store.playAll(navigator, playlist, services::playlistFilter)) whole = WholeList.PLAY },
                shuffle = if (finite) HeaderAction(some) { whole = WholeList.SHUFFLE } else null,
                download = if (finite && services.downloads != null) HeaderAction(some) { whole = WholeList.DOWNLOAD } else null,
                save = when {
                    mine?.isSaved == true -> SaveAction(true) { removing = true }
                    // The viewer's own (in the library or by the page) and mixes cannot be saved.
                    mine != null || !finite || own -> null
                    else -> SaveAction(false) { saving = true }
                },
                share = HeaderAction {
                    val link = playlistLink(playlistId)
                    if (share != null) share(d?.title ?: "Playlist", link) else { clipboard.setText(AnnotatedString(link)); toasts.show("Link copied") }
                },
                menu = buildList {
                    if (finite && some) add(PageAction("Add all to a playlist", "playlist.addAll") { whole = WholeList.ADD })
                    if (mine != null) add(PageAction("Open in your library", "playlist.openInLibrary") { navigator.push(Route.Playlist(mine.id)) })
                    add(PageAction("Open in browser", "playlist.openInBrowser") { runCatching { uri.openUri(playlistLink(playlistId)) } })
                    add(PageAction("Refresh", "playlist.refresh") { scope.launch { playlist.reload() } })
                },
            ),
        )
    })

    val footer: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            val hidden = playlist.videos.size - shown.size
            if (hidden > 0) Note("$hidden video${if (hidden == 1) "" else "s"} hidden by your filters or blocklist.", "playlist.hidden")
            if (playlist.unread > 0) Note("${playlist.unread} entr${if (playlist.unread == 1) "y" else "ies"} of this playlist could not be read.", "playlist.unread")
            val problem = playlist.moreProblem
            if (problem != null) {
                Note(problem.message, "playlist.moreProblem")
                TextButton(onClick = { scope.launch { if (problem.expired) playlist.reload() else playlist.loadMore() } }, enabled = !playlist.loading, modifier = Modifier.testTag("playlist.retryMore")) {
                    Text(if (problem.expired) "Refresh" else "Retry", maxLines = 1)
                }
            } else if (playlist.hasMore) TextButton(onClick = { scope.launch { playlist.loadMore() } }, enabled = !playlist.loading, modifier = Modifier.testTag("playlist.loadMore")) { Text("Load more", maxLines = 1) }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val beside = header != null && maxWidth >= PLAYLIST_SIDE_BY_SIDE
        val list: @Composable (Modifier, (@Composable () -> Unit)?) -> Unit = { listModifier, above ->
            VideoCollection(
                services, navigator, shown, state, listModifier, grid = false, testTag = "channelPlaylist.list", header = above, footer = footer, scroll = entry.scroll,
                onEndReached = if (playlist.hasMore && playlist.moreProblem == null) ({ scope.launch { playlist.loadMore() } }) else null,
            )
        }
        if (beside) Row(Modifier.fillMaxSize()) {
            val top = com.greenaltambe.vela.ui.nav.LocalTopBarInset.current
            val bottom = com.greenaltambe.vela.ui.nav.LocalBarInset.current
            Column(
                Modifier.width(PLAYLIST_HEADER_WIDTH).fillMaxHeight().verticalScroll(rememberScrollState())
                    .padding(start = Spacing.gutter, top = top + Spacing.sm, bottom = bottom + Spacing.sm).testTag("playlist.side"),
            ) { header?.invoke() }
            list(Modifier.weight(1f), null)
        } else list(Modifier.fillMaxSize(), header)
    }
}

@Composable
private fun Note(text: String, tag: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag(tag))
}

/** "Playlist · 44 videos · 1,814,014 views"; the count is left out while it is not known. */
internal fun playlistFacts(details: PlaylistDetails?, playlist: RemotePlaylist): String {
    val count = details?.videoCount ?: playlist.videos.size.toLong().takeIf { playlist.complete }
    return listOfNotNull(
        "Playlist",
        count?.let { "${grouped(it)} video${if (it == 1L) "" else "s"}" },
        details?.viewCount?.takeIf { it > 0 }?.let { "${grouped(it)} view${if (it == 1L) "" else "s"}" },
    ).joinToString(" · ")
}

private fun grouped(n: Long): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

/**
 * Reads [playlist] to its end, saying how far it is, and then calls [onRead] with the videos the feed rules let through. When it cannot be read whole
 * it says how much there is and asks, so that a list is never cut without a word. Closing it stops the reading.
 */
@Composable
private fun WholeListReader(services: AppServices, playlist: RemotePlaylist, onDismiss: () -> Unit, onRead: (List<VideoSummary>) -> Unit) {
    var attempt by remember { mutableStateOf(0) }
    var short by remember { mutableStateOf(false) }
    var use by remember { mutableStateOf(false) }
    LaunchedEffect(attempt, use) {
        if (!use) {
            short = false
            if (!playlist.loadAll()) { short = true; return@LaunchedEffect }
        }
        val read = playlist.videos
        onRead(withContext(Dispatchers.Default) { services.playlistFilter(read) })
    }
    // Nothing to wait for: no dialog to flash by.
    if (playlist.complete) return
    val stated = playlist.details?.videoCount
    val soFar = "${playlist.videos.size}${stated?.let { " of $it" }.orEmpty()}"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (short) "Not all of it could be read" else "Reading the playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(if (short) "$soFar videos were read. ${playlist.moreProblem?.message.orEmpty()}" else "$soFar videos", modifier = Modifier.testTag("playlist.reading"))
                if (!short) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            if (short) Row {
                TextButton(onClick = { if (playlist.moreProblem?.expired == true) onDismiss() else attempt++ }, modifier = Modifier.testTag("playlist.readRetry")) { Text(if (playlist.moreProblem?.expired == true) "Close" else "Try again") }
                TextButton(onClick = { short = false; use = true }, enabled = playlist.videos.isNotEmpty(), modifier = Modifier.testTag("playlist.readUse")) { Text("Use these ${playlist.videos.size}") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("playlist.readCancel")) { Text("Cancel") } },
    )
}
