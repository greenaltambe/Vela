package com.greenaltambe.vela.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
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

/** A video in any list. The thumbnail's corners, spacing and text come from the active Look. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCard(
    video: VideoSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onChannelClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    // DeArrow replaced the title / thumbnail: a long press shows the original and back.
    var showOriginal by remember(video.id) { mutableStateOf(false) }
    val replaced = video.originalTitle != null || video.originalThumbnailUrl != null
    val title = if (showOriginal) video.originalTitle ?: video.title else video.title
    val thumbnail = if (showOriginal) video.originalThumbnailUrl ?: video.thumbnailUrl else video.thumbnailUrl
    Row(
        modifier.fillMaxWidth().testTag(ComponentIds.VIDEO_CARD)
            .combinedClickable(onClick = onClick, onLongClick = if (replaced) ({ showOriginal = !showOriginal }) else null)
            .padding(vertical = Vela.xs),
        horizontalArrangement = Arrangement.spacedBy(Vela.md),
    ) {
        Box(Modifier.width(168.dp).aspectRatio(16f / 9f).clip(Vela.mediumShape).background(Vela.colors.surfaceVariant.toColor())) {
            if (thumbnail != null) AsyncImage(thumbnail, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            val length = formatDuration(video.durationSeconds)
            if (length != null) {
                Text(
                    length,
                    Modifier.align(Alignment.BottomEnd).padding(Vela.xs).clip(Vela.smallShape).background(Vela.colors.background.toColor().copy(alpha = 0.8f)).padding(horizontal = Vela.xs),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                video.channelName,
                style = MaterialTheme.typography.bodySmall,
                color = Vela.colors.onSurfaceVariant.toColor(),
                modifier = if (onChannelClick != null) Modifier.clickable(onClick = onChannelClick) else Modifier,
            )
            val meta = listOfNotNull(formatViews(video.viewCount), badge, if (showOriginal) "original" else null).joinToString("  ·  ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor())
        }
        trailing?.invoke()
    }
}

@Composable
fun ChannelRow(name: String, subtitle: String?, avatarUrl: String?, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().testTag(ComponentIds.CHANNEL_ROW).clickable(onClick = onClick).padding(vertical = Vela.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Vela.md),
    ) {
        Box(Modifier.size(40.dp).clip(Vela.pillShape).background(Vela.colors.surfaceVariant.toColor())) {
            if (avatarUrl != null) AsyncImage(avatarUrl, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor())
        }
        trailing?.invoke()
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().testTag(ComponentIds.SECTION_HEADER).padding(vertical = Vela.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        action?.invoke()
    }
}

@Composable
fun CommentItem(comment: Comment, replies: List<Comment>?, repliesLoading: Boolean, onToggleReplies: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag(ComponentIds.COMMENT_ITEM).padding(vertical = Vela.sm), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
            Text(comment.author, style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor())
            if (comment.publishedText.isNotEmpty()) Text(comment.publishedText, style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor())
            if (comment.isPinned) Text("Pinned", style = MaterialTheme.typography.bodySmall, color = Vela.colors.primary.toColor())
        }
        Text(comment.text, style = MaterialTheme.typography.bodyMedium)
        val likes = formatCount(comment.likeCount.takeIf { it > 0 })
        if (likes != null) Text("$likes likes", style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor())
        if (comment.replyCount > 0 && comment.repliesContinuation != null) {
            TextButton(onClick = onToggleReplies) {
                Text(if (replies != null) "Hide replies" else if (repliesLoading) "Loading replies..." else "${comment.replyCount} replies")
            }
        }
        replies?.forEach { reply ->
            Column(Modifier.padding(start = Vela.lg)) {
                Text(reply.author, style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor())
                Text(reply.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ChapterRow(chapter: Chapter, current: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().testTag(ComponentIds.CHAPTER_ROW).clickable(onClick = onClick).padding(vertical = Vela.xs),
        horizontalArrangement = Arrangement.spacedBy(Vela.md),
    ) {
        Text(formatTimestamp(chapter.startSeconds), style = MaterialTheme.typography.bodySmall, color = Vela.colors.primary.toColor())
        Text(chapter.title, style = MaterialTheme.typography.bodyMedium, color = if (current) Vela.colors.primary.toColor() else Vela.colors.onSurface.toColor())
    }
}

/** What the bottom bar and side rail show for a top-level [Route]. */
data class NavItem(val route: Route, val label: String, val glyph: String)

val NavItems = listOf(
    NavItem(Route.Home, "Home", "⌂"),
    NavItem(Route.Subscriptions, "Subscriptions", "▤"),
    NavItem(Route.Library, "Library", "▣"),
    NavItem(Route.Settings, "Settings", "⚙"),
)

@Composable
fun BottomNav(selected: Route, onSelect: (Route) -> Unit, modifier: Modifier = Modifier) {
    NavigationBar(modifier.testTag(ComponentIds.BOTTOM_NAV)) {
        NavItems.forEach { item ->
            NavigationBarItem(selected = item.route == selected, onClick = { onSelect(item.route) }, icon = { Text(item.glyph) }, label = { Text(item.label, maxLines = 1) })
        }
    }
}

@Composable
fun SideRail(selected: Route, onSelect: (Route) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    NavigationRail(modifier.testTag(ComponentIds.SIDE_RAIL).fillMaxHeight()) {
        NavigationRailItem(selected = false, onClick = onSearch, icon = { Text("⌕") }, label = { Text("Search") })
        Spacer(Modifier.size(Vela.sm))
        NavItems.forEach { item ->
            NavigationRailItem(selected = item.route == selected, onClick = { onSelect(item.route) }, icon = { Text(item.glyph) }, label = { Text(item.label, maxLines = 1) })
        }
    }
}

/** Docked above the nav while something plays and its page is not open: title, play/pause, close. Tap to expand. */
@Composable
fun MiniPlayer(title: String, playing: Boolean, onToggle: () -> Unit, onExpand: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().testTag(ComponentIds.MINI_PLAYER).background(Vela.colors.surfaceVariant.toColor()).clickable(onClick = onExpand).padding(horizontal = Vela.md, vertical = Vela.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Vela.sm),
    ) {
        Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onToggle) { Text(if (playing) "Pause" else "Play") }
        TextButton(onClick = onClose) { Text("Close") }
    }
}

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled, shape = Vela.pillShape) { Text(label) }
}
