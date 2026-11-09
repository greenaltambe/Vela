@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.greenaltambe.vela.ui.screens

import com.greenaltambe.vela.ui.theme.AppIcons
import androidx.compose.foundation.shape.CircleShape
import com.greenaltambe.vela.ui.theme.ExtraColors
import com.greenaltambe.vela.ui.theme.Spacing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.core.settings.shortsEnabled
import com.greenaltambe.vela.extractor.*
import com.greenaltambe.vela.ui.components.*
import com.greenaltambe.vela.ui.look.ComponentIds
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Preserved for backwards compatibility
enum class ChannelTab(val label: String) { HOME("Home"), VIDEOS("Videos"), SHORTS("Shorts"), LIVE("Live"), PLAYLISTS("Playlists"), ABOUT("About") }
enum class ChannelSort(val label: String) { LATEST("Latest"), POPULAR("Popular"), OLDEST("Oldest") }
enum class PlaylistSort(val label: String) { RECENT("Recently added"), AZ("A to Z") }

/**
 * A channel page: one scrolling list holding the header (banner, picture, subscribe), the pinned tab row and the open tab's content, under a small top
 * bar that shows the channel's name once the header has scrolled away. One list means the header can never overlap what is under it. Tabs are the ones
 * YouTube returns; swiping sideways moves between them.
 */
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
        Column(modifier.fillMaxSize()) {
            ChannelTopBar(channelName, showTitle = true, onBack = { navigator.back() }, menu = emptyList())
            Column(Modifier.padding(Spacing.md)) { ErrorRow(channelError!!, onRetry = { loadLanding() }) }
        }
        return
    }

    // Build tabs list (show only what server returned, preserving order; fallback only when raw is empty)
    val baseTabs = remember(landing, shortsOn) {
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
        // Store is a shop window (the About sheet lists the channel's shop link); no source can show it, so it is not offered.
        list.filter { (shortsOn || it.kind != ChannelTabKind.SHORTS) && it.kind != ChannelTabKind.STORE }
    }
    // A channel whose tab list has no Videos tab and whose first tab came back empty (an auto-generated "Topic" channel) still has videos: offer a
    // Videos tab, loaded through the same fallback chain, and open it once. Nothing changes for a channel that has a Videos tab or a filled first tab.
    val firstFeed = baseTabs.firstOrNull()?.let { st.feedFor(it.kind.name) }
    val firstTabEmpty = landing != null && firstFeed != null && firstFeed.items.isEmpty() && firstFeed.shelves.isEmpty() &&
        (firstFeed.state is ListState.Empty || firstFeed.state is ListState.Error)
    val addVideosTab = firstTabEmpty && baseTabs.none { it.kind == ChannelTabKind.VIDEOS }
    val tabs = remember(baseTabs, addVideosTab) {
        if (addVideosTab) baseTabs + listOf(
            com.greenaltambe.vela.extractor.ChannelTab(ChannelTabKind.VIDEOS, "Videos"),
            com.greenaltambe.vela.extractor.ChannelTab(ChannelTabKind.PLAYLISTS, "Playlists"),
        ).filter { f -> baseTabs.none { it.kind == f.kind } } else baseTabs
    }
    var fallbackOpened by remember(channelId) { mutableStateOf(false) }
    LaunchedEffect(addVideosTab) {
        if (addVideosTab && !fallbackOpened) { fallbackOpened = true; st.selectedTabIndex = tabs.indexOfFirst { it.kind == ChannelTabKind.VIDEOS }.coerceAtLeast(0) }
    }

    val tabIndex = st.selectedTabIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    val tab = tabs.getOrNull(tabIndex)
    val feed = tab?.let { st.feedFor(it.kind.name) }

    val pageHeader = landing?.header
    val name = pageHeader?.name?.ifBlank { null } ?: channelName
    val channelUrl = "https://www.youtube.com/channel/$channelId"

    val gutter = Spacing.gutter
    val listState = rememberLazyListState(st.listIndex, st.listOffset)
    DisposableEffect(Unit) {
        onDispose {
            st.listIndex = listState.firstVisibleItemIndex
            st.listOffset = listState.firstVisibleItemScrollOffset
        }
    }
    // Choosing another tab: the new tab starts at its top, just under the pinned tab row, not wherever the last one was scrolled to.
    var lastTab by remember { mutableIntStateOf(tabIndex) }
    // Motion between tabs: the new tab's content slides in from the side it lies on and fades up (`enter`, 1 to 0 on a spring), and while a finger swipes
    // the content follows it (`drag`, in pixels). Only the content moves; the header and the pinned tab row stay put.
    val enter = remember { androidx.compose.animation.core.Animatable(0f) }
    val drag = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(tabIndex) {
        if (lastTab != tabIndex) {
            val direction = if (tabIndex > lastTab) 1f else -1f
            lastTab = tabIndex
            launch {
                enter.snapTo(direction)
                enter.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow))
            }
            if (listState.firstVisibleItemIndex > 1) listState.scrollToItem(1)
        }
    }
    val tabMotion = Modifier.graphicsLayer {
        translationX = enter.value * size.width * 0.18f + drag.value
        alpha = (1f - kotlin.math.abs(enter.value) * 0.75f - kotlin.math.abs(drag.value) / (size.width.coerceAtLeast(1f) * 1.5f)).coerceIn(0.2f, 1f)
    }
    val titleVisible by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    LaunchedEffect(tab?.kind, channelId, landing != null) {
        if (tab != null && feed != null && landing != null && !feed.loaded) loadTab(scope, services, channelId, tab, feed, st.searchQuery, more = false)
    }

    val share = com.greenaltambe.vela.ui.platform.LocalShare.current
    val toasts = com.greenaltambe.vela.ui.look.LocalUndoToasts.current
    var blocked by remember(channelId) { mutableStateOf(services.blocklist.isBlocked(channelId)) }
    var groupPicker by remember { mutableStateOf(false) }
    val menu = remember(channelUrl, name, share, blocked, subscribed) {
        buildList {
            add(PageAction("About", "channel.menu.about") { aboutSheetOpen = true })
            if (share != null) add(PageAction("Share", "channel.menu.share") { share(name, channelUrl) })
            add(PageAction("Open in browser", "channel.menu.browser") { uriHandler.openUri(channelUrl) })
            if (subscribed && channelId.isNotBlank()) add(PageAction("Add to group", "channel.menu.group") { groupPicker = true })
            // Blocking hides this channel's videos everywhere (Home, Search, Subscriptions, Related); the page itself stays open so it can be unblocked.
            if (channelId.isNotBlank()) add(
                if (blocked) PageAction("Unblock channel", "channel.menu.unblock") { services.blocklist.unblockChannel(channelId); blocked = false; toasts.show("$name is unblocked") }
                else PageAction("Block channel", "channel.menu.block") { services.blocklist.blockChannel(channelId, name); blocked = true; toasts.show("$name is blocked. Its videos are hidden.") { services.blocklist.unblockChannel(channelId); blocked = false } },
            )
        }
    }

    Column(modifier.fillMaxSize()) {
        ChannelTopBar(name, showTitle = titleVisible, onBack = { navigator.back() }, menu = menu)
        Box(Modifier.weight(1f).fillMaxWidth().testTag("channel.pager"), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = listState,
                modifier = Modifier.widthIn(max = 900.dp).fillMaxSize().testTag("channel.list")
                    .pointerInput(tabs.size, tabIndex) {
                        // A swipe sideways moves to the next / previous tab. Rows that scroll sideways (shelves) consume their own drags first.
                        var dx = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dx = 0f },
                            onDragEnd = {
                                val next = when {
                                    dx < -140f && tabIndex < tabs.size - 1 -> tabIndex + 1
                                    dx > 140f && tabIndex > 0 -> tabIndex - 1
                                    else -> tabIndex
                                }
                                if (next != tabIndex) { st.selectedTabIndex = next; scope.launch { drag.snapTo(0f) } }
                                else scope.launch { drag.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 500f)) }
                            },
                            onDragCancel = { scope.launch { drag.animateTo(0f) } },
                            onHorizontalDrag = { _, d ->
                                dx += d
                                // At the first or last tab the content only gives a little (there is nowhere to go).
                                val atEdge = (dx > 0 && tabIndex == 0) || (dx < 0 && tabIndex == tabs.size - 1)
                                scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { drag.snapTo(if (atEdge) dx * 0.2f else dx * 0.6f) }
                            },
                        )
                    },
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                item(key = "header", contentType = "header") {
                    ChannelHeader(
                        services = services, channelId = channelId, name = name, page = pageHeader,
                        subscribed = subscribed, onSubscribedChange = { subscribed = it }, onOpenAbout = { aboutSheetOpen = true },
                    )
                }
                if (tabs.isNotEmpty()) {
                    stickyHeader(key = "tabs", contentType = "tabs") {
                        PrimaryScrollableTabRow(
                            selectedTabIndex = tabIndex,
                            edgePadding = Spacing.gutter,
                            containerColor = MaterialTheme.colorScheme.background,
                            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) },
                            modifier = Modifier.fillMaxWidth().testTag("channel.tabs"),
                        ) {
                            tabs.forEachIndexed { index, t ->
                                Tab(
                                    selected = index == tabIndex,
                                    onClick = { st.selectedTabIndex = index },
                                    selectedContentColor = MaterialTheme.colorScheme.primary,
                                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    text = { Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.testTag("channel.tab.${t.kind.name.lowercase()}"),
                                )
                            }
                        }
                    }
                    if (landing == null) {
                        // Until the landing arrives the tab list is only a placeholder (loading those tabs could hit ones this channel does not have).
                        items(3) { SkeletonCard(vertical = true, fullBleed = com.greenaltambe.vela.ui.layout.LocalWindowSize.current == com.greenaltambe.vela.ui.layout.WindowSizeClass.COMPACT, modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm)) }
                    } else if (tab != null && feed != null) {
                        channelTabItems(
                            services = services, navigator = navigator, tab = tab, feed = feed,
                            searchQuery = st.searchQuery, onSearchQueryChange = { st.searchQuery = it },
                            onLoad = { more, filter -> loadTab(scope, services, channelId, tab, feed, st.searchQuery, more, filter) },
                            onOpenPostDetail = { activePostDetail = it }, gutter = gutter,
                            channelUrl = channelUrl, ownerId = channelId, onOpenUrl = { uriHandler.openUri(it) },
                            motion = tabMotion,
                        )
                    }
                }
            }
        }
    }

    if (groupPicker) GroupPickerSheet(services, channelId, name) { groupPicker = false }

    // Full About Bottom Sheet
    if (aboutSheetOpen) {
        com.greenaltambe.vela.ui.components.VelaSheet({ aboutSheetOpen = false }, Modifier.testTag("channel.aboutSheet")) {
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
        com.greenaltambe.vela.ui.components.VelaSheet({ activePostDetail = null }, Modifier.testTag("channel.postDetailSheet")) {
            PostDetailContent(services, navigator, post, onDismiss = { activePostDetail = null })
        }
    }
}

/** The small bar over the page: back, the name (only once the header with the big name has scrolled away), and the page menu. */
@Composable
private fun ChannelTopBar(name: String, showTitle: Boolean, onBack: () -> Unit, menu: List<PageAction>) {
    TopAppBar(
        title = { AnimatedVisibility(showTitle, enter = fadeIn(), exit = fadeOut()) { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        navigationIcon = { IconButton(onClick = onBack, modifier = Modifier.testTag("nav.back")) { Icon(AppIcons.back, "Back") } },
        actions = {
            if (menu.isNotEmpty()) {
                var open by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { open = true }, modifier = Modifier.testTag("channel.menu")) { Icon(AppIcons.more, "More options") }
                    DropdownMenu(open, { open = false }) {
                        menu.forEach { a -> DropdownMenuItem(text = { Text(a.label, maxLines = 1) }, onClick = { open = false; a.onClick() }, modifier = Modifier.testTag(a.tag)) }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, scrolledContainerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.testTag("topbar"),
    )
}

/** Banner, picture, name, subscribe / bell, auto-download and the description teaser: the first item of the list. */
@Composable
private fun ChannelHeader(
    services: AppServices,
    channelId: String,
    name: String,
    page: ChannelPage?,
    subscribed: Boolean,
    onSubscribedChange: (Boolean) -> Unit,
    onOpenAbout: () -> Unit,
) {
    val avatarShape = remember { MaterialShapes.Cookie9Sided }.toShape()
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        page?.bannerUrl?.let { banner ->
            AsyncImage(banner, null, Modifier.fillMaxWidth().aspectRatio(16f / 5f).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh).testTag("channel.banner"), contentScale = ContentScale.Crop)
        }
        // The picture, then the name, the count and the Subscribe button (and the bell once subscribed) in one block: the header is one row shorter,
        // so the tabs and the first videos start higher on a phone.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Avatar(name, size = 72.dp, url = page?.avatarUrl, shape = avatarShape)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("channel.name"))
                page?.handle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("channel.handle")) }
                val stats = listOfNotNull(
                    formatCount(page?.subscriberCount)?.let { "$it subscribers" },
                    page?.videoCount?.let { n -> formatCount(n)?.let { "$it ${if (n == 1L) "video" else "videos"}" } },
                ).joinToString(" \u2022 ")
                if (stats.isNotEmpty()) Text(stats, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("channel.stats"))
                Row(Modifier.animateContentSize(), horizontalArrangement = Arrangement.spacedBy(androidx.compose.material3.ButtonGroupDefaults.ConnectedSpaceBetween), verticalAlignment = Alignment.CenterVertically) {
                    SubscribeButton(
                        subscribed, modifier = Modifier.testTag("channel.subscribe"),
                        position = if (subscribed) ActionPosition.LEADING else ActionPosition.ALONE,
                        onClick = {
                            if (subscribed) { services.subscriptions.unsubscribe(channelId); services.feed.reload(); onSubscribedChange(false) }
                            else { services.subscriptions.subscribe(channelId, name, page?.avatarUrl, services.clock.now()); onSubscribedChange(true) }
                        },
                    )
                    AnimatedVisibility(subscribed, enter = androidx.compose.animation.expandHorizontally(androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 380f)) + fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.6f), exit = androidx.compose.animation.shrinkHorizontally() + fadeOut()) { ChannelBell(services, channelId) }
                }
            }
        }

        // Appears with the Subscribe button's own change: it grows open and fades in on a spring (and closes the same way) instead of popping in.
        AnimatedVisibility(
            subscribed && services.downloads != null,
            enter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 380f)) + fadeIn(),
            exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.spring(stiffness = 500f)) + fadeOut(),
        ) {
            var auto by remember(channelId) { mutableStateOf(services.autoDownloadRules.ruleFor(channelId)?.enabled == true) }
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = Spacing.md, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text("Download new videos automatically", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                Switch(auto, {
                    auto = it
                    if (it) services.autoDownloadRules.upsert(com.greenaltambe.vela.downloads.AutoDownloadRule(channelId, name, sinceMs = services.clock.now().toEpochMilliseconds()))
                    else services.autoDownloadRules.ruleFor(channelId)?.let { r -> services.autoDownloadRules.upsert(r.copy(enabled = false)) }
                }, modifier = Modifier.testTag("channel.autoDownload"))
            }
        }

        // Short description snippet that opens the About sheet
        page?.description?.takeIf { it.isNotBlank() }?.let { desc ->
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onOpenAbout).padding(horizontal = Spacing.md, vertical = Spacing.sm).testTag("channel.aboutTrigger"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(desc, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("  More", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Loads a tab's first page or the next one into [feed] (an expired continuation restarts at page one and shows the "Refreshed" notice). */
private fun loadTab(
    scope: CoroutineScope,
    services: AppServices,
    channelId: String,
    tab: com.greenaltambe.vela.extractor.ChannelTab,
    feed: ChannelTabFeed,
    searchQuery: String,
    more: Boolean,
    filterOption: ChannelFilterOption? = feed.selectedFilter,
) {
    if (feed.loading) return
    feed.loading = true
    if (!more) feed.state = ListState.Loading
    if (!more && tab.kind == ChannelTabKind.SEARCH) feed.lastQuery = searchQuery
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
                var page = res.value
                // The tab call answered with nothing: ask the older per-kind call too (the fallback tiers serve it), because one source seeing an empty
                // page is not proof the channel has nothing (an auto-generated Topic channel shows no tabs natively but has videos and playlists).
                if (!more && page.items.isEmpty() && page.shelves.isEmpty()) legacyTabPage(services, channelId, tab.kind)?.takeIf { it.items.isNotEmpty() }?.let { page = it }
                feed.items = if (more) (feed.items + page.items) else page.items
                feed.shelves = if (more) (feed.shelves + page.shelves) else page.shelves
                if (page.filters.isNotEmpty()) feed.filters = page.filters
                feed.nextToken = page.nextToken
                feed.loaded = true
                feed.unsupported = false
                feed.state = if (feed.items.isEmpty() && feed.shelves.isEmpty()) ListState.Empty(tabEmptyMessage(tab.kind)) else ListState.Ready
            }
            is ExtractorResult.Failure -> {
                if (more && (res.error is ExtractorError.TokenExpired || res.error is ExtractorError.TokenNotMine)) {
                    feed.refreshedNotice = true
                    feed.loading = false
                    loadTab(scope, services, channelId, tab, feed, searchQuery, more = false, filterOption = filterOption)
                    return@launch
                }
                if (feed.items.isEmpty() && feed.shelves.isEmpty() && isTabUnsupported(tab.kind, res.error)) {
                    feed.unsupported = true
                    feed.state = ListState.Empty("Vela can't show this tab yet.")
                } else if (feed.items.isEmpty() && feed.shelves.isEmpty()) {
                    feed.state = ListState.Error("Could not load: ${res.error.message}") { loadTab(scope, services, channelId, tab, feed, searchQuery, false, filterOption) }
                }
            }
        }
        feed.loading = false
    }
}

/** The older per-kind channel calls as a second source for [kind]; null for kinds that have none. A failure counts as "nothing". */
private suspend fun legacyTabPage(services: AppServices, channelId: String, kind: ChannelTabKind): ChannelTabPage? = when (kind) {
    ChannelTabKind.VIDEOS -> (services.extractor.channelVideos(channelId) as? ExtractorResult.Success)?.value?.let { p -> ChannelTabPage(items = p.items.map { ChannelItem.Video(it) }, nextToken = p.continuation) }
    ChannelTabKind.SHORTS -> (services.extractor.channelShorts(channelId) as? ExtractorResult.Success)?.value?.let { p -> ChannelTabPage(items = p.items.map { ChannelItem.Video(it) }, nextToken = p.continuation) }
    ChannelTabKind.STREAMS -> (services.extractor.channelLive(channelId) as? ExtractorResult.Success)?.value?.let { p -> ChannelTabPage(items = p.items.map { ChannelItem.Video(it) }, nextToken = p.continuation) }
    ChannelTabKind.PLAYLISTS -> (services.extractor.channelPlaylists(channelId) as? ExtractorResult.Success)?.value?.let { p -> ChannelTabPage(items = p.items.map { ChannelItem.Playlist(it) }, nextToken = p.continuation) }
    else -> null
}

/**
 * True when the answer means "no source can serve this tab" (every tier said so), as opposed to a network or server problem, where a Retry makes sense.
 * An extractor that cannot do something answers `not supported`; the NewPipe fallback says a tab is "not available on fallback".
 */
internal fun isTabUnsupported(kind: ChannelTabKind, e: ExtractorError): Boolean =
    (e is ExtractorError.Unplayable && e.message == NOT_SUPPORTED) ||
        (e is ExtractorError.TierUnavailable && e.message.contains("not available on fallback")) ||
        (e is ExtractorError.ParseFailure && kind in setOf(ChannelTabKind.PODCASTS, ChannelTabKind.RELEASES, ChannelTabKind.SHOWS, ChannelTabKind.UNKNOWN))

/** The open tab's rows as list items: notices, sort chips, search box, states, shelves, items and the paging footer. */
private fun LazyListScope.channelTabItems(
    services: AppServices,
    navigator: Navigator,
    tab: com.greenaltambe.vela.extractor.ChannelTab,
    feed: ChannelTabFeed,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onLoad: (more: Boolean, filter: ChannelFilterOption?) -> Unit,
    onOpenPostDetail: (ChannelPost) -> Unit,
    gutter: androidx.compose.ui.unit.Dp,
    channelUrl: String,
    ownerId: String,
    onOpenUrl: (String) -> Unit,
    /** The slide-and-fade of a tab change; every row of the tab carries it. */
    motion: Modifier,
) {
    val side = motion.padding(horizontal = gutter)
    // Refreshed Notice banner when continuation expired and feed reloaded from page 1
    if (feed.refreshedNotice) {
        item(key = "refreshed") {
            Row(
                modifier = side.padding(top = Spacing.sm).fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(start = Spacing.md).testTag("channel.refreshedNotice"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Refreshed", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                IconButton(onClick = { feed.refreshedNotice = false }) { Icon(AppIcons.close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
            }
        }
    }
    // Sort & filter controls
    if (feed.filters.isNotEmpty()) {
        item(key = "filters") {
            Box(motion.padding(top = Spacing.sm)) {
                SortAndFilterBar(
                    groups = feed.filters, selectedOption = feed.selectedFilter,
                    onSelect = { option ->
                        feed.selectedFilter = option
                        onLoad(false, option)
                    },
                )
            }
        }
    }
    // In-channel search input on the SEARCH tab
    if (tab.kind == ChannelTabKind.SEARCH) {
        item(key = "search") {
            // A tonal pill with a search icon, a clear button and a search key: the same look as the app's other search fields.
            TextField(
                value = searchQuery, onValueChange = onSearchQueryChange,
                placeholder = { Text("Search this channel") }, singleLine = true, shape = CircleShape,
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (searchQuery.isNotEmpty()) IconButton(onClick = { onSearchQueryChange("") }) { Icon(AppIcons.close, "Clear") }
                        FilledIconButton(onClick = { onLoad(false, null) }, modifier = Modifier.padding(end = Spacing.xs).testTag("channel.searchButton")) { Icon(AppIcons.search, "Search") }
                    }
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onLoad(false, null) }),
                colors = TextFieldDefaults.colors(focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = side.fillMaxWidth().padding(vertical = Spacing.sm).testTag("channel.searchInput"),
            )
        }
    }
    // Loading skeleton / error / empty
    if (feed.state == ListState.Loading && feed.items.isEmpty() && feed.shelves.isEmpty()) {
        items(4) { SkeletonCard(vertical = true, fullBleed = false, modifier = side.padding(vertical = Spacing.sm)) }
    }
    if (feed.state is ListState.Error && feed.items.isEmpty() && feed.shelves.isEmpty()) {
        item(key = "error") { val err = feed.state as ListState.Error; Box(side.padding(top = Spacing.md)) { ErrorRow(err.message, err.onRetry) } }
    }
    if (feed.state is ListState.Empty && feed.items.isEmpty() && feed.shelves.isEmpty()) {
        item(key = "empty") {
            val emp = feed.state as ListState.Empty
            val beforeSearch = tab.kind == ChannelTabKind.SEARCH && feed.lastQuery.isNullOrBlank()
            val message = when {
                beforeSearch -> "Search this channel's videos, playlists and posts."
                tab.kind == ChannelTabKind.SEARCH -> "No results for \u201C${feed.lastQuery}\u201D."
                else -> emp.message
            }
            // An empty Home or Videos page (or a tab no source can show) may be a miss on our side: the browser is the way out.
            val toBrowser = tab.kind != ChannelTabKind.SEARCH && (feed.unsupported || tab.kind == ChannelTabKind.FEATURED || tab.kind == ChannelTabKind.VIDEOS)
            val tabUrl = tab.endpointUrl?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("http")) it else "https://www.youtube.com$it" } ?: channelUrl
            EmptyState(message, modifier = motion, actionLabel = if (toBrowser) "Open in browser" else null, onAction = if (toBrowser) ({ onOpenUrl(tabUrl) }) else null)
        }
    }
    // A short or empty tab keeps the pinned tab row where it was (otherwise the page jumps back up to the header).
    if (feed.items.isEmpty() && feed.shelves.isEmpty()) item(key = "filler") { Spacer(Modifier.fillParentMaxHeight(0.6f)) }

    // Shelves (Featured / Home)
    feed.shelves.forEachIndexed { si, shelf ->
        val isShortsShelf = shelf.title.contains("Short", ignoreCase = true) ||
            (shelf.items.isNotEmpty() && shelf.items.all { it is ChannelItem.Video && it.summary.isShort })
        item(key = "shelf-$si-title") { Box(side.padding(top = Spacing.md)) { SectionHeader(shelf.title) } }
        item(key = "shelf-$si-row") {
            HScrollRow(motion, contentPadding = PaddingValues(horizontal = gutter)) {
                items(shelf.items) { item ->
                    if (isShortsShelf && item is ChannelItem.Video) {
                        Box(Modifier.width(150.dp).padding(end = Spacing.sm)) { ShortCard(video = item.summary, onClick = { openShort(navigator, shelf.items.filterIsInstance<ChannelItem.Video>().map { it.summary }, item.summary.id, ownerId) }) }
                    } else {
                        Box(Modifier.width(280.dp).padding(end = Spacing.sm)) { ChannelItemCard(services, navigator, item, onOpenPostDetail, ownerId, inHorizontalShelf = item is ChannelItem.Post, inShelf = true) }
                    }
                }
            }
        }
    }

    // Top-level items
    if (tab.kind == ChannelTabKind.SHORTS) {
        val chunked = feed.items.filterIsInstance<ChannelItem.Video>().chunked(2)
        items(chunked.size, key = { "shorts-$it" }) { i ->
            Row(side.fillMaxWidth().padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                for (item in chunked[i]) Box(Modifier.weight(1f)) { ShortCard(video = item.summary, onClick = { openShort(navigator, feed.items.filterIsInstance<ChannelItem.Video>().map { it.summary }, item.summary.id, ownerId) }) }
                if (chunked[i].size < 2) Spacer(Modifier.weight(1f))
            }
        }
    } else {
        items(feed.items.size) { i ->
            val item = feed.items[i]
            Box(side.padding(top = Spacing.sm)) {
                ChannelItemCard(services, navigator, item, onOpenPostDetail, ownerId)
            }
        }
    }

    // Paging footer: loads by itself when it scrolls into view (once, unless the page was just refreshed); the button is the manual way
    if (feed.nextToken != null) {
        item(key = "more") {
            if (!feed.refreshedNotice) LaunchedEffect(feed.nextToken, feed.items.size) { if (!feed.loading && feed.state == ListState.Ready) onLoad(true, feed.selectedFilter) }
            Box(motion.fillMaxWidth().padding(Spacing.sm), contentAlignment = Alignment.Center) {
                if (feed.loading) LoadingSpinner(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                else TextButton(onClick = { onLoad(true, feed.selectedFilter) }, modifier = Modifier.testTag("channel.loadMore")) { Text("Load more") }
            }
        }
    }
}

/**
 * Opens a Short from a channel's list. In the Shorts player it plays the Shorts that follow it in that list, in order (then the rest of the channel's Shorts,
 * then others); a Short is never swapped for an unrelated one from elsewhere. With the Shorts player off, it opens as an ordinary video.
 */
private fun openShort(navigator: Navigator, list: List<VideoSummary>, id: String, channelId: String) {
    val i = list.indexOfFirst { it.id == id }.coerceAtLeast(0)
    val own = list.map { if (it.channelId.isBlank() && channelId.isNotBlank()) it.copy(channelId = channelId, isShort = true) else it.copy(isShort = true) }
    val v = own.getOrNull(i) ?: return
    if (navigator.shortsInPlayer) navigator.push(Route.ShortsPlayer(v, following = own.drop(i + 1), stayOnChannel = true)) else navigator.push(Route.Video(v))
}

/** Renders a single channel item according to its kind (Video, Playlist/Course, Post). */
@Composable
private fun ChannelItemCard(
    services: AppServices,
    navigator: Navigator,
    item: ChannelItem,
    onOpenPostDetail: (ChannelPost) -> Unit,
    ownerId: String,
    inHorizontalShelf: Boolean = false,
    inShelf: Boolean = false,
) {
    when (item) {
        is ChannelItem.Video -> {
            VideoCard(
                video = item.summary,
                onClick = { navigator.push(Route.Video(item.summary)) },
                showChannelAvatar = false,
                // On the channel's own page the name under every title is noise; a video by another channel (a collab shelf) keeps it.
                showChannelName = item.summary.channelId.isNotBlank() && item.summary.channelId != ownerId,
                style = if (inShelf || com.greenaltambe.vela.ui.layout.LocalWindowSize.current == com.greenaltambe.vela.ui.layout.WindowSizeClass.COMPACT) VideoCardStyle.VERTICAL else VideoCardStyle.HORIZONTAL,
                fullBleed = !inShelf && com.greenaltambe.vela.ui.layout.LocalWindowSize.current == com.greenaltambe.vela.ui.layout.WindowSizeClass.COMPACT,
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
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onClick).padding(Spacing.sm).testTag("channel.playlist"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(140.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            p.thumbnailUrl?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Row(
                Modifier.align(Alignment.BottomEnd).padding(Spacing.xs).clip(CircleShape).background(ExtraColors.mediaBackground.copy(alpha = 0.72f)).padding(horizontal = Spacing.xs, vertical = 2.dp).testTag("playlist.badge"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(AppIcons.playlist, null, Modifier.size(16.dp), tint = ExtraColors.onMedia)
                val badgeText = if (p.isCourse) "COURSE" else p.videoCount?.toString()
                badgeText?.let { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = ExtraColors.onMedia) }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(p.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (p.isCourse && p.lessonCountText != null) {
                Text(p.lessonCountText!!, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            } else if (p.videoCount != null) {
                Text("${p.videoCount} videos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Vertical 9:16 card for YouTube Shorts in channel shelves or the Shorts tab. */
@Composable
private fun ShortCard(video: VideoSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(ComponentIds.VIDEO_CARD).clip(MaterialTheme.shapes.small).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHigh).testTag("card.thumbnail")) {
            val shown = video.thumbnailUrl ?: if (video.id.isNotBlank()) "https://i.ytimg.com/vi/${video.id}/frame0.jpg" else null
            if (shown != null) AsyncImage(shown, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            val statText = video.viewCount?.let { formatViews(it) } ?: video.publishedText
            if (statText != null) {
                Text(
                    statText,
                    modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.xs).clip(MaterialTheme.shapes.extraSmall).background(ExtraColors.mediaScrim.copy(alpha = 0.8f)).padding(horizontal = Spacing.sm, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall, color = ExtraColors.onMedia, maxLines = 1,
                )
            }
        }
        Text(video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp).testTag("card.title"))
    }
}

/** Community post: a tonal card with the author, the text (long ones fold), the attachment, likes and a Comments button. */
@Composable
private fun PostCard(
    services: AppServices,
    navigator: Navigator,
    post: ChannelPost,
    onOpenDetail: () -> Unit,
    inHorizontalShelf: Boolean = false,
    showComments: Boolean = true,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    var expanded by remember(post.id) { mutableStateOf(false) }
    val foldAt = if (inHorizontalShelf) 3 else 6
    var overflowing by remember(post.id) { mutableStateOf(false) }
    var viewer by remember(post.id) { mutableStateOf<Pair<List<String>, Int>?>(null) }
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.surfaceContainer).padding(Spacing.md).testTag("channel.post"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Avatar(post.authorName, size = 40.dp, url = post.authorAvatarUrl)
            Column(Modifier.weight(1f)) {
                Text(post.authorName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                post.publishedText?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
            }
        }

        if (post.text.isNotBlank()) {
            val annotated = remember(post.text, linkColor) { linkedText(post.text, linkColor) {} }
            Text(
                annotated, style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else foldAt, overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) overflowing = it.hasVisualOverflow },
            )
            if (overflowing && !expanded && !inHorizontalShelf) {
                Text("Read more", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clip(MaterialTheme.shapes.small).clickable { expanded = true }.padding(vertical = 2.dp))
            }
        }

        when (val att = post.attachment) {
            is PostAttachment.Image -> AsyncImage(att.url, "Image, tap to view", Modifier.fillMaxWidth().aspectRatio(if (inHorizontalShelf) 16f / 7f else 16f / 10f).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { viewer = listOf(att.url) to 0 }.testTag("channel.post.image"), contentScale = ContentScale.Crop)
            is PostAttachment.MultiImage -> {
                val shown = att.images.take(if (inHorizontalShelf) 2 else 4)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    shown.chunked(2).forEach { rowImgs ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            rowImgs.forEach { img -> AsyncImage(img.url, "Image, tap to view", Modifier.weight(1f).aspectRatio(if (inHorizontalShelf) 16f / 9f else 1f).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { viewer = att.images.map { it.url } to att.images.indexOf(img).coerceAtLeast(0) }.testTag("channel.post.image"), contentScale = ContentScale.Crop) }
                            if (rowImgs.size < 2) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (att.images.size > shown.size) Text("+${att.images.size - shown.size} more, tap to see all", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is PostAttachment.Poll -> {
                val choices = if (inHorizontalShelf) att.choices.take(2) else att.choices
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    choices.forEachIndexed { i, choice ->
                        val ratio = choice.voteRatio?.toFloat() ?: 0f
                        val shape = com.greenaltambe.vela.ui.settings.segmentedShape(i, choices.size)
                        Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.CenterStart) {
                            if (ratio > 0f) Box(Modifier.matchParentSize()) { Box(Modifier.fillMaxHeight().fillMaxWidth(ratio.coerceIn(0f, 1f)).background(MaterialTheme.colorScheme.secondaryContainer)) }
                            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(choice.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                choice.votePercentage?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
                            }
                        }
                    }
                    // YouTube sends the shares only to a viewer who voted; anonymously a poll is just the choices and the total.
                    val hasResults = choices.any { it.voteRatio != null || it.votePercentage != null }
                    val totals = listOfNotNull(att.totalVotesText, if (hasResults) null else "Vote on YouTube to see the results").joinToString(" · ")
                    if (totals.isNotEmpty()) Text(totals, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs).testTag("channel.post.pollTotal"))
                }
            }
            is PostAttachment.Video -> {
                if (inHorizontalShelf) {
                    Text(att.video.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { navigator.push(Route.Video(att.video)) }.padding(Spacing.sm))
                } else {
                    VideoCard(att.video, { navigator.push(Route.Video(att.video)) }, showChannelAvatar = false, style = VideoCardStyle.HORIZONTAL, containerShape = com.greenaltambe.vela.ui.settings.segmentedShape(0, 1))
                }
            }
            is PostAttachment.Playlist -> CourseOrPlaylistCard(att.playlist) { navigator.push(Route.ChannelPlaylist(att.playlist.id, att.playlist.title)) }
            null -> {}
        }

        if (post.voteCountText != null || showComments) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                post.voteCountText?.let {
                    Icon(AppIcons.like, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                if (showComments) FilledTonalButton(onClick = onOpenDetail, shapes = ButtonDefaults.shapes(), modifier = Modifier.testTag("channel.postComments")) { Text("Comments") }
            }
        }
        viewer?.let { (urls, index) -> ImageViewer(urls, index) { viewer = null } }
    }
}

/** Sort & filter bar supporting dropdowns and chips. */
@Composable
private fun SortAndFilterBar(
    groups: List<ChannelFilterGroup>,
    selectedOption: ChannelFilterOption?,
    onSelect: (ChannelFilterOption) -> Unit,
) {
    HScrollRow(Modifier.testTag("channel.sort"), contentPadding = PaddingValues(horizontal = Spacing.gutter)) {
        groups.forEach { group ->
            if (group.isDropdown) {
                item {
                    var expanded by remember { mutableStateOf(false) }
                    val activeLabel = selectedOption?.label ?: group.options.firstOrNull { it.selected }?.label ?: group.title ?: "Sort by"
                    Box {
                        com.greenaltambe.vela.ui.components.ExpressiveChip(true, activeLabel, { expanded = true }, Modifier.testTag("channel.sortDropdown"))
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
                    // By label: a reload hands back new option objects (new tokens, new selected flags), so equality with the clicked one never matches.
                    val isSelected = if (selectedOption != null) opt.label == selectedOption.label else opt.selected
                    com.greenaltambe.vela.ui.components.ExpressiveChip(isSelected, opt.label, { onSelect(opt) }, Modifier.testTag("channel.sort.${opt.label.lowercase().replace(' ', '_')}"))
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
    val linkColor = MaterialTheme.colorScheme.primary

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
        Modifier.fillMaxWidth().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("About this channel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (about?.isPartial == true) {
                Text(
                    "Partial info (fallback mode)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = Spacing.xs, vertical = 2.dp)
                        .testTag("channel.about.partial")
                )
            }
        }
        if (loading) {
            LoadingSpinner(color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            val a = about ?: ChannelAbout()
            if (a.description.isNotBlank()) {
                val annotated = remember(a.description, linkColor) { linkedText(a.description, linkColor) {} }
                Text(annotated, style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                a.subscriberCountText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                a.videoCountText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                a.viewCountText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                a.joinedDateText?.let { Text("Joined ${it.removePrefix("Joined").trim()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                a.country?.let { Text("Country: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (a.links.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Text("Links", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    a.links.forEach { link ->
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onOpenLink(link.url) }.padding(vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Icon(AppIcons.link, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(link.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text(link.displayUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Post detail sheet: the post, then its comments (a failed load says so, with Retry and a way to the post in the browser). */
@Composable
private fun PostDetailContent(
    services: AppServices,
    navigator: Navigator,
    post: ChannelPost,
    onDismiss: () -> Unit,
) {
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(post.detailBrowseParams, attempt) {
        loading = true
        error = null
        val params = post.detailBrowseParams
        if (params == null) {
            error = "Comments are not available for this post."
        } else {
            when (val r = services.extractor.postDetail(params)) {
                is ExtractorResult.Success -> comments = r.value.second
                is ExtractorResult.Failure -> error = "Could not load comments: ${r.error.message}"
            }
        }
        loading = false
    }

    // The replies of a comment open as a thread, exactly as on the video page (same rows, lines and "Show more replies"); the post's author is
    // the "creator" whose name is marked.
    val repliesScope = rememberCoroutineScope()
    val repliesModel = remember(post.id) { RepliesModel(services, repliesScope, "") }
    var thread by remember(post.id) { mutableStateOf<Comment?>(null) }
    com.greenaltambe.vela.ui.platform.PlatformBackHandler(enabled = thread != null) { thread = null }
    thread?.let { open ->
        Column(Modifier.fillMaxSize().testTag("channel.postThread")) {
            Row(Modifier.fillMaxWidth().padding(start = Spacing.gutter, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                androidx.compose.material3.IconButton(onClick = { thread = null }, modifier = Modifier.testTag("channel.postThreadBack")) { Icon(AppIcons.back, "Back to comments") }
                Text("Replies", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                formatCount(open.replyCount.toLong().takeIf { it > 0 })?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ThreadView(open, repliesModel, {}, post.authorName, post.authorAvatarUrl, Modifier.fillMaxWidth().weight(1f))
        }
        return
    }
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).testTag("channel.postDetail"), verticalArrangement = Arrangement.spacedBy(Spacing.sm), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { PostCard(services, navigator, post, onOpenDetail = {}, showComments = false) }
        item {
            Row(Modifier.fillMaxWidth().padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(if (comments.isEmpty()) "Comments" else "${comments.size} comments", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { uriHandler.openUri("https://www.youtube.com/post/${post.id}") }, modifier = Modifier.testTag("channel.postOpenBrowser")) { Text("Open in browser") }
            }
        }
        when {
            loading -> item { Box(Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) { LoadingSpinner(color = MaterialTheme.colorScheme.primary) } }
            error != null -> item { ErrorRow(error!!, if (post.detailBrowseParams != null) ({ attempt++ }) else null, Modifier.testTag("channel.postCommentsError")) }
            comments.isEmpty() -> item { Text("No comments on this post.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("channel.postNoComments")) }
            else -> items(comments.size, key = { comments[it].id }) { i ->
                val c = comments[i]
                // The same comment row as under a video: a replies line opens the thread (a count without a way to open it is only shown).
                CommentRow(
                    c, avatar = 40.dp, onSeek = {}, creatorName = post.authorName, creatorAvatarUrl = post.authorAvatarUrl,
                    modifier = Modifier.padding(vertical = Spacing.xs).testTag("channel.postComment"),
                    onReplies = if (c.replyCount > 0 && c.repliesContinuation != null) ({ thread = c; repliesModel.open(c) }) else null,
                    repliesCountOnly = true,
                )
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
