package com.greenaltambe.vela.ui.components

import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilterChip
import com.greenaltambe.vela.ui.theme.AppIcons
import androidx.compose.foundation.shape.CircleShape
import com.greenaltambe.vela.ui.theme.ExtraColors
import com.greenaltambe.vela.ui.theme.Spacing
import com.greenaltambe.vela.ui.theme.Accent
import com.greenaltambe.vela.ui.theme.LocalMotion
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.TopAppBar
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
import com.greenaltambe.vela.ui.look.pressable
import com.greenaltambe.vela.ui.look.VelaMotion
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.lerp
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.onClick
import com.greenaltambe.vela.ui.player.playerVerticalSwipe
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
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
    /** VERTICAL only: the thumbnail runs the full width of the screen (still rounded), through the list's side gutters; the text keeps them. */
    fullBleed: Boolean = false,
    /** HORIZONTAL only: draw the row as a tonal container in this shape (a segmented stack passes [segmentedShape] here). */
    containerShape: androidx.compose.ui.graphics.Shape? = null,
) {
    var showOriginal by remember(video.id) { mutableStateOf(false) }
    val replaced = video.originalTitle != null || video.originalThumbnailUrl != null
    val title = if (showOriginal) video.originalTitle ?: video.title else video.title
    val thumbnail = if (showOriginal) video.originalThumbnailUrl ?: video.thumbnailUrl else video.thumbnailUrl
    // Material's own press indication (the ripple, with its hover and focus states on desktop); a long press shows the original title and picture when DeArrow replaced them.
    val press = Modifier.combinedClickable(onClick = onClick, onLongClick = if (replaced) ({ showOriginal = !showOriginal }) else null)
    val cardShape = MaterialTheme.shapes.large

    if (style == VideoCardStyle.VERTICAL) {
        val gutter = Spacing.gutter
        Column(modifier.then(if (fullBleed) Modifier.bleed(gutter) else Modifier).fillMaxWidth().testTag(ComponentIds.VIDEO_CARD).clip(cardShape).then(press), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Thumbnail(video, thumbnail, extras, Modifier.fillMaxWidth().clip(cardShape))
            Row(Modifier.fillMaxWidth().padding(horizontal = if (fullBleed) gutter + Spacing.xs else Spacing.sm).padding(bottom = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Top) {
                if (showChannelAvatar) Avatar(video.channelName, onChannelClick, url = video.channelAvatarUrl)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("card.title"))
                    if (video.channelName.isNotBlank()) Text(video.channelName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("card.channel"))
                    val stats = remember(video.viewCount, video.uploadedAt, video.publishedText, extras.badge, showOriginal) { listOfNotNull(formatViews(video.viewCount), (formatAge(video.uploadedAt, Clock.System.now()) ?: video.publishedText), extras.badge, if (showOriginal) "original" else null).joinToString(" • ") }
                    if (stats.isNotEmpty()) Text(stats, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("card.meta"))
                }
                trailing?.invoke()
            }
        }
    } else {
        val rowShape = containerShape ?: cardShape
        val tonal = if (containerShape != null) Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow) else Modifier
        Row(modifier.fillMaxWidth().testTag(ComponentIds.VIDEO_CARD).clip(rowShape).then(tonal).then(press).padding(Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(video, thumbnail, extras, Modifier.fillMaxWidth(0.36f).widthIn(max = 168.dp).clip(MaterialTheme.shapes.medium))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("card.title"))
                if (video.channelName.isNotBlank()) Text(
                    video.channelName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("card.channel"),
                )
                val stats = remember(video.viewCount, video.uploadedAt, video.publishedText, extras.badge, showOriginal) { listOfNotNull(formatViews(video.viewCount), (formatAge(video.uploadedAt, Clock.System.now()) ?: video.publishedText), extras.badge, if (showOriginal) "original" else null).joinToString(" • ") }
                if (stats.isNotEmpty()) Text(stats, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("card.meta"))
            }
            trailing?.invoke()
        }
    }
}

/** Lets this item be [extra] wider than its parent on each side (it is centred over the parent): used to run a card through a list's side gutters. */
fun Modifier.bleed(extra: Dp): Modifier = this.layout { measurable, constraints ->
    val e = extra.roundToPx()
    val w = constraints.maxWidth + 2 * e
    val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-e, 0) }
}

@Composable
private fun Thumbnail(video: VideoSummary, url: String?, extras: CardExtras, modifier: Modifier) {
    Box(modifier.aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceContainerHigh).testTag("card.thumbnail")) {
        // Some feeds (Home recommendations, account playlists) arrive without a picture; YouTube serves one for every video id.
        val shown = url ?: if (video.id.isNotBlank()) "https://i.ytimg.com/vi/${video.id}/mqdefault.jpg" else null
        if (shown != null) AsyncImage(shown, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        if (video.isLive) Badge("LIVE", MaterialTheme.colorScheme.error, Modifier.align(Alignment.TopStart).padding(Spacing.xs))
        else if (video.isShort) Badge("SHORT", MaterialTheme.colorScheme.primary, Modifier.align(Alignment.TopStart).padding(Spacing.xs))
        formatDuration(video.durationSeconds)?.takeIf { !video.isLive }?.let { Badge(it, MaterialTheme.colorScheme.background.copy(alpha = 0.85f), Modifier.align(Alignment.BottomEnd).padding(Spacing.xs), textColor = MaterialTheme.colorScheme.onBackground) }
        if (extras.downloaded) Icon(
            AppIcons.downloaded, "Downloaded",
            Modifier.align(Alignment.TopEnd).padding(Spacing.xs).size(20.dp).testTag("card.downloaded"), tint = MaterialTheme.colorScheme.primary,
        )
        extras.watched?.takeIf { it > 0f }?.let { w ->
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.outline)) {
                Box(Modifier.fillMaxWidth(w.coerceIn(0f, 1f)).fillMaxHeight().background(Accent.brush).testTag("card.watched"))
            }
        }
    }
}

@Composable
private fun Badge(text: String, background: androidx.compose.ui.graphics.Color, modifier: Modifier, textColor: androidx.compose.ui.graphics.Color = ExtraColors.onMedia) {
    Text(
        text, modifier.clip(androidx.compose.foundation.shape.CircleShape).background(background).padding(horizontal = 6.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall, color = textColor, maxLines = 1, softWrap = false,
    )
}

/** The channel's picture; until avatars are available it is the first letter on the surface colour. */
@Composable
fun Avatar(name: String, onClick: (() -> Unit)? = null, size: Dp = 36.dp, url: String? = null, shape: androidx.compose.ui.graphics.Shape = CircleShape) {
    Box(
        // The touch target is the full minimum size, but the press highlight is clipped to the circle (clip comes before clickable).
        Modifier.let { if (onClick != null) it.minimumInteractiveComponentSize() else it }
            .size(size).clip(shape).let { if (onClick != null) it.clickable(onClick = onClick) else it }.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) AsyncImage(url, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        else Text(name.trim().take(1).uppercase(), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

@Composable
fun ChannelRow(name: String, subtitle: String?, avatarUrl: String?, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().heightIn(min = Spacing.minTouch).clip(MaterialTheme.shapes.medium).testTag(ComponentIds.CHANNEL_ROW).clickable(onClick = onClick).padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Avatar(name, url = avatarUrl, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = Spacing.minTouch).testTag(ComponentIds.SECTION_HEADER), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        action?.invoke()
    }
}

@Composable
fun CommentItem(comment: Comment, replies: List<Comment>?, repliesLoading: Boolean, onToggleReplies: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag(ComponentIds.COMMENT_ITEM), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(comment.author, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (comment.publishedText.isNotEmpty()) Text(comment.publishedText, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (comment.isPinned) Text("Pinned", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, color = MaterialTheme.colorScheme.primary)
        }
        Text(comment.text, style = MaterialTheme.typography.bodyMedium, maxLines = 12, overflow = TextOverflow.Ellipsis)
        val likes = formatCount(comment.likeCount.takeIf { it > 0 })
        if (likes != null) Text("$likes likes", style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (comment.replyCount > 0 && comment.repliesContinuation != null) {
            TextButton(onClick = onToggleReplies) {
                Text(if (replies != null) "Hide replies" else if (repliesLoading) "Loading replies..." else "${comment.replyCount} replies", maxLines = 1)
            }
        }
        replies?.forEach { reply ->
            Column(Modifier.padding(start = Spacing.lg)) {
                Text(reply.author, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(reply.text, style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun ChapterRow(chapter: Chapter, current: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = Spacing.minTouch).clip(MaterialTheme.shapes.medium).testTag(ComponentIds.CHAPTER_ROW).clickable(onClick = onClick).padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(formatTimestamp(chapter.startSeconds), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, color = MaterialTheme.colorScheme.primary)
        Text(chapter.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

/** What a tab shows in the bar and the rail. Labels are short enough for a 320 dp bar. */
data class NavEntry(val tab: NavTab, val label: String)

fun navEntries(tabs: List<NavTab>) = tabs.map { NavEntry(it, when (it) {
    NavTab.HOME -> "Home"; NavTab.SHORTS -> "Shorts"; NavTab.SUBSCRIPTIONS -> "Subs"; NavTab.LIBRARY -> "Library"; NavTab.SEARCH -> "Search"; NavTab.DOWNLOADS -> "Downloads"; NavTab.EXPLORE -> "Explore"
}) }

@Composable
internal fun NavEntry.icon(selected: Boolean = false): ImageVector = when (tab) {
    NavTab.HOME -> if (selected) AppIcons.homeFilled else AppIcons.home
    NavTab.SHORTS -> if (selected) AppIcons.boltFilled else AppIcons.bolt
    NavTab.SUBSCRIPTIONS -> if (selected) AppIcons.subscriptionsFilled else AppIcons.subscriptions
    NavTab.LIBRARY -> if (selected) AppIcons.libraryFilled else AppIcons.library
    NavTab.SEARCH -> AppIcons.search
    NavTab.DOWNLOADS -> if (selected) AppIcons.downloadFilled else AppIcons.download
    NavTab.EXPLORE -> if (selected) AppIcons.exploreFilled else AppIcons.explore
}

/** One entry of a top bar's overflow menu. */
class MenuAction(val label: String, val onClick: () -> Unit)

/** True while a screen is drawn inside the right pane of the two-pane Settings: it has no Back arrow there (the list on the left is the way around). */
val LocalEmbeddedInPane = androidx.compose.runtime.compositionLocalOf { false }

/**
 * The bar at the top of every screen, the standard Material top app bar: back (when there is somewhere to go back to) or the app's mark,
 * the title on one line, search, an overflow menu and the profile button. It takes the status-bar and display-cutout insets itself.
 * [large] is Material's large flexible bar for pages that are lists (Settings); give it a [scrollBehavior] and the big title collapses as the list scrolls.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VelaTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    /** The bell: opens the notifications page; [unread] puts a dot on it. */
    onNotifications: (() -> Unit)? = null,
    unread: Boolean = false,
    menu: List<MenuAction> = emptyList(),
    actions: @Composable () -> Unit = {},
    /** The profile button (avatar), at the right end; shown only where there is no Back button, where the app's mark takes the left. */
    profile: (@Composable () -> Unit)? = null,
    large: Boolean = false,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior? = null,
) {
    val back = if (LocalEmbeddedInPane.current) null else onBack
    if (large) {
        LargeFlexibleTopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = modifier.testTag("topbar"),
            navigationIcon = { if (back != null) IconButton(onClick = back, modifier = Modifier.testTag("nav.back")) { Icon(AppIcons.back, "Back") } },
            actions = { actions() },
            scrollBehavior = scrollBehavior,
        )
        return
    }
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier.testTag("topbar"),
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            if (back != null) IconButton(onClick = back, modifier = Modifier.testTag("nav.back")) { Icon(AppIcons.back, "Back") }
            else if (profile != null) com.greenaltambe.vela.ui.look.VelaMark(Modifier.padding(start = Spacing.md).testTag("topbar.logo"), size = 32.dp)
        },
        actions = {
            actions()
            if (onSearch != null) IconButton(onClick = onSearch, modifier = Modifier.testTag("nav.search")) { Icon(AppIcons.search, "Search") }
            if (onNotifications != null) IconButton(onClick = onNotifications, modifier = Modifier.testTag("nav.notifications")) {
                androidx.compose.material3.BadgedBox(badge = { if (unread) androidx.compose.material3.Badge(Modifier.testTag("nav.notifications.unread")) }) {
                    Icon(if (unread) AppIcons.notificationsFilled else AppIcons.notifications, if (unread) "Notifications, unread" else "Notifications")
                }
            }
            if (menu.isNotEmpty()) {
                var open by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { open = true }, modifier = Modifier.testTag("nav.overflow")) { Icon(AppIcons.more, "More options") }
                    DropdownMenu(open, { open = false }) {
                        menu.forEach { a -> DropdownMenuItem(text = { Text(a.label, maxLines = 1) }, onClick = { open = false; a.onClick() }) }
                    }
                }
            }
            if (back == null && profile != null) profile()
        },
    )
}


/** The main button: Material's expressive filled button (medium size, its shape morphs while pressed) with a text label. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick, shapes = ButtonDefaults.shapes(), modifier = modifier.heightIn(min = ButtonDefaults.MediumContainerHeight), enabled = enabled,
        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
    ) { Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** The quieter button next to a [PrimaryButton]: Material's expressive outlined button, same size. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick, shapes = ButtonDefaults.shapes(), modifier = modifier.heightIn(min = ButtonDefaults.MediumContainerHeight), enabled = enabled,
        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
    ) { Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** Where an [ActionChip] sits in a connected group: alone, or the first / last of two that touch. */
enum class ActionPosition { ALONE, LEADING, TRAILING }

/**
 * The video page's actions (Like, Dislike, Download, Save, Share): Material's expressive toggle buttons. A button that is on turns primary and its
 * shape morphs from round to square; Like and Dislike sit as one connected group ([ActionPosition]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selectedIcon: ImageVector? = null,
    enabled: Boolean = true,
    position: ActionPosition = ActionPosition.ALONE,
) {
    ToggleButton(
        checked = selected, onCheckedChange = { onClick() }, enabled = enabled, modifier = modifier,
        shapes = when (position) {
            ActionPosition.LEADING -> ButtonGroupDefaults.connectedLeadingButtonShapes()
            ActionPosition.TRAILING -> ButtonGroupDefaults.connectedTrailingButtonShapes()
            ActionPosition.ALONE -> ToggleButtonDefaults.shapes()
        },
    ) {
        Icon(if (selected && selectedIcon != null) selectedIcon else icon, null, Modifier.size(ToggleButtonDefaults.IconSize))
        if (label.isNotBlank()) { Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing)); Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) }
    }
}

/** A text button: Material's own. */
@Composable
fun LinkButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}
