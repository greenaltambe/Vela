package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.feed.FeedContext
import com.greenaltambe.vela.feed.WatchedLookup
import com.greenaltambe.vela.feed.shortsFeedPipeline
import androidx.compose.material3.OutlinedButton as OutlinedButton2
import com.greenaltambe.vela.feed.standardFeedPipeline
import com.greenaltambe.vela.library.parseSubscriptionFile
import com.greenaltambe.vela.modules.dearrow.DeArrowStep
import com.greenaltambe.vela.modules.dearrow.DeArrowSurface
import com.greenaltambe.vela.ui.components.ChannelRow
import com.greenaltambe.vela.ui.components.SectionHeader
import com.greenaltambe.vela.ui.components.ErrorRow
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import com.greenaltambe.vela.ui.platform.rememberTextFilePicker
import kotlinx.coroutines.launch

private const val PAGE = 60

private fun quickShown(services: AppServices, entries: List<com.greenaltambe.vela.feed.FeedEntry>, tab: SubsTab): List<com.greenaltambe.vela.extractor.VideoSummary> =
    (if (tab == SubsTab.FEED) standardFeedPipeline(WatchedLookup.None) else shortsFeedPipeline(WatchedLookup.None)).run(entries.take(PAGE).map { it.video }, FeedContext(services.settings))

private enum class SubsTab(val label: String) { FEED("Feed"), SHORTS("Shorts"), CHANNELS("Channels") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    var tab by remember { mutableStateOf(SubsTab.FEED) }
    val scope = rememberCoroutineScope()
    val refreshing by services.feed.refreshing.collectAsState()
    val entries by services.feed.entries.collectAsState()
    val failures by services.feed.failures.collectAsState()
    val branding by services.modules.dearrow.revision.collectAsState()

    // The cache is already on screen; this brings it up to date.
    LaunchedEffect(Unit) { services.feed.refresh() }

    Column(modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab.ordinal) {
            SubsTab.entries.forEach { t -> Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.label, maxLines = 1, softWrap = false) }, modifier = Modifier.testTag("subs.tab.${t.name}")) }
        }
        when (tab) {
            SubsTab.FEED, SubsTab.SHORTS -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { services.feed.refresh() } }, modifier = Modifier.fillMaxSize()) {
                val noSubs = rememberAsync(refreshing, keepWhileLoading = true, first = { false }, compute = { services.subscriptions.all().isEmpty() })
                // The pipeline (watched lookup included) runs off the main thread; the cached rows show at once through the quick pass.
                val all = rememberAsync(entries, tab, failures, branding, keepWhileLoading = true,
                    first = { quickShown(services, entries, tab) },
                    compute = {
                        val pipeline = if (tab == SubsTab.FEED) standardFeedPipeline(WatchedLookup.of(services.history.watchedVideoIds())) else shortsFeedPipeline(WatchedLookup.of(services.history.watchedVideoIds()))
                        pipeline.with(DeArrowStep(services.modules.dearrow, DeArrowSurface.FEED)).run(entries.map { it.video }, FeedContext(services.settings))
                    })
                // Only the first page is handed to the list and prefetched; the next page is appended when the end is reached.
                var pageSize by remember(tab) { mutableStateOf(PAGE) }
                val shown = remember(all, pageSize) { all.take(pageSize) }
                val shownIds = remember(shown) { shown.map { it.id } }
                LaunchedEffect(shownIds) { services.modules.dearrow.prefetch(shownIds) }
                val state: ListState = when {
                    shown.isEmpty() && refreshing -> ListState.Loading
                    shown.isEmpty() && noSubs -> ListState.Empty("No subscriptions yet. Subscribe to a channel, or import a file.", "Import or manage") { tab = SubsTab.CHANNELS }
                    shown.isEmpty() -> ListState.Empty(if (tab == SubsTab.FEED) "Nothing new from your channels." else "No Shorts from your channels.", "Refresh") { scope.launch { services.feed.refresh() } }
                    else -> ListState.Ready
                }
                VideoCollection(
                    services, navigator, shown, state, testTag = "subs.feed",
                    onEndReached = if (shown.size < all.size) ({ pageSize += PAGE }) else null,
                    header = {
                        Column {
                            SectionHeader(if (tab == SubsTab.FEED) "New from your channels" else "Shorts") {
                                TextButton(onClick = { scope.launch { services.feed.refresh() } }, enabled = !refreshing, modifier = Modifier.testTag("subs.refresh")) { Text(if (refreshing) "Refreshing" else "Refresh", maxLines = 1) }
                            }
                            if (failures.isNotEmpty()) ErrorRow("${failures.size} channel(s) could not be refreshed; showing what was cached.", { scope.launch { services.feed.refresh() } }, Modifier.testTag("subs.failures"))
                        }
                    },
                )
            }
            SubsTab.CHANNELS -> ChannelsTab(services, navigator, onChanged = { services.feed.reload() })
        }
    }
}

@Composable
private fun ChannelsTab(services: AppServices, navigator: Navigator, onChanged: () -> Unit) {
    var revision by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val channels = remember(revision) { services.subscriptions.all() }
    val pick = rememberTextFilePicker { text ->
        message = when {
            text == null -> "No file was read."
            else -> {
                val result = parseSubscriptionFile(text)
                if (result.channels.isEmpty()) "Nothing to import (${result.skipped} entries had no usable channel id)."
                else {
                    val added = services.subscriptions.import(result.channels, services.clock.now())
                    revision++
                    onChanged()
                    "Imported $added new channel(s); ${result.channels.size - added} already subscribed; ${result.skipped} skipped."
                }
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize().testTag("subs.channels"), verticalArrangement = Arrangement.spacedBy(Vela.xs), contentPadding = Vela.contentPadding) {
        item {
            SectionHeader("Channels (${channels.size})") {
                OutlinedButton(onClick = pick, modifier = Modifier.testTag("subs.import")) { Text("Import", maxLines = 1) }
            }
            Text("Accepts a YouTube Takeout subscriptions.csv or a NewPipe export.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, maxLines = 2)
            message?.let { Text(it, Modifier.testTag("subs.importMessage")) }
        }
        items(channels, key = { it.channelId }) { c ->
            ChannelRow(c.name, null, c.avatarUrl, onClick = { navigator.push(Route.Channel(c.channelId, c.name)) }, trailing = {
                TextButton(onClick = { services.subscriptions.unsubscribe(c.channelId); revision++; onChanged() }) { Text("Unsubscribe", maxLines = 1) }
            })
        }
    }
}
