package com.greenaltambe.vela.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.ui.layout.LocalWindowSize
import com.greenaltambe.vela.ui.layout.WindowSizeClass
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.shimmer
import com.greenaltambe.vela.ui.look.toColor

/**
 * The one way a row of things scrolls sideways in Vela: a title (and, when the row has a reason, a line under it), "See all" at the end of the
 * title line, then a row that runs through the screen's side gutters (so it visibly goes on past the edge) and settles on a card at a time.
 * [content] draws one item at the width the row asks for: on a phone 82 % of the page, so one large picture and the start of the next.
 */
@Composable
fun <T> Carousel(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onSeeAll: (() -> Unit)? = null,
    tag: String = "carousel",
    seeAllLabel: String = "See all",
    /** The width of one card when the screen does not decide it (Shorts are narrow). */
    fixedCardWidth: Dp? = null,
    content: @Composable (item: T, cardWidth: Dp) -> Unit,
) {
    val gutter = Vela.gutter
    val size = LocalWindowSize.current
    Column(modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
        Column {
            SectionHeader(title) {
                if (onSeeAll != null) TextButton(onClick = onSeeAll, modifier = Modifier.testTag("$tag.all")) { Text(seeAllLabel, maxLines = 1) }
            }
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = Vela.xs))
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = fixedCardWidth ?: when (size) { WindowSizeClass.COMPACT -> maxWidth * 0.82f; WindowSizeClass.MEDIUM -> 320.dp; WindowSizeClass.EXPANDED -> 340.dp }
            HScrollRow(Modifier.bleed(gutter).testTag("$tag.row"), Arrangement.spacedBy(Vela.md), PaddingValues(horizontal = gutter), snap = fixedCardWidth == null) {
                items(items, key = key) { item -> content(item, width) }
            }
        }
    }
}

/** A carousel of videos: the same card as the feed (picture, title, channel, numbers, the "..." menu), only at the carousel's width. */
@Composable
fun VideoCarousel(
    title: String,
    videos: List<VideoSummary>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onSeeAll: (() -> Unit)? = null,
    tag: String = "carousel",
    extras: (VideoSummary) -> CardExtras = { CardExtras() },
    onOpen: (VideoSummary) -> Unit,
    onChannel: ((VideoSummary) -> Unit)? = null,
    trailing: @Composable ((VideoSummary) -> Unit)? = null,
    /** False where the videos carry no channel (the watch history lists none): an empty circle would only be a gap. */
    showChannelAvatar: Boolean = true,
) {
    Carousel(title, videos, { it.id }, modifier, subtitle, onSeeAll, tag) { v, width ->
        VideoCard(
            v, { onOpen(v) }, Modifier.width(width).testTag("$tag.item"), style = VideoCardStyle.VERTICAL, extras = extras(v),
            onChannelClick = onChannel?.let { c -> { c(v) } }, trailing = trailing?.let { t -> { t(v) } }, showChannelAvatar = showChannelAvatar,
        )
    }
}

/** Shorts are tall: portrait cards about 150 dp wide (two and a bit on a phone), the title under the picture. */
@Composable
fun ShortsCarousel(
    title: String,
    videos: List<VideoSummary>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onSeeAll: (() -> Unit)? = null,
    tag: String = "carousel",
    onOpen: (VideoSummary) -> Unit,
) {
    Carousel(title, videos, { it.id }, modifier, subtitle, onSeeAll, tag, fixedCardWidth = 150.dp) { v, width ->
        Column(Modifier.width(width).clickable { onOpen(v) }.testTag("$tag.item"), verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
            Box(Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(Vela.thumbnailShape).background(Vela.colors.surfaceVariant.toColor())) {
                AsyncImage(v.thumbnailUrl ?: "https://i.ytimg.com/vi/${v.id}/hq720.jpg", null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Column(Modifier.padding(horizontal = Vela.sm).padding(bottom = Vela.xs), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(v.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (v.channelName.isNotBlank()) Text(v.channelName, style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                formatViews(v.viewCount)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor(), maxLines = 1) }
            }
        }
    }
}

/**
 * A carousel still loading: the header line, then cards built like the real ones (picture, avatar and two text lines; the same widths and
 * heights), shimmering like the list's own placeholders, so nothing jumps when the cards arrive and the screen has one loading look.
 */
@Composable
fun CarouselSkeleton(modifier: Modifier = Modifier, portrait: Boolean = false) {
    val gutter = Vela.gutter
    val size = LocalWindowSize.current
    Column(modifier.fillMaxWidth().testTag("carousel.skeleton"), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
        Box(Modifier.fillMaxWidth().heightIn(min = Vela.minTouch), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.width(160.dp).height(18.dp).clip(Vela.smallShape).shimmer())
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = if (portrait) 150.dp else when (size) { WindowSizeClass.COMPACT -> maxWidth * 0.82f; WindowSizeClass.MEDIUM -> 320.dp; WindowSizeClass.EXPANDED -> 340.dp }
            Row(Modifier.bleed(gutter).padding(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(Vela.md)) {
                repeat(if (portrait) 3 else 2) {
                    Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(if (portrait) 9f / 16f else 16f / 9f).clip(Vela.thumbnailShape).shimmer())
                        Row(Modifier.padding(horizontal = Vela.sm), horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
                            if (!portrait) Box(Modifier.size(36.dp).clip(Vela.pillShape).shimmer())
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
                                Box(Modifier.fillMaxWidth().height(14.dp).clip(Vela.smallShape).shimmer())
                                Box(Modifier.fillMaxWidth(0.6f).height(12.dp).clip(Vela.smallShape).shimmer())
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A strip of chips (filters, topics, sort orders): the same scroller as the carousels, running through the gutters, no snapping. */
@Composable
fun ChipRow(modifier: Modifier = Modifier, bleed: Boolean = true, content: LazyListScope.() -> Unit) {
    val gutter = Vela.gutter
    // [bleed]: the parent is padded by the gutter (a list's content padding), so the row reaches through it; otherwise the row sits in the full width and only pads itself.
    HScrollRow(if (bleed) modifier.bleed(gutter) else modifier, Arrangement.spacedBy(Vela.sm), PaddingValues(horizontal = gutter), content = content)
}

/** A row of channels (picture and name): same carousel header and scroller, narrow tiles. */
@Composable
fun ChannelCarousel(
    title: String,
    channels: List<com.greenaltambe.vela.data.SubscribedChannel>,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null,
    seeAllLabel: String = "See all",
    tag: String = "carousel",
    onOpen: (com.greenaltambe.vela.data.SubscribedChannel) -> Unit,
) {
    Carousel(title, channels, { it.channelId }, modifier, null, onSeeAll, tag, seeAllLabel, fixedCardWidth = 80.dp) { c, width ->
        Column(
            Modifier.width(width).clip(Vela.mediumShape).clickable { onOpen(c) }.padding(vertical = Vela.xs).testTag("$tag.item"),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Vela.xs),
        ) {
            Avatar(c.name, size = 64.dp, url = c.avatarUrl)
            Text(c.name, Modifier.padding(horizontal = Vela.xs), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
