package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
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
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.pressFeedback
import com.greenaltambe.vela.ui.look.toColor
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

/** Trending, through the same feed pipeline as the subscription feed. */
@Composable
fun HomeScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    val store = LocalHomeFeed.current
    val accountId = services.accounts.active.value.id
    val cached = store != null && store.usable(accountId, services.clock.now())
    var videos by remember { mutableStateOf(if (cached) store!!.videos else videoPagedList()) }
    var state by remember { mutableStateOf<ListState>(if (cached) ListState.Ready else ListState.Loading) }
    var loading by remember { mutableStateOf(false) }
    var pulling by remember { mutableStateOf(false) }       // the pull-to-refresh spinner, only for a pull (not for the first load or paging)
    // What the list shows: the account's Recommended feed, or Trending (signed out, setting, or the account feed failed or was empty).
    var showing by remember { mutableStateOf(if (cached) store!!.showing else "Trending") }
    var note by remember { mutableStateOf<String?>(if (cached) store!!.note else null) }
    val scope = rememberCoroutineScope()
    fun load(more: Boolean) {
        if (loading) return
        loading = true
        if (!more && videos.items.isEmpty()) state = ListState.Loading
        scope.launch {
            val wantRecommended = !more && services.reads != null && services.accounts.active.value.isSignedIn &&
                services.settings.resolve(com.greenaltambe.vela.core.settings.Settings.homeSource) == com.greenaltambe.vela.core.settings.HomeSource.RECOMMENDED
            if (wantRecommended) {
                val rec = services.reads!!.recommended()
                if (!rec.isNullOrEmpty()) {
                    videos = videos.replace(com.greenaltambe.vela.extractor.Page(rec)); showing = "Recommended"; note = null
                    state = ListState.Ready; loading = false
                    store?.loadedAt = services.clock.now()
                    return@launch
                }
                note = if (rec == null) "Recommended is unavailable right now; showing Trending." else "Your account has no recommendations yet; showing Trending."
            } else if (!more) { note = null }
            showing = "Trending"
            when (val result = services.extractor.trending(videos.continuation.takeIf { more })) {
                is ExtractorResult.Success -> {
                    videos = if (more) videos.append(result.value) else videos.replace(result.value)
                    state = if (videos.items.isEmpty()) ListState.Empty("Nothing is trending right now.", "Refresh") { load(false) } else ListState.Ready
                    if (!more) store?.loadedAt = services.clock.now()
                }
                is ExtractorResult.Failure -> if (videos.items.isEmpty()) state = ListState.Error("Could not load trending: ${result.error.message}") { load(false) }
            }
            loading = false
        }
    }
    LaunchedEffect(loading) { if (!loading) pulling = false }
    // Remember what is shown for the next visit; a good answer stamps the time.
    androidx.compose.runtime.SideEffect {
        if (store != null && videos.items.isNotEmpty() && state == ListState.Ready) {
            store.accountId = accountId; store.videos = videos; store.showing = showing; store.note = note
        }
    }
    LaunchedEffect(accountId) {
        // Back on Home with a recent feed: show it as it was. Otherwise (first visit, another account, too old, nothing loaded) fetch.
        if (cached && store!!.accountId == accountId) return@LaunchedEffect
        videos = videoPagedList(); load(more = false)
    }

    val toasts = LocalUndoToasts.current
    val settingsRevision by services.settings.revision.collectAsState()
    val shelfOrder = remember(settingsRevision) { HomeShelves.parse(services.settings.resolve(LookSettings.homeShelves)) }
    val showChips = remember(settingsRevision) { services.settings.resolve(LookSettings.homeChips) }
    val density = remember(settingsRevision) { services.settings.resolve(LookSettings.homeDensity) }
    var dismissed by remember(accountId) { mutableStateOf(services.homePrefs.dismissed()) }
    var filter by remember { mutableStateOf(HomeFilter.ALL) }
    var topic by remember { mutableStateOf<String?>(null) }
    val watched = rememberAsync(accountId, navigator.current, keepWhileLoading = true, first = { emptySet<String>() }, compute = { services.history.watchedVideoIds() })
    val topics = rememberAsync(accountId, keepWhileLoading = true, first = { emptyList<String>() }, compute = { services.homeSources.topics() })
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

    val shownBase = rememberFiltered(services, videos.items, DeArrowSurface.FEED)
    val shown = remember(shownBase, dismissed, filter, topic, watched) { shownBase.withoutDismissed(dismissed).filter { v -> filter.matches(v, watched) && (topic == null || matchesTopic(v, topic!!)) } }
    val continueWatching = rememberAsync(
        accountId,
        navigator.current,
        navigator.nowPlaying?.id,
        keepWhileLoading = true,
        first = { emptyList<WatchRecord>() },
        compute = { services.history.continueWatching() }
    )

    val slots = remember(shelfOrder, shown.size, density) { planSlots(shelfOrder, shown.size, density.step) }
    val injections = slots.map { slot ->
        Injection(slot.afterVideos, slot.shelf.name) {
            ShelfBlock(
                slot.shelf, shelves[slot.shelf], shelfTick, { ensureShelf(slot.shelf) }, services, navigator, dismissed, filter, topic, watched, lastOpened, continueWatching,
                ::notInterested, ::hideChannel,
            )
        }
    }

    com.greenaltambe.vela.ui.components.RefreshBox(isRefreshing = pulling && loading, onRefresh = { pulling = true; shelfTick++; load(more = false) }, modifier = modifier) {
    VideoCollection(
        services, navigator, shown, state, Modifier, testTag = "home.list", scroll = store?.scroll,
        trailing = { v -> VideoMenu(services, v, onNotInterested = { notInterested(v) }, onHideChannel = { hideChannel(v) }) },
        injections = injections,
        header = {
            if (showChips) HomeChips(filter, { filter = it }, topics, topic, { topic = it }, Modifier.padding(bottom = Vela.sm))
            note?.let {
                Box(
                    Modifier.fillMaxWidth().padding(bottom = Vela.sm)
                        .clip(Vela.smallShape)
                        .background(Vela.colors.surfaceVariant.toColor().copy(alpha = 0.5f))
                        .padding(horizontal = Vela.sm, vertical = Vela.xs)
                ) {
                    Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor(), modifier = Modifier.testTag("home.note"))
                }
            }
            (videos.items.size - shownBase.size).takeIf { it > 0 }?.let {
                Text("$it hidden by your feed filters (Settings > Feed).", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor(), modifier = Modifier.padding(bottom = Vela.sm))
            }
        },
        // More is fetched by itself as the last card comes into view; the button stays as a fallback if that fetch failed.
        // The account's recommended feed is one page with no continuation: at its end the list carries on with Trending (then Trending's own pages).
        onEndReached = { if ((videos.continuation != null || showing == "Recommended") && !loading && state == ListState.Ready) load(more = true) },
        footer = if (videos.continuation != null || showing == "Recommended") ({ TextButton(onClick = { load(more = true) }, enabled = !loading) { Text("Load more", maxLines = 1) } }) else null,
    )
    }
}

/** What one shelf is doing. A shelf that fails or has nothing is [Gone] and takes no room. */
internal sealed interface ShelfUi {
    data object Loading : ShelfUi
    data object Gone : ShelfUi
    class Ready(val data: ShelfData) : ShelfUi
}

/** One place in the list: the shelf that belongs there, as a carousel, a placeholder while it loads, or nothing. */
@Composable
private fun ShelfBlock(
    shelf: HomeShelf, ui: ShelfUi?, tick: Int, ensure: () -> Unit, services: AppServices, navigator: Navigator, dismissed: HomeDismissed, filter: HomeFilter, topic: String?,
    watched: Set<String>, lastOpened: kotlin.time.Instant?, continueWatching: List<WatchRecord>, onNotInterested: (VideoSummary) -> Unit, onHideChannel: (VideoSummary) -> Unit,
) {
    LaunchedEffect(shelf, tick) { ensure() }
    if (shelf == HomeShelf.CONTINUE) {
        if (continueWatching.isNotEmpty() && filter == HomeFilter.ALL && topic == null) ContinueBlock(continueWatching, navigator)
        return
    }
    when (ui) {
        is ShelfUi.Ready -> ShelfContent(shelf, ui.data, services, navigator, dismissed, filter, topic, watched, lastOpened, onNotInterested, onHideChannel)
        ShelfUi.Gone -> {}
        ShelfUi.Loading, null -> if (filter == HomeFilter.ALL && topic == null) CarouselSkeleton(portrait = shelf == HomeShelf.SHORTS)
    }
}

/** A loaded shelf, through the same filters as the lists (DeArrow, keywords, hide watched), the dismissed videos and channels, and the chips. Under three videos after them: not drawn. */
@Composable
private fun ShelfContent(
    shelf: HomeShelf, data: ShelfData, services: AppServices, navigator: Navigator, dismissed: HomeDismissed, filter: HomeFilter, topic: String?, watched: Set<String>,
    lastOpened: kotlin.time.Instant?, onNotInterested: (VideoSummary) -> Unit, onHideChannel: (VideoSummary) -> Unit,
) {
    val base = rememberFiltered(services, data.videos, DeArrowSurface.FEED)
    val videos = remember(base, dismissed, filter, topic, watched) { base.withoutDismissed(dismissed).filter { v -> filter.matches(v, watched) && (topic == null || matchesTopic(v, topic)) } }
    if (videos.size < MIN_SHELF_VIDEOS) return
    // A listing often carries no channel picture; the ones of the channels you subscribe to fill the gap (as in the main list).
    val subscribedAvatars = rememberAsync(keepWhileLoading = true, first = { emptyMap<String, String?>() }, compute = { services.subscriptions.all().filter { it.avatarUrl != null }.associate { it.channelId to it.avatarUrl } })
    val shown = remember(videos, subscribedAvatars) { if (subscribedAvatars.isEmpty()) videos else videos.map { v -> if (v.channelAvatarUrl == null) subscribedAvatars[v.channelId]?.let { v.copy(channelAvatarUrl = it) } ?: v else v } }
    val title = data.title ?: if (shelf == HomeShelf.INTERESTS) "Your interests" else shelf.label
    val subtitle = data.subtitle ?: if (shelf == HomeShelf.INTERESTS && data.topics.isNotEmpty()) data.topics.take(2).joinToString(" \u00B7 ") else if (shelf == HomeShelf.CHANNELS) shelf.hint else null
    val tag = "home.shelf.${shelf.name}"
    val seeAll = { navigator.push(when (shelf) { HomeShelf.SUBSCRIPTIONS -> Route.Subscriptions; HomeShelf.SHORTS -> Route.Shorts; else -> Route.HomeShelf(shelf.name) }) }
    if (shelf == HomeShelf.SHORTS) {
        ShortsCarousel(title, shown, subtitle = subtitle, onSeeAll = seeAll, tag = tag, onOpen = { v -> navigator.push(Route.ShortsPlayer(v)) })
        return
    }
    val extras = rememberCardExtras(services, shown)
    val marked: (VideoSummary) -> CardExtras = if (shelf == HomeShelf.SUBSCRIPTIONS) { v -> extras(v).let { if (isNewSince(v, lastOpened)) it.copy(badge = "New") else it } } else extras
    VideoCarousel(
        title, shown, subtitle = subtitle, onSeeAll = seeAll, tag = tag, extras = marked,
        onOpen = { v -> navigator.push(Route.Video(v)) },
        onChannel = { v -> navigator.push(Route.Channel(v.channelId, v.channelName, if (v.channelId.isBlank()) v.id else "")) },
        trailing = { v -> VideoMenu(services, v, onNotInterested = { onNotInterested(v) }, onHideChannel = { onHideChannel(v) }) },
    )
}

/** Videos started and not finished, as the same carousel: the progress bar on the picture and what is left as the numbers line. */
@Composable
private fun ContinueBlock(records: List<WatchRecord>, navigator: Navigator) =
    WatchRecordCarousel("Continue watching", records, navigator, tag = "home.continueWatching", onSeeAll = { navigator.push(Route.History) })

/** Watch-history rows as a carousel of the standard card: progress on the picture, "7:34 left" as the numbers; the history keeps no channel, so no picture of one. */
@Composable
fun WatchRecordCarousel(title: String, records: List<WatchRecord>, navigator: Navigator, tag: String, onSeeAll: (() -> Unit)? = null) {
    val videos = remember(records) { records.map { r -> VideoSummary(id = r.videoId, title = r.title, channelId = r.channelId, channelName = "", durationSeconds = r.durationMs.takeIf { it > 0 }?.div(1000)) } }
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
