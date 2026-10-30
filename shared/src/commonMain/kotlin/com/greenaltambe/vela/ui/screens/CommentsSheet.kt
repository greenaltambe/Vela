package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.greenaltambe.vela.ui.player.playerVerticalSwipe
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.extractor.Comment
import com.greenaltambe.vela.extractor.CommentSort
import com.greenaltambe.vela.extractor.ExtractorResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.greenaltambe.vela.ui.components.Avatar
import com.greenaltambe.vela.ui.components.ErrorRow
import com.greenaltambe.vela.ui.components.SkeletonCard
import com.greenaltambe.vela.ui.components.formatCount
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.toColor

/**
 * The comments as a bottom panel in the app's look (rounded top, the Look's surface). It sits in the page *under the video* (the caller gives it
 * the room below the picture), so it never covers what is playing. Each comment is a small block: picture, name and age, the text, likes and a
 * replies toggle; opened replies hang off it as a thread with a line in the palette gradient down their side.
 */
@Composable
fun CommentsPanel(
    comments: List<Comment>, state: ListState, hasMore: Boolean, loadingMore: Boolean,
    canSort: Boolean, newestFirst: Boolean, onToggleSort: () -> Unit,
    replies: Map<String, List<Comment>>, repliesLoading: Set<String>, onToggleReplies: (Comment) -> Unit,
    onLoadMore: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier, onSeek: (Long) -> Unit = {},
) {
    // Dragging the header (or the handle above it) down follows the finger and, past a short distance or a flick, dismisses the panel; otherwise it springs back.
    var drag by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dismissAt = with(density) { 72.dp.toPx() }
    Column(
        modifier.graphicsLayer { translationY = drag }.clip(RoundedCornerShape(topStart = Vela.look.radii.largeDp.dp, topEnd = Vela.look.radii.largeDp.dp)).background(Vela.colors.surface.toColor()).testTag("comments.sheet"),
    ) {
        Column(
            Modifier.fillMaxWidth().playerVerticalSwipe(
                onDelta = { dy -> drag = (drag + dy).coerceAtLeast(0f) },
                onEnd = { _, v -> if (drag > dismissAt || v > 900f) onDismiss() else scope.launch { androidx.compose.animation.core.animate(drag, 0f) { x, _ -> drag = x } } },
            ),
        ) {
        Box(Modifier.align(Alignment.CenterHorizontally).padding(top = Vela.xs).width(36.dp).height(4.dp).clip(Vela.pillShape).background(Vela.colors.outline.toColor()))
        Row(Modifier.fillMaxWidth().padding(start = Vela.gutter, end = Vela.xs, top = Vela.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
            Text("Comments", style = MaterialTheme.typography.titleMedium)
            if (comments.isNotEmpty()) Text(comments.size.toString() + if (hasMore) "+" else "", Modifier.clip(Vela.pillShape).background(Vela.colors.surfaceVariant.toColor()).padding(horizontal = Vela.sm, vertical = 2.dp).testTag("comments.count"), style = MaterialTheme.typography.labelMedium, color = Vela.colors.onSurfaceVariant.toColor())
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            androidx.compose.material3.IconButton(onClick = onDismiss, modifier = Modifier.testTag("comments.close")) { Icon(Vela.icons.close, "Close comments") }
        }
        if (canSort) Row(Modifier.padding(horizontal = Vela.gutter), horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
            androidx.compose.material3.FilterChip(selected = !newestFirst, onClick = { if (newestFirst) onToggleSort() }, label = { Text("Top", maxLines = 1) }, modifier = Modifier.testTag("comments.sort"))
            androidx.compose.material3.FilterChip(selected = newestFirst, onClick = { if (!newestFirst) onToggleSort() }, label = { Text("Newest", maxLines = 1) }, modifier = Modifier.testTag("comments.sort.newest"))
        }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(start = Vela.gutter, end = Vela.gutter, top = Vela.sm, bottom = Vela.lg), verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
            when (state) {
                ListState.Loading -> items(4) { SkeletonCard(vertical = false) }
                is ListState.Empty -> item { Text(state.message, Modifier.testTag("video.commentsStatus"), style = MaterialTheme.typography.bodyMedium, color = Vela.colors.onSurfaceVariant.toColor()) }
                is ListState.Error -> item { ErrorRow(state.message, state.onRetry) }
                ListState.Ready -> {}
            }
            items(comments, key = { "c-${it.id}" }) { c -> CommentThread(c, replies[c.id], c.id in repliesLoading, onSeek) { onToggleReplies(c) } }
            if (hasMore) item { TextButton(onClick = onLoadMore, enabled = !loadingMore, modifier = Modifier.testTag("video.moreComments")) { Text(if (loadingMore) "Loading…" else "Load more comments", maxLines = 1) } }
        }
    }
}

@Composable
private fun CommentThread(comment: Comment, replies: List<Comment>?, loadingReplies: Boolean, onSeek: (Long) -> Unit, onToggleReplies: () -> Unit) {
    // Each comment is a card on the sheet; its replies open inside it, set in by a line in the palette gradient.
    Column(Modifier.fillMaxWidth().clip(Vela.mediumShape).background(Vela.colors.background.toColor()).padding(Vela.md).testTag("comment.item"), verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
        CommentBlock(comment, avatar = 36.dp, onSeek = onSeek)
        if (comment.replyCount > 0 && comment.repliesContinuation != null) {
            Row(
                Modifier.padding(start = 36.dp + Vela.sm).clip(Vela.pillShape).background(Vela.colors.surface.toColor()).clickable(onClick = onToggleReplies).heightIn(min = 32.dp).padding(horizontal = Vela.md).testTag("comment.repliesToggle"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.xs),
            ) {
                Text(
                    if (replies != null) "Hide replies" else if (loadingReplies) "Loading replies…" else "${comment.replyCount} ${if (comment.replyCount == 1) "reply" else "replies"}",
                    style = MaterialTheme.typography.labelMedium, color = Vela.colors.primary.toColor(), maxLines = 1,
                )
                Icon(if (replies != null) Vela.icons.chevronUp else Vela.icons.chevronDown, null, Modifier.height(16.dp), tint = Vela.colors.primary.toColor())
            }
        }
        if (!replies.isNullOrEmpty()) {
            Row(Modifier.padding(start = 18.dp).height(IntrinsicSize.Min)) {
                Box(Modifier.width(2.dp).fillMaxHeight().clip(Vela.pillShape).background(androidx.compose.ui.graphics.Brush.verticalGradient(Vela.look.accent.wake.map { it.toColor() })).testTag("comment.threadLine"))
                Column(Modifier.padding(start = Vela.md), verticalArrangement = Arrangement.spacedBy(Vela.md)) {
                    replies.forEach { r -> CommentBlock(r, avatar = 26.dp, onSeek = onSeek) }
                }
            }
        }
    }
}

@Composable
private fun CommentBlock(c: Comment, avatar: androidx.compose.ui.unit.Dp, onSeek: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
        Avatar(c.author.removePrefix("@"), size = avatar)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Vela.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(c.author.removePrefix("@"), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (c.publishedText.isNotEmpty()) Text(c.publishedText, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false, color = Vela.colors.onSurfaceVariant.toColor())
                if (c.isPinned) Text("Pinned", Modifier.clip(Vela.pillShape).background(Vela.wake).padding(horizontal = Vela.sm, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false, color = Vela.colors.onPrimary.toColor())
            }
            // Links open and timestamps seek the video, as in the description.
            val linkColor = Vela.colors.primary.toColor()
            Text(androidx.compose.runtime.remember(c.text, linkColor) { linkedText(c.text, linkColor, onSeek) }, style = MaterialTheme.typography.bodyMedium, maxLines = 12, overflow = TextOverflow.Ellipsis)
            formatCount(c.likeCount.takeIf { it > 0 })?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Vela.icons.like, null, Modifier.height(14.dp), tint = Vela.colors.onSurfaceVariant.toColor())
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Vela.colors.onSurfaceVariant.toColor(), maxLines = 1)
                }
            }
        }
    }
}


/** Loads and holds one video's comments for a [CommentsSheet] that is not part of the video page (the Shorts player). */
class CommentsController(private val services: AppServices, private val scope: CoroutineScope, val videoId: String) {
    var comments by mutableStateOf<List<Comment>>(emptyList()); private set
    var next by mutableStateOf<String?>(null); private set
    var state by mutableStateOf<ListState>(ListState.Loading); private set
    var loading by mutableStateOf(false); private set
    var sort by mutableStateOf(CommentSort.TOP); private set
    var replies by mutableStateOf<Map<String, List<Comment>>>(emptyMap()); private set
    var repliesLoading by mutableStateOf<Set<String>>(emptySet()); private set

    fun load(more: Boolean) {
        if (loading) return
        loading = true
        if (!more) state = ListState.Loading
        scope.launch {
            when (val r = services.extractor.comments(videoId, sort, next.takeIf { more })) {
                is ExtractorResult.Success -> {
                    comments = if (more) (comments + r.value.comments).distinctBy { it.id } else r.value.comments
                    next = r.value.continuation
                    state = if (comments.isEmpty()) ListState.Empty("No comments yet.") else ListState.Ready
                }
                is ExtractorResult.Failure -> if (comments.isEmpty()) state = ListState.Error("Comments unavailable: ${r.error.message}") { load(false) }
            }
            loading = false
        }
    }

    fun toggleSort() { sort = if (sort == CommentSort.TOP) CommentSort.NEWEST else CommentSort.TOP; load(false) }

    fun toggleReplies(c: Comment) {
        if (replies.containsKey(c.id)) { replies = replies - c.id; return }
        repliesLoading = repliesLoading + c.id
        scope.launch {
            val r = services.extractor.replies(videoId, c.id, c.repliesContinuation)
            replies = replies + (c.id to ((r as? ExtractorResult.Success)?.value?.comments ?: emptyList()))
            repliesLoading = repliesLoading - c.id
        }
    }
}

/** The panel for a [CommentsController]. */
@Composable
fun CommentsPanel(controller: CommentsController, canSort: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier, onSeek: (Long) -> Unit = {}) = CommentsPanel(
    controller.comments, controller.state, hasMore = controller.next != null, loadingMore = controller.loading,
    canSort = canSort, newestFirst = controller.sort == CommentSort.NEWEST, onToggleSort = controller::toggleSort,
    replies = controller.replies, repliesLoading = controller.repliesLoading, onToggleReplies = controller::toggleReplies,
    onLoadMore = { controller.load(true) }, onDismiss = onDismiss, modifier = modifier, onSeek = onSeek,
)
