package com.greenaltambe.vela.ui.screens

import com.greenaltambe.vela.home.placeHomeBlocks
import com.greenaltambe.vela.home.visibleGroups
import com.greenaltambe.vela.home.allowed
import com.greenaltambe.vela.home.ShownGroup
import com.greenaltambe.vela.home.HomeBlock
import com.greenaltambe.vela.home.HomeList
import com.greenaltambe.vela.core.settings.shortsEnabled
import com.greenaltambe.vela.auth.FeedGroupKind
import com.greenaltambe.vela.ui.theme.Spacing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import com.greenaltambe.vela.ui.theme.AppIcons
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.data.WatchRecord
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.extractor.videoPagedList
import com.greenaltambe.vela.modules.dearrow.DeArrowSurface
import com.greenaltambe.vela.ui.components.HScrollRow
import com.greenaltambe.vela.ui.components.SectionHeader
import com.greenaltambe.vela.ui.components.formatDuration
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import androidx.compose.runtime.collectAsState
import com.greenaltambe.vela.core.settings.HomeShelf
import com.greenaltambe.vela.core.settings.HomeShelves
import com.greenaltambe.vela.core.settings.LookSettings
import com.greenaltambe.vela.home.HomeDismissed
import com.greenaltambe.vela.home.HomeFilter
import com.greenaltambe.vela.home.ShelfData
import com.greenaltambe.vela.home.matchesTopic
import com.greenaltambe.vela.home.withoutDismissed
import com.greenaltambe.vela.ui.look.LocalUndoToasts
import com.greenaltambe.vela.home.MIN_SHELF_VIDEOS
import com.greenaltambe.vela.home.isNewSince
import com.greenaltambe.vela.home.planSlots
import com.greenaltambe.vela.ui.components.CardExtras
import com.greenaltambe.vela.ui.components.CarouselSkeleton
import com.greenaltambe.vela.ui.components.ShortsCarousel
import com.greenaltambe.vela.ui.components.VideoCarousel
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home. Everything it holds belongs to one "Region for content": a change starts the page afresh, so nothing asked for the old region (a list,
 * the chips, a shelf, an answer still on its way) can show under the new one.
 */
@Composable
fun HomeScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    val revision by services.settings.revision.collectAsState()
    val region = remember(revision) { services.settings.resolve(com.greenaltambe.vela.core.settings.Settings.contentRegion) }
    LocalHomeFeed.current?.forRegion(region)
    androidx.compose.runtime.key(region) { HomeContent(services, navigator, modifier) }
}

/** Signed in, the account's feed; signed out, the shelves that need no account. */
@Composable
private fun HomeContent(services: AppServices, navigator: Navigator, modifier: Modifier) {
    val store = LocalHomeFeed.current
    val accountId = services.accounts.active.value.id
    store?.forAccount(accountId)
    // The chip the viewer was on (its label; null = All), and what that list held if it is still fresh.
    val startChip = store?.selectedChip
    val cached = store != null && (if (startChip == null) store.usable(accountId, services.clock.now()) else store.chipUsable(startChip, services.clock.now()))
    var chips by remember { mutableStateOf(store?.chips.orEmpty()) }
    var chipLabel by remember { mutableStateOf(startChip) }
    var hiddenChips by remember { mutableStateOf<Set<String>>(store?.emptyChips.orEmpty().toSet()) }
    var feed by remember { mutableStateOf(if (!cached) HomeList() else if (startChip == null) store!!.list else store!!.chipEntry(startChip).list) }
    var state by remember { mutableStateOf<ListState>(if (cached || !(services.reads != null && services.accounts.active.value.isSignedIn)) ListState.Ready else ListState.Loading) }
    var loading by remember { mutableStateOf(false) }
    var pulling by remember { mutableStateOf(false) }       // the pull-to-refresh spinner, only for a pull (not for the first load or paging)
    // What the list shows: the account's feed ("Recommended", with or without a chip) or Trending (signed out).
    var showing by remember { mutableStateOf(if (cached && startChip == null) store!!.showing else if (cached) "Recommended" else "Trending") }
    var note by remember { mutableStateOf<String?>(if (cached && startChip == null) store!!.note else null) }
    var feedFailed by remember { mutableStateOf(false) }    // the account feed could not be loaded at all: Explore is offered beside Retry
    val showAll = remember { arrayOfNulls<() -> Unit>(1) }   // "Show all" on an empty chip: set once selectChip exists
    val loadId = remember { intArrayOf(0) }                 // a load whose number is no longer current (another chip was chosen meanwhile) is dropped
    // Signed in, Home is the account's feed; signed out it is Trending.
    fun accountFeedOn() = services.reads != null && services.accounts.active.value.isSignedIn
    val scope = rememberCoroutineScope()
    fun stamp() { val now = services.clock.now(); if (chipLabel == null) store?.loadedAt = now else store?.chipEntry(chipLabel!!)?.loadedAt = now }
    /** A chip's first page; if its token is refused (they belong to the response they came in), fresh chips are read from the unfiltered feed and it is tried once more. */
    suspend fun chipFirstPage(reads: com.greenaltambe.vela.auth.AccountReads, chip: com.greenaltambe.vela.auth.HomeChip): com.greenaltambe.vela.auth.HomePage? {
        chip.token?.let { t -> reads.home(chip = t) }?.let { return it }
        val fresh = reads.home() ?: return null
        if (fresh.chips.isNotEmpty()) { chips = fresh.chips; store?.chips = fresh.chips }
        val token = fresh.chips.firstOrNull { it.label == chip.label }?.token ?: return null
        return reads.home(chip = token)
    }
    fun load(more: Boolean) {
        if (loading) return
        loading = true
        val myId = ++loadId[0]
        val forChip = chipLabel
        if (!more && feed.isEmpty) state = ListState.Loading
        scope.launch {
            // Signed in (and the setting says so): Home is the account's own feed, page after page, and nothing else. Trending is never added to it.
            if (accountFeedOn()) {
                val reads = services.reads!!
                val chip = chips.firstOrNull { it.label == forChip && it.kind != com.greenaltambe.vela.auth.HomeChipKind.ALL }
                // The chip that was open is gone from the chips (they were reloaded): fall back to All.
                if (forChip != null && chip == null && !more) { chipLabel = null; store?.selectedChip = null }
                val first = if (chip != null && !more) chipFirstPage(reads, chip) else reads.home(continuation = feed.videos.continuation.takeIf { more })
                // A page that comes back with no videos but offers another is followed a few times before calling the feed empty; what it did hold
                // (YouTube's groups) is kept.
                val pages = listOfNotNull(first).toMutableList()
                var page = first
                var follow = 0
                while (page != null && page.videos.isEmpty() && page.continuation != null && follow++ < 3) { page = reads.home(continuation = page.continuation); page?.let(pages::add) }
                if (myId != loadId[0]) return@launch   // another chip was chosen while this loaded: its own load owns the screen now
                val next = pages.fold(if (more) feed else HomeList()) { list, p -> list.append(p) }
                val groupsOn = services.settings.resolve(com.greenaltambe.vela.core.settings.Settings.homeYoutubeGroups)
                val somethingToShow = next.videos.items.isNotEmpty() || next.groups.any { it.allowed(groupsOn, services.settings.shortsEnabled()) }
                when {
                    page != null && (somethingToShow || more) -> {
                        feed = next
                        if (chip == null && !more && first!!.chips.isNotEmpty()) { chips = first.chips; store?.chips = first.chips }
                        showing = "Recommended"; note = null; feedFailed = false
                        state = ListState.Ready
                        if (!more) stamp()
                    }
                    page != null -> {   // answered with nothing that can be shown
                        feedFailed = false
                        if (chip != null && next.isEmpty) {
                            // A chip with nothing in it is left out of the row for the rest of the session.
                            store?.emptyChips?.add(chip.label); hiddenChips = hiddenChips + chip.label
                            feed = next
                            state = ListState.Empty("Nothing in \"${chip.label}\" for you right now.", "Show all") { showAll[0]?.invoke() }
                        } else if (chip != null) {
                            // Only rows that the settings hide: the chip stays, in case they are switched on again.
                            feed = next
                            state = ListState.Empty("Everything in \"${chip.label}\" is in rows your settings hide.", "Show all") { showAll[0]?.invoke() }
                        } else if (feed.isEmpty) state = ListState.Empty("Your account has no recommendations yet.", "Browse Explore") { navigator.selectTab(Route.Explore) }
                    }
                    feed.isEmpty -> { feedFailed = chip == null; state = ListState.Error(if (chip == null) "Your feed could not be loaded right now." else "\"${chip.label}\" could not be loaded right now.") { load(false) } }
                    !more -> note = "Could not refresh your feed. Showing what was loaded before."
                    // else: a later page failed; the list stays and its "Load more" button tries again.
                }
                loading = false
                return@launch
            }
            // Signed out: there is no feed (YouTube has no Trending page any more and shows no videos without an account). The list stays empty;
            // the shelves that work without an account and a sign-in card are what Home shows.
            feedFailed = false; note = null; showing = "None"
            feed = HomeList(); state = ListState.Ready
            loading = false
        }
    }
    /** Opens a chip (null = All): what it held a moment ago if that is fresh, otherwise its first page. The list that was open is kept for coming back to. */
    fun selectChip(label: String?) {
        if (label == chipLabel) return
        if (!feed.isEmpty) { if (chipLabel == null) store?.list = feed else store?.chipEntry(chipLabel!!)?.list = feed }
        loadId[0]++; loading = false; pulling = false
        chipLabel = label; store?.selectedChip = label; feedFailed = false
        val fresh = store != null && (if (label == null) store.usable(accountId, services.clock.now()) else store.chipUsable(label, services.clock.now()))
        feed = if (!fresh) HomeList() else if (label == null) store!!.list else store!!.chipEntry(label).list
        note = if (label == null && fresh) store?.note else null
        state = if (fresh) ListState.Ready else ListState.Loading
        if (!fresh) load(more = false)
    }
    showAll[0] = { selectChip(null) }
    LaunchedEffect(loading) { if (!loading) pulling = false }
    // Remember what is shown for the next visit; a good answer stamps the time.
    androidx.compose.runtime.SideEffect {
        if (store != null && !feed.isEmpty && state == ListState.Ready) {
            if (chipLabel == null) { store.accountId = accountId; store.list = feed; store.showing = showing; store.note = note }
            else store.chipEntry(chipLabel!!).list = feed
        }
    }
    LaunchedEffect(accountId) {
        // Back on Home with a recent feed: show it as it was. Otherwise (first visit, another account, too old, nothing loaded) fetch.
        if (cached && (startChip != null || store!!.accountId == accountId)) return@LaunchedEffect
        feed = HomeList(); load(more = false)
    }

    val toasts = LocalUndoToasts.current
    val settingsRevision by services.settings.revision.collectAsState()
    val signedOutHome = !accountFeedOn()
    // Signed in, the feed carries YouTube's own Shorts rows, so the app's Shorts shelf is left for signed out.
    val shelfOrder = remember(settingsRevision, signedOutHome) { HomeShelves.parse(services.settings.resolve(LookSettings.homeShelves)).filter { signedOutHome || it != HomeShelf.SHORTS } }
    val groupsOn = remember(settingsRevision) { services.settings.resolve(com.greenaltambe.vela.core.settings.Settings.homeYoutubeGroups) }
    val shortsOn = remember(settingsRevision) { services.settings.shortsEnabled() }
    val showChips = remember(settingsRevision) { services.settings.resolve(LookSettings.homeChips) }
    val youtubeChips = remember(settingsRevision) { services.settings.resolve(com.greenaltambe.vela.core.settings.Settings.homeYoutubeChips) }
    // Chips switched off while one was open: back to the plain feed.
    LaunchedEffect(youtubeChips) { if (!youtubeChips && chipLabel != null) selectChip(null) }
    val density = remember(settingsRevision) { services.settings.resolve(LookSettings.homeDensity) }
    var dismissed by remember(accountId) { mutableStateOf(services.homePrefs.dismissed()) }
    var filter by remember { mutableStateOf(HomeFilter.ALL) }
    val watched = rememberAsync(accountId, navigator.current, keepWhileLoading = true, first = { emptySet<String>() }, compute = { services.history.watchedVideoIds() })
    // The visit before this one, for the "New" marker on the subscription shelf; this visit is recorded at once.
    val lastOpened = remember(accountId) { services.homePrefs.lastOpened() }
    LaunchedEffect(accountId) { services.homePrefs.markOpened(services.clock.now()) }

    // Each shelf loads on its own and fails on its own: one that has nothing or breaks is simply not drawn, and the rest carry on. A shelf loads
    // when its place in the list is about to come into view (the first two at once), at most three at a time. What was loaded before (however
    // old) is shown first and is only replaced by a good new answer, so a failed refresh never empties a shelf.
    var shelfTick by remember { mutableStateOf(0) }
    val shelves = remember(accountId) { androidx.compose.runtime.mutableStateMapOf<HomeShelf, ShelfUi>() }
    val requested = remember(accountId, shelfTick) { HashSet<HomeShelf>() }
    val gate = remember { kotlinx.coroutines.sync.Semaphore(3) }
    fun ensureShelf(shelf: HomeShelf) {
        if (shelf == HomeShelf.CONTINUE || !requested.add(shelf)) return
        scope.launch {
            val kept = store?.shelf(accountId, shelf.name)
            if (kept != null) {
                if (shelves[shelf] == null) shelves[shelf] = kept.second?.let { ShelfUi.Ready(it) } ?: ShelfUi.Gone
                if (shelfTick == 0 && store.isFresh(kept.first, services.clock.now())) return@launch
            } else if (shelves[shelf] == null) shelves[shelf] = ShelfUi.Loading
            val data = gate.withPermit { services.homeSources.load(shelf, dismissed) }
            if (data != null) { store?.keepShelf(accountId, shelf.name, services.clock.now(), data); shelves[shelf] = ShelfUi.Ready(data) }
            else if (shelves[shelf] !is ShelfUi.Ready) { store?.keepShelf(accountId, shelf.name, services.clock.now(), null); shelves[shelf] = ShelfUi.Gone }
            // The subscription feed shown at once may be old: bring it up to date in the background and show what that finds.
            if (shelf == HomeShelf.SUBSCRIPTIONS && data != null) {
                runCatching { services.feed.refreshIfStale() }
                services.homeSources.load(shelf, dismissed)?.let { fresh -> store?.keepShelf(accountId, shelf.name, services.clock.now(), fresh); shelves[shelf] = ShelfUi.Ready(fresh) }
            }
        }
    }
    LaunchedEffect(accountId, shelfTick, shelfOrder) { shelfOrder.filter { it != HomeShelf.CONTINUE }.take(2).forEach(::ensureShelf) }
    fun notInterested(v: VideoSummary) {
        services.homePrefs.dismissVideo(v.id); dismissed = services.homePrefs.dismissed()
        toasts.show("Not interested. Hidden from Home.") { services.homePrefs.restoreVideo(v.id); dismissed = services.homePrefs.dismissed() }
    }
    fun hideChannel(v: VideoSummary) {
        services.homePrefs.hideChannel(v.channelId); dismissed = services.homePrefs.dismissed()
        toasts.show("${v.channelName.ifBlank { "This channel" }} is hidden from Home.") { services.homePrefs.restoreChannel(v.channelId); dismissed = services.homePrefs.dismissed() }
    }

    val shownBase = rememberFiltered(services, feed.videos.items, DeArrowSurface.FEED)
    // What the filters and the blocklist left out is said once after a refresh, as a toast that goes away by itself, not as a permanent bar over the list.
    var announceHidden by remember { mutableStateOf(false) }
    LaunchedEffect(loading, feed.videos.items.size, shownBase.size) {
        if (!loading && announceHidden && feed.videos.items.isNotEmpty()) {
            announceHidden = false
            val hidden = feed.videos.items.size - shownBase.size
            if (hidden > 0) toasts.show("$hidden hidden by your filters or blocklist")
        }
    }
    val shown = remember(shownBase, dismissed, filter, watched) { shownBase.withoutDismissed(dismissed).filter { v -> filter.matches(v, watched) } }
    // Videos saved without a title (opened from a link before this was fixed) are looked up once, then the shelf reads again.
    var healed by remember(accountId) { mutableStateOf(0) }
    LaunchedEffect(accountId) { healed = withContext(kotlinx.coroutines.Dispatchers.Default) { runCatching { services.healHistoryTitles() }.getOrDefault(0) } }
    var continueTick by remember(accountId) { mutableStateOf(0) }
    val continueWatching = rememberAsync(
        accountId, healed, continueTick,
        navigator.current,
        navigator.nowPlaying?.id,
        keepWhileLoading = true,
        first = { emptyList<WatchRecord>() },
        compute = { services.history.continueWatching() }
    )

    // One read of the subscriptions and of the card numbers for the whole screen (feed and every shelf), not one per shelf.
    val subscribed = rememberAsync(accountId, keepWhileLoading = true, first = { emptyList<com.greenaltambe.vela.data.SubscribedChannel>() }, compute = { services.subscriptions.all() })
    val subscribedAvatars = remember(subscribed) { subscribed.filter { it.avatarUrl != null }.associate { it.channelId to it.avatarUrl } }
    val channelNames = remember(subscribed) { subscribed.associate { it.channelId to it.name } }
    val localFilter = filter != HomeFilter.ALL
    // YouTube's groups go through the same filters as every list, and what was sent away from Home stays away.
    val groupVideos = remember(feed.groups) { feed.groups.flatMap { it.videos } }
    val groupBase = rememberFiltered(services, groupVideos, DeArrowSurface.FEED)
    val shownGroups = remember(feed.groups, groupBase, dismissed, groupsOn, shortsOn, localFilter) {
        visibleGroups(feed.groups, groupBase.withoutDismissed(dismissed).associateBy { it.id }, groupsOn, shortsOn, localFilter)
    }
    val readyShelves = shelfOrder.mapNotNull { (shelves[it] as? ShelfUi.Ready)?.data?.videos }
    val allVideos = remember(shown, readyShelves, shownGroups) { shown + readyShelves.flatten() + shownGroups.flatMap { it.group.videos } }
    val extras = rememberCardExtras(services, allVideos)

    // The places of the shelves do not depend on how much has loaded. With a chip active the shelves are out of the way (the chips filter the feed);
    // while the feed's first answer is awaited only Continue watching is up, so the screen has one set of placeholders, not shelves stacked over them.
    val filtering = localFilter || chipLabel != null
    val feedPending = feed.isEmpty && state == ListState.Loading
    val slots = remember(shelfOrder, density) { planSlots(shelfOrder, density.step) }
    val shelfSlots = if (filtering) emptyList() else slots.filter { !feedPending || it.shelf == HomeShelf.CONTINUE }
    val blocks = placeHomeBlocks(shelfSlots, shownGroups, feed.videos.items.map { it.id }, shown.map { it.id })
    val shelfInjections = blocks.map { block ->
        when (block) {
            is HomeBlock.Shelf -> Injection(block.afterVideos, block.shelf.name, "shelf-${block.shelf.name}") {
                ShelfBlock(
                    block.shelf, shelves[block.shelf], shelfTick, { ensureShelf(block.shelf) }, services, navigator, dismissed, lastOpened, continueWatching, { continueTick++ },
                    subscribedAvatars, channelNames, extras, ::notInterested, ::hideChannel,
                )
            }
            is HomeBlock.Group -> Injection(block.afterVideos, "yt-${block.shown.key}", "youtube-group") {
                YouTubeGroup(block.shown, services, navigator, subscribedAvatars, extras, ::notInterested, ::hideChannel)
            }
        }
    }
    // Signed out the list has no videos, so every shelf lands at the top in order. The card comes right after Continue watching (which is something to do, so it
    // stays first) and before the other shelves: at the end it would sit below several screens of shelves, where nobody would see it.
    val injections = if (signedOutHome) {
        val card = Injection(0, "signin", "home-signin") { SignInCard(onSignIn = { navigator.push(Route.Accounts) }, onExplore = { navigator.selectTab(Route.Explore) }) }
        shelfInjections.takeWhile { it.key == HomeShelf.CONTINUE.name } + card + shelfInjections.dropWhile { it.key == HomeShelf.CONTINUE.name }
    } else shelfInjections

    // A chip whose matches are not among what is loaded: an empty list has no last card to ask for more, so ask here, a few pages at most, behind placeholders;
    // then say plainly that nothing matches, with the way back.
    val canMore = feed.videos.continuation != null
    var autoPages by remember(filter, chipLabel) { mutableStateOf(0) }
    val nothingYet = localFilter && shown.isEmpty() && state == ListState.Ready
    LaunchedEffect(nothingYet, loading, feed.videos.items.size, filter) {
        if (nothingYet && !loading && canMore && autoPages < MAX_FILTER_PAGES) { autoPages++; load(more = true) }
    }
    val listState = when {
        !nothingYet -> state
        loading || (canMore && autoPages < MAX_FILTER_PAGES) -> ListState.Loading
        else -> ListState.Empty("Nothing in your feed matches \"${filter.label}\" right now.", "Show all") { filter = HomeFilter.ALL }
    }

    // The chips stay put above the list while it scrolls, so a filter is always one tap away.
    val hideOnScroll = remember(settingsRevision) { services.settings.resolve(LookSettings.hideChipsOnScroll) }
    val scrolledAway = hideOnScroll && com.greenaltambe.vela.ui.nav.LocalScrollChrome.current?.hidden == true
    val chromeEnter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)) + androidx.compose.animation.fadeIn()
    val chromeExit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)) + androidx.compose.animation.fadeOut()
    // With see-through bars the shell gives Home the whole page and a top inset, and hosts the strip itself inside the frosted top area (one surface with the top bar).
    val topExtras = com.greenaltambe.vela.ui.nav.LocalTopExtras.current
    val overlay = topExtras?.active == true
    val header: @Composable () -> Unit = {
        if (showChips) androidx.compose.animation.AnimatedVisibility(!scrolledAway, enter = chromeEnter, exit = chromeExit) {
            HomeChips(chips, chipLabel, ::selectChip, accountFeedOn() && youtubeChips, filter, { filter = it }, hiddenChips, Modifier.padding(top = Spacing.sm))
        }
        // What the feed has to say sits here, under the chips, not as a list item: a first item that appears after the list has loaded would land above the videos, out of sight.
        note?.let {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).padding(top = Spacing.sm)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            ) {
                Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("home.note"))
            }
        }
        if (feedFailed && feed.isEmpty) TextButton(onClick = { navigator.selectTab(Route.Explore) }, modifier = Modifier.padding(horizontal = Spacing.sm).testTag("home.explore")) { Text("Browse Explore") }
    }
    val list: @Composable (Modifier) -> Unit = { box ->
        com.greenaltambe.vela.ui.components.RefreshBox(isRefreshing = pulling && loading, onRefresh = { pulling = true; announceHidden = true; shelfTick++; load(more = false) }, modifier = box) {
            VideoCollection(
                services, navigator, shown, listState, Modifier, testTag = "home.list", scroll = if (chipLabel == null) store?.scroll else null,
                trailing = { v -> VideoMenu(services, v, onNotInterested = { notInterested(v) }, onHideChannel = { hideChannel(v) }) },
                injections = injections,
                skeletonCount = 3,
                scrollToTopKey = Triple(filter, chipLabel, accountId),
                sharedAvatars = subscribedAvatars,
                sharedExtras = extras,
                // More is fetched by itself a few cards before the end; while it is on its way a placeholder card shows, and the button is only the way back if that fetch failed.
                // The account feed pages by its own continuation and ends where YouTube's list ends; Trending is never appended to it.
                onEndReached = { if (feed.videos.continuation != null && !loading && state == ListState.Ready) load(more = true) },
                footer = if (canMore && !nothingYet) ({
                    if (loading) com.greenaltambe.vela.ui.components.SkeletonCard(vertical = true, fullBleed = true)
                    else TextButton(onClick = { load(more = true) }) { Text("Load more", maxLines = 1) }
                }) else null,
            )
        }
    }
    if (overlay) {
        // The strip lives in the shell's top area while this page is open; the room kept for it is not re-measured while it is slid away on scroll.
        androidx.compose.runtime.SideEffect { topExtras!!.content = header; topExtras.held = scrolledAway }
        androidx.compose.runtime.DisposableEffect(topExtras) { onDispose { topExtras!!.content = null; topExtras.held = false } }
        Box(modifier.fillMaxSize()) { list(Modifier.fillMaxSize()) }
    } else Column(modifier.fillMaxSize()) { header(); list(Modifier.weight(1f)) }
}

/** How many more pages a filter that matches nothing loaded may fetch before it says so. */
private const val MAX_FILTER_PAGES = 4

/** What one shelf is doing. A shelf that fails or has nothing is [Gone] and takes no room. */
internal sealed interface ShelfUi {
    data object Loading : ShelfUi
    data object Gone : ShelfUi
    class Ready(val data: ShelfData) : ShelfUi
}

/** One place in the list: the shelf that belongs there, as a carousel, a placeholder while it loads, or nothing. A shelf that fills or empties eases its height. */
@Composable
private fun ShelfBlock(
    shelf: HomeShelf, ui: ShelfUi?, tick: Int, ensure: () -> Unit, services: AppServices, navigator: Navigator, dismissed: HomeDismissed,
    lastOpened: kotlin.time.Instant?, continueWatching: List<WatchRecord>, onContinueChanged: () -> Unit, avatars: Map<String, String?>, channelNames: Map<String, String>,
    extras: (VideoSummary) -> CardExtras, onNotInterested: (VideoSummary) -> Unit, onHideChannel: (VideoSummary) -> Unit,
) {
    LaunchedEffect(shelf, tick) { ensure() }
    Box(Modifier.fillMaxWidth().animateContentSize()) {
        if (shelf == HomeShelf.CONTINUE) {
            if (continueWatching.isNotEmpty()) ContinueBlock(continueWatching, channelNames, navigator, services, onContinueChanged)
            return@Box
        }
        when (ui) {
            is ShelfUi.Ready -> ShelfContent(shelf, ui.data, services, navigator, dismissed, lastOpened, avatars, extras, onNotInterested, onHideChannel)
            ShelfUi.Gone -> {}
            ShelfUi.Loading, null -> CarouselSkeleton(portrait = shelf == HomeShelf.SHORTS)
        }
    }
}

/** A loaded shelf, through the same filters as the lists (DeArrow, keywords, hide watched) and the dismissed videos and channels. Under three videos after them: not drawn. */
@Composable
private fun ShelfContent(
    shelf: HomeShelf, data: ShelfData, services: AppServices, navigator: Navigator, dismissed: HomeDismissed, lastOpened: kotlin.time.Instant?,
    avatars: Map<String, String?>, extras: (VideoSummary) -> CardExtras, onNotInterested: (VideoSummary) -> Unit, onHideChannel: (VideoSummary) -> Unit,
) {
    val base = rememberFiltered(services, data.videos, DeArrowSurface.FEED)
    val videos = remember(base, dismissed) { base.withoutDismissed(dismissed) }
    if (videos.size < MIN_SHELF_VIDEOS) return
    // A listing often carries no channel picture; the ones of the channels you subscribe to fill the gap (as in the main list).
    val shown = remember(videos, avatars) { if (avatars.isEmpty()) videos else videos.map { v -> if (v.channelAvatarUrl == null) avatars[v.channelId]?.let { v.copy(channelAvatarUrl = it) } ?: v else v } }
    val title = data.title ?: if (shelf == HomeShelf.INTERESTS) "Your interests" else shelf.label
    val subtitle = data.subtitle ?: if (shelf == HomeShelf.INTERESTS && data.topics.isNotEmpty()) data.topics.take(2).joinToString(" \u00B7 ") else if (shelf == HomeShelf.CHANNELS) shelf.hint else null
    val tag = "home.shelf.${shelf.name}"
    val seeAll = { navigator.push(when (shelf) { HomeShelf.SUBSCRIPTIONS -> Route.Subscriptions; HomeShelf.SHORTS -> Route.Shorts; else -> Route.HomeShelf(shelf.name) }) }
    if (shelf == HomeShelf.SHORTS) {
        ShortsCarousel(title, shown, subtitle = subtitle, onSeeAll = seeAll, tag = tag, onOpen = { v -> navigator.push(Route.ShortsPlayer(v)) })
        return
    }
    val marked: (VideoSummary) -> CardExtras = if (shelf == HomeShelf.SUBSCRIPTIONS) { v -> extras(v).let { if (isNewSince(v, lastOpened)) it.copy(badge = "New") else it } } else extras
    VideoCarousel(
        title, shown, subtitle = subtitle, onSeeAll = seeAll, tag = tag, extras = marked,
        onOpen = { v -> navigator.push(Route.Video(v)) },
        onChannel = { v -> navigator.push(Route.Channel(v.channelId, v.channelName, if (v.channelId.isBlank()) v.id else "")) },
        trailing = { v -> VideoMenu(services, v, onNotInterested = { onNotInterested(v) }, onHideChannel = { onHideChannel(v) }) },
    )
}

/** One of YouTube's own groups: a row of Shorts as tall cards, anything else as the usual cards. Opening a Short carries the rest of its row along. */
@Composable
private fun YouTubeGroup(
    shown: ShownGroup, services: AppServices, navigator: Navigator, avatars: Map<String, String?>, extras: (VideoSummary) -> CardExtras,
    onNotInterested: (VideoSummary) -> Unit, onHideChannel: (VideoSummary) -> Unit,
) {
    val group = shown.group
    val videos = remember(group.videos, avatars) { if (avatars.isEmpty()) group.videos else group.videos.map { v -> if (v.channelAvatarUrl == null) avatars[v.channelId]?.let { v.copy(channelAvatarUrl = it) } ?: v else v } }
    val tag = "home.group.${shown.key}"
    if (group.kind == FeedGroupKind.SHORTS) {
        ShortsCarousel(group.title, videos, subtitle = group.subtitle, tag = tag, onOpen = { v -> navigator.push(Route.ShortsPlayer(v, following = videos.dropWhile { it.id != v.id }.drop(1), fromYouTubeRow = true)) })
        return
    }
    VideoCarousel(
        group.title, videos, subtitle = group.subtitle, tag = tag, extras = extras,
        onOpen = { v -> navigator.push(Route.Video(v)) },
        onChannel = { v -> navigator.push(Route.Channel(v.channelId, v.channelName, if (v.channelId.isBlank()) v.id else "")) },
        trailing = { v -> VideoMenu(services, v, onNotInterested = { onNotInterested(v) }, onHideChannel = { onHideChannel(v) }) },
    )
}

/** Videos started and not finished, as the same carousel: the progress bar on the picture and what is left as the numbers line. */
@Composable
private fun ContinueBlock(records: List<WatchRecord>, channelNames: Map<String, String>, navigator: Navigator, services: AppServices, onRemoved: () -> Unit) {
    val toasts = com.greenaltambe.vela.ui.look.LocalUndoToasts.current
    WatchRecordCarousel(
        "Continue watching", records, navigator, tag = "home.continueWatching", onSeeAll = { navigator.push(Route.History) }, channelNames = channelNames,
        trailing = { v ->
            VideoMenu(services, v, onRemoveFromContinue = {
                if (services.history.dismissContinue(v.id)) {
                    onRemoved()
                    toasts.show("Removed from Continue watching") { services.history.restoreContinue(v.id); onRemoved() }
                }
            })
        },
    )
}

/** Watch-history rows as a carousel of the standard card: progress on the picture, "7:34 left" as the numbers; the history keeps no channel name, so only the channels you subscribe to show one (no picture). */
@Composable
fun WatchRecordCarousel(
    title: String, records: List<WatchRecord>, navigator: Navigator, tag: String, onSeeAll: (() -> Unit)? = null, channelNames: Map<String, String> = emptyMap(),
    trailing: @Composable ((VideoSummary) -> Unit)? = null,
) {
    val videos = remember(records, channelNames) { records.map { r -> VideoSummary(id = r.videoId, title = r.title, channelId = r.channelId, channelName = channelNames[r.channelId].orEmpty(), durationSeconds = r.durationMs.takeIf { it > 0 }?.div(1000)) } }
    val byId = remember(records) { records.associateBy { it.videoId } }
    VideoCarousel(
        title, videos, tag = tag, onSeeAll = onSeeAll, showChannelAvatar = false,
        extras = { v ->
            val r = byId[v.id]
            val fraction = r?.takeIf { it.durationMs > 0 }?.let { (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) }
            val left = r?.takeIf { it.durationMs > 0 && it.positionMs < it.durationMs }?.let { formatDuration((it.durationMs - it.positionMs) / 1000) }?.let { "$it left" }
            CardExtras(watched = fraction, badge = left)
        },
        onOpen = { v -> navigator.push(Route.Video(v)) },
        trailing = trailing,
    )
}

/** The whole list behind a shelf's "See all". */
@Composable
fun HomeShelfScreen(services: AppServices, navigator: Navigator, shelfId: String, modifier: Modifier = Modifier) {
    val shelf = HomeShelf.entries.firstOrNull { it.name == shelfId }
    val store = LocalHomeFeed.current
    val accountId = services.accounts.active.value.id
    var data by remember(shelfId, accountId) { mutableStateOf(shelf?.let { store?.shelf(accountId, it.name)?.second }) }
    var state by remember(shelfId, accountId) { mutableStateOf<ListState>(if (data != null) ListState.Ready else ListState.Loading) }
    LaunchedEffect(shelfId, accountId) {
        if (data != null || shelf == null) return@LaunchedEffect
        val loaded = services.homeSources.load(shelf, services.homePrefs.dismissed())
        data = loaded
        state = if (loaded == null) ListState.Empty("Nothing to show here right now.") else ListState.Ready
    }
    val videos = rememberFiltered(services, data?.videos.orEmpty(), DeArrowSurface.FEED)
    VideoCollection(services, navigator, videos, state, modifier, testTag = "home.shelfList.$shelfId")
}
