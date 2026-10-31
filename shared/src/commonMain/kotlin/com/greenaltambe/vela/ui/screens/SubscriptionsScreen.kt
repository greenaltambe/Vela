package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.toColor
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
                Column(verticalArrangement = Arrangement.spacedBy(Vela.md)) {
                    if (channels.isNotEmpty()) ChannelsRow(channels, navigator)
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
private fun ChannelsRow(channels: List<SubscribedChannel>, navigator: Navigator) {
    Column(Modifier.testTag("subs.channelsRow")) {
        SectionHeader("Channels") { TextButton(onClick = { navigator.push(Route.ManageSubscriptions) }, modifier = Modifier.testTag("subs.viewAll")) { Text("View all", maxLines = 1) } }
        HScrollRow(horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
            items(channels, key = { it.channelId }) { c ->
                Column(
                    Modifier.width(76.dp).clip(Vela.mediumShape).clickable { navigator.push(Route.Channel(c.channelId, c.name)) }.padding(vertical = Vela.xs).testTag("subs.channel"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Vela.xs),
                ) {
                    Avatar(c.name, size = 56.dp, url = c.avatarUrl)
                    Text(c.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** A few of the newest Shorts as tall cards; a tap opens the Shorts player on it. */
@Composable
private fun ShortsRow(shorts: List<VideoSummary>, navigator: Navigator) {
    Column(Modifier.testTag("subs.shortsRow")) {
        SectionHeader("Shorts")
        HScrollRow(horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
            items(shorts, key = { "short-${it.id}" }) { v ->
                Box(
                    Modifier.width(118.dp).aspectRatio(9f / 16f).clip(Vela.thumbnailShape).background(Vela.colors.surfaceVariant.toColor()).clickable { navigator.push(Route.Video(v)) }.testTag("subs.short"),
                ) {
                    AsyncImage(v.thumbnailUrl ?: "https://i.ytimg.com/vi/${v.id}/hq720.jpg", null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Vela.colors.mediaScrim.toColor().copy(alpha = 0.55f)).padding(Vela.xs)) {
                        Column {
                            Text(v.title, color = Vela.colors.onMedia.toColor(), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            formatViews(v.viewCount)?.let { Text(it, color = Vela.colors.onMedia.toColor().copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Every channel you follow, as a grid with a search box. Tap opens the channel; press and hold offers Unsubscribe. Import takes a YouTube Takeout
 * subscriptions.csv or a NewPipe export.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AllChannelsScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    var revision by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val channels = remember(revision) { services.subscriptions.all() }
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
    LazyVerticalGrid(
        GridCells.Adaptive(minSize = 104.dp), modifier.fillMaxSize().testTag("subs.channels"), contentPadding = Vela.contentPadding,
        horizontalArrangement = Arrangement.spacedBy(Vela.sm), verticalArrangement = Arrangement.spacedBy(Vela.sm),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${channels.size} channel${if (channels.size == 1) "" else "s"}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    SecondaryButton("Import", pick, Modifier.testTag("subs.import"))
                }
                if (channels.size > 8) VelaSearchField(query, { query = it }, {}, Modifier.fillMaxWidth(), placeholder = "Search channels")
                Text("Press and hold a channel to unsubscribe. Import accepts a YouTube Takeout subscriptions.csv or a NewPipe export.", style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor())
                message?.let { Text(it, Modifier.testTag("subs.importMessage"), style = MaterialTheme.typography.bodySmall) }
            }
        }
        gridItems(shown, key = { it.channelId }) { c ->
            var menu by remember { mutableStateOf(false) }
            Box {
                Column(
                    Modifier.fillMaxWidth().clip(Vela.mediumShape).background(Vela.colors.surface.toColor())
                        .combinedClickable(onClick = { navigator.push(Route.Channel(c.channelId, c.name)) }, onLongClick = { menu = true })
                        .padding(vertical = Vela.md, horizontal = Vela.xs).testTag("subs.channelTile"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Vela.xs),
                ) {
                    Avatar(c.name, size = 64.dp, url = c.avatarUrl)
                    Text(c.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Unsubscribe") },
                        onClick = { menu = false; services.subscriptions.unsubscribe(c.channelId); revision++; services.feed.reload() },
                        modifier = Modifier.testTag("subs.unsubscribe"),
                    )
                }
            }
        }
    }
}
