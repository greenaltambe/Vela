package com.greenaltambe.vela.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import com.greenaltambe.vela.core.settings.NavTab
import com.greenaltambe.vela.core.settings.MiniLook
import com.greenaltambe.vela.core.settings.NavLabels
import com.greenaltambe.vela.ui.look.pressable
import com.greenaltambe.vela.ui.look.VelaMotion
import com.greenaltambe.vela.ui.look.glassEnabled
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.lerp
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.greenaltambe.vela.ui.player.playerVerticalSwipe
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.greenaltambe.vela.extractor.Chapter
import com.greenaltambe.vela.extractor.Comment
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.library.formatTimestamp
import com.greenaltambe.vela.ui.look.ComponentIds
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.pressFeedback
import com.greenaltambe.vela.ui.look.popOnSelect
import com.greenaltambe.vela.ui.look.toColor
import com.greenaltambe.vela.ui.nav.Route
import kotlin.time.Clock

enum class VideoCardStyle {
    /** Thumbnail across the full width, text below: phones. */
    VERTICAL,

    /** A thumbnail beside the text: lists (search, playlists, history) and wider windows. */
    HORIZONTAL,
}

/** Everything a card can say beyond the video itself. */
data class CardExtras(
    /** 0..1 of the video already watched, drawn as a thin bar along the thumbnail's bottom edge. */
    val watched: Float? = null,
    val downloaded: Boolean = false,
    /** Shown after the view count (RYD numbers). */
    val badge: String? = null,
)

/**
 * A video in any list. The thumbnail's corners, spacing and text come from the active Look. Titles take at most two
 * lines and everything else one, so a card is never taller than its content. A long press shows the original
 * title/thumbnail when DeArrow replaced them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCard(
    video: VideoSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: VideoCardStyle = VideoCardStyle.HORIZONTAL,
    extras: CardExtras = CardExtras(),
    onChannelClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    /** False on a channel's own page, where every video is by that channel. */
    showChannelAvatar: Boolean = true,
) {
    var showOriginal by remember(video.id) { mutableStateOf(false) }
    val replaced = video.originalTitle != null || video.originalThumbnailUrl != null
    val title = if (showOriginal) video.originalTitle ?: video.title else video.title
    val thumbnail = if (showOriginal) video.originalThumbnailUrl ?: video.thumbnailUrl else video.thumbnailUrl
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    // Mouse over a video (desktop): a palette-gradient outline and a faint wash, rounded like the cards.
    val hovered by source.collectIsHoveredAsState()
    val hover = if (hovered) Modifier.border(2.dp, Vela.wake, Vela.mediumShape).background(Vela.colors.onSurface.toColor().copy(alpha = 0.05f), Vela.mediumShape) else Modifier
    val click = hover.clip(Vela.mediumShape).hoverable(source).pressFeedback(source).combinedClickable(interactionSource = source, indication = null, onClick = onClick, onLongClick = if (replaced) ({ showOriginal = !showOriginal }) else null)

    if (style == VideoCardStyle.VERTICAL) {
        Column(modifier.fillMaxWidth().testTag(ComponentIds.VIDEO_CARD).then(click), verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
            Thumbnail(video, thumbnail, extras, Modifier.fillMaxWidth().clip(Vela.thumbnailShape))
            Row(horizontalArrangement = Arrangement.spacedBy(Vela.sm), verticalAlignment = Alignment.Top) {
                if (showChannelAvatar) Avatar(video.channelName, onChannelClick, url = video.channelAvatarUrl)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("card.title"))
                    // Channel on its own line (it can be very long), the numbers under it: neither pushes the other off.
                    if (video.channelName.isNotBlank()) Text(video.channelName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Vela.colors.onSurfaceVariant.toColor(), modifier = Modifier.testTag("card.channel"))
                    val stats = remember(video.viewCount, video.uploadedAt, video.publishedText, extras.badge, showOriginal) { listOfNotNull(formatViews(video.viewCount), (formatAge(video.uploadedAt, Clock.System.now()) ?: video.publishedText), extras.badge, if (showOriginal) "original" else null).joinToString(" · ") }
                    if (stats.isNotEmpty()) Text(stats, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Vela.colors.onSurfaceVariant.toColor(), modifier = Modifier.testTag("card.meta"))
                }
                trailing?.invoke()
            }
        }
    } else {
        Row(modifier.fillMaxWidth().testTag(ComponentIds.VIDEO_CARD).then(click), horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
            // 36% of the row, but never wider than 168 dp: on a 320 dp phone the text (beside the menu button) still keeps 120 dp.
            Thumbnail(video, thumbnail, extras, Modifier.fillMaxWidth(0.36f).widthIn(max = 168.dp).clip(Vela.thumbnailShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("card.title"))
                if (video.channelName.isNotBlank()) Text(
                    video.channelName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = Vela.colors.onSurfaceVariant.toColor(),
                    modifier = Modifier.testTag("card.channel"),
                )
                val stats = remember(video.viewCount, video.uploadedAt, video.publishedText, extras.badge, showOriginal) { listOfNotNull(formatViews(video.viewCount), (formatAge(video.uploadedAt, Clock.System.now()) ?: video.publishedText), extras.badge, if (showOriginal) "original" else null).joinToString(" · ") }
                if (stats.isNotEmpty()) Text(stats, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Vela.colors.onSurfaceVariant.toColor(), modifier = Modifier.testTag("card.meta"))
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun Thumbnail(video: VideoSummary, url: String?, extras: CardExtras, modifier: Modifier) {
    Box(modifier.aspectRatio(16f / 9f).background(Vela.colors.surfaceVariant.toColor()).testTag("card.thumbnail")) {
        // Some feeds (Home recommendations, account playlists) arrive without a picture; YouTube serves one for every video id.
        val shown = url ?: if (video.id.isNotBlank()) "https://i.ytimg.com/vi/${video.id}/mqdefault.jpg" else null
        if (shown != null) AsyncImage(shown, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        if (video.isLive) Badge("LIVE", Vela.colors.error.toColor(), Modifier.align(Alignment.TopStart).padding(Vela.xs))
        else if (video.isShort) Badge("SHORT", Vela.colors.primary.toColor(), Modifier.align(Alignment.TopStart).padding(Vela.xs))
        formatDuration(video.durationSeconds)?.takeIf { !video.isLive }?.let { Badge(it, Vela.colors.background.toColor().copy(alpha = 0.85f), Modifier.align(Alignment.BottomEnd).padding(Vela.xs), textColor = Vela.colors.onBackground.toColor()) }
        if (extras.downloaded) Icon(
            Vela.icons.downloaded, "Downloaded",
            Modifier.align(Alignment.TopEnd).padding(Vela.xs).size(20.dp).testTag("card.downloaded"), tint = Vela.colors.primary.toColor(),
        )
        extras.watched?.takeIf { it > 0f }?.let { w ->
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Vela.colors.outline.toColor())) {
                Box(Modifier.fillMaxWidth(w.coerceIn(0f, 1f)).fillMaxHeight().background(Vela.wake).testTag("card.watched"))
            }
        }
    }
}

@Composable
private fun Badge(text: String, background: androidx.compose.ui.graphics.Color, modifier: Modifier, textColor: androidx.compose.ui.graphics.Color = Vela.colors.onMedia.toColor()) {
    Text(
        text, modifier.clip(Vela.smallShape).background(background).padding(horizontal = Vela.xs),
        style = MaterialTheme.typography.bodySmall, color = textColor, maxLines = 1, softWrap = false,
    )
}

/** The channel's picture; until avatars are available it is the first letter on the surface colour. */
@Composable
fun Avatar(name: String, onClick: (() -> Unit)? = null, size: Dp = 36.dp, url: String? = null) {
    Box(
        // The touch target is the full minimum size, but the press highlight is clipped to the circle (clip comes before clickable).
        Modifier.let { if (onClick != null) it.minimumInteractiveComponentSize() else it }
            .size(size).clip(Vela.pillShape).let { if (onClick != null) it.clickable(onClick = onClick) else it }.background(Vela.colors.surfaceVariant.toColor()),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) AsyncImage(url, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        else Text(name.trim().take(1).uppercase(), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

@Composable
fun ChannelRow(name: String, subtitle: String?, avatarUrl: String?, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().heightIn(min = Vela.minTouch).clip(Vela.mediumShape).testTag(ComponentIds.CHANNEL_ROW).clickable(onClick = onClick).padding(horizontal = Vela.sm),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.md),
    ) {
        Avatar(name, url = avatarUrl, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Vela.colors.onSurfaceVariant.toColor())
        }
        trailing?.invoke()
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = Vela.minTouch).testTag(ComponentIds.SECTION_HEADER), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        action?.invoke()
    }
}

@Composable
fun CommentItem(comment: Comment, replies: List<Comment>?, repliesLoading: Boolean, onToggleReplies: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag(ComponentIds.COMMENT_ITEM), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
            Text(comment.author, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (comment.publishedText.isNotEmpty()) Text(comment.publishedText, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, color = Vela.colors.onSurfaceVariant.toColor())
            if (comment.isPinned) Text("Pinned", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, color = Vela.colors.primary.toColor())
        }
        Text(comment.text, style = MaterialTheme.typography.bodyMedium, maxLines = 12, overflow = TextOverflow.Ellipsis)
        val likes = formatCount(comment.likeCount.takeIf { it > 0 })
        if (likes != null) Text("$likes likes", style = MaterialTheme.typography.bodySmall, maxLines = 1, color = Vela.colors.onSurfaceVariant.toColor())
        if (comment.replyCount > 0 && comment.repliesContinuation != null) {
            TextButton(onClick = onToggleReplies) {
                Text(if (replies != null) "Hide replies" else if (repliesLoading) "Loading replies..." else "${comment.replyCount} replies", maxLines = 1)
            }
        }
        replies?.forEach { reply ->
            Column(Modifier.padding(start = Vela.lg)) {
                Text(reply.author, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Vela.colors.onSurfaceVariant.toColor())
                Text(reply.text, style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun ChapterRow(chapter: Chapter, current: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = Vela.minTouch).clip(Vela.mediumShape).testTag(ComponentIds.CHAPTER_ROW).clickable(onClick = onClick).padding(horizontal = Vela.sm),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.md),
    ) {
        Text(formatTimestamp(chapter.startSeconds), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, color = Vela.colors.primary.toColor())
        Text(chapter.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (current) Vela.colors.primary.toColor() else Vela.colors.onSurface.toColor())
    }
}

/** What a tab shows in the bar and the rail. Labels are short enough for a 320 dp bar. */
data class NavEntry(val tab: NavTab, val label: String)

fun navEntries(tabs: List<NavTab>) = tabs.map { NavEntry(it, when (it) {
    NavTab.HOME -> "Home"; NavTab.SHORTS -> "Shorts"; NavTab.SUBSCRIPTIONS -> "Subs"; NavTab.LIBRARY -> "Library"; NavTab.SEARCH -> "Search"; NavTab.DOWNLOADS -> "Downloads"; NavTab.EXPLORE -> "Explore"
}) }

@Composable
private fun NavEntry.icon(): ImageVector = when (tab) {
    NavTab.HOME -> Vela.icons.home
    NavTab.SHORTS -> Vela.icons.bolt
    NavTab.SUBSCRIPTIONS -> Vela.icons.subscriptions
    NavTab.LIBRARY -> Vela.icons.library
    NavTab.SEARCH -> Vela.icons.search
    NavTab.DOWNLOADS -> Vela.icons.download
    NavTab.EXPLORE -> Vela.icons.explore
}

/** The line that marks the selected tab: the palette gradient. */
@Composable
private fun SelectedMarker(modifier: Modifier = Modifier) {
    Box(modifier.height(2.dp).width(Vela.lg).clip(Vela.pillShape).background(Vela.wake).testTag("nav.marker"))
}

/**
 * The tab bar. [shrink] (0..1) is how far it has contracted: at 0 the tabs share the full width with their labels; at 1 it is a small
 * pill of icons only. Everything between is the same layout evaluated at that fraction (width, height, spacing between the icons and the
 * labels' height and opacity all interpolate), so the contraction is one smooth motion with the icons moving together, never a switch
 * between two layouts. The caller decides where it sits and gives it a [shape]; its surface is the bar tint (at least the Look's bar
 * opacity when glass is on), so page text never reads through it.
 */
@Composable
fun BottomNav(entries: List<NavEntry>, selected: NavTab?, labelsVisible: Boolean, marker: Boolean = true, shrink: Float, shape: androidx.compose.ui.graphics.Shape, onSelect: (NavTab) -> Unit, modifier: Modifier = Modifier, bottomInset: Boolean = false) {
    val tint = if (glassEnabled()) Vela.look.effects.barTintAlpha else 1f
    val surface = Vela.colors.surface.toColor().copy(alpha = tint)
    val n = entries.size.coerceAtLeast(1)
    val f = shrink.coerceIn(0f, 1f)
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val pad = Vela.xs
        val iconGap = Vela.xs * f
        val compactWidth = Vela.minTouch * n + Vela.xs * (n - 1) + pad * 2
        val barWidth = lerp(maxWidth, compactWidth, f)
        val itemWidth = (barWidth - pad * 2 - iconGap * (n - 1)) / n
        val fullHeight = if (labelsVisible) Vela.minTouch + Vela.md else Vela.minTouch + Vela.xs
        val itemHeight = lerp(fullHeight, Vela.minTouch, f) - pad * 2   // the bar's own padding above and below keeps its overall height
        Row(
            Modifier.testTag(ComponentIds.BOTTOM_NAV).width(barWidth).clip(shape).background(surface)
                // A docked bar is painted behind the gesture area, so its content keeps clear of it.
                .then(if (bottomInset) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)) else Modifier).padding(pad),
            horizontalArrangement = Arrangement.spacedBy(iconGap), verticalAlignment = Alignment.CenterVertically,
        ) {
            // The highlight sits one inset inside the bar's edge, so its corner is the bar's minus that inset (concentric, not a different curve).
            val highlightShape = RoundedCornerShape((Vela.look.radii.largeDp.dp - pad - Vela.xs / 2).coerceAtLeast(0.dp))
            entries.forEach { e ->
                val isSelected = e.tab == selected
                Column(
                    Modifier.width(itemWidth).height(itemHeight).padding(Vela.xs / 2).clip(highlightShape)
                        .background(if (isSelected) Vela.colors.surfaceVariant.toColor() else androidx.compose.ui.graphics.Color.Transparent)
                        .semantics { contentDescription = e.label; this.selected = isSelected }.pressable(role = Role.Tab) { onSelect(e.tab) }.testTag("nav.tab.${e.tab.name}"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(e.icon(), null, tint = if (isSelected) Vela.colors.onSurface.toColor() else Vela.colors.onSurfaceVariant.toColor())
                    // The label's room and opacity fade out as the bar contracts; it is gone from the tree once the pill is complete.
                    if (labelsVisible && f < 0.99f) Box(Modifier.height(NAV_LABEL_HEIGHT * (1f - f)).alpha(1f - f), contentAlignment = Alignment.TopCenter) {
                        Text(e.label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.bodySmall, color = if (isSelected) Vela.colors.onSurface.toColor() else Vela.colors.onSurfaceVariant.toColor(), modifier = Modifier.wrapContentHeight(unbounded = true, align = Alignment.Top).testTag("nav.label"))
                    }
                    if (marker) { if (isSelected) SelectedMarker() else Spacer(Modifier.height(2.dp)) }
                }
            }
        }
    }
}

/** The height reserved for a tab's label while the bar is full size. */
private val NAV_LABEL_HEIGHT = 18.dp

/**
 * The left rail on wide windows and Linux: the same tab list, collapsible to icons. Keyboard: Tab moves between items (a ring shows
 * the focus), Enter opens one; hovering or focusing a collapsed item shows its name as a tooltip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SideRail(entries: List<NavEntry>, selected: NavTab?, collapsed: Boolean, marker: Boolean = true, onToggleCollapsed: () -> Unit, onSelect: (NavTab) -> Unit, modifier: Modifier = Modifier) {
    val width by androidx.compose.animation.core.animateDpAsState(if (collapsed) 72.dp else 176.dp, VelaMotion.standard(Vela.motion), label = "railWidth")
    Column(
        modifier.fillMaxHeight().width(width).background(Vela.colors.surface.toColor()).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
            .padding(horizontal = Vela.xs, vertical = Vela.sm).testTag(ComponentIds.SIDE_RAIL),
        verticalArrangement = Arrangement.spacedBy(Vela.xs),
    ) {
        Box(Modifier.heightIn(min = Vela.minTouch).widthIn(min = Vela.minTouch).clip(Vela.mediumShape).semantics { contentDescription = if (collapsed) "Expand the rail" else "Collapse the rail" }.pressable(role = Role.Button, onClick = onToggleCollapsed).testTag("rail.toggle"), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(if (collapsed) com.greenaltambe.vela.ui.look.Vela.icons.chevronRight else com.greenaltambe.vela.ui.look.Vela.icons.chevronLeft, null)
        }
        entries.forEach { e ->
            val isSelected = e.tab == selected
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { if (collapsed) PlainTooltip { Text(e.label) } },
                state = rememberTooltipState(),
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Vela.minTouch).clip(Vela.mediumShape)
                        .background(if (isSelected) Vela.colors.surfaceVariant.toColor() else androidx.compose.ui.graphics.Color.Transparent)
                        .semantics { contentDescription = e.label; this.selected = isSelected }.pressable(role = Role.Tab) { onSelect(e.tab) }.padding(horizontal = Vela.sm).testTag("rail.tab.${e.tab.name}"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm),
                ) {
                    // A short upright gradient bar at the row's left edge (a horizontal line does not fit a side rail); the space is kept when off or unselected.
                    if (marker) Box(Modifier.width(3.dp).height(20.dp).clip(Vela.pillShape).then(if (isSelected) Modifier.background(Vela.wake).testTag("nav.marker") else Modifier))
                    Icon(e.icon(), null, tint = if (isSelected) Vela.colors.onSurface.toColor() else Vela.colors.onSurfaceVariant.toColor())
                    if (!collapsed) Text(e.label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false).testTag("nav.label"))
                }
            }
        }
    }
}

/** One entry of a top bar's overflow menu. */
class MenuAction(val label: String, val onClick: () -> Unit)

/**
 * The bar at the top of every screen: back (when there is somewhere to go back to), the title on one line, search, and
 * an overflow menu. Insets for the status bar and display cutout are applied here, so content below never needs them.
 */
@Composable
fun VelaTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    menu: List<MenuAction> = emptyList(),
    actions: @Composable () -> Unit = {},
    /** The profile button (avatar), at the right end; shown only where there is no Back button, where the app's mark takes the left. */
    profile: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .heightIn(min = 56.dp).padding(horizontal = Vela.xs).testTag("topbar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.testTag("nav.back")) { Icon(Vela.icons.back, "Back") }
        else if (profile != null) com.greenaltambe.vela.ui.look.VelaMark(Modifier.padding(start = Vela.xs).testTag("topbar.logo"), size = 32.dp)
        else Spacer(Modifier.size(Vela.sm))
        Text(title, Modifier.weight(1f).padding(horizontal = Vela.xs), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
        actions()
        if (onSearch != null) IconButton(onClick = onSearch, modifier = Modifier.testTag("nav.search")) { Icon(Vela.icons.search, "Search") }
        if (menu.isNotEmpty()) {
            var open by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { open = true }, modifier = Modifier.testTag("nav.overflow")) { Icon(Vela.icons.more, "More options") }
                DropdownMenu(open, { open = false }) {
                    menu.forEach { a -> DropdownMenuItem(text = { Text(a.label, maxLines = 1) }, onClick = { open = false; a.onClick() }) }
                }
            }
        }
        if (onBack == null && profile != null) profile()
    }
}

/** What the mini player shows and does; built once from the player host and used by every layout (bar over/under the page, wide rail). */
class MiniState(
    val title: String, val thumbnailUrl: String?, val playing: Boolean, val progress: Float,
    val onToggle: () -> Unit, val onExpand: () -> Unit, val onClose: () -> Unit,
    /** Draws the playing video into the given box (the mini player shows the live picture, not just the thumbnail); null shows the thumbnail only. */
    val surface: (@Composable (Modifier) -> Unit)? = null,
)

/**
 * The mini player while something plays and its page is closed. REGULAR (CARD) is a bar-wide card: thumbnail, title, play/pause,
 * close and a thin progress line. PIP is a small picture-in-picture style card (the thumbnail with the controls on it). Both take
 * their [shape] from the corner-radius setting (the caller passes it, because attached to the bar only the outer corners are round).
 */
@Composable
fun MiniPlayer(state: MiniState, look: MiniLook, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier = Modifier) {
    when (look) {
        MiniLook.CARD -> {
          // Swiping up on the card opens the video page (like a tap, but the card follows the finger a little first).
          var lift by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
          val openAt = with(androidx.compose.ui.platform.LocalDensity.current) { 40.dp.toPx() }
          Column(
            modifier.graphicsLayer { translationY = lift * 0.6f }
                .playerVerticalSwipe(
                    onDelta = { dy -> lift = (lift + dy).coerceAtMost(0f) },
                    onEnd = { total, v -> lift = 0f; if (total < -openAt || v < -900f) state.onExpand() },
                )
                .clip(shape).background(Vela.colors.surfaceVariant.toColor()).testTag(ComponentIds.MINI_PLAYER),
          ) {
            Row(Modifier.fillMaxWidth().clickable(onClick = state.onExpand).padding(start = Vela.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
                Box(Modifier.size(width = 64.dp, height = 36.dp).clip(Vela.smallShape).background(Vela.colors.mediaBackground.toColor())) {
                    if (state.thumbnailUrl != null) AsyncImage(state.thumbnailUrl, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    state.surface?.invoke(Modifier.matchParentSize())
                }
                Text(state.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = state.onToggle, modifier = Modifier.testTag("mini.toggle")) { Icon(if (state.playing) Vela.icons.pause else Vela.icons.play, if (state.playing) "Pause" else "Play") }
                IconButton(onClick = state.onClose, modifier = Modifier.testTag("mini.close")) { Icon(Vela.icons.close, "Close player") }
            }
            // The progress line is the card's bottom edge.
            Box(Modifier.fillMaxWidth().height(2.dp).background(Vela.colors.outline.toColor())) {
                Box(Modifier.fillMaxWidth(state.progress.coerceIn(0f, 1f)).fillMaxHeight().background(Vela.wake))
            }
          }
        }
        MiniLook.PIP -> Box(modifier.size(PIP_WIDTH, PIP_WIDTH * 9 / 16).clip(shape).background(Vela.colors.mediaBackground.toColor()).clickable(onClick = state.onExpand).testTag(ComponentIds.MINI_PLAYER)) {
            if (state.thumbnailUrl != null) AsyncImage(state.thumbnailUrl, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            state.surface?.invoke(Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Vela.colors.mediaScrim.toColor().copy(alpha = 0.35f)))
            IconButton(onClick = state.onToggle, modifier = Modifier.align(Alignment.Center).testTag("mini.toggle")) { Icon(if (state.playing) Vela.icons.pause else Vela.icons.play, if (state.playing) "Pause" else "Play", tint = Vela.colors.onMedia.toColor()) }
            IconButton(onClick = state.onClose, modifier = Modifier.align(Alignment.TopEnd).testTag("mini.close")) { Icon(Vela.icons.close, "Close player", tint = Vela.colors.onMedia.toColor()) }
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Vela.colors.outline.toColor().copy(alpha = 0.5f))) {
                Box(Modifier.fillMaxWidth(state.progress.coerceIn(0f, 1f)).fillMaxHeight().background(Vela.wake))
            }
        }
    }
}

/** The picture-in-picture style mini player is this wide (16:9). */
val PIP_WIDTH = 176.dp

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    // The palette gradient is the button's fill, darkened just enough for white text to read on every part of it.
    val onGradient = Vela.onButton
    Button(
        onClick = onClick, modifier = modifier.pressFeedback(source).alpha(if (enabled) 1f else 0.4f).background(Vela.buttonFill, Vela.buttonShape), enabled = enabled, shape = Vela.buttonShape, interactionSource = source,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.Transparent, disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent, contentColor = onGradient, disabledContentColor = onGradient),
    ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** The quieter button next to a [PrimaryButton]: an outline in the Look's button shape, so a row of options all look like one family. */
@Composable
fun SecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    androidx.compose.material3.OutlinedButton(
        onClick = onClick, modifier = modifier.pressFeedback(source), enabled = enabled, shape = Vela.buttonShape, interactionSource = source,
        border = androidx.compose.foundation.BorderStroke(1.dp, Vela.colors.outline.toColor()),
    ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** An icon with a short label under it: the video page's action row (Download, Save, Share). */
@Composable
fun ActionChip(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    Column(
        modifier.heightIn(min = Vela.minTouch).widthIn(min = Vela.minTouch).clip(Vela.mediumShape).clickable(onClick = onClick).padding(horizontal = Vela.sm, vertical = Vela.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.popOnSelect(selected), tint = if (selected) Vela.colors.primary.toColor() else Vela.colors.onSurface.toColor())
        Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}

/**
 * A text link-style button that starts exactly at the content edge (Material's text button centres its label in a
 * 48 dp box, which leaves it indented). The touch area is still at least 48 dp each way.
 */
@Composable
fun LinkButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    // Clipped to the Look's radius and padded, so the press highlight is a rounded patch around the text, not a bare rectangle.
    Box(modifier.heightIn(min = Vela.minTouch).widthIn(min = Vela.minTouch).clip(Vela.mediumShape).clickable(enabled = enabled, onClick = onClick).padding(horizontal = Vela.sm), contentAlignment = Alignment.CenterStart) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = (if (enabled) Vela.colors.primary else Vela.colors.onSurfaceVariant).toColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
