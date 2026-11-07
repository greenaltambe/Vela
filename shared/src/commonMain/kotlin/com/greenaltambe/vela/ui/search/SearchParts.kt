@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.greenaltambe.vela.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.greenaltambe.vela.extractor.DurationFilter
import com.greenaltambe.vela.extractor.SearchFilters
import com.greenaltambe.vela.extractor.SearchSort
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.ui.components.ChipRow
import com.greenaltambe.vela.ui.components.formatViews
import com.greenaltambe.vela.ui.settings.segmentedShape
import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.Spacing

/** What the result chips choose between. All is the mixed page, the others are views of one kind. */
enum class SearchType(val label: String, val tag: String) {
    ALL("All", "all"), VIDEOS("Videos", "videos"), SHORTS("Shorts", "shorts"), LIVE("Live", "live"), CHANNELS("Channels", "channels"), PLAYLISTS("Playlists", "playlists"),
}

/** One row of expressive toggle buttons, a check on the chosen one. */
@Composable
fun SearchTypeChips(type: SearchType, types: List<SearchType>, onType: (SearchType) -> Unit, modifier: Modifier = Modifier) {
    ChipRow(modifier.testTag("search.filters"), bleed = false) {
        types.forEach { t ->
            item(key = t.name) { com.greenaltambe.vela.ui.components.ExpressiveChip(type == t, t.label, { onType(t) }, Modifier.testTag("search.filter.${t.tag}")) }
        }
    }
}

/** The tune button at the end of the search bar; a dot shows that a filter is on. */
@Composable
fun FilterTuneButton(active: Boolean, onClick: () -> Unit) {
    Box {
        IconButton(onClick = onClick, modifier = Modifier.testTag("search.filter.tune")) { Icon(AppIcons.tune, "Filters") }
        if (active) Box(Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).testTag("search.filter.active"))
    }
}

/** Length and order, as the choices they are: segmented buttons for the length, a radio list for the order. */
@Composable
fun SearchFilterSheet(filters: SearchFilters, onChange: (SearchFilters) -> Unit, onDismiss: () -> Unit) {
    com.greenaltambe.vela.ui.components.VelaSheet(onDismiss, Modifier.testTag("search.filter.sheet")) {
        Column(Modifier.padding(horizontal = Spacing.gutter).padding(bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { onChange(SearchFilters(shortsOnly = filters.shortsOnly)) }, enabled = !filters.isDefault, modifier = Modifier.testTag("search.filter.reset")) { Text("Reset") }
            }
            Text("Length", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            val lengths = DurationFilter.entries
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                lengths.forEachIndexed { i, d ->
                    SegmentedButton(
                        selected = filters.duration == d, onClick = { onChange(filters.copy(duration = d)) },
                        shape = SegmentedButtonDefaults.itemShape(i, lengths.size), modifier = Modifier.testTag("search.filter.duration.${d.name}"),
                        label = { Text(when (d) { DurationFilter.ANY -> "Any"; DurationFilter.SHORT -> "< 4 min"; DurationFilter.MEDIUM -> "4-20 min"; DurationFilter.LONG -> "> 20 min" }, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
            Text("Order", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SearchSort.entries.forEachIndexed { i, s ->
                    Row(
                        Modifier.fillMaxWidth().clip(segmentedShape(i, SearchSort.entries.size)).background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { onChange(filters.copy(sort = s)) }.heightIn(min = 52.dp).padding(horizontal = Spacing.md).testTag("search.filter.sort.${s.name}"),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        RadioButton(selected = filters.sort == s, onClick = null)
                        Text(s.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

/** A row of a segmented stack: an icon, a line of text, and up to two trailing actions. */
@Composable
fun SearchTextRow(
    text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, index: Int, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().clip(segmentedShape(index, count)).background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(onClick = onClick)
            .heightIn(min = 56.dp).padding(start = Spacing.md, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
        trailing()
    }
}

/** Shorts as a two-column grid of tall rounded cards; asks for the next page 4 cards before the end. */
@Composable
fun ShortsGrid(videos: List<VideoSummary>, onOpen: (VideoSummary) -> Unit, onEndReached: () -> Unit, footer: (@Composable () -> Unit)? = null, modifier: Modifier = Modifier) {
    val state = rememberLazyGridState()
    val nearEnd by remember(state, videos.size) { derivedStateOf { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= videos.size - 4 } }
    LaunchedEffect(nearEnd, videos.size) { if (nearEnd) onEndReached() }
    LazyVerticalGrid(
        GridCells.Adaptive(150.dp), modifier.fillMaxSize().testTag("search.shorts.grid"), state = state, contentPadding = Spacing.contentPadding,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        items(videos, key = { it.id }, contentType = { "short" }) { v ->
            Column(Modifier.clip(MaterialTheme.shapes.large).clickable { onOpen(v) }.testTag("search.short"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box(Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    AsyncImage(v.thumbnailUrl ?: "https://i.ytimg.com/vi/${v.id}/hq720.jpg", null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Column(Modifier.padding(horizontal = Spacing.xs), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(v.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    formatViews(v.viewCount)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                }
            }
        }
        if (footer != null) item(span = { GridItemSpan(maxLineSpan) }) { footer() }
    }
}

/** The channel the search is about: a tonal card with its picture, name, subscribers and the two things you do next. */
@Composable
fun TopChannelCard(channel: com.greenaltambe.vela.extractor.ChannelRef, subscribed: Boolean, onSubscribe: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier, bell: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.secondaryContainer).clickable(onClick = onOpen).padding(Spacing.md).testTag("search.topChannel"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            com.greenaltambe.vela.ui.components.Avatar(channel.name, size = 72.dp, url = channel.avatarUrl)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(channel.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSecondaryContainer)
                com.greenaltambe.vela.ui.components.formatCount(channel.subscriberCount)?.let { Text("$it subscribers", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(Modifier.animateContentSize(), horizontalArrangement = Arrangement.spacedBy(androidx.compose.material3.ButtonGroupDefaults.ConnectedSpaceBetween)) {
                com.greenaltambe.vela.ui.components.SubscribeButton(subscribed, onSubscribe, Modifier.testTag("search.topChannel.subscribe"), position = if (subscribed && bell != null) com.greenaltambe.vela.ui.components.ActionPosition.LEADING else com.greenaltambe.vela.ui.components.ActionPosition.ALONE)
                androidx.compose.animation.AnimatedVisibility(subscribed && bell != null, enter = androidx.compose.animation.expandHorizontally(androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 380f)) + androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.6f), exit = androidx.compose.animation.shrinkHorizontally() + androidx.compose.animation.fadeOut()) { bell?.invoke() }
            }
            com.greenaltambe.vela.ui.components.SecondaryButton("Go to channel", onOpen, Modifier.weight(1f).testTag("search.topChannel.open"))
        }
    }
}
