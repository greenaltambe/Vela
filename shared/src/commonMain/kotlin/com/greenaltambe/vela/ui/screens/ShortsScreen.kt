@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.greenaltambe.vela.ui.screens

import androidx.compose.material3.toShape
import com.greenaltambe.vela.ui.components.LoadingSpinner
import com.greenaltambe.vela.ui.theme.AppIcons
import androidx.compose.foundation.shape.CircleShape
import com.greenaltambe.vela.ui.theme.ExtraColors
import com.greenaltambe.vela.ui.theme.Spacing
import com.greenaltambe.vela.ui.theme.Accent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import com.greenaltambe.vela.ui.platform.PlatformBackHandler
import com.greenaltambe.vela.modules.ryd.compact
import com.greenaltambe.vela.auth.ActionResult
import com.greenaltambe.vela.auth.VideoRating
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.PagedList
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.extractor.videoPagedList
import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.player.SessionState
import com.greenaltambe.vela.ui.components.Avatar
import com.greenaltambe.vela.ui.components.EmptyState
import com.greenaltambe.vela.ui.components.formatViews
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import com.greenaltambe.vela.ui.platform.LocalShare
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** What the Shorts page last showed (the list and which one was on), kept for as long as the shell lives so coming back does not reload it. */
class ShortsFeedStore {
    var accountId: String? = null
    var shorts: PagedList<VideoSummary> = videoPagedList()
    var page: Int = 0
    internal var source: ShortsSource? = null
}

/** A channel Shorts can be taken from: its id, name and picture. */
internal class ShortsChannel(val id: String, val name: String, val avatar: String?)

/**
 * Where an endless Shorts feed comes from: the Shorts tabs of channels you subscribe to and channels seen on Home, a few at a time, three
 * shorts from each per round, mixed up. (Searching for Shorts only returns ordinary videos, so the channels' own Shorts tabs are the source.)
 * A channel's page of Shorts is fetched once and handed out over later rounds, then its next page.
 */
internal class ShortsSource(private val services: AppServices, private val first: ShortsChannel? = null, private val seed: () -> List<ShortsChannel>) {
    private val buffers = HashMap<String, ArrayDeque<VideoSummary>>()
    private val continuations = HashMap<String, String?>()
    private var order: List<ShortsChannel> = emptyList()
    private var cursor = 0

    suspend fun next(count: Int = 8): List<VideoSummary> {
        if (order.isEmpty()) order = (listOfNotNull(first) + (seed() + trendingChannels()).distinctBy { it.id }.filter { it.id.isNotBlank() && it.id != first?.id }.shuffled()).filter { it.id.isNotBlank() }
        if (order.isEmpty()) return emptyList()
        val out = mutableListOf<VideoSummary>()
        var rounds = 0
        while (out.size < count && rounds < order.size + 3) {
            val batch = (0 until minOf(3, order.size)).map { order[(cursor + it) % order.size] }
            cursor += batch.size; rounds += batch.size
            kotlinx.coroutines.coroutineScope { batch.map { c -> async { fill(c) } }.forEach { it.await() } }
            batch.forEach { c -> repeat(3) { buffers[c.id]?.removeFirstOrNull()?.let(out::add) } }
        }
        return out.shuffled()
    }

    private suspend fun trendingChannels(): List<ShortsChannel> =
        (services.extractor.trending(null) as? ExtractorResult.Success)?.value?.items.orEmpty().map { ShortsChannel(it.channelId, it.channelName, it.channelAvatarUrl) }

    private suspend fun fill(c: ShortsChannel) {
        val buffer = buffers.getOrPut(c.id) { ArrayDeque() }
        if (buffer.size >= 3 || (continuations.containsKey(c.id) && continuations[c.id] == null)) return
        when (val r = services.extractor.channelShorts(c.id, continuations[c.id])) {
            is ExtractorResult.Success -> {
                continuations[c.id] = r.value.continuation
                // The channel's own tab does not say whose they are: fill that in.
                buffer += r.value.items.map { it.copy(isShort = true, channelId = c.id, channelName = it.channelName.ifBlank { c.name }, channelAvatarUrl = it.channelAvatarUrl ?: c.avatar) }
            }
            is ExtractorResult.Failure -> continuations[c.id] = null
        }
    }
}

val LocalShortsFeed = staticCompositionLocalOf<ShortsFeedStore?> { null }

/**
 * An endless feed of Shorts, one full-screen video per page: swipe up for the next, down for the previous. Only the page in view plays
 * (the player has one picture, which is drawn on that page); the others show their thumbnail. Tap pauses or plays; a finished short starts
 * again. The feed is built from channels' Shorts tabs (see [ShortsSource]) and loaded further as you near the end. Opened with a [start] Short
 * (from a card elsewhere) it begins on that one, then carries on with Shorts from its channel and the rest. Like and dislike work as on the video
 * page (the counts are Return YouTube Dislike's), and Comments opens the comments in a panel under a shrunk picture: they never cover the video.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortsScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier, start: VideoSummary? = null) {
    val store = LocalShortsFeed.current.takeIf { start == null }
    val accountId = services.accounts.active.value.id
    val reuse = store != null && store.accountId == accountId && store.shorts.items.isNotEmpty()
    var list by remember { mutableStateOf(if (reuse) store!!.shorts else if (start != null) videoPagedList().append(com.greenaltambe.vela.extractor.Page(listOf(start), "more")) else videoPagedList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val host = services.player
    val home = LocalHomeFeed.current
    val source = remember(accountId, start?.id) {
        store?.source?.takeIf { reuse } ?: ShortsSource(services, start?.takeIf { it.channelId.isNotBlank() }?.let { ShortsChannel(it.channelId, it.channelName, it.channelAvatarUrl) }) {
            services.subscriptions.all().map { ShortsChannel(it.channelId, it.name, it.avatarUrl) } +
                home?.videos?.items.orEmpty().map { ShortsChannel(it.channelId, it.channelName, it.channelAvatarUrl) }
        }.also { store?.source = it }
    }

    fun load(more: Boolean) {
        if (loading) return
        loading = true
        scope.launch {
            val batch = source.next()
            if (batch.isEmpty() && list.items.isEmpty()) failed = "No channel to take Shorts from yet. Subscribe to some, or open Home first."
            else {
                list = if (more) list.append(com.greenaltambe.vela.extractor.Page(batch, if (batch.isEmpty()) null else "more")) else list.replace(com.greenaltambe.vela.extractor.Page(batch, "more"))
                failed = null
            }
            loading = false
        }
    }
    LaunchedEffect(accountId, start?.id) { if (!reuse) load(more = start != null) }

    val items = list.items
    if (items.isEmpty()) {
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            when {
                failed != null -> EmptyState("Shorts could not load: $failed", actionLabel = "Try again", onAction = { failed = null; load(false) })
                else -> LoadingSpinner(Modifier.size(40.dp), color = MaterialTheme.colorScheme.primary)
            }
        }
        return
    }

    val pager = rememberPagerState(initialPage = (store?.page ?: 0).coerceIn(0, items.lastIndex)) { items.size }
    val player by (host?.controllerState ?: remember { kotlinx.coroutines.flow.MutableStateFlow(com.greenaltambe.vela.player.PlayerState()) }).collectAsState()
    var paused by remember { mutableStateOf(false) }
    val muted by (host?.session?.muted ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsState()
    val sessionState by (host?.session?.state ?: remember { kotlinx.coroutines.flow.MutableStateFlow<SessionState>(SessionState.Idle) }).collectAsState()
    val rydRevision by services.modules.ryd.revision.collectAsState()
    var note by remember { mutableStateOf<String?>(null) }
    var commentsOpen by remember { mutableStateOf(false) }
    // The comments panel slides in as the picture shrinks (and back out): one animated fraction drives both, and the panel stays until it has gone.
    val open by androidx.compose.animation.core.animateFloatAsState(if (commentsOpen) 1f else 0f, androidx.compose.animation.core.tween(280), label = "shortsComments")
    var commentsFor by remember { mutableStateOf<String?>(null) }
    if (commentsOpen) commentsFor = items.getOrNull(pager.settledPage)?.id
    LaunchedEffect(commentsOpen, open) { if (!commentsOpen && open == 0f) commentsFor = null }
    val comments = remember(commentsFor) { commentsFor?.let { CommentsController(services, scope, it) } }
    LaunchedEffect(comments) { comments?.load(false) }

    // Remember where we were and what we loaded, for the next visit.
    DisposableEffect(Unit) {
        onDispose {
            if (store != null) { store.accountId = accountId; store.shorts = list; store.page = pager.settledPage }
            host?.session?.stop()                 // nothing else shows this video: do not leave it playing out of sight
        }
    }
    // The page in view plays; more is fetched as the end nears; its Return YouTube Dislike counts are asked for.
    // The list the effect reads is the CURRENT one: it was captured at the first composition before, so a Short opened from a channel (a list of one
    // that then grows) never found the next page's video, never started it, and the previous Short went on playing.
    val itemsNow by androidx.compose.runtime.rememberUpdatedState(items)
    LaunchedEffect(pager, host) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
            val v = itemsNow.getOrNull(page)
            if (v == null) { host?.session?.stop(); return@collect }   // nothing to play on this page yet: never leave the previous one sounding
            paused = false
            if (host != null) { host.onBeforePlayback(); host.session.start(v.id, v) }
            if (page >= itemsNow.size - 3) load(more = true)
            services.modules.ryd.votes(v.id)
        }
    }
    // Silence the Short being swiped away at once, not only when the next one has settled and started (the next one can take a while to resolve).
    LaunchedEffect(pager, host) {
        snapshotFlow { pager.currentPage }.distinctUntilChanged().collect { page ->
            if (page != pager.settledPage && host != null) host.session.pause()
        }
    }
    // A swipe that comes back to the same Short (let go half way) carries on playing it.
    LaunchedEffect(pager, host) {
        snapshotFlow { pager.isScrollInProgress }.distinctUntilChanged().collect { scrolling ->
            if (!scrolling && host != null && !paused && (sessionState as? SessionState.Ready)?.videoId == itemsNow.getOrNull(pager.settledPage)?.id) host.session.play()
        }
    }
    LaunchedEffect(note) { if (note != null) { kotlinx.coroutines.delay(2500); note = null } }
    // A short that ends starts over.
    LaunchedEffect(player.status) { if (player.status == PlaybackStatus.ENDED) { host?.session?.seekTo(0); host?.session?.play() } }
    val clipboard = LocalClipboardManager.current
    val share = LocalShare.current
    val canRate = services.actions != null && services.accounts.active.value.isSignedIn
    val liked = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val disliked = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }

    PlatformBackHandler(enabled = commentsOpen) { commentsOpen = false }
    Column(modifier.fillMaxSize().background(ExtraColors.mediaBackground)) {
        Box(Modifier.fillMaxWidth().weight(1f - 0.58f * open)) {
            VerticalPager(pager, Modifier.fillMaxSize().testTag("shorts.pager"), beyondViewportPageCount = 0) { page ->
                val v = items[page]
                val current = pager.settledPage == page && pager.currentPage == page
                val live = current && (sessionState as? SessionState.Ready)?.videoId == v.id
                val votes = remember(rydRevision, v.id) { services.modules.ryd.cached(v.id) }
                fun rate(target: VideoRating) {
                    if (!canRate) { note = "Sign in to rate videos"; return }
                    val (wasLiked, wasDisliked) = (liked[v.id] == true) to (disliked[v.id] == true)
                    liked[v.id] = target == VideoRating.LIKE; disliked[v.id] = target == VideoRating.DISLIKE
                    scope.launch { val r = services.actions!!.rate(v.id, target); (r as? ActionResult.Queued)?.let { note = it.message }; (r as? ActionResult.Failed)?.let { liked[v.id] = wasLiked; disliked[v.id] = wasDisliked; note = "Could not rate: ${it.message}" } }
                }
                Box(Modifier.fillMaxSize().clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                    if (current && host != null) { if (player.status == PlaybackStatus.PLAYING) { host.session.pause(); paused = true } else { host.session.play(); paused = false } }
                }) {
                    AsyncImage("https://i.ytimg.com/vi/${v.id}/hq720.jpg", null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    if (current && host != null) host.Surface(Modifier.fillMaxSize())
                    if (current && (player.status == PlaybackStatus.BUFFERING || !live)) LoadingSpinner(Modifier.align(Alignment.Center).size(40.dp), color = ExtraColors.onMedia)
                    if (current && paused && player.status != PlaybackStatus.PLAYING) Box(
                        Modifier.align(Alignment.Center).size(80.dp).clip(androidx.compose.material3.MaterialShapes.Cookie9Sided.toShape()).background(ExtraColors.mediaScrim.copy(alpha = 0.45f)), contentAlignment = Alignment.Center,
                    ) { Icon(AppIcons.play, "Play", Modifier.size(40.dp), tint = ExtraColors.onMedia) }

                    // The title and channel, bottom left, clear of the tab bar that floats over the page.
                    Column(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth(0.72f).padding(start = Spacing.md, bottom = Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Row(
                            Modifier.clip(CircleShape).background(ExtraColors.mediaScrim.copy(alpha = 0.35f)).clickable { navigator.push(Route.Channel(v.channelId, v.channelName, if (v.channelId.isBlank()) v.id else "")) }.padding(start = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs, end = Spacing.md).testTag("shorts.channel"),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Avatar(v.channelName, size = 32.dp, url = v.channelAvatarUrl)
                            Text(v.channelName, color = ExtraColors.onMedia, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(v.title, color = ExtraColors.onMedia, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        formatViews(v.viewCount)?.let { Text(it, color = ExtraColors.onMedia.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall) }
                    }
                    // Down the right edge: like and dislike with their counts, comments, mute, share, open as a normal video.
                    val onMedia = ExtraColors.onMedia
                    Column(Modifier.align(Alignment.BottomEnd).padding(end = Spacing.xs, bottom = Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        ShortsAction(if (liked[v.id] == true) AppIcons.likeFilled else AppIcons.like, "Like", votes?.let { compact(it.likes) }, liked[v.id] == true, onMedia, Modifier.testTag("shorts.like")) { rate(if (liked[v.id] == true) VideoRating.NONE else VideoRating.LIKE) }
                        ShortsAction(if (disliked[v.id] == true) AppIcons.dislikeFilled else AppIcons.dislike, "Dislike", votes?.let { compact(it.dislikes) }, disliked[v.id] == true, onMedia, Modifier.testTag("shorts.dislike")) { rate(if (disliked[v.id] == true) VideoRating.NONE else VideoRating.DISLIKE) }
                        ShortsAction(if (commentsOpen) AppIcons.commentFilled else AppIcons.comment, "Comments", null, commentsOpen, onMedia, Modifier.testTag("shorts.comments")) { commentsOpen = !commentsOpen }
                        ShortsAction(if (muted) AppIcons.mute else AppIcons.volume, if (muted) "Unmute" else "Mute", null, false, onMedia, Modifier.testTag("shorts.mute")) { host?.session?.toggleMute() }
                        ShortsAction(AppIcons.share, "Share", null, false, onMedia, Modifier.testTag("shorts.share")) { val link = "https://youtu.be/${v.id}"; if (share != null) share(v.title, link) else clipboard.setText(AnnotatedString(link)) }
                        ShortsAction(AppIcons.fullscreen, "Open as a video", null, false, onMedia, Modifier.testTag("shorts.open")) { host?.session?.stop(); navigator.push(Route.Video(v, null, asVideoPage = true)) }
                    }
                    // Progress along the bottom edge: Material's wavy bar.
                    if (current && player.durationMs > 0) androidx.compose.material3.LinearWavyProgressIndicator(
                        progress = { (player.positionMs.toFloat() / player.durationMs).coerceIn(0f, 1f) },
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = Spacing.md).testTag("shorts.progress"),
                        trackColor = ExtraColors.onMedia.copy(alpha = 0.3f),
                    )
                }
            }
            // Back, when opened from elsewhere in the app.
            if (start != null) androidx.compose.material3.FilledIconButton(
                onClick = { navigator.back() }, shapes = androidx.compose.material3.IconButtonDefaults.shapes(),
                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(containerColor = ExtraColors.mediaScrim.copy(alpha = 0.35f), contentColor = ExtraColors.onMedia),
                modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Start)).padding(Spacing.sm).testTag("shorts.back"),
            ) { Icon(AppIcons.back, "Back") }
            note?.let { Text(it, Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).padding(top = Spacing.md).clip(CircleShape).background(ExtraColors.mediaScrim.copy(alpha = 0.6f)).padding(horizontal = Spacing.md, vertical = Spacing.xs).testTag("shorts.note"), color = ExtraColors.onMedia, style = MaterialTheme.typography.bodySmall) }
        }
        // The comments take the lower part of the screen; the picture above them shrinks to make room.
        if (open > 0.01f && comments != null) Box(Modifier.fillMaxWidth().weight(0.58f * open)) {
            CommentsPanel(comments, canSort = services.extractor.capabilities.supportsCommentSort, onDismiss = { commentsOpen = false }, modifier = Modifier.fillMaxSize(), onSeek = { seconds -> host?.session?.seekTo(seconds * 1000) })
        }
    }
}

/** One button down the Shorts player's right edge: an expressive toggle icon button on a scrim (it morphs when pressed and fills when chosen), with a [caption] only where there is a number to show. */
@Composable
private fun ShortsAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, caption: String?, on: Boolean, tint: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.FilledIconToggleButton(
            checked = on, onCheckedChange = { onClick() }, modifier = modifier.size(52.dp),
            shapes = androidx.compose.material3.IconButtonDefaults.toggleableShapes(),
            colors = androidx.compose.material3.IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = ExtraColors.mediaScrim.copy(alpha = 0.35f), contentColor = tint,
                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer, checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) { Icon(icon, description) }
        if (caption != null) Text(caption, color = tint, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
    }
}
