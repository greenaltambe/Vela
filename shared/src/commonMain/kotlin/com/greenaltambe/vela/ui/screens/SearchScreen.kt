package com.greenaltambe.vela.ui.screens

import com.greenaltambe.vela.extractor.map
import com.greenaltambe.vela.ui.theme.AppIcons
import androidx.compose.foundation.shape.CircleShape
import com.greenaltambe.vela.ui.theme.Spacing
import androidx.compose.foundation.clickable
import com.greenaltambe.vela.extractor.VideoSummary
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import com.greenaltambe.vela.ui.components.SkeletonCard
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.core.settings.shortsEnabled
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.ChannelRef
import com.greenaltambe.vela.extractor.PlaylistRef
import com.greenaltambe.vela.extractor.videoPagedList
import com.greenaltambe.vela.modules.dearrow.DeArrowSurface
import com.greenaltambe.vela.ui.components.SectionHeader
import com.greenaltambe.vela.ui.components.EmptyState
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
import com.greenaltambe.vela.ui.search.*

/**
 * The full search view: a query field with clear and back, the history list while the field is empty, paged results
 * below once something is searched. The field always starts from this route's query, never from the last one typed
 * elsewhere, and a new search replaces this screen rather than stacking another.
 */
@Composable
fun SearchScreen(services: AppServices, navigator: Navigator, routeQuery: String, edit: Boolean, modifier: Modifier = Modifier) {
    // The typing view of a results page (opened from the top bar's pill) is this screen with nothing searched yet and the old query in the field.
    val query = if (edit) "" else routeQuery
    var text by remember(routeQuery, edit) { mutableStateOf(routeQuery) }
    val store = LocalSearchStore.current
    val cached = remember(query) { if (query.isBlank()) null else store?.fresh(query, services.clock.now()) }
    var results by remember(query) { mutableStateOf(cached?.videos ?: videoPagedList()) }
    var state by remember(query) { mutableStateOf<ListState>(if (query.isBlank() || cached != null) ListState.Ready else ListState.Loading) }
    var loading by remember(query) { mutableStateOf(false) }
    var confirmClearSearches by remember { mutableStateOf(false) }
    var historyRevision by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val history = remember(historyRevision, query) { if (query.isBlank()) services.searchHistory.recent() else emptyList() }

    var type by remember(query) { mutableStateOf(SearchType.ALL) }
    val channelFeed = remember(query) { TabFeed<ChannelRef>() }
    val playlistFeed = remember(query) { TabFeed<PlaylistRef>() }
    // The video search returns almost no Shorts, so they are looked for on their own ("<query> #shorts") and kept when YouTube marks them or they last a minute or less.
    val shortsFeed = remember(query) { TabFeed<VideoSummary>() }
    // A pasted YouTube address opens what it points at instead of being searched for.
    fun submit(q: String) { if (q.isNotBlank() && !navigator.openYoutubeLink(q)) navigator.replace(Route.Search(q.trim())) }

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
                is ExtractorResult.Failure -> if (results.items.isEmpty()) state = ListState.Error("Couldn't load results. Check your connection and try again.") { loading = false; load(false) }
            }
            loading = false
        }
    }
    /** Channel and playlist results are searched when their pill is first chosen, and paged like the videos. */
    fun <T> loadKind(feed: TabFeed<T>, more: Boolean, empty: String, idOf: (T) -> String, fetch: suspend (String?) -> ExtractorResult<com.greenaltambe.vela.extractor.Page<T>>) {
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
                is ExtractorResult.Failure -> if (feed.items.isEmpty()) feed.state = ListState.Error("Couldn't load these. Check your connection and try again.") { feed.loading = false; loadKind(feed, false, empty, idOf, fetch) }
            }
            feed.loading = false
        }
    }
    fun moreChannels(more: Boolean) = loadKind(channelFeed, more, "No channels for \"$query\".", { it.id }) { services.extractor.searchChannels(query, it).map { p -> p.copy(items = p.items.filterNot { c -> services.blocklist.isBlocked(c.id) }) } }
    fun morePlaylists(more: Boolean) = loadKind(playlistFeed, more, "No playlists for \"$query\".", { it.id }) { services.extractor.searchPlaylists(query, it) }
    val shortsPages = remember(query) { intArrayOf(0) }
    fun moreShorts(more: Boolean) {
        if (shortsFeed.loading) return
        if (!more) shortsPages[0] = 0
        shortsPages[0]++
        shortsFeed.loading = true
        if (!more) shortsFeed.state = ListState.Loading
        scope.launch {
            when (val r = services.extractor.search("$query #shorts", shortsFeed.next.takeIf { more })) {
                is ExtractorResult.Success -> {
                    // "#shorts" pulls in other people's Shorts: keep those whose title or channel holds every word searched for.
                    val words = query.lowercase().split(' ', '-', '_').filter { it.length >= 2 }
                    val shorts = r.value.items.filter { it.isShort || (it.durationSeconds ?: Long.MAX_VALUE) in 1..60 }
                        .filter { v -> val hay = (v.title + " " + v.channelName).lowercase(); val flat = hay.filter { c -> c.isLetterOrDigit() }; words.all { w -> w in hay } || query.lowercase().filter { c -> c.isLetterOrDigit() } in flat }
                        .map { if (it.isShort) it else it.copy(isShort = true) }
                    shortsFeed.items = if (more) (shortsFeed.items + shorts).distinctBy { it.id } else shorts
                    shortsFeed.next = r.value.continuation; shortsFeed.loaded = true
                    shortsFeed.state = if (shortsFeed.items.isEmpty() && shortsFeed.next == null) ListState.Empty("No Shorts for \"$query\".") else ListState.Ready
                }
                is ExtractorResult.Failure -> if (shortsFeed.items.isEmpty()) shortsFeed.state = ListState.Error("Couldn't load Shorts. Check your connection and try again.") { shortsFeed.loading = false; moreShorts(false) }
            }
            shortsFeed.loading = false
            // The "#shorts" search mixes in long videos; a thin page asks for the next one, a few times.
            if (shortsFeed.items.size < 8 && shortsFeed.next != null && shortsPages[0] < 4) moreShorts(true)
        }
    }
    LaunchedEffect(type, query, state) {
        if (query.isBlank()) return@LaunchedEffect
        if (type == SearchType.SHORTS && !shortsFeed.loaded) moreShorts(false)
        // The All page borrows a channel card, a channels shelf and Shorts blocks from these once the videos are in.
        if (type == SearchType.ALL && state == ListState.Ready) {
            if (!channelFeed.loaded) moreChannels(false)
            if (!shortsFeed.loaded) moreShorts(false)
        }
        if (type == SearchType.CHANNELS && !channelFeed.loaded) moreChannels(false)
        if (type == SearchType.PLAYLISTS && !playlistFeed.loaded) morePlaylists(false)
    }
    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            services.searchHistory.add(query, services.clock.now())
            // Coming back to a search that was just made: its results are already here.
            if (cached == null) load(more = false)
        } else runCatching { focus.requestFocus() }
    }
    // Keep what is shown for the next visit; a fresh answer stamps the time.
    androidx.compose.runtime.SideEffect { if (query.isNotBlank() && results.items.isNotEmpty() && store != null) store.put(query, results, services.clock.now(), refreshedAt = cached == null) }
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

    var sheet by remember { mutableStateOf(false) }
    if (sheet) SearchFilterSheet(filters, { filters = it }, { sheet = false })
    val shortsOn = services.settings.shortsEnabled()
    val types = remember(shortsOn) { listOfNotNull(SearchType.ALL, SearchType.VIDEOS, SearchType.SHORTS.takeIf { shortsOn }, SearchType.LIVE, SearchType.CHANNELS, SearchType.PLAYLISTS) }
    // Videos, Shorts and Live are views of the same loaded results; when one has few entries the next pages are fetched (a few, behind the placeholders).
    val view = remember(shown, type, shortsFeed.items, filters) {
        when (type) {
            SearchType.VIDEOS -> shown.filter { !it.isShort && !it.isLive }
            SearchType.SHORTS -> filters.apply(shortsFeed.items)
            SearchType.LIVE -> shown.filter { it.isLive }
            else -> shown
        }
    }
    var autoPages by remember(query, type) { mutableStateOf(0) }
    LaunchedEffect(type, view.size, results.continuation, loading, state) {
        val isView = type == SearchType.VIDEOS || type == SearchType.LIVE
        if (isView && view.size < 8 && results.continuation != null && !loading && state == ListState.Ready && autoPages < 4) { autoPages++; load(more = true) }
    }

    val searching = loading || ((type == SearchType.VIDEOS || type == SearchType.LIVE) && view.size < 8 && results.continuation != null && autoPages < 4)
    // Desktop: the top bar's search pill leads here, so the field starts as wide as the pill and grows to the row's width.
    val desktop = com.greenaltambe.vela.ui.platform.LocalVolumeSlider.current
    // The field settles into place instead of sliding in: from where the top bar's search pill was (slightly higher, a touch smaller) and faded in; on a phone only the
    // fade and the small scale. Skipped with reduced motion.
    val reduced = com.greenaltambe.vela.ui.theme.LocalMotion.current.reduced
    var entered by remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) { entered = true }
    val enter by androidx.compose.animation.core.animateFloatAsState(if (entered) 1f else 0f, androidx.compose.animation.core.tween(240, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "searchFieldEnter")
    val fieldShare = 1f
    val settle = Modifier.graphicsLayer {
        alpha = enter
        val s = 0.96f + 0.04f * enter
        scaleX = s; scaleY = s
        if (desktop) translationY = -(1f - enter) * 56.dp.toPx()
    }
    if (confirmClearSearches) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmClearSearches = false }, title = { Text("Clear recent searches?") },
        text = { Text("This clears them here and, through Sync, on your other devices too.") },
        confirmButton = { androidx.compose.material3.TextButton({ services.syncService.markIntentionalDelete(); services.searchHistory.clear(); historyRevision++; confirmClearSearches = false }, Modifier.testTag("search.clearHistory.confirm")) { Text("Clear") } },
        dismissButton = { androidx.compose.material3.TextButton({ confirmClearSearches = false }) { Text("Cancel") } },
    )
    Column(modifier.fillMaxSize()) {
        val tuneVisible = query.isNotBlank() && type != SearchType.CHANNELS && type != SearchType.PLAYLISTS
        if (desktop && query.isNotBlank()) {
            // A results page: the top bar's pill shows what was searched (and opens the typing view); here only back and the filters.
            Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).heightIn(min = 48.dp).padding(horizontal = Spacing.xs)) {
                IconButton(onClick = { navigator.back() }, modifier = Modifier.align(Alignment.CenterStart).testTag("nav.back")) { Icon(AppIcons.back, "Back") }
                if (tuneVisible) Box(Modifier.align(Alignment.CenterEnd)) { FilterTuneButton(!filters.isDefault) { sheet = true } }
            }
        } else if (desktop) {
            // One centred column for the field and the lists under it, so their edges line up; the back and filter buttons sit at the window's sides.
            Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).heightIn(min = 64.dp).padding(horizontal = Spacing.xs)) {
                IconButton(onClick = { navigator.back() }, modifier = Modifier.align(Alignment.CenterStart).testTag("nav.back")) { Icon(AppIcons.back, "Back") }
                com.greenaltambe.vela.ui.components.VelaSearchField(text, { text = it }, { submit(text) }, Modifier.align(Alignment.Center).widthIn(max = SearchColumnMax).fillMaxWidth(fieldShare).then(settle), focusRequester = focus, compact = true)
                if (tuneVisible) Box(Modifier.align(Alignment.CenterEnd)) { FilterTuneButton(!filters.isDefault) { sheet = true } }
            }
        } else Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).heightIn(min = 56.dp).padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navigator.back() }, modifier = Modifier.testTag("nav.back")) { Icon(AppIcons.back, "Back") }
            Box(Modifier.weight(1f).padding(end = Spacing.xs), contentAlignment = Alignment.CenterEnd) {
                com.greenaltambe.vela.ui.components.VelaSearchField(text, { text = it }, { submit(text) }, Modifier.fillMaxWidth(fieldShare).then(settle), focusRequester = focus)
            }
            if (tuneVisible) FilterTuneButton(!filters.isDefault) { sheet = true }
        }
        if (suggestions.isNotEmpty() && text.trim() != query) {
            LazyColumn(Modifier.then(if (desktop) Modifier.widthIn(max = SearchColumnMax).align(Alignment.CenterHorizontally) else Modifier).fillMaxSize().testTag("search.suggestions"), contentPadding = if (desktop) PaddingValues(top = Spacing.sm, bottom = Spacing.lg) else Spacing.contentPadding, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(suggestions, key = { _, it -> it }) { i, q ->
                    SearchTextRow(q, AppIcons.search, i, suggestions.size, onClick = { submit(q) }) {
                        // The arrow copies the suggestion into the field to keep typing after it, without searching.
                        IconButton(onClick = { text = "$q " }, modifier = Modifier.testTag("search.suggestion.fill")) { Icon(AppIcons.arrowUp, "Use \"$q\" in the search field", Modifier.graphicsLayer { rotationZ = -45f }) }
                    }
                }
            }
        } else if (query.isBlank()) {
            // The start page: only while nothing is typed. Typing hides it, leaving the field and its suggestions.
            if (text.isBlank()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { SearchStart(navigator, history, onFill = { text = "$it " }, onRemove = { services.searchHistory.remove(it); historyRevision++ }, onClear = { if (services.syncService.config.value.isSetUp && com.greenaltambe.vela.sync.SyncKind.HISTORY in services.syncService.config.value.kinds) confirmClearSearches = true else { services.searchHistory.clear(); historyRevision++ } }) }
        } else {
            SearchTypeChips(type, types, { type = it }, Modifier.padding(bottom = Spacing.sm))
            when (type) {
                SearchType.CHANNELS -> SearchKindList(channelFeed, "search.channels.list", { moreChannels(true) }) { c ->
                    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerLow).clickable { navigator.push(Route.Channel(c.id, c.name)) }.padding(Spacing.md).testTag("search.channel"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        com.greenaltambe.vela.ui.components.Avatar(c.name, size = 64.dp, url = c.avatarUrl)
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            com.greenaltambe.vela.ui.components.formatCount(c.subscriberCount)?.let { Text("$it subscribers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (c.description.isNotBlank()) Text(c.description, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                SearchType.PLAYLISTS -> SearchKindList(playlistFeed, "search.playlists.list", { morePlaylists(true) }) { p -> PlaylistRow(p) { navigator.push(Route.ChannelPlaylist(p.id, p.title)) } }
                SearchType.SHORTS -> if (view.isEmpty()) SearchKindList(shortsFeed, "search.shorts.list", { moreShorts(true) }) { }
                    else ShortsGrid(view, onOpen = { v -> navigator.push(Route.Video(v)) }, onEndReached = { if (shortsFeed.next != null) moreShorts(true) })
                else -> {
                    // Never an unexplained empty list: say when Settings > Feed rules (minimum length, hide watched, keywords) hid results.
                    (results.items.size - shownAll.size).takeIf { it > 0 }?.let { Text("$it result${if (it == 1) "" else "s"} hidden by your filters or blocklist.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = Spacing.gutter).testTag("search.hiddenByFeed")) }
                    if (!filters.isDefault && shown.isEmpty() && shownAll.isNotEmpty()) Text("Nothing in the ${shownAll.size} loaded results matches. Filters apply to loaded results; load more or relax them.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = Spacing.gutter).testTag("search.filterEmpty"))
                    val isAll = type == SearchType.ALL
                    val mainVideos = remember(view, isAll) { if (isAll) view.filter { !it.isShort } else view }
                    val top = remember(query, channelFeed.items, isAll) { if (isAll) com.greenaltambe.vela.search.topChannel(query, channelFeed.items) else null }
                    val latest = remember(top, mainVideos) { top?.let { com.greenaltambe.vela.search.latestFrom(it, mainVideos) } }
                    val shortsPool = shortsFeed.items
                    val slots = remember(mainVideos.size, shortsPool.size, isAll) { if (isAll) com.greenaltambe.vela.search.planShortsSlots(mainVideos.size, shortsPool.size) else emptyList() }
                    val otherChannels = remember(channelFeed.items, top, isAll) { if (isAll) channelFeed.items.filter { it.id != top?.id }.take(12) else emptyList() }
                    val injections = remember(slots, otherChannels, shortsPool, mainVideos.size) {
                        val list = ArrayList<Injection>()
                        slots.forEach { sl -> list += Injection(sl.afterVideos, "shorts-${sl.from}", "shorts") {
                            com.greenaltambe.vela.ui.components.ShortsCarousel("Shorts", shortsPool.subList(sl.from, sl.to), tag = "search.shorts", onSeeAll = { type = SearchType.SHORTS }, onOpen = { v -> navigator.push(Route.Video(v)) })
                        } }
                        if (otherChannels.size >= 2 && mainVideos.size > 6) list += Injection(6, "channels", "channels") {
                            com.greenaltambe.vela.ui.components.ChannelCarousel("Channels", otherChannels.map { com.greenaltambe.vela.data.SubscribedChannel(it.id, it.name, it.avatarUrl, kotlin.time.Instant.fromEpochMilliseconds(0)) }, tag = "search.channelShelf", seeAllLabel = "See all", onSeeAll = { type = SearchType.CHANNELS }, onOpen = { c -> navigator.push(Route.Channel(c.channelId, c.name)) })
                        }
                        list
                    }
                    var topSubscribed by remember(top?.id) { mutableStateOf(top?.let { services.subscriptions.isSubscribed(it.id) } ?: false) }
                    VideoCollection(
                        services, navigator, mainVideos, if (mainVideos.isEmpty() && type != SearchType.ALL && state == ListState.Ready && !searching) ListState.Empty("Nothing here for \"$query\".") else state,
                        grid = true, testTag = "search.list", scroll = store?.scrollFor(query, services.clock.now()), injections = injections,
                        // The channel card arrives after the first videos; the list jumps back up so it is not left above the view.
                        scrollToTopKey = top?.id,
                        header = if (top != null) ({
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                TopChannelCard(top, topSubscribed, onSubscribe = {
                                    if (topSubscribed) services.subscriptions.unsubscribe(top.id) else services.subscriptions.subscribe(top.id, top.name, top.avatarUrl, services.clock.now())
                                    services.feed.reload(); topSubscribed = !topSubscribed
                                }, onOpen = { navigator.push(Route.Channel(top.id, top.name)) }, bell = { ChannelBell(services, top.id) })
                                if (latest != null) com.greenaltambe.vela.ui.components.VideoCarousel("From ${top.name}", latest, tag = "search.latest", showChannelAvatar = false, onOpen = { v -> navigator.push(Route.Video(v)) })
                            }
                        }) else null,
                        // More arrives by itself a few cards before the end; while it does, a placeholder card closes the list.
                        onEndReached = { if (results.continuation != null && !loading && state == ListState.Ready) load(more = true) },
                        footer = if (results.continuation != null) ({ SkeletonCard(vertical = true, fullBleed = true, modifier = Modifier.testTag("search.more")) }) else null,
                    )
                }
            }
        }
    }
}

/** A page of channel or playlist results with its loading state and a Load more button. */
@Composable
private fun <T> SearchKindList(feed: TabFeed<T>, tag: String, onMore: () -> Unit, row: @Composable (T) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag(tag), contentPadding = Spacing.contentPadding, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        when (val st = feed.state) {
            ListState.Loading -> item { SectionHeader("Searching…") }
            is ListState.Empty -> item { EmptyState(st.message) }
            is ListState.Error -> item { com.greenaltambe.vela.ui.components.ErrorRow(st.message, st.onRetry) }
            ListState.Ready -> {}
        }
        items(feed.items) { row(it) }
        if (feed.next != null) item { TextButton(onClick = onMore, enabled = !feed.loading) { Text("Load more", maxLines = 1) } }
    }
}

/** The width of the search field and the lists under it on desktop (one column, so their edges line up). */
private val SearchColumnMax = 720.dp

/** Before anything is typed: only the recent searches. Each has an arrow that puts it in the search field (to edit it before searching) and a cross that removes it. */
@Composable
private fun SearchStart(navigator: Navigator, history: List<String>, onFill: (String) -> Unit, onRemove: (String) -> Unit, onClear: () -> Unit) {
    // On desktop the recent searches are a centred list of reasonable width, not a stripe across the whole window.
    val desktop = com.greenaltambe.vela.ui.platform.LocalVolumeSlider.current
    LazyColumn(Modifier.then(if (desktop) Modifier.widthIn(max = SearchColumnMax) else Modifier).fillMaxSize().testTag("search.list"), contentPadding = if (desktop) PaddingValues(top = Spacing.sm, bottom = Spacing.lg) else Spacing.contentPadding, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (history.isEmpty()) item { EmptyState("Search for videos, channels and topics.") }
        else {
            item(key = "recent-h") { SectionHeader("Recent searches") { TextButton(onClick = onClear, modifier = Modifier.testTag("search.clearHistory")) { Text("Clear all", maxLines = 1) } } }
            itemsIndexed(history, key = { _, q -> "r-$q" }) { i, q ->
                SearchTextRow(q, AppIcons.history, i, history.size, onClick = { navigator.replace(Route.Search(q)) }, modifier = Modifier.testTag("search.recent")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // The same arrow as on a suggestion: copies the search into the field to keep typing after it, without searching.
                        IconButton(onClick = { onFill(q) }, modifier = Modifier.testTag("search.recent.fill")) { Icon(AppIcons.arrowUp, "Use \"$q\" in the search field", Modifier.graphicsLayer { rotationZ = -45f }) }
                        IconButton(onClick = { onRemove(q) }) { Icon(AppIcons.close, "Remove from history", Modifier.size(20.dp)) }
                    }
                }
            }
        }
    }
}
