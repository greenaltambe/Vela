package com.greenaltambe.vela.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
) {
    var showOriginal by remember(video.id) { mutableStateOf(false) }
    val replaced = video.originalTitle != null || video.originalThumbnailUrl != null
    val title = if (showOriginal) video.originalTitle ?: video.title else video.title
    val thumbnail = if (showOriginal) video.originalThumbnailUrl ?: video.thumbnailUrl else video.thumbnailUrl
    val click = Modifier.combinedClickable(onClick = onClick, onLongClick = if (replaced) ({ showOriginal = !showOriginal }) else null)

    if (style == VideoCardStyle.VERTICAL) {
        Column(modifier.fillMaxWidth().testTag(ComponentIds.VIDEO_CARD).then(click), verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
            Thumbnail(video, thumbnail, extras, Modifier.fillMaxWidth().clip(Vela.mediumShape))
            Row(horizontalArrangement = Arrangement.spacedBy(Vela.sm), verticalAlignment = Alignment.Top) {
                Avatar(video.channelName, onChannelClick)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("card.title"))
                    // Channel on its own line (it can be very long), the numbers under it: neither pushes the other off.
                    if (video.channelName.isNotBlank()) Text(video.channelName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Vela.colors.onSurfaceVariant.toColor(), modifier = Modifier.testTag("card.channel"))
                    val stats = listOfNotNull(formatViews(video.viewCount), formatAge(video.uploadedAt, Clock.System.now()), extras.badge, if (showOriginal) "original" else null).joinToString(" · ")
                    if (stats.isNotEmpty()) Text(stats, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Vela.colors.onSurfaceVariant.toColor(), modifier = Modifier.testTag("card.meta"))
                }
                trailing?.invoke()
            }
        }
    } else {
        Row(modifier.fillMaxWidth().testTag(ComponentIds.VIDEO_CARD).then(click), horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
            // 36% of the row, but never wider than 168 dp: on a 320 dp phone the text (beside the menu button) still keeps 120 dp.
            Thumbnail(video, thumbnail, extras, Modifier.fillMaxWidth(0.36f).widthIn(max = 168.dp).clip(Vela.mediumShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("card.title"))
                if (video.channelName.isNotBlank()) Text(
                    video.channelName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = Vela.colors.onSurfaceVariant.toColor(),
                    modifier = Modifier.testTag("card.channel"),
                )
                val stats = listOfNotNull(formatViews(video.viewCount), formatAge(video.uploadedAt, Clock.System.now()), extras.badge, if (showOriginal) "original" else null).joinToString(" · ")
                if (stats.isNotEmpty()) Text(stats, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Vela.colors.onSurfaceVariant.toColor(), modifier = Modifier.testTag("card.meta"))
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun Thumbnail(video: VideoSummary, url: String?, extras: CardExtras, modifier: Modifier) {
    Box(modifier.aspectRatio(16f / 9f).background(Vela.colors.surfaceVariant.toColor()).testTag("card.thumbnail")) {
        if (url != null) AsyncImage(url, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        if (video.isLive) Badge("LIVE", Vela.colors.error.toColor(), Modifier.align(Alignment.TopStart).padding(Vela.xs))
        else if (video.isShort) Badge("SHORT", Vela.colors.primary.toColor(), Modifier.align(Alignment.TopStart).padding(Vela.xs))
        formatDuration(video.durationSeconds)?.takeIf { !video.isLive }?.let { Badge(it, Vela.colors.background.toColor().copy(alpha = 0.85f), Modifier.align(Alignment.BottomEnd).padding(Vela.xs), textColor = Vela.colors.onBackground.toColor()) }
        if (extras.downloaded) Icon(
            Vela.icons.downloaded, "Downloaded",
            Modifier.align(Alignment.TopEnd).padding(Vela.xs).size(20.dp).testTag("card.downloaded"), tint = Vela.colors.primary.toColor(),
        )
        extras.watched?.takeIf { it > 0f }?.let { w ->
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Vela.colors.outline.toColor())) {
                Box(Modifier.fillMaxWidth(w.coerceIn(0f, 1f)).fillMaxHeight().background(Vela.colors.error.toColor()).testTag("card.watched"))
            }
        }
    }
}

@Composable
private fun Badge(text: String, background: androidx.compose.ui.graphics.Color, modifier: Modifier, textColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.White) {
    Text(
        text, modifier.clip(Vela.smallShape).background(background).padding(horizontal = Vela.xs),
        style = MaterialTheme.typography.bodySmall, color = textColor, maxLines = 1, softWrap = false,
    )
}

/** The channel's picture; until avatars are available it is the first letter on the surface colour. */
@Composable
fun Avatar(name: String, onClick: (() -> Unit)? = null, size: Dp = 36.dp, url: String? = null) {
    Box(
        Modifier.let { if (onClick != null) it.minimumInteractiveComponentSize().clickable(onClick = onClick) else it }
            .size(size).clip(Vela.pillShape).background(Vela.colors.surfaceVariant.toColor()),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) AsyncImage(url, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        else Text(name.trim().take(1).uppercase(), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

@Composable
fun ChannelRow(name: String, subtitle: String?, avatarUrl: String?, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().heightIn(min = Vela.minTouch).testTag(ComponentIds.CHANNEL_ROW).clickable(onClick = onClick),
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
        modifier.fillMaxWidth().heightIn(min = Vela.minTouch).testTag(ComponentIds.CHAPTER_ROW).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.md),
    ) {
        Text(formatTimestamp(chapter.startSeconds), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, color = Vela.colors.primary.toColor())
        Text(chapter.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (current) Vela.colors.primary.toColor() else Vela.colors.onSurface.toColor())
    }
}

/** What the bottom bar and side rail show for a top-level [Route]. Labels are short enough for a 320 dp bar. */
data class NavItem(val route: Route, val label: String)

val NavItems = listOf(
    NavItem(Route.Home, "Home"),
    NavItem(Route.Subscriptions, "Subs"),
    NavItem(Route.Library, "Library"),
    NavItem(Route.Settings, "Settings"),
)

@Composable
private fun NavItem.icon(): ImageVector = when (route) {
    Route.Home -> Vela.icons.home
    Route.Subscriptions -> Vela.icons.subscriptions
    Route.Library -> Vela.icons.library
    else -> Vela.icons.settings
}

@Composable
fun BottomNav(selected: Route, onSelect: (Route) -> Unit, modifier: Modifier = Modifier) {
    NavigationBar(modifier.testTag(ComponentIds.BOTTOM_NAV)) {
        NavItems.forEach { item ->
            NavigationBarItem(
                selected = item.route == selected, onClick = { onSelect(item.route) },
                icon = { Icon(item.icon(), null) },
                label = { Text(item.label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("nav.label")) },
                modifier = Modifier.semantics { contentDescription = item.label },
            )
        }
    }
}

@Composable
fun SideRail(selected: Route, onSelect: (Route) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    NavigationRail(modifier.testTag(ComponentIds.SIDE_RAIL).fillMaxHeight()) {
        NavigationRailItem(selected = false, onClick = onSearch, icon = { Icon(Vela.icons.search, null) }, label = { Text("Search", maxLines = 1, softWrap = false, style = MaterialTheme.typography.bodySmall) }, modifier = Modifier.testTag("rail.search"))
        Spacer(Modifier.size(Vela.sm))
        NavItems.forEach { item ->
            NavigationRailItem(
                selected = item.route == selected, onClick = { onSelect(item.route) },
                icon = { Icon(item.icon(), null) },
                label = { Text(item.label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("nav.label")) },
            )
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
) {
    Row(
        modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .heightIn(min = 56.dp).padding(horizontal = Vela.xs).testTag("topbar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.testTag("nav.back")) { Icon(Vela.icons.back, "Back") }
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
    }
}

/** Docked above the nav while something plays and its page is closed: thumbnail, title, play/pause, close, and a thin progress line. */
@Composable
fun MiniPlayer(
    title: String, thumbnailUrl: String?, playing: Boolean, progress: Float,
    onToggle: () -> Unit, onExpand: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(Vela.colors.surfaceVariant.toColor()).testTag(ComponentIds.MINI_PLAYER)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(Vela.colors.outline.toColor())) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Vela.colors.primary.toColor()))
        }
        Row(Modifier.fillMaxWidth().clickable(onClick = onExpand).padding(start = Vela.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
            Box(Modifier.size(width = 64.dp, height = 36.dp).clip(Vela.smallShape).background(Vela.colors.outline.toColor())) {
                if (thumbnailUrl != null) AsyncImage(thumbnailUrl, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            }
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onToggle, modifier = Modifier.testTag("mini.toggle")) { Icon(if (playing) Vela.icons.pause else Vela.icons.play, if (playing) "Pause" else "Play") }
            IconButton(onClick = onClose, modifier = Modifier.testTag("mini.close")) { Icon(Vela.icons.close, "Close player") }
        }
    }
}

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled, shape = Vela.pillShape) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** An icon with a short label under it: the video page's action row (Download, Save, Share). */
@Composable
fun ActionChip(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    Column(
        modifier.heightIn(min = Vela.minTouch).widthIn(min = Vela.minTouch).clip(Vela.mediumShape).clickable(onClick = onClick).padding(horizontal = Vela.sm, vertical = Vela.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (selected) Vela.colors.primary.toColor() else Vela.colors.onSurface.toColor())
        Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}

/**
 * A text link-style button that starts exactly at the content edge (Material's text button centres its label in a
 * 48 dp box, which leaves it indented). The touch area is still at least 48 dp each way.
 */
@Composable
fun LinkButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(modifier.heightIn(min = Vela.minTouch).widthIn(min = Vela.minTouch).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.CenterStart) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = (if (enabled) Vela.colors.primary else Vela.colors.onSurfaceVariant).toColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
