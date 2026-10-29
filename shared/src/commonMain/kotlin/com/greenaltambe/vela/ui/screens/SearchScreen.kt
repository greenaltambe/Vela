package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.clickable
import com.greenaltambe.vela.ui.look.toColor
import com.greenaltambe.vela.extractor.VideoSummary
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.videoPagedList
import com.greenaltambe.vela.modules.dearrow.DeArrowSurface
import com.greenaltambe.vela.ui.components.SectionHeader
import com.greenaltambe.vela.ui.components.EmptyState
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip

/**
 * The full search view: a query field with clear and back, the history list while the field is empty, paged results
 * below once something is searched. The field always starts from this route's query, never from the last one typed
 * elsewhere, and a new search replaces this screen rather than stacking another.
 */
@Composable
fun SearchScreen(services: AppServices, navigator: Navigator, query: String, modifier: Modifier = Modifier) {
    var text by remember(query) { mutableStateOf(query) }
    var results by remember(query) { mutableStateOf(videoPagedList()) }
    var state by remember(query) { mutableStateOf<ListState>(if (query.isBlank()) ListState.Ready else ListState.Loading) }
    var loading by remember(query) { mutableStateOf(false) }
    var historyRevision by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val history = remember(historyRevision, query) { if (query.isBlank()) services.searchHistory.recent() else emptyList() }

    fun submit(q: String) { if (q.isNotBlank()) navigator.replace(Route.Search(q.trim())) }

    fun load(more: Boolean) {
        if (loading || query.isBlank()) return
        loading = true
        if (!more) state = ListState.Loading
        scope.launch {
            when (val result = services.extractor.search(query, results.continuation.takeIf { more })) {
                is ExtractorResult.Success -> {
                    results = if (more) results.append(result.value) else results.replace(result.value)
                    state = if (results.items.isEmpty()) ListState.Empty("No results for \"$query\".") else ListState.Ready
                }
                is ExtractorResult.Failure -> if (results.items.isEmpty()) state = ListState.Error("Search failed: ${result.error.message}") { load(false) }
            }
            loading = false
        }
    }
    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            services.searchHistory.add(query, services.clock.now())
            load(more = false)
        } else runCatching { focus.requestFocus() }
    }
    // Filters are applied to the loaded results (see SearchFilters): the source's order is "relevance".
    var filters by remember(query) { mutableStateOf(com.greenaltambe.vela.extractor.SearchFilters()) }
    val shownAll = rememberFiltered(services, results.items, DeArrowSurface.SEARCH)
    val shown = remember(shownAll, filters) { filters.apply(shownAll) }
    // Search-as-you-type: asked 250 ms after the last keystroke, only while the field differs from the searched query.
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(text) {
        suggestions = emptyList()
        if (text.trim().length < 2 || text.trim() == query) return@LaunchedEffect
        kotlinx.coroutines.delay(250)
        (services.extractor.suggestions(text.trim()) as? ExtractorResult.Success)?.let { suggestions = it.value }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).heightIn(min = 56.dp).padding(horizontal = Vela.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navigator.back() }, modifier = Modifier.testTag("nav.back")) { Icon(Vela.icons.back, "Back") }
            com.greenaltambe.vela.ui.components.VelaSearchField(text, { text = it }, { submit(text) }, Modifier.weight(1f).padding(end = Vela.xs), focusRequester = focus)
        }
        if (suggestions.isNotEmpty() && text.trim() != query) {
            LazyColumn(Modifier.fillMaxSize().testTag("search.suggestions"), contentPadding = Vela.contentPadding) {
                items(suggestions, key = { it }) { q ->
                    Row(Modifier.fillMaxWidth().heightIn(min = Vela.minTouch).clickable { submit(q) }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Vela.icons.search, null); Spacer(Modifier.width(Vela.sm)); Text(q, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        } else if (query.isBlank()) {
            // The start page: only while nothing is typed. Typing hides it, leaving the field and its suggestions.
            if (text.isBlank()) SearchStart(services, navigator, history, onRemove = { services.searchHistory.remove(it); historyRevision++ }, onClear = { services.searchHistory.clear(); historyRevision++ })
        } else {
            SearchFilterRow(filters) { filters = it }
            // Never an unexplained empty list: say when Settings > Feed rules (minimum length, hide watched, keywords) hid results.
            (results.items.size - shownAll.size).takeIf { it > 0 }?.let { Text("$it result${if (it == 1) "" else "s"} hidden by your feed filters (Settings > Feed).", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = Vela.gutter).testTag("search.hiddenByFeed")) }
            if (!filters.isDefault && shown.isEmpty() && shownAll.isNotEmpty()) Text("Nothing in the ${shownAll.size} loaded results matches. Filters apply to loaded results; load more or relax them.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = Vela.gutter).testTag("search.filterEmpty"))
            VideoCollection(
                services, navigator, shown, state, grid = false, testTag = "search.list",
                footer = if (results.continuation != null) ({ TextButton(onClick = { load(more = true) }, enabled = !loading, modifier = Modifier.testTag("search.more")) { Text("Load more", maxLines = 1) } }) else null,
            )
        }
    }
}

@Composable
private fun SearchFilterRow(filters: com.greenaltambe.vela.extractor.SearchFilters, onChange: (com.greenaltambe.vela.extractor.SearchFilters) -> Unit) {
    var durationMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = Vela.gutter), horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
        Box {
            FilterChip(selected = filters.duration != com.greenaltambe.vela.extractor.DurationFilter.ANY, onClick = { durationMenu = true }, label = { Text(filters.duration.label, maxLines = 1) }, modifier = Modifier.testTag("search.filter.duration"))
            DropdownMenu(durationMenu, { durationMenu = false }) {
                com.greenaltambe.vela.extractor.DurationFilter.entries.forEach { d -> DropdownMenuItem(text = { Text(d.label) }, onClick = { durationMenu = false; onChange(filters.copy(duration = d)) }) }
            }
        }
        Box {
            FilterChip(selected = filters.sort != com.greenaltambe.vela.extractor.SearchSort.RELEVANCE, onClick = { sortMenu = true }, label = { Text(filters.sort.label, maxLines = 1) }, modifier = Modifier.testTag("search.filter.sort"))
            DropdownMenu(sortMenu, { sortMenu = false }) {
                com.greenaltambe.vela.extractor.SearchSort.entries.forEach { o -> DropdownMenuItem(text = { Text(o.label) }, onClick = { sortMenu = false; onChange(filters.copy(sort = o)) }) }
            }
        }
    }
}

/** Before anything is typed: recent searches as pills, recently watched videos, and the channels you subscribe to. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SearchStart(services: AppServices, navigator: Navigator, history: List<String>, onRemove: (String) -> Unit, onClear: () -> Unit) {
    val activeId = services.accounts.active.value.id
    val watched = remember(activeId, history) {
        services.history.recent(60).distinctBy { it.videoId }.take(12)
            .map { VideoSummary(it.videoId, it.title, it.channelId, "", null, it.durationMs.takeIf { d -> d > 0 }?.div(1000)) }
    }
    val channels = remember(activeId) { services.subscriptions.all().take(24) }
    LazyColumn(Modifier.fillMaxSize().testTag("search.list"), contentPadding = Vela.contentPadding, verticalArrangement = Arrangement.spacedBy(Vela.md)) {
        if (history.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
                SectionHeader("Recent searches") { TextButton(onClick = onClear, modifier = Modifier.testTag("search.clearHistory")) { Text("Clear", maxLines = 1) } }
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Vela.sm), verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
                    history.forEach { q ->
                        Row(
                            Modifier.clip(Vela.pillShape).background(Vela.colors.surfaceVariant.toColor()).clickable { navigator.replace(Route.Search(q)) }.heightIn(min = 36.dp).padding(start = Vela.md, end = Vela.xs).testTag("search.recent"),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.xs),
                        ) {
                            Text(q, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.widthIn(max = 220.dp))
                            IconButton(onClick = { onRemove(q) }, modifier = Modifier.size(28.dp)) { Icon(Vela.icons.close, "Remove from history", Modifier.size(16.dp)) }
                        }
                    }
                }
            }
        }
        if (watched.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
                SectionHeader("Recently watched")
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(Vela.md), modifier = Modifier.testTag("search.watched")) {
                    items(watched, key = { it.id }) { v ->
                        Column(Modifier.width(168.dp).clip(Vela.mediumShape).clickable { navigator.push(Route.Video(v)) }, verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
                            coil3.compose.AsyncImage("https://i.ytimg.com/vi/${v.id}/mqdefault.jpg", null, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(Vela.thumbnailShape).background(Vela.colors.surfaceVariant.toColor()), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        if (channels.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
                SectionHeader("Subscriptions")
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(Vela.md), modifier = Modifier.testTag("search.channels")) {
                    items(channels, key = { it.channelId }) { c ->
                        Column(Modifier.width(72.dp).clip(Vela.mediumShape).clickable { navigator.push(Route.Channel(c.channelId, c.name)) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
                            com.greenaltambe.vela.ui.components.Avatar(c.name, size = 56.dp, url = c.avatarUrl)
                            Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        if (history.isEmpty() && watched.isEmpty() && channels.isEmpty()) item { EmptyState("Search for videos, channels and topics.") }
    }
}
