package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.clickable
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
            OutlinedTextField(
                text, { text = it }, modifier = Modifier.weight(1f).focusRequester(focus).testTag("search.field"), singleLine = true,
                placeholder = { Text("Search videos", maxLines = 1) },
                trailingIcon = { if (text.isNotEmpty()) IconButton(onClick = { text = "" }, modifier = Modifier.testTag("search.clear")) { Icon(Vela.icons.close, "Clear") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { submit(text) }),
            )
            IconButton(onClick = { submit(text) }, enabled = text.isNotBlank(), modifier = Modifier.testTag("search.submit")) { Icon(Vela.icons.search, "Search") }
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
            LazyColumn(Modifier.fillMaxSize().testTag("search.list"), contentPadding = Vela.contentPadding) {
                item {
                    SectionHeader("Recent searches") {
                        if (history.isNotEmpty()) TextButton(onClick = { services.searchHistory.clear(); historyRevision++ }, modifier = Modifier.testTag("search.clearHistory")) { Text("Clear", maxLines = 1) }
                    }
                }
                if (history.isEmpty()) item { EmptyState("Nothing searched yet.") }
                items(history, key = { it }) { q ->
                    Row(Modifier.fillMaxWidth().heightIn(min = Vela.minTouch).clickable { navigator.replace(Route.Search(q)) }, verticalAlignment = Alignment.CenterVertically) {
                        Text(q, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { services.searchHistory.remove(q); historyRevision++ }) { Icon(Vela.icons.close, "Remove from history") }
                    }
                }
            }
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
