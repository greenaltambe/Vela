package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.videoPagedList
import com.greenaltambe.vela.modules.dearrow.DeArrowSurface
import com.greenaltambe.vela.ui.components.SectionHeader
import com.greenaltambe.vela.ui.components.VideoCard
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import kotlinx.coroutines.launch

/** Paged, de-duplicated results for [query]; with no query, the search history (per account) with a clear button. */
@Composable
fun SearchScreen(services: AppServices, navigator: Navigator, query: String, modifier: Modifier = Modifier) {
    var results by remember(query) { mutableStateOf(videoPagedList()) }
    var status by remember(query) { mutableStateOf("") }
    var loading by remember(query) { mutableStateOf(false) }
    var historyRevision by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val shownResults = rememberFiltered(services, results.items, DeArrowSurface.SEARCH)
    val history = remember(historyRevision, query) { if (query.isBlank()) services.searchHistory.recent() else emptyList() }

    fun load(more: Boolean) {
        if (loading || query.isBlank()) return
        loading = true
        status = "Searching..."
        scope.launch {
            when (val result = services.extractor.search(query, results.continuation.takeIf { more })) {
                is ExtractorResult.Success -> {
                    results = if (more) results.append(result.value) else results.replace(result.value)
                    status = if (results.items.isEmpty()) "No results." else ""
                }
                is ExtractorResult.Failure -> status = "Search failed: ${result.error.message}"
            }
            loading = false
        }
    }
    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            services.searchHistory.add(query, services.clock.now())
            load(more = false)
        }
    }

    LazyColumn(modifier.fillMaxSize().testTag("search.list"), verticalArrangement = Arrangement.spacedBy(Vela.sm), contentPadding = Vela.pagePadding) {
        if (query.isBlank()) {
            item {
                SectionHeader("Recent searches") {
                    if (history.isNotEmpty()) TextButton(onClick = { services.searchHistory.clear(); historyRevision++ }, modifier = Modifier.testTag("search.clearHistory")) { Text("Clear") }
                }
            }
            if (history.isEmpty()) item { Text("Nothing searched yet.") }
            items(history, key = { it }) { q ->
                Row(Modifier.fillMaxWidth().clickable { navigator.push(Route.Search(q)) }, verticalAlignment = Alignment.CenterVertically) {
                    Text(q, Modifier.weight(1f))
                    TextButton(onClick = { services.searchHistory.remove(q); historyRevision++ }) { Text("✕") }
                }
            }
        } else {
            item { SectionHeader("Results for \"$query\"") }
            if (status.isNotEmpty()) item { Text(status, Modifier.testTag("search.status")) }
            items(shownResults, key = { it.id }) { v ->
                VideoCard(v, badge = rydBadge(services, v.id), onClick = { navigator.push(Route.Video(v)) }, onChannelClick = { navigator.push(Route.Channel(v.channelId, v.channelName)) }, trailing = { VideoMenu(services, v) })
            }
            if (results.continuation != null) item { TextButton(onClick = { load(more = true) }, enabled = !loading, modifier = Modifier.testTag("search.more")) { Text("Load more") } }
        }
    }
}
