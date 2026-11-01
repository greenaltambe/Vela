package com.greenaltambe.vela.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import coil3.compose.AsyncImage
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.core.settings.shortsEnabled
import com.greenaltambe.vela.extractor.*
import com.greenaltambe.vela.ui.components.*
import com.greenaltambe.vela.ui.look.ComponentIds
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.toColor
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import kotlinx.coroutines.launch

// Preserved for backwards compatibility
enum class ChannelTab(val label: String) { HOME("Home"), VIDEOS("Videos"), SHORTS("Shorts"), LIVE("Live"), PLAYLISTS("Playlists"), ABOUT("About") }
enum class ChannelSort(val label: String) { LATEST("Latest"), POPULAR("Popular"), OLDEST("Oldest") }
enum class PlaylistSort(val label: String) { RECENT("Recently added"), AZ("A to Z") }

/**
 * A channel page with dynamic native tabs discovered from YouTube's response,
 * swipe animations between tabs (HorizontalPager), server-side sort controls,
 * rich Post cards with detail sheet, Course badges, in-channel search and About sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    services: AppServices,
    navigator: Navigator,
    routeChannelId: String,
    channelName: String,
    modifier: Modifier = Modifier,
    viaVideoId: String = "",
) {
    var resolvedId by remember(routeChannelId, viaVideoId) { mutableStateOf(routeChannelId) }
    val channelId = resolvedId
    val store = LocalChannelStore.current
    val st = remember(channelId) {
        if (channelId.isBlank()) ChannelState(services.clock.now())
        else store?.stateFor(channelId, services.clock.now()) ?: ChannelState(services.clock.now())
    }

    var landing by st::landing
    var subscribed by remember(channelId) { mutableStateOf(services.subscriptions.isSubscribed(channelId)) }
    val scope = rememberCoroutineScope()
    val shortsOn = services.settings.shortsEnabled()
    val uriHandler = LocalUriHandler.current

    var aboutSheetOpen by remember { mutableStateOf(false) }
    var activePostDetail by remember { mutableStateOf<ChannelPost?>(null) }
    var channelError by remember(channelId) { mutableStateOf<String?>(null) }
    var channelLoading by remember(channelId) { mutableStateOf(false) }

    fun loadLanding() {
        if (channelId.isBlank() || channelLoading) return
        channelLoading = true
        channelError = null
        scope.launch {
            when (val r = services.extractor.channelLanding(channelId)) {
                is ExtractorResult.Success -> {
                    landing = r.value
                    channelError = null
                    // Seed initial tab feed if initial page was returned
                    val firstTab = r.value.tabs.firstOrNull()
                    if (firstTab != null && r.value.initialPage != null) {
                        val feed = st.feedFor(firstTab.kind.name)
                        feed.items = r.value.initialPage!!.items
                        feed.shelves = r.value.initialPage!!.shelves
                        feed.filters = r.value.initialPage!!.filters
                        feed.nextToken = r.value.initialPage!!.nextToken
                        feed.loaded = true
                        feed.state = if (feed.items.isEmpty() && feed.shelves.isEmpty()) ListState.Empty("No items found.") else ListState.Ready
                    }
                }
                is ExtractorResult.Failure -> {
                    // Fallback to legacy channel call if landing fails
                    when (val legacy = services.extractor.channel(channelId)) {
                        is ExtractorResult.Success -> {
                            val fallbackTabs = listOf(
                                com.greenaltambe.vela.extractor.ChannelTab(ChannelTabKind.VIDEOS, "Videos", selected = true),
                                com.greenaltambe.vela.extractor.ChannelTab(ChannelTabKind.SHORTS, "Shorts"),
                                com.greenaltambe.vela.extractor.ChannelTab(ChannelTabKind.STREAMS, "Live"),
                                com.greenaltambe.vela.extractor.ChannelTab(ChannelTabKind.PLAYLISTS, "Playlists"),
                            )
                            val initPage = ChannelTabPage(items = legacy.value.videos.map { ChannelItem.Video(it) }, nextToken = legacy.value.continuation)
                            landing = ChannelLanding(header = legacy.value, tabs = fallbackTabs, initialPage = initPage)
                            channelError = null
                        }
                        is ExtractorResult.Failure -> {
                            channelError = "Channel unavailable: ${legacy.error.message}"
                        }
                    }
                }
            }
            channelLoading = false
        }
    }

    // Resolve channel id if opened via video id
    LaunchedEffect(channelId, viaVideoId) {
        if (channelId.isBlank() && viaVideoId.isNotBlank()) {
            val info = (services.extractor.videoInfo(viaVideoId) as? ExtractorResult.Success)?.value
            if (info != null && info.channelId.isNotBlank()) {
                resolvedId = info.channelId
            } else {
                channelError = "This channel could not be found."
            }
        }
    }

    // Load initial channel landing
    LaunchedEffect(channelId) {
        if (channelId.isBlank()) return@LaunchedEffect
        if (landing == null) {
            loadLanding()
        }
    }

    if (channelError != null && landing == null) {
        Column(modifier.fillMaxSize().padding(Vela.md)) {
            ErrorRow(channelError!!, onRetry = { loadLanding() })
        }
        return
    }

    // Build tabs list (Area 6: show only what server returned, preserving order; fallback only when raw is empty)
    val tabs = remember(landing, shortsOn) {
        val raw = landing?.tabs ?: emptyList()
        val list = if (raw.isNotEmpty()) {
            // Keep tabs with non-blank titles; UNKNOWN tabs are displayed and clickable
            raw.filter { it.title.isNotBlank() }
        } else listOf(
            com.greenaltambe.vela.extractor.ChannelTab(ChannelTabKind.FEATURED, "Home"),
            com.greenaltambe.vela.extractor.ChannelTab(ChannelTabKind.VIDEOS, "Videos", selected = true),
            com.greenaltambe.vela.extractor.ChannelTab(ChannelTabKind.SHORTS, "Shorts", params = ChannelTabParams.SHORTS),
            com.greenaltambe.vela.extractor.ChannelTab(ChannelTabKind.PLAYLISTS, "Playlists", params = ChannelTabParams.PLAYLISTS),
        )
        list.filter { shortsOn || it.kind != ChannelTabKind.SHORTS }
    }

    val pagerState = rememberPagerState(
        initialPage = st.selectedTabIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)),
        pageCount = { tabs.size }
    )

    LaunchedEffect(pagerState.currentPage) {
        st.selectedTabIndex = pagerState.currentPage
    }

    val pageHeader = landing?.header
    val name = pageHeader?.name?.ifBlank { null } ?: channelName

    var headerHeightPx by remember { mutableStateOf(0f) }
    var headerOffsetPx by remember { mutableStateOf(0f) }

    val nestedScrollConnection = remember(headerHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < 0f && headerHeightPx > 0f) {
                    val newOffset = (headerOffsetPx + delta).coerceIn(-headerHeightPx, 0f)
                    val consumed = newOffset - headerOffsetPx
                    headerOffsetPx = newOffset
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta > 0f && headerHeightPx > 0f) {
                    val newOffset = (headerOffsetPx + delta).coerceIn(-headerHeightPx, 0f)
                    val consumedY = newOffset - headerOffsetPx
                    headerOffsetPx = newOffset
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }
        }
    }

    val density = LocalDensity.current
    val currentHeaderHeightPx = (headerHeightPx + headerOffsetPx).coerceAtLeast(0f)
    val currentHeaderHeightDp = with(density) { currentHeaderHeightPx.toDp() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .nestedScroll(nestedScrollConnection)
    ) {
        // Channel Header: collapses smoothly upwards until tabs touch the scaffolding top bar.
        // Clipped to visible bounds so it never overlaps or scrolls behind the top bar or status bar.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (headerHeightPx > 0f) Modifier.height(currentHeaderHeightDp)
                    else Modifier.wrapContentHeight()
                )
                .clipToBounds()
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        if (headerHeightPx > 0f) {
                            val newOffset = (headerOffsetPx + delta).coerceIn(-headerHeightPx, 0f)
                            headerOffsetPx = newOffset
                        }
                    }
                )
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .onSizeChanged { size ->
                        if (size.height > 0) {
                            headerHeightPx = size.height.toFloat()
                            headerOffsetPx = headerOffsetPx.coerceIn(-size.height.toFloat(), 0f)
                        }
                    }
                    .offset { IntOffset(0, headerOffsetPx.roundToInt()) }
                    .background(Vela.colors.surface.toColor())
                    .padding(horizontal = Vela.md, vertical = Vela.sm),
                verticalArrangement = Arrangement.spacedBy(Vela.sm)
            ) {
                pageHeader?.bannerUrl?.let { banner ->
                    AsyncImage(banner, null, Modifier.fillMaxWidth().aspectRatio(16f / 4.5f).clip(Vela.thumbnailShape).testTag("channel.banner"), contentScale = ContentScale.Crop)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.md)) {
                    Avatar(name, size = 64.dp, url = pageHeader?.avatarUrl)
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("channel.name"))
                        formatCount(pageHeader?.subscriberCount)?.let { Text("$it subscribers", style = MaterialTheme.typography.bodySmall, maxLines = 1, color = Vela.colors.onSurfaceVariant.toColor()) }
                    }
                }
                if (subscribed) {
                    OutlinedButton(onClick = { services.subscriptions.unsubscribe(channelId); services.feed.reload(); subscribed = false }, modifier = Modifier.testTag("channel.subscribe")) { Text("Subscribed", maxLines = 1) }
                } else {
                    PrimaryButton("Subscribe", onClick = { services.subscriptions.subscribe(channelId, name, pageHeader?.avatarUrl, services.clock.now()); subscribed = true }, modifier = Modifier.testTag("channel.subscribe"))
                }

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

                // Short description snippet that opens About sheet
                pageHeader?.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Row(
                        Modifier.fillMaxWidth().clip(Vela.mediumShape).background(Vela.colors.surfaceVariant.toColor()).clickable { aboutSheetOpen = true }.padding(Vela.sm).testTag("channel.aboutTrigger"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(desc, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), color = Vela.colors.onSurfaceVariant.toColor())
                        Text(" More", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Vela.colors.primary.toColor())
                    }
                }
            }
        }

        // Tab strip: stays pinned touching the scaffolding top bar when the header is collapsed
        if (tabs.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = Vela.md,
                containerColor = Vela.colors.surface.toColor(),
                contentColor = Vela.colors.onSurface.toColor(),
                indicator = { tabPositions ->
                    if (pagerState.currentPage < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            color = Vela.colors.primary.toColor()
                        )
                    }
                },
                divider = { HorizontalDivider(color = Vela.colors.outline.toColor()) },
                modifier = Modifier.fillMaxWidth().testTag("channel.tabs")
            ) {
                tabs.forEachIndexed { index, tabItem ->
                    val isSelected = pagerState.currentPage == index
                    Tab(
                        selected = isSelected,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(
                                tabItem.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) Vela.colors.primary.toColor() else Vela.colors.onSurfaceVariant.toColor(),
                                maxLines = 1
                            )
                        },
                        modifier = Modifier.testTag("channel.tab.${tabItem.kind.name.lowercase()}")
                    )
                }
            }

            // HorizontalPager with swipe animations between tabs
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("channel.pager")
            ) { pageIndex ->
                val tab = tabs.getOrNull(pageIndex) ?: return@HorizontalPager
                val feed = st.feedFor(tab.kind.name)

                ChannelTabPane(
                    services = services,
                    navigator = navigator,
                    channelId = channelId,
                    tab = tab,
                    feed = feed,
                    searchQuery = st.searchQuery,
                    onSearchQueryChange = { st.searchQuery = it },
                    onOpenAbout = { aboutSheetOpen = true },
                    onOpenPostDetail = { activePostDetail = it }
                )
            }
        }
    }

    // Full About Bottom Sheet
    if (aboutSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { aboutSheetOpen = false },
            containerColor = Vela.colors.surface.toColor(),
            contentColor = Vela.colors.onSurface.toColor(),
            modifier = Modifier.testTag("channel.aboutSheet")
        ) {
            ChannelAboutContent(
                services = services,
                channelId = channelId,
                aboutToken = landing?.aboutToken,
                initialAbout = st.about,
                onAboutLoaded = { st.about = it },
                onOpenLink = { url -> uriHandler.openUri(url) }
            )
        }
    }

    // Post Detail Sheet with comments
    activePostDetail?.let { post ->
        ModalBottomSheet(
            onDismissRequest = { activePostDetail = null },
            containerColor = Vela.colors.surface.toColor(),
            contentColor = Vela.colors.onSurface.toColor(),
            modifier = Modifier.testTag("channel.postDetailSheet")
        ) {
            PostDetailContent(services, navigator, post, onDismiss = { activePostDetail = null })
        }
    }
}

/** Content for one tab page: load on demand, handles sort controls, paging, item rendering. */
@Composable
private fun ChannelTabPane(
    services: AppServices,
    navigator: Navigator,
    channelId: String,
    tab: com.greenaltambe.vela.extractor.ChannelTab,
    feed: ChannelTabFeed,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenAbout: () -> Unit,
    onOpenPostDetail: (ChannelPost) -> Unit,
) {
    val scope = rememberCoroutineScope()

    fun load(more: Boolean = false, filterOption: ChannelFilterOption? = feed.selectedFilter) {
        if (feed.loading) return
        feed.loading = true
        if (!more) feed.state = ListState.Loading
        scope.launch {
            val res = if (more) {
                services.extractor.channelTab(channelId, tab, filterOption, feed.nextToken)
            } else if (tab.kind == ChannelTabKind.SEARCH) {
                if (searchQuery.isNotBlank()) services.extractor.channelSearch(channelId, searchQuery)
                else ExtractorResult.Success(ChannelTabPage())
            } else {
                services.extractor.channelTab(channelId, tab, filterOption)
            }
            when (res) {
                is ExtractorResult.Success -> {
                    feed.items = if (more) (feed.items + res.value.items) else res.value.items
                    feed.shelves = if (more) (feed.shelves + res.value.shelves) else res.value.shelves
                    if (res.value.filters.isNotEmpty()) feed.filters = res.value.filters
                    feed.nextToken = res.value.nextToken
                    feed.loaded = true
                    feed.state = if (feed.items.isEmpty() && feed.shelves.isEmpty()) {
                        ListState.Empty(tabEmptyMessage(tab.kind))
                    } else {
                        ListState.Ready
                    }
                }
                is ExtractorResult.Failure -> {
                    if (more && (res.error is ExtractorError.TokenExpired || res.error is ExtractorError.TokenNotMine)) {
                        feed.refreshedNotice = true
                        feed.scroll.index = 0
                        feed.scroll.offset = 0
                        feed.loading = false
                        load(more = false, filterOption = filterOption)
                        return@launch
                    }
                    if (feed.items.isEmpty() && feed.shelves.isEmpty()) {
                        feed.state = ListState.Error("Could not load: ${res.error.message}") { load(false, filterOption) }
                    }
                }
            }
            feed.loading = false
        }
    }

    LaunchedEffect(tab.kind, channelId) {
        if (!feed.loaded) load()
    }

    val listState = rememberLazyListState(feed.scroll.index, feed.scroll.offset)
    DisposableEffect(Unit) {
        onDispose {
            feed.scroll.index = listState.firstVisibleItemIndex
            feed.scroll.offset = listState.firstVisibleItemScrollOffset
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("channel.list"),
        contentPadding = Vela.contentPadding,
        verticalArrangement = Arrangement.spacedBy(Vela.md)
    ) {
        // Refreshed Notice banner when continuation expired and feed reloaded from page 1
        if (feed.refreshedNotice) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(Vela.mediumShape)
                        .background(Vela.colors.surfaceVariant.toColor())
                        .padding(horizontal = Vela.md, vertical = Vela.xs)
                        .testTag("channel.refreshedNotice"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Refreshed",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vela.colors.onSurfaceVariant.toColor()
                    )
                    IconButton(
                        onClick = { feed.refreshedNotice = false },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Vela.icons.close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        // Sort & Filter Controls (Dropdown or Chips)
        if (feed.filters.isNotEmpty()) {
            item {
                SortAndFilterBar(
                    groups = feed.filters,
                    selectedOption = feed.selectedFilter,
                    onSelect = { option ->
                        feed.selectedFilter = option
                        feed.scroll.index = 0
                        feed.scroll.offset = 0
                        load(more = false, filterOption = option)
                    }
                )
            }
        }

        // In-Channel Search input on SEARCH tab
        if (tab.kind == ChannelTabKind.SEARCH) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Vela.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Vela.sm)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search this channel") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("channel.searchInput")
                    )
                    Button(
                        onClick = { load(more = false) },
                        modifier = Modifier.testTag("channel.searchButton")
                    ) {
                        Text("Search")
                    }
                }
            }
        }

        // Loading Skeleton
        if (feed.state == ListState.Loading && feed.items.isEmpty() && feed.shelves.isEmpty()) {
            items(4) {
                SkeletonCard(vertical = true)
            }
        }

        // Error Row
        if (feed.state is ListState.Error && feed.items.isEmpty() && feed.shelves.isEmpty()) {
            item {
                val err = feed.state as ListState.Error
                ErrorRow(err.message, err.onRetry)
            }
        }

        // Empty State
        if (feed.state is ListState.Empty && feed.items.isEmpty() && feed.shelves.isEmpty()) {
            item {
                val emp = feed.state as ListState.Empty
                EmptyState(emp.message)
            }
        }

        // Shelves (e.g. Featured / Home shelves)
        feed.shelves.forEach { shelf ->
            val isShortsShelf = shelf.title.contains("Short", ignoreCase = true) ||
                (shelf.items.isNotEmpty() && shelf.items.all { it is ChannelItem.Video && it.summary.isShort })
            item {
                SectionHeader(shelf.title)
            }
            item {
                HScrollRow {
                    items(shelf.items) { item ->
                        if (isShortsShelf && item is ChannelItem.Video) {
                            Box(Modifier.width(150.dp).padding(end = Vela.sm)) {
                                ShortCard(
                                    video = item.summary,
                                    onClick = { navigator.push(Route.Video(item.summary)) }
                                )
                            }
                        } else if (item is ChannelItem.Post) {
                            Box(Modifier.width(280.dp).height(200.dp).padding(end = Vela.sm)) {
                                ChannelItemCard(services, navigator, item, onOpenPostDetail, inHorizontalShelf = true)
                            }
                        } else {
                            Box(Modifier.width(280.dp).padding(end = Vela.sm)) {
                                ChannelItemCard(services, navigator, item, onOpenPostDetail)
                            }
                        }
                    }
                }
            }
        }

        // Top-level Items
        if (tab.kind == ChannelTabKind.SHORTS) {
            val videoItems = feed.items.filterIsInstance<ChannelItem.Video>()
            val chunked = videoItems.chunked(2)
            items(chunked) { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Vela.sm)
                ) {
                    for (item in rowItems) {
                        Box(Modifier.weight(1f)) {
                            ShortCard(
                                video = item.summary,
                                onClick = { navigator.push(Route.Video(item.summary)) }
                            )
                        }
                    }
                    if (rowItems.size < 2) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        } else {
            items(feed.items) { item ->
                ChannelItemCard(services, navigator, item, onOpenPostDetail)
            }
        }

        // Load More button / Paging
        if (feed.nextToken != null) {
            item {
                Box(Modifier.fillMaxWidth().padding(Vela.sm), contentAlignment = Alignment.Center) {
                    if (feed.loading) {
                        CircularProgressIndicator(color = Vela.colors.primary.toColor(), modifier = Modifier.size(28.dp))
                    } else {
                        TextButton(
                            onClick = { load(more = true) },
                            modifier = Modifier.testTag("channel.loadMore")
                        ) {
                            Text("Load more")
                        }
                    }
                }
            }
        }
    }
}

/** Renders a single channel item according to its kind (Video, Playlist/Course, Post). */
@Composable
private fun ChannelItemCard(
    services: AppServices,
    navigator: Navigator,
    item: ChannelItem,
    onOpenPostDetail: (ChannelPost) -> Unit,
    inHorizontalShelf: Boolean = false,
) {
    when (item) {
        is ChannelItem.Video -> {
            VideoCard(
                video = item.summary,
                onClick = { navigator.push(Route.Video(item.summary)) },
                showChannelAvatar = false,
                style = VideoCardStyle.VERTICAL
            )
        }
        is ChannelItem.Playlist -> {
            CourseOrPlaylistCard(item.playlist) {
                navigator.push(Route.ChannelPlaylist(item.playlist.id, item.playlist.title))
            }
        }
        is ChannelItem.Post -> {
            PostCard(services, navigator, item.post, onOpenDetail = { onOpenPostDetail(item.post) }, inHorizontalShelf = inHorizontalShelf)
        }
    }
}

/** Legacy row export for SearchScreen and other callers. */
@Composable
internal fun PlaylistRow(p: PlaylistRef, onClick: () -> Unit) = CourseOrPlaylistCard(p, onClick)

/** Playlist card with support for Course badge and lesson count text. */
@Composable
private fun CourseOrPlaylistCard(p: PlaylistRef, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(Vela.mediumShape).clickable(onClick = onClick).padding(Vela.xs).testTag("channel.playlist"),
        horizontalArrangement = Arrangement.spacedBy(Vela.sm)
    ) {
        Box(Modifier.width(140.dp).aspectRatio(16f / 9f).clip(Vela.thumbnailShape).background(Vela.colors.surfaceVariant.toColor())) {
            p.thumbnailUrl?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Row(
                Modifier.align(Alignment.BottomEnd).padding(Vela.xs).clip(Vela.pillShape).background(Vela.colors.surface.toColor().copy(alpha = 0.85f)).padding(horizontal = Vela.xs, vertical = 2.dp).testTag("playlist.badge"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(Vela.icons.playlist, null, Modifier.size(16.dp), tint = Vela.colors.onSurface.toColor())
                val badgeText = if (p.isCourse) "COURSE" else p.videoCount?.toString()
                badgeText?.let { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = Vela.colors.onSurface.toColor()) }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
            Text(p.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (p.isCourse && p.lessonCountText != null) {
                Text(p.lessonCountText!!, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Vela.colors.primary.toColor())
            } else if (p.videoCount != null) {
                Text("${p.videoCount} videos", style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor())
            }
        }
    }
}

/** Vertical 9:16 card for YouTube Shorts in channel shelves or Shorts tab. */
@Composable
private fun ShortCard(
    video: VideoSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val hover = if (hovered) Modifier.border(2.dp, Vela.wake, Vela.mediumShape).background(Vela.colors.onSurface.toColor().copy(alpha = 0.05f), Vela.mediumShape) else Modifier

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ComponentIds.VIDEO_CARD)
            .then(hover)
            .clip(Vela.mediumShape)
            .clickable(onClick = onClick)
            .padding(Vela.xs)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(Vela.thumbnailShape)
                .background(Vela.colors.surfaceVariant.toColor())
                .testTag("card.thumbnail")
        ) {
            val shown = video.thumbnailUrl ?: if (video.id.isNotBlank()) "https://i.ytimg.com/vi/${video.id}/frame0.jpg" else null
            if (shown != null) {
                AsyncImage(shown, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            }
            Text(
                "SHORT",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Vela.xs)
                    .clip(Vela.smallShape)
                    .background(Vela.colors.primary.toColor())
                    .padding(horizontal = Vela.xs),
                style = MaterialTheme.typography.bodySmall,
                color = Vela.colors.onMedia.toColor(),
                maxLines = 1
            )
            val statText = video.viewCount?.let { formatViews(it) } ?: video.publishedText
            if (statText != null) {
                Text(
                    statText,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(Vela.xs)
                        .clip(Vela.smallShape)
                        .background(Vela.colors.background.toColor().copy(alpha = 0.85f))
                        .padding(horizontal = Vela.xs),
                    style = MaterialTheme.typography.bodySmall,
                    color = Vela.colors.onBackground.toColor(),
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(Vela.xs))
        Text(
            video.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp).testTag("card.title")
        )
    }
}

/** Community post card supporting text, attachments, polls, video embeds, likes. */
@Composable
private fun PostCard(
    services: AppServices,
    navigator: Navigator,
    post: ChannelPost,
    onOpenDetail: () -> Unit,
    inHorizontalShelf: Boolean = false,
) {
    val linkColor = Vela.colors.primary.toColor()
    val baseModifier = if (inHorizontalShelf) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
    Column(
        baseModifier.clip(Vela.mediumShape).background(Vela.colors.surface.toColor()).padding(Vela.md).testTag("channel.post"),
        verticalArrangement = Arrangement.spacedBy(Vela.sm)
    ) {
        // Author header
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
            Avatar(post.authorName, size = 36.dp, url = post.authorAvatarUrl)
            Column(Modifier.weight(1f)) {
                Text(post.authorName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                post.publishedText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor()) }
            }
        }

        // Post text
        if (post.text.isNotBlank()) {
            val annotated = remember(post.text, linkColor) { linkedText(post.text, linkColor) {} }
            Text(
                annotated,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (inHorizontalShelf) 3 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Post Attachment
        when (val att = post.attachment) {
            is PostAttachment.Image -> {
                val imgMod = if (inHorizontalShelf) Modifier.fillMaxWidth().height(70.dp).clip(Vela.mediumShape) else Modifier.fillMaxWidth().clip(Vela.mediumShape)
                AsyncImage(att.url, null, imgMod, contentScale = if (inHorizontalShelf) ContentScale.Crop else ContentScale.FillWidth)
            }
            is PostAttachment.MultiImage -> {
                if (inHorizontalShelf) {
                    att.images.firstOrNull()?.let { img ->
                        AsyncImage(img.url, null, Modifier.fillMaxWidth().height(70.dp).clip(Vela.mediumShape), contentScale = ContentScale.Crop)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
                        att.images.forEach { img ->
                            AsyncImage(img.url, null, Modifier.fillMaxWidth().clip(Vela.mediumShape), contentScale = ContentScale.FillWidth)
                        }
                    }
                }
            }
            is PostAttachment.Poll -> {
                val choices = if (inHorizontalShelf) att.choices.take(2) else att.choices
                Column(Modifier.fillMaxWidth().padding(vertical = Vela.xs), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
                    choices.forEach { choice ->
                        val ratio = choice.voteRatio?.toFloat() ?: 0f
                        Box(
                            Modifier.fillMaxWidth().height(if (inHorizontalShelf) 26.dp else 40.dp).clip(Vela.smallShape).background(Vela.colors.surfaceVariant.toColor()).padding(horizontal = Vela.sm),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (ratio > 0f) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(ratio).clip(Vela.smallShape).background(Vela.colors.primary.toColor().copy(alpha = 0.25f)))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(choice.text, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                choice.votePercentage?.let { Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                    if (!inHorizontalShelf) {
                        att.totalVotesText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor()) }
                    }
                }
            }
            is PostAttachment.Video -> {
                if (inHorizontalShelf) {
                    Box(Modifier.fillMaxWidth().height(60.dp).clip(Vela.smallShape).background(Vela.colors.surfaceVariant.toColor()).clickable { navigator.push(Route.Video(att.video)) }.padding(Vela.xs)) {
                        Text(att.video.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    VideoCard(att.video, { navigator.push(Route.Video(att.video)) }, showChannelAvatar = false, style = VideoCardStyle.VERTICAL)
                }
            }
            is PostAttachment.Playlist -> {
                CourseOrPlaylistCard(att.playlist) { navigator.push(Route.ChannelPlaylist(att.playlist.id, att.playlist.title)) }
            }
            null -> {}
        }

        // Actions: Likes & Comments
        Row(
            Modifier.fillMaxWidth().padding(top = Vela.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Vela.md)
        ) {
            post.voteCountText?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.xs)) {
                    Icon(Vela.icons.like, null, Modifier.size(16.dp), tint = Vela.colors.onSurfaceVariant.toColor())
                    Text(it, style = MaterialTheme.typography.labelMedium, color = Vela.colors.onSurfaceVariant.toColor())
                }
            }
            if (post.detailBrowseParams != null) {
                TextButton(onClick = onOpenDetail, modifier = Modifier.testTag("channel.postComments")) {
                    Text("Comments")
                }
            }
        }
    }
}

/** Sort & filter bar supporting dropdowns and chips. */
@Composable
private fun SortAndFilterBar(
    groups: List<ChannelFilterGroup>,
    selectedOption: ChannelFilterOption?,
    onSelect: (ChannelFilterOption) -> Unit,
) {
    HScrollRow(Modifier.testTag("channel.sort")) {
        groups.forEach { group ->
            if (group.isDropdown) {
                item {
                    var expanded by remember { mutableStateOf(false) }
                    val activeLabel = group.options.firstOrNull { it.selected || it == selectedOption }?.label ?: group.title ?: "Sort by"
                    Box {
                        FilterChip(
                            selected = true,
                            onClick = { expanded = true },
                            label = { Text(activeLabel) },
                            modifier = Modifier.testTag("channel.sortDropdown")
                        )
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            group.options.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt.label) },
                                    onClick = {
                                        expanded = false
                                        onSelect(opt)
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                items(group.options) { opt ->
                    val isSelected = opt == selectedOption || (selectedOption == null && opt.selected)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelect(opt) },
                        label = { Text(opt.label) },
                        modifier = Modifier.testTag("channel.sort.${opt.label.lowercase().replace(' ', '_')}")
                    )
                }
            }
        }
    }
}

/** Full About sheet content with links, country, joined date, totals. */
@Composable
private fun ChannelAboutContent(
    services: AppServices,
    channelId: String,
    aboutToken: String?,
    initialAbout: ChannelAbout?,
    onAboutLoaded: (ChannelAbout) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    var about by remember { mutableStateOf(initialAbout) }
    var loading by remember { mutableStateOf(about == null) }
    val linkColor = Vela.colors.primary.toColor()

    LaunchedEffect(channelId) {
        if (about == null) {
            when (val res = services.extractor.channelAbout(channelId, aboutToken)) {
                is ExtractorResult.Success -> {
                    about = res.value
                    onAboutLoaded(res.value)
                }
                is ExtractorResult.Failure -> {}
            }
            loading = false
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(Vela.md),
        verticalArrangement = Arrangement.spacedBy(Vela.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
            Text("About this channel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (about?.isPartial == true) {
                Text(
                    "Partial info (fallback mode)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Vela.colors.onSurfaceVariant.toColor(),
                    modifier = Modifier
                        .clip(Vela.pillShape)
                        .background(Vela.colors.surfaceVariant.toColor())
                        .padding(horizontal = Vela.xs, vertical = 2.dp)
                        .testTag("channel.about.partial")
                )
            }
        }
        if (loading) {
            CircularProgressIndicator(color = Vela.colors.primary.toColor(), modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            val a = about ?: ChannelAbout()
            if (a.description.isNotBlank()) {
                val annotated = remember(a.description, linkColor) { linkedText(a.description, linkColor) {} }
                Text(annotated, style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider(color = Vela.colors.outline.toColor())
            Column(verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
                a.subscriberCountText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor()) }
                a.videoCountText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor()) }
                a.viewCountText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor()) }
                a.joinedDateText?.let { Text("Joined $it", style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor()) }
                a.country?.let { Text("Country: $it", style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor()) }
            }
            if (a.links.isNotEmpty()) {
                HorizontalDivider(color = Vela.colors.outline.toColor())
                Text("Links", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
                    a.links.forEach { link ->
                        Row(
                            Modifier.fillMaxWidth().clip(Vela.mediumShape).clickable { onOpenLink(link.url) }.padding(vertical = Vela.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Vela.sm)
                        ) {
                            Icon(Vela.icons.link, null, Modifier.size(16.dp), tint = Vela.colors.primary.toColor())
                            Column(Modifier.weight(1f)) {
                                Text(link.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text(link.displayUrl, style = MaterialTheme.typography.bodySmall, color = Vela.colors.primary.toColor(), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Post detail bottom sheet with post comments thread. */
@Composable
private fun PostDetailContent(
    services: AppServices,
    navigator: Navigator,
    post: ChannelPost,
    onDismiss: () -> Unit,
) {
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(post.detailBrowseParams) {
        val params = post.detailBrowseParams
        if (params != null) {
            when (val r = services.extractor.postDetail(params)) {
                is ExtractorResult.Success -> {
                    comments = r.value.second
                }
                is ExtractorResult.Failure -> {}
            }
        }
        loading = false
    }

    LazyColumn(
        Modifier.fillMaxWidth().padding(Vela.md),
        verticalArrangement = Arrangement.spacedBy(Vela.md)
    ) {
        item {
            PostCard(services, navigator, post, onOpenDetail = {})
        }
        item {
            HorizontalDivider(color = Vela.colors.outline.toColor())
            Text("Comments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(Vela.md), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Vela.colors.primary.toColor())
                }
            }
        } else if (comments.isEmpty()) {
            item {
                Text("No comments on this post.", style = MaterialTheme.typography.bodyMedium, color = Vela.colors.onSurfaceVariant.toColor())
            }
        } else {
            items(comments) { comment ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
                    Avatar(comment.author, size = 32.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.xs)) {
                            Text(comment.author, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            if (comment.publishedText.isNotBlank()) {
                                Text(comment.publishedText, style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor())
                            }
                        }
                        Text(comment.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun tabEmptyMessage(kind: ChannelTabKind): String = when (kind) {
    ChannelTabKind.VIDEOS -> "This channel has no videos."
    ChannelTabKind.SHORTS -> "No Shorts on this channel."
    ChannelTabKind.STREAMS -> "No live streams on this channel."
    ChannelTabKind.PLAYLISTS -> "No playlists on this channel."
    ChannelTabKind.COURSES -> "No courses on this channel."
    ChannelTabKind.POSTS -> "No community posts on this channel."
    ChannelTabKind.SEARCH -> "No results found on this channel."
    else -> "No content found on this tab."
}
