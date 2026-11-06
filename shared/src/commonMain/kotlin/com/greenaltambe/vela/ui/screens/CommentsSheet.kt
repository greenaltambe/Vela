package com.greenaltambe.vela.ui.screens

import com.greenaltambe.vela.ui.theme.AppIcons
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.shape.CircleShape
import com.greenaltambe.vela.ui.theme.Spacing
import com.greenaltambe.vela.ui.theme.Accent
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import com.greenaltambe.vela.ui.platform.PlatformBackHandler
import com.greenaltambe.vela.library.DescriptionPart
import com.greenaltambe.vela.library.parseDescription
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.size
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
import com.greenaltambe.vela.ui.components.CommentSkeleton
import com.greenaltambe.vela.ui.components.formatCount

/** One comment's replies as loaded so far (they come in pages; [next] is the token of the following page). */
@Immutable
data class ReplyThread(val replies: List<Comment> = emptyList(), val next: String? = null, val loading: Boolean = false, val failed: Boolean = false)

/** Loads the replies of a video's comments, a page at a time, and keeps them so a thread opens at once the second time. */
class RepliesModel(private val services: AppServices, private val scope: CoroutineScope, private val videoId: String) {
    var threads by mutableStateOf<Map<String, ReplyThread>>(emptyMap()); private set

    /** Opening a thread loads its first page (once; a failed load is tried again). */
    fun open(c: Comment) { val t = threads[c.id]; if (t == null || (t.failed && !t.loading)) fetch(c, first = true) }
    /** Appends the next page of the thread's replies. */
    fun more(c: Comment) { val t = threads[c.id] ?: return; if (!t.loading && t.next != null) fetch(c, first = false) }

    private fun fetch(c: Comment, first: Boolean) {
        val before = threads[c.id] ?: ReplyThread()
        threads = threads + (c.id to before.copy(loading = true, failed = false))
        scope.launch {
            val token = if (first) c.repliesContinuation else before.next
            threads = threads + (c.id to when (val r = services.extractor.replies(videoId, c.id, token)) {
                is ExtractorResult.Success -> ReplyThread((if (first) emptyList() else before.replies).plus(r.value.comments).distinctBy { it.id }, r.value.continuation)
                is ExtractorResult.Failure -> before.copy(loading = false, failed = true)
            })
        }
    }
}

/** A reply and how deep it sits in the thread (0: answers the comment itself). */
internal data class ThreadRow(val comment: Comment, val depth: Int)

/**
 * YouTube replies are one flat list, but people answer each other with "@name ...". A reply that starts with the name of someone who spoke
 * earlier in the thread hangs under that person's latest reply; anything else answers the comment itself. The result is the tree, flattened
 * depth-first (the order a reader follows), at most [maxDepth] levels deep.
 */
internal fun threadRows(replies: List<Comment>, maxDepth: Int = 4): List<ThreadRow> {
    val parent = IntArray(replies.size) { -1 }
    val depth = IntArray(replies.size)
    replies.forEachIndexed { i, r ->
        // The text may open with blanks or zero-width marks before the name.
        val mention = Regex("^[\\s\\u00A0\\u200B-\\u200D\\u2060\\uFEFF]*@([^\\s,:.!?]+)").find(r.text)?.groupValues?.get(1)
        val p = if (mention == null) -1 else replies.subList(0, i).indexOfLast { it.author.removePrefix("@").equals(mention, ignoreCase = true) }
        parent[i] = p
        depth[i] = if (p < 0) 0 else minOf(depth[p] + 1, maxDepth)
    }
    val children = HashMap<Int, MutableList<Int>>()
    parent.forEachIndexed { i, p -> children.getOrPut(p) { ArrayList() }.add(i) }
    val out = ArrayList<ThreadRow>(replies.size)
    fun visit(i: Int) { out += ThreadRow(replies[i], depth[i]); children[i]?.forEach(::visit) }
    children[-1]?.forEach(::visit)
    return out
}

/** The first timestamp ("3:42") the comment mentions, in seconds; null when it has none. */
internal fun firstTimestamp(text: String): Long? = parseDescription(text).filterIsInstance<DescriptionPart.Time>().firstOrNull()?.seconds

/**
 * The comments as a bottom panel in the app's look (rounded top, the Look's surface). It sits in the page *under the video* (the caller gives it
 * the room below the picture), so it never covers what is playing. The list is flat: picture, name (in a pill when it is the creator), age,
 * the text, likes and a replies button. That button opens the comment as a thread (like Reddit): the replies below it, answers to a name set
 * in under the reply they answer, and "Show more replies" appends to the same thread.
 * Order: Top and Newest come from YouTube (when the extractor can); Timed lists the loaded comments that mention a moment, in video order.
 */
@Composable
fun CommentsPanel(
    comments: List<Comment>, state: ListState, hasMore: Boolean, loadingMore: Boolean,
    canSort: Boolean, sort: CommentSort, onSort: (CommentSort) -> Unit,
    replies: RepliesModel,
    onLoadMore: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier, onSeek: (Long) -> Unit = {}, totalCount: Long? = null,
    /** The video's channel (its picture marks a comment the creator hearted, as in YouTube). */
    creatorName: String = "", creatorAvatarUrl: String? = null,
    /** Said under the order chips when the order shown is not the one asked for (or could not be loaded). */
    sortNote: String? = null,
    /** The last "load more" failed: the button says so and tries again. */
    loadMoreFailed: Boolean = false,
) {
    // Dragging the header (or the handle above it) down follows the finger and, past a short distance or a flick, dismisses the panel; otherwise it springs back.
    var drag by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dismissAt = with(density) { 72.dp.toPx() }
    var thread by remember { mutableStateOf<Comment?>(null) }
    var timed by remember { mutableStateOf(false) }
    PlatformBackHandler(enabled = thread != null) { thread = null }
    val hasTimed = remember(comments) { comments.any { firstTimestamp(it.text) != null } }
    val shown = remember(comments, timed) { if (timed) comments.filter { firstTimestamp(it.text) != null }.sortedBy { firstTimestamp(it.text) } else comments }
    Column(
        modifier.graphicsLayer { translationY = drag }.clip(MaterialTheme.shapes.large.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)).background(MaterialTheme.colorScheme.surfaceContainerLow).testTag("comments.sheet"),
    ) {
        Column(
            Modifier.fillMaxWidth().playerVerticalSwipe(
                onDelta = { dy -> drag = (drag + dy).coerceAtLeast(0f) },
                onEnd = { _, v -> if (drag > dismissAt || v > 900f) onDismiss() else scope.launch { androidx.compose.animation.core.animate(drag, 0f) { x, _ -> drag = x } } },
            ),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally)) { com.greenaltambe.vela.ui.components.SheetHandle() }
            Row(Modifier.fillMaxWidth().padding(start = Spacing.gutter, end = Spacing.xs, top = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                val open = thread
                if (open != null) {
                    IconButton(onClick = { thread = null }, modifier = Modifier.testTag("comments.back")) { Icon(AppIcons.back, "Back to comments") }
                    Text("Replies", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    formatCount(open.replyCount.toLong().takeIf { it > 0 })?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    Text("Comments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (comments.isNotEmpty()) Text(totalCount?.let { formatCount(it) } ?: (comments.size.toString() + if (hasMore) "+" else ""), Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = Spacing.sm, vertical = 2.dp).testTag("comments.count"), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("comments.close")) { Icon(AppIcons.close, "Close comments") }
            }
            if (thread == null && sortNote != null) Text(sortNote, Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs).testTag("comments.sortNote"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (thread == null && (canSort || hasTimed)) Row(Modifier.padding(horizontal = Spacing.gutter), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                com.greenaltambe.vela.ui.components.ExpressiveChip(!timed && sort == CommentSort.TOP, "Top", { timed = false; if (sort != CommentSort.TOP) onSort(CommentSort.TOP) }, Modifier.testTag("comments.sort"))
                if (canSort) com.greenaltambe.vela.ui.components.ExpressiveChip(!timed && sort == CommentSort.NEWEST, "Newest", { timed = false; if (sort != CommentSort.NEWEST) onSort(CommentSort.NEWEST) }, Modifier.testTag("comments.sort.newest"))
                if (hasTimed) com.greenaltambe.vela.ui.components.ExpressiveChip(timed, "Timed", { timed = true }, Modifier.testTag("comments.sort.timed"))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        val open = thread
        if (open != null) ThreadView(open, replies, onSeek, creatorName, creatorAvatarUrl, Modifier.fillMaxWidth().weight(1f))
        else LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.sm, bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            when (state) {
                ListState.Loading -> items(5, key = { "sk-$it" }) { i -> CommentSkeleton(avatar = 40.dp, lastLineWidth = if (i % 2 == 0) 0.6f else 0.8f) }
                is ListState.Empty -> item { Text(state.message, Modifier.testTag("video.commentsStatus"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                is ListState.Error -> item { ErrorRow(state.message, state.onRetry) }
                ListState.Ready -> {}
            }
            if (timed && shown.isEmpty() && state == ListState.Ready) item { Text("No loaded comment mentions a moment of the video yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(shown, key = { "c-${it.id}" }) { c ->
                CommentRow(c, avatar = 40.dp, onSeek = onSeek, creatorName = creatorName, creatorAvatarUrl = creatorAvatarUrl, modifier = Modifier.testTag("comment.item"), onReplies = if (c.replyCount > 0 && c.repliesContinuation != null) ({ thread = c; replies.open(c) }) else null)
            }
            if (hasMore) item { TextButton(onClick = onLoadMore, enabled = !loadingMore, modifier = Modifier.testTag("video.moreComments")) { Text(if (loadingMore) "Loading…" else if (loadMoreFailed) "Could not load more. Try again" else "Load more comments", maxLines = 1) } }
        }
    }
}

/** A comment opened as a thread: the comment, then its replies with answers set in under the reply they answer, then "Show more replies". */
@Composable
private fun ThreadView(root: Comment, replies: RepliesModel, onSeek: (Long) -> Unit, creatorName: String, creatorAvatarUrl: String?, modifier: Modifier) {
    val t = replies.threads[root.id] ?: ReplyThread(loading = true)
    val rows = remember(t.replies) { threadRows(t.replies) }
    val lines = remember(rows) { threadLines(rows) }
    val wake = Accent.colors
    LazyColumn(modifier.testTag("comment.thread"), contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.sm, bottom = Spacing.lg)) {
        item(key = "root") {
            // The comment the thread belongs to. A line leaves the foot of its picture and runs down to the replies.
            CommentRow(
                root, avatar = ThreadGeometry.RootAvatar, onSeek = onSeek, onReplies = null, expanded = true, creatorName = creatorName, creatorAvatarUrl = creatorAvatarUrl,
                modifier = Modifier.threadLines(null, wake, rootHasReplies = rows.isNotEmpty() || t.loading).padding(bottom = Spacing.xs).testTag("comment.threadRoot"),
            )
        }
        itemsIndexed(rows, key = { _, r -> "r-${r.comment.id}" }) { i, row ->
            Row(Modifier.fillMaxWidth().threadLines(lines[i], wake).padding(top = ThreadGeometry.RowTop, bottom = Spacing.xs).testTag("comment.reply")) {
                androidx.compose.foundation.layout.Spacer(Modifier.width((ThreadGeometry.ReplyLeft + ThreadGeometry.Step * row.depth).dp))
                CommentRow(row.comment, avatar = ThreadGeometry.ReplyAvatar, onSeek = onSeek, onReplies = null, creatorName = creatorName, creatorAvatarUrl = creatorAvatarUrl, modifier = Modifier.weight(1f))
            }
        }
        // Placeholders shaped like the replies that are coming: indented like a reply, with the smaller picture.
        if (t.loading && rows.isEmpty()) items(3, key = { "sk-$it" }) { i ->
            Row(Modifier.fillMaxWidth().padding(top = ThreadGeometry.RowTop, bottom = Spacing.xs)) {
                androidx.compose.foundation.layout.Spacer(Modifier.width(ThreadGeometry.ReplyLeft.dp))
                CommentSkeleton(Modifier.weight(1f), avatar = ThreadGeometry.ReplyAvatar, lastLineWidth = if (i % 2 == 0) 0.7f else 0.5f)
            }
        }
        if (t.failed) item { ErrorRow("Could not load the replies.", onRetry = { replies.open(root) }) }
        if (t.next != null) item { TextButton(onClick = { replies.more(root) }, enabled = !t.loading, modifier = Modifier.testTag("comment.moreReplies")) { Text(if (t.loading) "Loading…" else "Show more replies", maxLines = 1) } }
    }
}

/** The measures of a thread, in dp: where the pictures sit and where the lines run. */
private object ThreadGeometry {
    val RootAvatar = 40.dp
    val ReplyAvatar = 30.dp
    val RowTop = 12.dp
    const val ReplyLeft = 34f   // the left edge of a reply picture at depth 0
    const val Step = 30f        // how far each level of depth moves in
    /** The x of the line that runs down from the picture of the comment at level [level] - 1 (level 0: the thread's own comment). */
    fun trunk(level: Int): Float = if (level == 0) 20f else 19f + Step * level
}

/** What a reply's row has to draw: the lines of the levels above it that go on past it, its own elbow, and the line down to its own answers. */
private class RowLines(val depth: Int, val continues: BooleanArray, val isLast: Boolean, val hasAnswers: Boolean)

private fun threadLines(rows: List<ThreadRow>): List<RowLines> = rows.mapIndexed { i, row ->
    val d = row.depth
    // A line at level k goes on past this row when a later row at level k follows before the thread climbs above level k.
    fun goesOn(level: Int): Boolean {
        var j = i + 1
        while (j < rows.size && rows[j].depth >= level) { if (rows[j].depth == level) return true; j++ }
        return false
    }
    RowLines(d, BooleanArray(d) { k -> goesOn(k) }, isLast = !goesOn(d), hasAnswers = i + 1 < rows.size && rows[i + 1].depth > d)
}

/**
 * Draws the thread's lines behind a row, in the palette (one colour per level): a line down from the foot of a picture to its answers, and
 * for a reply a rounded elbow from the line of its level into its own picture (the line goes on when more replies follow at that level).
 */
private fun Modifier.threadLines(info: RowLines?, wake: List<androidx.compose.ui.graphics.Color>, rootHasReplies: Boolean = false): Modifier = drawBehind {
    val width = 2.dp.toPx()
    fun colour(level: Int) = wake[level % wake.size].copy(alpha = 0.75f)
    fun stroke(path: androidx.compose.ui.graphics.Path, c: androidx.compose.ui.graphics.Color) = drawPath(path, c, style = androidx.compose.ui.graphics.drawscope.Stroke(width, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    fun vertical(x: Float, from: Float, to: Float, c: androidx.compose.ui.graphics.Color) {
        if (to <= from) return
        stroke(androidx.compose.ui.graphics.Path().apply { moveTo(x.dp.toPx(), from); lineTo(x.dp.toPx(), to) }, c)
    }
    if (info == null) {
        // The thread's own comment: from the foot of its picture to the bottom of the row.
        if (rootHasReplies) vertical(ThreadGeometry.trunk(0), ThreadGeometry.RootAvatar.toPx() + 4.dp.toPx(), size.height, colour(0))
        return@drawBehind
    }
    val avatar = ThreadGeometry.ReplyAvatar.toPx()
    val top = ThreadGeometry.RowTop.toPx()
    val cy = top + avatar / 2f
    val radius = 12.dp.toPx()
    // The lines of the levels above, where they go on past this row.
    for (k in 0 until info.depth) if (info.continues[k]) vertical(ThreadGeometry.trunk(k), 0f, size.height, colour(k))
    // This reply's own elbow: down the line of its level, round the corner, into its picture.
    val x = ThreadGeometry.trunk(info.depth).dp.toPx()
    val left = (ThreadGeometry.ReplyLeft + ThreadGeometry.Step * info.depth).dp.toPx() - 3.dp.toPx()
    stroke(androidx.compose.ui.graphics.Path().apply { moveTo(x, 0f); lineTo(x, cy - radius); quadraticTo(x, cy, x + radius, cy); lineTo(left, cy) }, colour(info.depth))
    if (!info.isLast) vertical(ThreadGeometry.trunk(info.depth), cy - radius, size.height, colour(info.depth))
    // From the foot of its picture, down to the replies that answer it.
    if (info.hasAnswers) vertical(ThreadGeometry.trunk(info.depth + 1), top + avatar + 3.dp.toPx(), size.height, colour(info.depth + 1))
}

/**
 * One comment: [avatar]-sized picture, the name (a filled pill when it is the video's creator), age, the text (links open, timestamps seek), then
 * likes, the creator's heart and, when [onReplies] is given, a replies button.
 */
@Composable
private fun CommentRow(c: Comment, avatar: Dp, onSeek: (Long) -> Unit, onReplies: (() -> Unit)?, modifier: Modifier = Modifier, expanded: Boolean = false, creatorName: String = "", creatorAvatarUrl: String? = null) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Avatar(c.author.removePrefix("@"), size = avatar, url = c.authorAvatarUrl)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (c.isPinned) Text("Pinned", Modifier.clip(CircleShape).background(Accent.brush).padding(horizontal = Spacing.sm, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false, color = MaterialTheme.colorScheme.onPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                val name = c.author.removePrefix("@")
                if (c.isByCreator) Text("@$name", Modifier.weight(1f, fill = false).clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = Spacing.sm, vertical = 1.dp).testTag("comment.creator"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onPrimary)
                else Text("@$name", Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (c.publishedText.isNotEmpty()) Text("\u00B7 " + c.publishedText + if (c.isEdited) " \u00B7 edited" else "", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Links open and timestamps seek the video, as in the description.
            val linkColor = MaterialTheme.colorScheme.primary
            // Long comments are cut after a few lines, with "Read more" (as in YouTube).
            var open by remember(c.id) { mutableStateOf(expanded) }
            var cut by remember(c.id) { mutableStateOf(false) }
            Text(
                remember(c.text, linkColor) { linkedText(c.text, linkColor, onSeek) }, style = MaterialTheme.typography.bodyMedium,
                maxLines = if (open) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis, onTextLayout = { if (it.hasVisualOverflow) cut = true },
            )
            if (cut && !open) Text("Read more", Modifier.clickable { open = true }.testTag("comment.readMore"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // A flow, so a narrow screen puts the replies button on a second line instead of squeezing it.
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(verticalArrangement = Arrangement.spacedBy(Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.md), itemVerticalAlignment = Alignment.CenterVertically) {
                formatCount(c.likeCount.takeIf { it > 0 })?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(AppIcons.like, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                if (c.isHearted) HeartedByCreator(creatorName, creatorAvatarUrl)
            }
            // The replies are the comment's own last line, apart from the likes: an icon and the count, bold in the accent.
            if (onReplies != null) Row(
                Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onReplies).heightIn(min = 36.dp).padding(end = Spacing.md).testTag("comment.repliesToggle"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(AppIcons.comment, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text("${c.replyCount} ${if (c.replyCount == 1) "reply" else "replies"}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            }
        }
    }
}

/** Loads and holds one video's comments for a [CommentsSheet] that is not part of the video page (the Shorts player). */
class CommentsController(private val services: AppServices, private val scope: CoroutineScope, val videoId: String) {
    var comments by mutableStateOf<List<Comment>>(emptyList()); private set
    var next by mutableStateOf<String?>(null); private set
    var total by mutableStateOf<Long?>(null); private set
    var state by mutableStateOf<ListState>(ListState.Loading); private set
    var loading by mutableStateOf(false); private set
    var sort by mutableStateOf(CommentSort.TOP); private set
    var note by mutableStateOf<String?>(null); private set
    var moreFailed by mutableStateOf(false); private set
    val replies = RepliesModel(services, scope, videoId)

    private var previousSort = CommentSort.TOP

    fun load(more: Boolean) {
        if (loading) return
        loading = true
        if (!more) { state = ListState.Loading }
        scope.launch {
            when (val r = services.extractor.comments(videoId, sort, next.takeIf { more })) {
                is ExtractorResult.Success -> {
                    comments = if (more) (comments + r.value.comments).distinctBy { it.id } else r.value.comments
                    next = r.value.continuation
                    if (!more) r.value.totalCount?.let { total = it }
                    if (!more) {
                        val applied = r.value.appliedSort ?: sort
                        note = if (applied != sort) "Newest first is not available right now. Showing top comments." else null
                        sort = applied
                    }
                    moreFailed = false
                    state = if (comments.isEmpty()) ListState.Empty("No comments yet.") else ListState.Ready
                }
                is ExtractorResult.Failure -> when {
                    more -> moreFailed = true
                    comments.isEmpty() -> state = ListState.Error("Comments unavailable: ${r.error.message}") { load(false) }
                    else -> { state = ListState.Ready; note = "Could not load that order. Showing ${if (previousSort == CommentSort.NEWEST) "newest" else "top"} comments."; sort = previousSort }
                }
            }
            loading = false
        }
    }

    fun select(order: CommentSort) { if (order == sort) return; previousSort = sort; note = null; sort = order; load(false) }
}

/** The panel for a [CommentsController]. */
@Composable
fun CommentsPanel(controller: CommentsController, canSort: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier, onSeek: (Long) -> Unit = {}) = CommentsPanel(
    controller.comments, controller.state, hasMore = controller.next != null, loadingMore = controller.loading,
    canSort = canSort, sort = controller.sort, onSort = controller::select, replies = controller.replies,
    onLoadMore = { controller.load(true) }, onDismiss = onDismiss, modifier = modifier, onSeek = onSeek, totalCount = controller.total,
    sortNote = controller.note, loadMoreFailed = controller.moreFailed,
)

/** The creator's picture with a small red heart on its corner: what YouTube shows under a comment the creator hearted. */
@Composable
private fun HeartedByCreator(name: String, avatarUrl: String?) {
    val heart = MaterialTheme.colorScheme.error
    val ring = MaterialTheme.colorScheme.background
    Box(Modifier.size(30.dp).testTag("comment.hearted").semantics { contentDescription = "Hearted by the creator" }) {
        Box(Modifier.align(Alignment.TopStart)) { Avatar(name.removePrefix("@"), size = 24.dp, url = avatarUrl) }
        Box(Modifier.align(Alignment.BottomEnd).size(16.dp).clip(CircleShape).background(ring), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.size(11.dp)) {
                val w = size.width; val h = size.height
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w / 2f, h * 0.92f)
                    cubicTo(-w * 0.25f, h * 0.45f, w * 0.15f, -h * 0.12f, w / 2f, h * 0.28f)
                    cubicTo(w * 0.85f, -h * 0.12f, w * 1.25f, h * 0.45f, w / 2f, h * 0.92f)
                    close()
                }
                drawPath(path, heart)
            }
        }
    }
}
