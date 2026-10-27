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
import com.greenaltambe.vela.feed.standardFeedPipeline
import com.greenaltambe.vela.library.parseSubscriptionFile
import com.greenaltambe.vela.modules.dearrow.DeArrowStep
import com.greenaltambe.vela.modules.dearrow.DeArrowSurface
import com.greenaltambe.vela.ui.components.ChannelRow
import com.greenaltambe.vela.ui.components.SectionHeader
import com.greenaltambe.vela.ui.components.VideoCard
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import com.greenaltambe.vela.ui.platform.rememberTextFilePicker
import kotlinx.coroutines.launch

private enum class SubsTab(val label: String) { FEED("Feed"), SHORTS("Shorts"), CHANNELS("Channels") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    var tab by remember { mutableStateOf(SubsTab.FEED) }
    val scope = rememberCoroutineScope()
    val refreshing by services.feed.refreshing.collectAsState()
    val entries by services.feed.entries.collectAsState()
    val failures by services.feed.failures.collectAsState()

    // The cache is already on screen; this brings it up to date.
    LaunchedEffect(Unit) { services.feed.refresh() }

    Column(modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab.ordinal) {
            SubsTab.entries.forEach { t -> Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.label) }, modifier = Modifier.testTag("subs.tab.${t.name}")) }
        }
        when (tab) {
            SubsTab.FEED, SubsTab.SHORTS -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { services.feed.refresh() } }, modifier = Modifier.fillMaxSize()) {
                val watched = WatchedLookup.of(services.history.watchedVideoIds())
                val pipeline = if (tab == SubsTab.FEED) standardFeedPipeline(watched) else shortsFeedPipeline(watched)
                val branding by services.modules.dearrow.revision.collectAsState()
                val shown = remember(entries, tab, failures, branding) {
                    pipeline.with(DeArrowStep(services.modules.dearrow, DeArrowSurface.FEED)).run(entries.map { it.video }, FeedContext(services.settings))
                }
                val shownIds = remember(entries) { entries.map { it.video.id } }
                LaunchedEffect(shownIds) { services.modules.dearrow.prefetch(shownIds) }
                LazyColumn(Modifier.fillMaxSize().testTag("subs.feed"), verticalArrangement = Arrangement.spacedBy(Vela.sm), contentPadding = Vela.pagePadding) {
                    item {
                        SectionHeader(if (tab == SubsTab.FEED) "New from your subscriptions" else "Shorts") {
                            TextButton(onClick = { scope.launch { services.feed.refresh() } }, enabled = !refreshing, modifier = Modifier.testTag("subs.refresh")) { Text(if (refreshing) "Refreshing..." else "Refresh") }
                        }
                    }
                    if (failures.isNotEmpty()) item { Text("${failures.size} channel(s) could not be refreshed; showing what was cached.", modifier = Modifier.testTag("subs.failures")) }
                    if (shown.isEmpty() && !refreshing) item { Text(if (services.subscriptions.all().isEmpty()) "No subscriptions yet. Subscribe to a channel or import a file in Channels." else "Nothing new.") }
                    items(shown, key = { it.id }) { v ->
                        VideoCard(v, badge = rydBadge(services, v.id), onClick = { navigator.push(Route.Video(v)) }, onChannelClick = { navigator.push(Route.Channel(v.channelId, v.channelName)) }, trailing = { VideoMenu(services, v) })
                    }
                }
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

    LazyColumn(Modifier.fillMaxSize().testTag("subs.channels"), verticalArrangement = Arrangement.spacedBy(Vela.xs), contentPadding = Vela.pagePadding) {
        item {
            SectionHeader("Subscriptions (${channels.size})") {
                OutlinedButton(onClick = pick, modifier = Modifier.testTag("subs.import")) { Text("Import file") }
            }
            Text("Accepts YouTube Takeout subscriptions.csv or a NewPipe subscriptions export.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            message?.let { Text(it, Modifier.testTag("subs.importMessage")) }
        }
        items(channels, key = { it.channelId }) { c ->
            ChannelRow(c.name, null, c.avatarUrl, onClick = { navigator.push(Route.Channel(c.channelId, c.name)) }, trailing = {
                TextButton(onClick = { services.subscriptions.unsubscribe(c.channelId); revision++; onChanged() }) { Text("Unsubscribe") }
            })
        }
    }
}
