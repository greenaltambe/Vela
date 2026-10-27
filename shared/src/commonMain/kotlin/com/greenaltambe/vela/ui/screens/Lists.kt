package com.greenaltambe.vela.ui.screens

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
import androidx.compose.runtime.Composable
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

/** What the cards of a list need to know that is not in the video: downloaded, how far watched, RYD numbers. */
@Composable
fun rememberCardExtras(services: AppServices, videos: List<VideoSummary>): (VideoSummary) -> CardExtras {
    val downloaded = remember(videos) { services.downloads?.completedIds().orEmpty() }
    val progress = remember(videos) { services.history.progress() }
    val ryd = videos.associate { it.id to rydBadgeFor(services, it.id) }
    return { v -> CardExtras(watched = progress[v.id], downloaded = v.id in downloaded, badge = ryd[v.id]) }
}

@Composable
private fun rydBadgeFor(services: AppServices, id: String): String? = rydBadge(services, id)

/**
 * A scrolling list of videos that adapts to the window: COMPACT gets one column of vertical cards (thumbnail across the
 * width), MEDIUM and EXPANDED a grid of vertical cards at least ~280 dp wide (or, with [grid] false, a centred list of
 * horizontal rows, which is what search, playlists and history use everywhere). Loading, empty and error states replace
 * the items; [header] and [footer] sit above and below.
 */
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
) {
    val size = LocalWindowSize.current
    val extras = rememberCardExtras(services, videos)
    val padding = Vela.contentPadding
    val open = { v: VideoSummary -> navigator.push(Route.Video(v)) }
    val channel = { v: VideoSummary -> navigator.push(Route.Channel(v.channelId, v.channelName)) }

    if (grid && size != WindowSizeClass.COMPACT) {
        LazyVerticalGrid(
            GridCells.Adaptive(minSize = 280.dp), modifier.fillMaxSize().testTag(testTag), contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(Vela.md), verticalArrangement = Arrangement.spacedBy(Vela.lg),
        ) {
            header?.let { h -> item(span = { GridItemSpan(maxLineSpan) }) { h() } }
            stateItems(state) { SkeletonCard(vertical = true) }
            items(videos, key = { it.id }) { v -> VideoCard(v, { open(v) }, style = VideoCardStyle.VERTICAL, extras = extras(v), onChannelClick = { channel(v) }, trailing = trailing?.let { t -> { t(v) } }) }
            footer?.let { f -> item(span = { GridItemSpan(maxLineSpan) }) { f() } }
        }
    } else {
        val vertical = grid // a COMPACT feed uses vertical cards; list screens use horizontal rows
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = if (vertical) Int.MAX_VALUE.dp else 900.dp).fillMaxSize().testTag(testTag),
                contentPadding = padding, verticalArrangement = Arrangement.spacedBy(if (vertical) Vela.lg else Vela.md),
            ) {
                header?.let { h -> item { h() } }
                stateItems(state) { SkeletonCard(vertical = vertical) }
                items(videos, key = { it.id }) { v ->
                    VideoCard(v, { open(v) }, style = if (vertical) VideoCardStyle.VERTICAL else VideoCardStyle.HORIZONTAL, extras = extras(v), onChannelClick = { channel(v) }, trailing = trailing?.let { t -> { t(v) } })
                }
                footer?.let { f -> item { f() } }
            }
        }
    }
}

private fun LazyListScope.stateItems(state: ListState, skeleton: @Composable () -> Unit) {
    when (state) {
        ListState.Loading -> items(6) { skeleton() }
        ListState.Ready -> {}
        is ListState.Empty -> item { EmptyState(state.message, actionLabel = state.actionLabel, onAction = state.onAction) }
        is ListState.Error -> item { ErrorRow(state.message, state.onRetry) }
    }
}

private fun LazyGridScope.stateItems(state: ListState, skeleton: @Composable () -> Unit) {
    when (state) {
        ListState.Loading -> items(6) { skeleton() }
        ListState.Ready -> {}
        is ListState.Empty -> item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(state.message, actionLabel = state.actionLabel, onAction = state.onAction) }
        is ListState.Error -> item(span = { GridItemSpan(maxLineSpan) }) { ErrorRow(state.message, state.onRetry) }
    }
}
