package com.greenaltambe.vela.ui.screens

import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import com.greenaltambe.vela.ui.look.appearOnce
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.greenaltambe.vela.modules.ryd.summary
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.ui.components.CardExtras
import com.greenaltambe.vela.ui.components.EmptyState
import com.greenaltambe.vela.ui.components.ErrorRow
import com.greenaltambe.vela.ui.components.SkeletonCard
import com.greenaltambe.vela.ui.components.VideoCard
import com.greenaltambe.vela.ui.components.VideoCardStyle
import com.greenaltambe.vela.ui.layout.WindowSizeClass
import com.greenaltambe.vela.ui.layout.LocalWindowSize
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route

/** Where a list of videos is in its life: the screens show a skeleton, the items, an empty state or an error row from this. */
sealed interface ListState {
    data object Loading : ListState
    data object Ready : ListState
    data class Empty(val message: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null) : ListState
    data class Error(val message: String, val onRetry: (() -> Unit)?) : ListState
}

/** What the cards of a list need to know that is not in the video: downloaded, how far watched, RYD numbers. Read off the main thread; cards fill in as the answers arrive. */
@Composable
fun rememberCardExtras(services: AppServices, videos: List<VideoSummary>): (VideoSummary) -> CardExtras {
    val downloaded = rememberAsync(videos.size, keepWhileLoading = true, first = { emptySet<String>() }, compute = { services.downloads?.completedIds().orEmpty() })
    val progress = rememberAsync(videos.size, keepWhileLoading = true, first = { emptyMap<String, Float>() }, compute = { services.history.progress() })
    val rydRevision by services.modules.ryd.revision.collectAsState()
    val rydOn = services.settings.resolve(com.greenaltambe.vela.core.settings.Settings.rydInFeed) && services.modules.ryd.isEnabled(services.settings)
    // One lookup per list change, not one composable (and one revision collector) per card.
    val ryd = remember(rydRevision, videos, rydOn) { if (rydOn) videos.associate { it.id to services.modules.ryd.cached(it.id)?.summary() } else emptyMap() }
    return { v -> CardExtras(watched = progress[v.id], downloaded = v.id in downloaded, badge = ryd[v.id]) }
}

/**
 * A scrolling list of videos that adapts to the window: COMPACT gets one column of vertical cards (thumbnail across the
 * width), MEDIUM and EXPANDED a grid of vertical cards at least ~280 dp wide (or, with [grid] false, a centred list of
 * horizontal rows, which is what search, playlists and history use everywhere). Loading, empty and error states replace
 * the items; [header] and [footer] sit above and below.
 */
/** A list screen's remembered scroll position (see [VideoCollection]); held by whoever outlives the screen. */
class ScrollMemory(var index: Int = 0, var offset: Int = 0)

@Composable
fun VideoCollection(
    services: AppServices,
    navigator: Navigator,
    videos: List<VideoSummary>,
    state: ListState,
    modifier: Modifier = Modifier,
    grid: Boolean = true,
    testTag: String = "videos.list",
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    trailing: @Composable ((VideoSummary) -> Unit)? = { v -> VideoMenu(services, v) },
    /** Called when the last item of [videos] is composed, so a screen can append the next page. */
    onEndReached: (() -> Unit)? = null,
    /** Where the list was scrolled to last time it was on screen, so coming back to it (after a video, another tab) does not jump to the top. */
    scroll: ScrollMemory? = null,
    /** False on a channel's own page, where every video is by that channel. */
    showChannelAvatar: Boolean = true,
    /** Blocks (carousels) set between the videos; see [Injection]. */
    injections: List<Injection> = emptyList(),
    /** How many placeholder cards stand in while [state] is Loading. */
    skeletonCount: Int = 6,
    /** When this changes (not on the first composition) the list jumps back to the top: a new filter shows its own results from the start. */
    scrollToTopKey: Any? = null,
    /** Channel pictures and card numbers a screen that also draws other rows (Home's shelves) has already read, so they are read once, not once per row. */
    sharedAvatars: Map<String, String?>? = null,
    sharedExtras: ((VideoSummary) -> CardExtras)? = null,
) {
    val size = LocalWindowSize.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(scroll?.index ?: 0, scroll?.offset ?: 0)
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(scroll?.index ?: 0, scroll?.offset ?: 0)
    val useGrid = grid && size != WindowSizeClass.COMPACT
    var scrolledOnce by remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(useGrid) {
        onDispose {
            if (scroll != null) {
                if (useGrid) { scroll.index = gridState.firstVisibleItemIndex; scroll.offset = gridState.firstVisibleItemScrollOffset }
                else { scroll.index = listState.firstVisibleItemIndex; scroll.offset = listState.firstVisibleItemScrollOffset }
            }
        }
    }
    // A listing often carries no channel picture (feeds, history, playlists); the ones of the channels you subscribe to fill the gap.
    // Read off the main thread (it is a database query): the cards show without the pictures first, then fill in.
    val subscribedAvatars = sharedAvatars ?: rememberAsync(keepWhileLoading = true, first = { emptyMap<String, String?>() }, compute = { services.subscriptions.all().filter { it.avatarUrl != null }.associate { it.channelId to it.avatarUrl } })
    val videos = remember(videos, subscribedAvatars) { if (subscribedAvatars.isEmpty()) videos else videos.map { v -> if (v.channelAvatarUrl == null) subscribedAvatars[v.channelId]?.let { v.copy(channelAvatarUrl = it) } ?: v else v } }
    val extras = sharedExtras ?: rememberCardExtras(services, videos)
    val padding = Vela.contentPadding
    // Remembered so the per-card click lambdas below keep their identity and an unchanged card can be skipped.
    val open = remember(navigator) { { v: VideoSummary -> navigator.push(Route.Video(v)) } }
    // Some listings (shorts, certain search results) carry no channel id; ask the video itself before opening, never open a channel with a blank id.
    // The channel page opens at once; when the listing carries no channel id, the page finds it from the video behind its own loader.
    val channel = remember(navigator) { { v: VideoSummary -> navigator.push(Route.Channel(v.channelId, v.channelName, if (v.channelId.isBlank()) v.id else "")) } }

    val rows = remember(videos, injections.map { it.key to it.afterVideos }) { listRows(videos, injections) }
    // The next page is asked for a few cards before the end, so it is usually there by the time the end is reached.
    val prefetchId = videos.getOrNull((videos.size - 1 - PREFETCH_DISTANCE).coerceAtLeast(0))?.id
    androidx.compose.runtime.LaunchedEffect(scrollToTopKey) {
        if (scrollToTopKey == null || !scrolledOnce) { scrolledOnce = true; return@LaunchedEffect }
        if (useGrid) gridState.animateScrollToItem(0) else listState.animateScrollToItem(0)
    }

    if (grid && size != WindowSizeClass.COMPACT) {
        LazyVerticalGrid(
            GridCells.Adaptive(minSize = 280.dp), modifier.fillMaxSize().testTag(testTag), state = gridState, contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(Vela.md), verticalArrangement = Arrangement.spacedBy(Vela.lg),
        ) {
            header?.let { h -> item(key = "list-header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") { h() } }
            // Blocks that belong above the first video come first, then the loading / empty / error state, then the videos with the other blocks between them.
            // A problem (error, nothing to show) comes first, with the blocks under it; while loading, the blocks come first and the placeholders after them.
            val problemFirst = state is ListState.Error || state is ListState.Empty
            if (problemFirst) stateItems(state, skeletonCount) { SkeletonCard(vertical = true) }
            gridItemsIndexed(rows.leading, key = { _, r -> r.key }, span = { _, _ -> GridItemSpan(maxLineSpan) }, contentType = { _, r -> (r as ListRow.Block).injection.contentType }) { _, r -> r.let { (it as ListRow.Block).injection.content() } }
            if (!problemFirst) stateItems(state, skeletonCount) { SkeletonCard(vertical = true) }
            gridItemsIndexed(rows.rest, key = { _, r -> r.key }, span = { _, r -> if (r is ListRow.Block) GridItemSpan(maxLineSpan) else GridItemSpan(1) }, contentType = { _, r -> if (r is ListRow.Block) r.injection.contentType else "video" }) { _, r ->
                when (r) {
                    is ListRow.Block -> r.injection.content()
                    is ListRow.Video -> {
                        val v = r.video
                        VideoCard(v, { open(v) }, modifier = Modifier.appearOnce(v.id, r.index), style = VideoCardStyle.VERTICAL, extras = extras(v), onChannelClick = { channel(v) }, showChannelAvatar = showChannelAvatar, trailing = trailing?.let { t -> { t(v) } })
                        if (onEndReached != null && v.id == prefetchId) LaunchedEffect(videos.size) { onEndReached() }
                    }
                }
            }
            footer?.let { f -> item(key = "list-footer", span = { GridItemSpan(maxLineSpan) }, contentType = "footer") { f() } }
        }
    } else {
        val vertical = grid // a COMPACT feed uses vertical cards; list screens use horizontal rows
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = if (vertical) Int.MAX_VALUE.dp else 900.dp).fillMaxSize().testTag(testTag),
                state = listState, contentPadding = padding, verticalArrangement = Arrangement.spacedBy(if (vertical) Vela.lg else Vela.md),
            ) {
                header?.let { h -> item(key = "list-header", contentType = "header") { h() } }
                val problemFirst = state is ListState.Error || state is ListState.Empty
                if (problemFirst) stateItems(state, skeletonCount) { SkeletonCard(vertical = vertical, fullBleed = vertical) }
                items(rows.leading, key = { it.key }, contentType = { (it as ListRow.Block).injection.contentType }) { r -> (r as ListRow.Block).injection.content() }
                if (!problemFirst) stateItems(state, skeletonCount) { SkeletonCard(vertical = vertical, fullBleed = vertical) }
                items(rows.rest, key = { it.key }, contentType = { r -> if (r is ListRow.Block) r.injection.contentType else "video" }) { r ->
                    when (r) {
                        is ListRow.Block -> r.injection.content()
                        is ListRow.Video -> {
                            val v = r.video
                            VideoCard(v, { open(v) }, modifier = Modifier.appearOnce(v.id, r.index), style = if (vertical) VideoCardStyle.VERTICAL else VideoCardStyle.HORIZONTAL, extras = extras(v), onChannelClick = { channel(v) }, showChannelAvatar = showChannelAvatar, fullBleed = vertical, trailing = trailing?.let { t -> { t(v) } })
                            if (onEndReached != null && v.id == prefetchId) LaunchedEffect(videos.size) { onEndReached() }
                        }
                    }
                }
                footer?.let { f -> item(key = "list-footer", contentType = "footer") { f() } }
            }
        }
    }
}

/** A block (a carousel) set into a list after [afterVideos] videos (0: above the first). The [key] keeps it in place as more videos arrive. */
class Injection(val afterVideos: Int, val key: String, val contentType: String = "injected", val content: @Composable () -> Unit)

/** How many cards before the last one the next page is asked for. */
private const val PREFETCH_DISTANCE = 3

private sealed interface ListRow {
    val key: String
    class Video(val video: VideoSummary, val index: Int) : ListRow { override val key get() = video.id }
    class Block(val injection: Injection) : ListRow { override val key get() = "inj-" + injection.key }
}

private class ListRows(val leading: List<ListRow>, val rest: List<ListRow>)

/** The videos with the blocks between them. Blocks above the first video are kept apart so the list's own state (loading, error) can sit under them. */
private fun listRows(videos: List<VideoSummary>, injections: List<Injection>): ListRows {
    val at = injections.groupBy { it.afterVideos.coerceIn(0, videos.size) }
    val leading = at[0].orEmpty().map { ListRow.Block(it) }
    val rest = ArrayList<ListRow>()
    videos.forEachIndexed { i, v ->
        rest += ListRow.Video(v, i)
        at[i + 1]?.forEach { rest += ListRow.Block(it) }
    }
    return ListRows(leading, rest)
}

private fun LazyListScope.stateItems(state: ListState, count: Int, skeleton: @Composable () -> Unit) {
    when (state) {
        ListState.Loading -> items(count, key = { "skeleton-$it" }, contentType = { "skeleton" }) { skeleton() }
        ListState.Ready -> {}
        is ListState.Empty -> item(key = "list-empty", contentType = "state") { EmptyState(state.message, actionLabel = state.actionLabel, onAction = state.onAction) }
        is ListState.Error -> item(key = "list-error", contentType = "state") { ErrorRow(state.message, state.onRetry) }
    }
}

private fun LazyGridScope.stateItems(state: ListState, count: Int, skeleton: @Composable () -> Unit) {
    when (state) {
        ListState.Loading -> items(count, key = { "skeleton-$it" }, contentType = { "skeleton" }) { skeleton() }
        ListState.Ready -> {}
        is ListState.Empty -> item(key = "list-empty", span = { GridItemSpan(maxLineSpan) }, contentType = "state") { EmptyState(state.message, actionLabel = state.actionLabel, onAction = state.onAction) }
        is ListState.Error -> item(key = "list-error", span = { GridItemSpan(maxLineSpan) }, contentType = "state") { ErrorRow(state.message, state.onRetry) }
    }
}
