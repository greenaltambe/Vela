package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.extractor.ChannelPage
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.ui.components.Avatar
import com.greenaltambe.vela.ui.components.PrimaryButton
import com.greenaltambe.vela.ui.components.SectionHeader
import com.greenaltambe.vela.ui.components.formatCount
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.toColor
import com.greenaltambe.vela.ui.nav.Navigator
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import com.greenaltambe.vela.ui.components.ErrorRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.ui.layout.ContentScale
import com.greenaltambe.vela.extractor.Page
import com.greenaltambe.vela.extractor.PlaylistRef
import com.greenaltambe.vela.core.settings.shortsEnabled
import com.greenaltambe.vela.ui.components.HScrollRow
import com.greenaltambe.vela.ui.nav.Route
import coil3.compose.AsyncImage

enum class ChannelTab(val label: String) { HOME("Home"), VIDEOS("Videos"), SHORTS("Shorts"), LIVE("Live"), PLAYLISTS("Playlists"), ABOUT("About") }
enum class ChannelSort(val label: String) { LATEST("Latest"), POPULAR("Popular"), OLDEST("Oldest") }
enum class PlaylistSort(val label: String) { RECENT("Recently added"), AZ("A to Z") }

/** The sort applies to what is loaded so far; the source's own order is newest first. */
private fun List<VideoSummary>.sortedBy(sort: ChannelSort): List<VideoSummary> = when (sort) {
    ChannelSort.LATEST -> this
    ChannelSort.POPULAR -> sortedByDescending { it.viewCount ?: -1 }
    ChannelSort.OLDEST -> asReversed()
}

/**
 * A channel: its header (picture, name, subscribers, Subscribe, automatic downloads), then tabs: Home (the latest uploads), Videos, Shorts (when
 * Shorts are on), Live, Playlists and About. Each list tab has sorting pills; they sort what has loaded so far (more loads as you reach the end).
 */
@Composable
fun ChannelScreen(services: AppServices, navigator: Navigator, routeChannelId: String, channelName: String, modifier: Modifier = Modifier, viaVideoId: String = "") {
    // A listing without a channel id: the id is found from the video while the page already shows its loader.
    var resolvedId by remember(routeChannelId, viaVideoId) { mutableStateOf(routeChannelId) }
    val channelId = resolvedId
    val store = LocalChannelStore.current
    val st = remember(channelId) { if (channelId.isBlank()) ChannelState(services.clock.now()) else store?.stateFor(channelId, services.clock.now()) ?: ChannelState(services.clock.now()) }
    var page by st::page
    var subscribed by remember(channelId) { mutableStateOf(services.subscriptions.isSubscribed(channelId)) }
    val scope = rememberCoroutineScope()
    val shortsOn = services.settings.shortsEnabled()
    val tabs = remember(shortsOn) { ChannelTab.entries.filter { shortsOn || it != ChannelTab.SHORTS } }
    var tab by st::tab
    var sort by st::sort
    var playlistSort by st::playlistSort
    val videos = st.videos
    val shorts = st.shorts
    val live = st.live
    val playlists = st.playlists

    fun first() {
        videos.state = ListState.Loading
        scope.launch {
            when (val r = services.extractor.channel(channelId)) {
                is ExtractorResult.Success -> {
                    page = r.value; videos.items = r.value.videos; videos.next = r.value.continuation; videos.loaded = true
                    videos.state = if (videos.items.isEmpty()) ListState.Empty("This channel has no videos.") else ListState.Ready
                }
                is ExtractorResult.Failure -> videos.state = ListState.Error("Channel unavailable: ${r.error.message}") { first() }
            }
        }
    }
    LaunchedEffect(channelId, viaVideoId) {
        if (channelId.isNotBlank()) { if (!videos.loaded) first(); return@LaunchedEffect }
        videos.state = ListState.Loading
        val info = if (viaVideoId.isBlank()) null else (services.extractor.videoInfo(viaVideoId) as? ExtractorResult.Success)?.value
        if (info != null && info.channelId.isNotBlank()) resolvedId = info.channelId
        else videos.state = ListState.Error("This channel could not be found.") { }
    }

    /** Loads a page of one tab (the first when [more] is false). */
    fun <T> load(feed: TabFeed<T>, more: Boolean, empty: String, idOf: (T) -> String, fetch: suspend (String?) -> ExtractorResult<Page<T>>) {
        if (feed.loading) return
        feed.loading = true
        if (!more) feed.state = ListState.Loading
        scope.launch {
            when (val r = fetch(feed.next.takeIf { more })) {
                is ExtractorResult.Success -> {
                    feed.items = if (more) (feed.items + r.value.items).distinctBy(idOf) else r.value.items
                    feed.next = r.value.continuation; feed.loaded = true
                    feed.state = if (feed.items.isEmpty()) ListState.Empty(empty) else ListState.Ready
                }
                is ExtractorResult.Failure -> if (feed.items.isEmpty()) feed.state = ListState.Error("Could not load: ${r.error.message}") { feed.loading = false; load(feed, false, empty, idOf, fetch) }
            }
            feed.loading = false
        }
    }
    fun moreVideos() = load(videos, true, "This channel has no videos.", { it.id }) { services.extractor.channelVideos(channelId, it) }
    LaunchedEffect(tab, channelId) {
        if (channelId.isBlank()) return@LaunchedEffect
        when (tab) {
            ChannelTab.SHORTS -> if (!shorts.loaded) load(shorts, false, "No Shorts on this channel.", { it.id }) { services.extractor.channelShorts(channelId, it) }
            ChannelTab.LIVE -> if (!live.loaded) load(live, false, "No live streams on this channel.", { it.id }) { services.extractor.channelLive(channelId, it) }
            ChannelTab.PLAYLISTS -> if (!playlists.loaded) load(playlists, false, "No playlists on this channel.", { it.id }) { services.extractor.channelPlaylists(channelId, it) }
            else -> {}
        }
    }

    val name = page?.name ?: channelName
    // Everything above the list: the channel, then the tab pills and, on list tabs, the sort pills.
    val header: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
            page?.bannerUrl?.let { banner ->
                AsyncImage(banner, null, Modifier.fillMaxWidth().aspectRatio(16f / 4.5f).clip(Vela.thumbnailShape).testTag("channel.banner"), contentScale = ContentScale.Crop)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.md)) {
                Avatar(name, size = 64.dp, url = page?.avatarUrl)
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("channel.name"))
                    formatCount(page?.subscriberCount)?.let { Text("$it subscribers", style = MaterialTheme.typography.bodySmall, maxLines = 1, color = Vela.colors.onSurfaceVariant.toColor()) }
                }
            }
            if (subscribed) OutlinedButton(onClick = { services.subscriptions.unsubscribe(channelId); services.feed.reload(); subscribed = false }, modifier = Modifier.testTag("channel.subscribe")) { Text("Subscribed", maxLines = 1) }
            else PrimaryButton("Subscribe", onClick = { services.subscriptions.subscribe(channelId, name, page?.avatarUrl, services.clock.now()); subscribed = true }, modifier = Modifier.testTag("channel.subscribe"))
            if (subscribed && services.downloads != null) {
                var auto by remember(channelId) { mutableStateOf(services.autoDownloadRules.ruleFor(channelId)?.enabled == true) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
                    androidx.compose.material3.Switch(auto, {
                        auto = it
                        if (it) services.autoDownloadRules.upsert(com.greenaltambe.vela.downloads.AutoDownloadRule(channelId, name, sinceMs = services.clock.now().toEpochMilliseconds()))
                        else services.autoDownloadRules.ruleFor(channelId)?.let { r -> services.autoDownloadRules.upsert(r.copy(enabled = false)) }
                    }, modifier = Modifier.testTag("channel.autoDownload"))
                    Text("Download new videos automatically", style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                }
            }
            HScrollRow(Modifier.testTag("channel.tabs")) {
                items(tabs) { t -> FilterChip(selected = tab == t, onClick = { tab = t; sort = ChannelSort.LATEST }, label = { Text(t.label, maxLines = 1) }, modifier = Modifier.testTag("channel.tab.${t.name}")) }
            }
            when (tab) {
                ChannelTab.VIDEOS, ChannelTab.SHORTS, ChannelTab.LIVE -> {
                    HScrollRow(Modifier.testTag("channel.sort")) {
                        items(ChannelSort.entries) { o -> FilterChip(selected = sort == o, onClick = { sort = o }, label = { Text(o.label, maxLines = 1) }, modifier = Modifier.testTag("channel.sort.${o.name}")) }
                    }
                    if (sort != ChannelSort.LATEST) Text("Sorted among the videos loaded so far.", style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor())
                }
                ChannelTab.PLAYLISTS -> HScrollRow(Modifier.testTag("channel.sort")) {
                    items(PlaylistSort.entries) { o -> FilterChip(selected = playlistSort == o, onClick = { playlistSort = o }, label = { Text(o.label, maxLines = 1) }, modifier = Modifier.testTag("channel.sort.${o.name}")) }
                }
                else -> {}
            }
        }
    }

    when (tab) {
        ChannelTab.HOME -> VideoCollection(
            services, navigator, videos.items.take(12), videos.state, testTag = "channel.list", showChannelAvatar = false, scroll = st.scrollFor(ChannelTab.HOME),
            header = {
                Column(verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
                    header()
                    page?.description?.takeIf { it.isNotBlank() }?.let { Text(it, Modifier.fillMaxWidth().clip(Vela.mediumShape).background(Vela.colors.surface.toColor()).padding(Vela.md), style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis) }
                    SectionHeader("Latest uploads")
                }
            },
            footer = if (videos.items.size > 12 || videos.next != null) ({ TextButton(onClick = { tab = ChannelTab.VIDEOS }, modifier = Modifier.testTag("channel.seeAll")) { Text("See all videos", maxLines = 1) } }) else null,
        )
        ChannelTab.VIDEOS -> VideoCollection(
            services, navigator, videos.items.sortedBy(sort), videos.state, testTag = "channel.list", showChannelAvatar = false, header = header, scroll = st.scrollFor(ChannelTab.VIDEOS),
            onEndReached = if (videos.next != null) ({ moreVideos() }) else null,
            footer = if (videos.next != null) ({ TextButton(onClick = ::moreVideos, enabled = !videos.loading) { Text("Load more", maxLines = 1) } }) else null,
        )
        ChannelTab.SHORTS -> VideoCollection(
            services, navigator, shorts.items.sortedBy(sort), shorts.state, testTag = "channel.list", showChannelAvatar = false, header = header, scroll = st.scrollFor(ChannelTab.SHORTS),
            onEndReached = if (shorts.next != null) ({ load(shorts, true, "No Shorts on this channel.", { it.id }) { services.extractor.channelShorts(channelId, it) } }) else null,
        )
        ChannelTab.LIVE -> VideoCollection(
            services, navigator, live.items.sortedBy(sort), live.state, testTag = "channel.list", showChannelAvatar = false, header = header, scroll = st.scrollFor(ChannelTab.LIVE),
            onEndReached = if (live.next != null) ({ load(live, true, "No live streams on this channel.", { it.id }) { services.extractor.channelLive(channelId, it) } }) else null,
        )
        ChannelTab.PLAYLISTS -> {
            val shown = remember(playlists.items, playlistSort) { if (playlistSort == PlaylistSort.AZ) playlists.items.sortedBy { it.title.lowercase() } else playlists.items }
            val mem = st.scrollFor(ChannelTab.PLAYLISTS)
            val plState = androidx.compose.foundation.lazy.rememberLazyListState(mem.index, mem.offset)
            androidx.compose.runtime.DisposableEffect(Unit) { onDispose { mem.index = plState.firstVisibleItemIndex; mem.offset = plState.firstVisibleItemScrollOffset } }
            LazyColumn(Modifier.fillMaxSize().testTag("channel.playlists"), state = plState, contentPadding = Vela.contentPadding, verticalArrangement = Arrangement.spacedBy(Vela.md)) {
                item { header() }
                when (val st = playlists.state) {
                    ListState.Loading -> item { SectionHeader("Loading playlists…") }
                    is ListState.Empty -> item { Text(st.message, style = MaterialTheme.typography.bodyMedium, color = Vela.colors.onSurfaceVariant.toColor()) }
                    is ListState.Error -> item { ErrorRow(st.message, st.onRetry) }
                    ListState.Ready -> {}
                }
                items(shown, key = { "pl-${it.id}" }) { p -> PlaylistRow(p) { navigator.push(Route.ChannelPlaylist(p.id, p.title)) } }
                if (playlists.next != null) item { TextButton(onClick = { load(playlists, true, "No playlists on this channel.", { it.id }) { services.extractor.channelPlaylists(channelId, it) } }, enabled = !playlists.loading) { Text("Load more", maxLines = 1) } }
            }
        }
        ChannelTab.ABOUT -> LazyColumn(Modifier.fillMaxSize().testTag("channel.about"), contentPadding = Vela.contentPadding, verticalArrangement = Arrangement.spacedBy(Vela.md)) {
            item { header() }
            item {
                val linkColor = Vela.colors.primary.toColor()
                val text = page?.description?.takeIf { it.isNotBlank() }
                Column(Modifier.fillMaxWidth().clip(Vela.mediumShape).background(Vela.colors.surface.toColor()).padding(Vela.md), verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
                    if (text != null) Text(remember(text, linkColor) { linkedText(text, linkColor) {} }, style = MaterialTheme.typography.bodyMedium)
                    else Text("This channel has no description.", style = MaterialTheme.typography.bodyMedium, color = Vela.colors.onSurfaceVariant.toColor())
                    formatCount(page?.subscriberCount)?.let { Text("$it subscribers", style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor()) }
                }
            }
        }
    }
}

/** A playlist as a row: thumbnail, title, video count. */
@Composable
internal fun PlaylistRow(p: PlaylistRef, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(Vela.mediumShape).clickable(onClick = onClick).testTag("channel.playlist"), horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
        Box(Modifier.width(140.dp).aspectRatio(16f / 9f).clip(Vela.thumbnailShape).background(Vela.colors.surfaceVariant.toColor())) {
            p.thumbnailUrl?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            // Marks it as a playlist, not a video: the icon of the chosen icon pack on the thumbnail's corner.
            Row(Modifier.align(Alignment.BottomEnd).padding(Vela.xs).clip(Vela.pillShape).background(Vela.colors.surface.toColor().copy(alpha = 0.85f)).padding(horizontal = Vela.xs, vertical = 2.dp).testTag("playlist.badge"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                androidx.compose.material3.Icon(Vela.icons.playlist, null, Modifier.size(16.dp), tint = Vela.colors.onSurface.toColor())
                p.videoCount?.let { Text("$it", style = MaterialTheme.typography.labelSmall, maxLines = 1, color = Vela.colors.onSurface.toColor()) }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
            Text(p.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (p.channelName.isNotBlank()) Text(p.channelName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Vela.colors.onSurfaceVariant.toColor())
            p.videoCount?.let { Text("$it video${if (it == 1L) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor()) }
        }
    }
}
