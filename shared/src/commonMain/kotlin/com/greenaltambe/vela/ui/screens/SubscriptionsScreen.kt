package com.greenaltambe.vela.ui.screens

import com.greenaltambe.vela.ui.theme.Spacing
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.key
import com.greenaltambe.vela.ui.theme.AppIcons
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import com.greenaltambe.vela.ui.components.HScrollRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.core.settings.shortsEnabled
import com.greenaltambe.vela.data.SubscribedChannel
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.feed.FeedContext
import com.greenaltambe.vela.feed.WatchedLookup
import com.greenaltambe.vela.feed.shortsFeedPipeline
import com.greenaltambe.vela.feed.standardFeedPipeline
import com.greenaltambe.vela.library.parseSubscriptionFile
import com.greenaltambe.vela.modules.dearrow.DeArrowStep
import com.greenaltambe.vela.modules.dearrow.DeArrowSurface
import com.greenaltambe.vela.ui.components.Avatar
import com.greenaltambe.vela.ui.components.ErrorRow
import com.greenaltambe.vela.ui.components.SecondaryButton
import com.greenaltambe.vela.ui.components.SectionHeader
import com.greenaltambe.vela.ui.components.VelaSearchField
import com.greenaltambe.vela.ui.components.formatViews
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import com.greenaltambe.vela.ui.platform.rememberTextFilePicker
import kotlinx.coroutines.launch

private const val PAGE = 60
private const val SHORTS_IN_ROW = 20

private fun quickShown(services: AppServices, entries: List<com.greenaltambe.vela.feed.FeedEntry>): List<VideoSummary> =
    standardFeedPipeline(WatchedLookup.None).run(entries.take(PAGE).map { it.video }, FeedContext(services.settings)).filter { !it.isShort }

/**
 * The subscriptions page, one scrolling page: a row of the channels you follow (with "View all"), a row of their recent Shorts (when Shorts are on;
 * a tap opens the Shorts player), then the feed of new videos. Pull down to refresh; it only refetches on its own when the last refresh is old.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val refreshing by services.feed.refreshing.collectAsState()
    val entries by services.feed.entries.collectAsState()
    val failures by services.feed.failures.collectAsState()
    val branding by services.modules.dearrow.revision.collectAsState()
    // Shorts are off for the whole page (no Shorts row) when the global Shorts setting is Hide; read live so changing it takes effect at once.
    val settingsRevision by services.settings.revision.collectAsState()
    val shortsOn = remember(settingsRevision) { services.settings.shortsEnabled() }

    // The cache is already on screen; this brings it up to date, but only when it has gone stale (not on every visit).
    LaunchedEffect(Unit) { services.feed.refreshIfStale() }

    com.greenaltambe.vela.ui.components.RefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { services.feed.refresh() } }, modifier = modifier.fillMaxSize()) {
        val channels = rememberAsync(refreshing, entries, keepWhileLoading = true, first = { services.subscriptions.all() }, compute = { services.subscriptions.all() })
        val notifRevision by services.notificationsRevision.collectAsState()
        val unread = rememberAsync(notifRevision, keepWhileLoading = true, first = { emptySet<String>() }, compute = { services.notifications.unreadUploadChannels() })
        // The pipeline (watched lookup included) runs off the main thread; the cached rows show at once through the quick pass.
        val all = rememberAsync(entries, failures, branding, keepWhileLoading = true,
            first = { quickShown(services, entries) },
            compute = {
                standardFeedPipeline(WatchedLookup.of(services.history.watchedVideoIds())).with(DeArrowStep(services.modules.dearrow, DeArrowSurface.FEED))
                    .run(entries.map { it.video }, FeedContext(services.settings)).filter { !it.isShort }
            })
        val shorts = rememberAsync(entries, shortsOn, keepWhileLoading = true, first = { emptyList<VideoSummary>() },
            compute = { if (!shortsOn) emptyList() else shortsFeedPipeline(WatchedLookup.of(services.history.watchedVideoIds())).run(entries.map { it.video }, FeedContext(services.settings)).take(SHORTS_IN_ROW) })
        // Only the first page is handed to the list and prefetched; the next page is appended when the end is reached.
        var pageSize by remember { mutableStateOf(PAGE) }
        val shown = remember(all, pageSize) { all.take(pageSize) }
        val shownIds = remember(shown) { shown.map { it.id } }
        LaunchedEffect(shownIds) { services.modules.dearrow.prefetch(shownIds) }
        val state: ListState = when {
            shown.isEmpty() && refreshing -> ListState.Loading
            shown.isEmpty() && channels.isEmpty() -> ListState.Empty("No subscriptions yet. Subscribe to a channel, or import a file.", "Import or manage") { navigator.push(Route.ManageSubscriptions) }
            shown.isEmpty() -> ListState.Empty("Nothing new from your channels.", "Refresh") { scope.launch { services.feed.refresh() } }
            else -> ListState.Ready
        }
        VideoCollection(
            services, navigator, shown, state, testTag = "subs.feed",
            onEndReached = if (shown.size < all.size) ({ pageSize += PAGE }) else null,
            header = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    if (channels.isNotEmpty()) ChannelsRow(channels, navigator, unread)
                    if (shorts.isNotEmpty()) ShortsRow(shorts, navigator)
                    if (failures.isNotEmpty()) ErrorRow("${failures.size} channel(s) could not be refreshed; showing what was cached.", { scope.launch { services.feed.refresh() } }, Modifier.testTag("subs.failures"))
                    if (shown.isNotEmpty()) SectionHeader("New from your channels")
                }
            },
        )
    }
}

/** The channels you follow, one after another, with "View all" opening the full list. */
@Composable
private fun ChannelsRow(channels: List<SubscribedChannel>, navigator: Navigator, unread: Set<String>) {
    com.greenaltambe.vela.ui.components.ChannelStrip(
        channels, unread, onOpen = { c -> navigator.push(Route.Channel(c.channelId, c.name)) }, onAll = { navigator.push(Route.ManageSubscriptions) },
    )
}

/** A few of the newest Shorts as tall cards; a tap opens the Shorts player on it. */
@Composable
private fun ShortsRow(shorts: List<VideoSummary>, navigator: Navigator) {
    com.greenaltambe.vela.ui.components.ShortsCarousel("Shorts", shorts, tag = "subs.shortsRow", onOpen = { v -> navigator.push(Route.ShortsPlayer(v)) })
}

/**
 * Every channel you follow, as one list with a search box, a bell on each row and the notification level for all of them. Tap opens the channel; the menu offers Unsubscribe. Import takes a YouTube Takeout
 * subscriptions.csv or a NewPipe export.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AllChannelsScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    var revision by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val channels = remember(revision) { services.subscriptions.all() }
    val levels = remember(revision) { services.subscriptions.notifyLevels() }
    val shown = remember(channels, query) { if (query.isBlank()) channels else channels.filter { it.name.contains(query.trim(), ignoreCase = true) } }
    val pick = rememberTextFilePicker { text ->
        message = when {
            text == null -> "No file was read."
            else -> {
                val result = parseSubscriptionFile(text)
                if (result.channels.isEmpty()) "Nothing to import (${result.skipped} entries had no usable channel id)."
                else {
                    val added = services.subscriptions.import(result.channels, services.clock.now())
                    revision++
                    services.feed.reload()
                    "Imported $added new channel(s); ${result.channels.size - added} already subscribed; ${result.skipped} skipped."
                }
            }
        }
    }
    com.greenaltambe.vela.ui.settings.SettingsScaffold(
        "Channels", modifier, onBack = { navigator.back() }, listTag = "subs.channels",
        actions = { com.greenaltambe.vela.ui.components.ActionChip(AppIcons.download, "Import", pick, Modifier.padding(end = Spacing.sm).testTag("subs.import")) },
    ) {
        item(key = "summary") {
            Column(Modifier.padding(bottom = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("${channels.size} channel${if (channels.size == 1) "" else "s"} you follow", style = MaterialTheme.typography.titleMedium)
                if (channels.size > 8) VelaSearchField(query, { query = it }, {}, Modifier.fillMaxWidth(), placeholder = "Search channels")
                // Notifications for all of them at once; each channel's own bell is on its row.
                if (channels.isNotEmpty()) Column(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text("Notifications for every channel", style = MaterialTheme.typography.titleSmall)
                    Text("All also shows a system notification; Personalized only lists new videos in Notifications; None is silent.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        com.greenaltambe.vela.data.NotifyLevel.entries.forEach { l ->
                            com.greenaltambe.vela.ui.components.ExpressiveChip(levels.values.isNotEmpty() && levels.values.all { it == l }, l.label, { services.subscriptions.setAllNotifyLevels(l); revision++; message = "All channels set to ${l.label}." }, Modifier.testTag("subs.notifyAll.${l.name}"))
                        }
                    }
                }
                message?.let { Text(it, Modifier.testTag("subs.importMessage"), style = MaterialTheme.typography.bodySmall) }
            }
        }
        itemsIndexed(shown, key = { _, c -> c.channelId }) { i, c ->
            var menu by remember { mutableStateOf(false) }
            androidx.compose.material3.SegmentedListItem(
                onClick = { navigator.push(Route.Channel(c.channelId, c.name)) },
                shapes = androidx.compose.material3.ListItemDefaults.segmentedShapes(i, shown.size), colors = com.greenaltambe.vela.ui.settings.segmentedItemColors(),
                leadingContent = { Avatar(c.name, size = 48.dp, url = c.avatarUrl) },
                supportingContent = { Text(levels[c.channelId]?.label ?: "Personalized", maxLines = 1) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        key(revision) { com.greenaltambe.vela.ui.screens.ChannelBell(services, c.channelId, connected = false, onChanged = { revision++ }) }
                        Box {
                            androidx.compose.material3.IconButton(onClick = { menu = true }, modifier = Modifier.testTag("subs.channelMenu")) { androidx.compose.material3.Icon(AppIcons.more, "Options") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Unsubscribe") },
                                    onClick = { menu = false; services.subscriptions.unsubscribe(c.channelId); revision++; services.feed.reload() },
                                    modifier = Modifier.testTag("subs.unsubscribe"),
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.testTag("subs.channelTile"),
            ) { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}
