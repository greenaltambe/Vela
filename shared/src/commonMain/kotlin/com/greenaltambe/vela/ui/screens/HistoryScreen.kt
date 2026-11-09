@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.auth.HistorySyncStatus
import com.greenaltambe.vela.core.settings.AccountHistorySync
import com.greenaltambe.vela.data.AccountWatchedEntry
import com.greenaltambe.vela.data.WatchRecord
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.library.groupHistoryByDay
import com.greenaltambe.vela.ui.components.DeferredRemoval
import com.greenaltambe.vela.ui.components.EmptyState
import com.greenaltambe.vela.ui.components.LibraryScaffold
import com.greenaltambe.vela.ui.components.MediaRow
import com.greenaltambe.vela.ui.components.MediaRowSkeleton
import com.greenaltambe.vela.ui.components.PageAction
import com.greenaltambe.vela.ui.look.LocalUndoToasts
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Watch history: one list of rows grouped by day under sticky day labels. Long press selects rows (then Delete, Save, Download act on all of them);
 * every removal shows an Undo toast; "Clear all" asks once. The account's own history can be added from the menu, loaded once, under its own label.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    var revision by remember { mutableStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    var limit by remember { mutableStateOf(500) }   // older history is loaded 500 rows at a time, never all at once
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val toasts = LocalUndoToasts.current
    val removal = remember { DeferredRemoval<String>(scope, toasts) }
    val selected = remember { mutableStateListOf<String>() }   // video ids
    var saveFor by remember { mutableStateOf<List<VideoSummary>?>(null) }
    var downloadFor by remember { mutableStateOf<List<VideoSummary>?>(null) }

    // Read off the main thread; null until the first answer (placeholders meanwhile, never a false "Nothing watched yet").
    val loaded: List<WatchRecord>? = rememberAsync(revision, limit, keepWhileLoading = true, first = { null as List<WatchRecord>? }, compute = { services.history.recent(limit.toLong()) })
    val progress = rememberAsync(revision, limit, keepWhileLoading = true, first = { emptyMap<String, Float>() }, compute = { services.history.progress() })
    // The account's own history as stored by the sync (setting history.accountSync); a failed read keeps what is stored and says so here only.
    val signedIn = services.accounts.active.value.isSignedIn
    val syncStatus by services.accountHistory.status.collectAsState()
    val accountRevision by services.accountHistory.revision.collectAsState()
    val accountRows = rememberAsync(revision, accountRevision, services.activeId, keepWhileLoading = true, first = { emptyList<AccountWatchedEntry>() }, compute = { if (signedIn) services.accountWatched.recent() else emptyList() })
    var askedHere by remember { mutableStateOf(false) }   // the "N on your account" line is only for a read the viewer asked for on this screen
    var accountShown by remember { mutableStateOf(40) }
    val hereIds = remember(loaded) { loaded.orEmpty().mapTo(HashSet()) { it.videoId } }
    // De-duplicated by video: a video watched on this device is listed once, in the local part (the account's entries carry no time of their own).
    val accountItems = remember(accountRows, hereIds, loaded == null) { if (loaded == null) emptyList() else accountRows.filter { it.videoId !in hereIds } }

    val records = remember(loaded, removal.hidden.size, query) {
        loaded.orEmpty().filter { it.videoId !in removal.hidden && (query.isBlank() || it.title.contains(query.trim(), ignoreCase = true)) }
    }
    val days = remember(records) { groupHistoryByDay(records, services.clock.now(), services.utcOffsetMinutes()) }
    fun summary(r: WatchRecord) = VideoSummary(r.videoId, r.title, r.channelId, "", durationSeconds = r.durationMs / 1000)
    fun toggle(id: String) { if (id in selected) selected.remove(id) else selected.add(id) }
    fun delete(ids: List<String>) {
        selected.clear()
        removal.remove(ids, if (ids.size == 1) "Removed from history" else "Removed ${ids.size} from history") { ids.forEach { services.history.deleteVideo(it) }; revision++ }
    }

    LibraryScaffold(
        "History", modifier, subtitle = loaded?.let { "${it.map { r -> r.videoId }.distinct().size} videos" }, onBack = { navigator.back() }, listTag = "history.list",
        barActions = { IconButton(onClick = { searching = !searching; if (!searching) query = "" }, modifier = Modifier.testTag("history.search")) { Icon(AppIcons.search, "Search history") } },
        menu = buildList {
            if (services.reads != null && signedIn && services.accountHistory.mode() != AccountHistorySync.OFF)
                add(PageAction("Update from YouTube account", "history.loadAccount") {
                    askedHere = true
                    scope.launch { services.accountHistory.sync() }
                })
            add(PageAction("Clear all", "history.clear") { confirmClear = true })
        },
        selectionCount = selected.size, onClearSelection = { selected.clear() },
        selectionActions = {
            IconButton(onClick = { selected.clear(); selected.addAll(records.map { it.videoId }.distinct()) }, modifier = Modifier.testTag("history.selectAll")) { Icon(AppIcons.check, "Select all") }
            IconButton(onClick = { saveFor = records.filter { it.videoId in selected }.map(::summary).distinctBy { it.id } }, modifier = Modifier.testTag("history.saveSelected")) { Icon(AppIcons.save, "Save to a playlist") }
            if (services.downloads != null) IconButton(onClick = { downloadFor = records.filter { it.videoId in selected }.map(::summary).distinctBy { it.id } }, modifier = Modifier.testTag("history.downloadSelected")) { Icon(AppIcons.download, "Download") }
            IconButton(onClick = { delete(selected.toList()) }, modifier = Modifier.testTag("history.deleteSelected")) { Icon(AppIcons.delete, "Remove from history") }
        },
    ) {
        if (searching) item(key = "search") {
            com.greenaltambe.vela.ui.components.VelaSearchField(query, { query = it }, {}, Modifier.fillMaxWidth().padding(bottom = Spacing.sm), placeholder = "Search your history")
        }
        if (syncStatus is HistorySyncStatus.Running) item(key = "account-loading") { MediaRowSkeleton() }
        if (syncStatus is HistorySyncStatus.Failed) item(key = "account-failed") { AccountNote("Could not read your YouTube history right now.", "history.accountFailed") }
        else if (askedHere && syncStatus is HistorySyncStatus.Done)
            item(key = "account-done") { AccountNote("${accountRows.size} on your account, ${accountRows.count { it.videoId in hereIds }} also watched here.", "history.accountDone") }
        if (loaded == null) items(5, key = { "sk-$it" }, contentType = { "skeleton" }) { MediaRowSkeleton() }
        else if (records.isEmpty()) item(key = "empty") { EmptyState(if (query.isBlank()) "Nothing watched yet." else "Nothing in your history matches \"$query\".") }
        days.forEach { day ->
            stickyHeader(key = "day-${day.label}", contentType = "day") { DayLabel(day.label) }
            items(day.records, key = { "h-${it.id}" }, contentType = { "history" }) { r ->
                HistoryRow(services, navigator, r, progress[r.videoId], r.videoId in selected, selected.isNotEmpty(), { toggle(r.videoId) },
                    onRemove = { delete(listOf(r.videoId)) }, onSave = { saveFor = listOf(summary(r)) }, onDownload = { downloadFor = listOf(summary(r)) })
            }
        }
        if (loaded != null && loaded.size >= limit) item(key = "load-older") {
            TextButton(onClick = { limit += 500 }, modifier = Modifier.testTag("history.older")) { Text("Load older history (showing the latest $limit)", maxLines = 1) }
        }
        accountItems.takeIf { it.isNotEmpty() && query.isBlank() }?.let { list ->
            stickyHeader(key = "account-header", contentType = "day") { DayLabel("From your YouTube account", Modifier.testTag("history.accountHeader")) }
            val shown = list.take(accountShown)
            items(shown, key = { "a-${it.videoId}" }) { e ->
                MediaRow(
                    e.title, "https://i.ytimg.com/vi/${e.videoId}/mqdefault.jpg", onClick = { navigator.push(Route.Video(VideoSummary(e.videoId, e.title, e.channelId.orEmpty(), ""))) }, onLongClick = {},
                    progress = e.percent?.let { it / 100f }, supporting = listOf(e.percent?.let { "$it% watched" } ?: "On your account"),
                )
            }
            if (list.size > accountShown) item(key = "account-more") { TextButton(onClick = { accountShown += 40 }) { Text("Show more (${list.size - accountShown} left)", maxLines = 1) } }
        }
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear all watch history?") },
        text = { Text("This only removes the history on this device.") },
        confirmButton = { TextButton(onClick = { services.history.clear(); revision++; confirmClear = false }, modifier = Modifier.testTag("history.clearConfirm")) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
    saveFor?.let { list -> SaveManySheet(services, list, onDismiss = { saveFor = null; selected.clear() }) }
    downloadFor?.let { list -> DownloadDialog(services, list, onDismiss = { downloadFor = null; selected.clear() }) }
}

/** A status line about reading the account's history: a quiet tonal card inside the list, never a popup. */
@Composable
private fun AccountNote(text: String, tag: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag(tag)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm))
    }
}

/** A day label that stays at the top while its rows scroll: a small tonal pill. */
@Composable
internal fun DayLabel(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = Spacing.xs)) {
        Text(
            text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1,
            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}

@Composable
private fun HistoryRow(
    services: AppServices, navigator: Navigator, r: WatchRecord, watched: Float?, selected: Boolean, selecting: Boolean,
    toggle: () -> Unit, onRemove: () -> Unit, onSave: () -> Unit, onDownload: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val v = VideoSummary(r.videoId, r.title, r.channelId, "", durationSeconds = r.durationMs / 1000)
    MediaRow(
        r.title, "https://i.ytimg.com/vi/${r.videoId}/mqdefault.jpg",
        onClick = { if (selecting) toggle() else navigator.push(Route.Video(v)) }, onLongClick = toggle,
        duration = com.greenaltambe.vela.ui.components.formatDuration(r.durationMs / 1000), progress = watched, selected = selected,
        supporting = listOf(com.greenaltambe.vela.ui.components.formatAge(r.watchedAt, services.clock.now())?.let { "Watched $it" } ?: ""),
        modifier = Modifier.testTag("history.row"),
        trailing = {
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.testTag("history.rowMenu")) { Icon(AppIcons.more, "Options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Remove from history") }, leadingIcon = { Icon(AppIcons.delete, null) }, onClick = { menu = false; onRemove() }, modifier = Modifier.testTag("history.remove"))
                    DropdownMenuItem(text = { Text("Save to playlist") }, leadingIcon = { Icon(AppIcons.save, null) }, onClick = { menu = false; onSave() })
                    if (services.downloads != null) DropdownMenuItem(text = { Text("Download") }, leadingIcon = { Icon(AppIcons.download, null) }, onClick = { menu = false; onDownload() })
                }
            }
        },
    )
}
