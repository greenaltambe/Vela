package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import com.greenaltambe.vela.auth.ActionResult
import com.greenaltambe.vela.auth.VideoRating
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.downloads.OfflineVideo
import com.greenaltambe.vela.extractor.Chapter
import com.greenaltambe.vela.extractor.Comment
import com.greenaltambe.vela.extractor.CommentSort
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.VideoInfo
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.library.DescriptionPart
import com.greenaltambe.vela.library.currentChapter
import com.greenaltambe.vela.library.effectiveChapters
import com.greenaltambe.vela.library.parseDescription
import com.greenaltambe.vela.modules.dearrow.DeArrowSurface
import com.greenaltambe.vela.modules.ryd.compact
import com.greenaltambe.vela.modules.sponsorblock.SkipNotice
import com.greenaltambe.vela.player.PlaybackPlan
import com.greenaltambe.vela.player.PlaybackSession
import com.greenaltambe.vela.player.SessionState
import com.greenaltambe.vela.ui.components.ActionChip
import com.greenaltambe.vela.ui.components.Avatar
import com.greenaltambe.vela.ui.components.ChapterRow
import com.greenaltambe.vela.ui.components.CommentItem
import com.greenaltambe.vela.ui.components.ErrorRow
import com.greenaltambe.vela.ui.components.LinkButton
import com.greenaltambe.vela.ui.components.PrimaryButton
import com.greenaltambe.vela.ui.components.SectionHeader
import com.greenaltambe.vela.ui.components.SkeletonCard
import com.greenaltambe.vela.ui.components.VideoCard
import com.greenaltambe.vela.ui.components.VideoCardStyle
import com.greenaltambe.vela.ui.components.formatViews
import com.greenaltambe.vela.ui.layout.LocalWindowSize
import com.greenaltambe.vela.ui.layout.LocalWindowWidthDp
import com.greenaltambe.vela.ui.layout.WindowSizeClass
import com.greenaltambe.vela.ui.look.LocalLook
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.toColor
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import com.greenaltambe.vela.ui.platform.LocalInPictureInPicture
import com.greenaltambe.vela.ui.platform.LocalWindowControls
import com.greenaltambe.vela.ui.platform.PlatformBackHandler
import com.greenaltambe.vela.ui.player.PlayerView
import com.greenaltambe.vela.ui.player.playerVerticalSwipe
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import com.greenaltambe.vela.ui.player.SeekMarker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private fun SessionState.videoIdOrNull(): String? = when (this) {
    SessionState.Idle -> null
    is SessionState.Resolving -> videoId
    is SessionState.Ready -> videoId
    is SessionState.Failed -> videoId
}

/**
 * The video page. The player is pinned at the top (below the status bar) and never scrolls away; on a phone the
 * details, then the comments and related videos, scroll beneath it. On wider windows the details stay under the player
 * and comments and related videos move to a column beside it.
 */
@Composable
fun VideoScreen(services: AppServices, navigator: Navigator, video: VideoSummary, startSeconds: Long?, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val host = services.player
    val window = LocalWindowControls.current
    val inPip = LocalInPictureInPicture.current
    val size = LocalWindowSize.current

    // A finished download plays from its file and shows its stored page: no stream, info, comment or related request is made.
    val offline = remember(video.id) { services.downloads?.offline(video.id) }
    val pageCache = com.greenaltambe.vela.ui.screens.LocalVideoPageCache.current
    val cached = remember(video.id) { if (offline == null) pageCache?.get(video.id, services.clock.now()) else null }
    var info by remember(video.id) { mutableStateOf<VideoInfo?>(offline?.let(::storedVideoInfo) ?: cached?.info) }
    var infoError by remember(video.id) { mutableStateOf<String?>(null) }
    var related by remember(video.id) { mutableStateOf<List<VideoSummary>>(cached?.related.orEmpty()) }
    var comments by remember(video.id) { mutableStateOf<List<Comment>>(offline?.comments ?: cached?.comments.orEmpty()) }
    var commentsNext by remember(video.id) { mutableStateOf<String?>(cached?.commentsNext) }
    var commentsOpen by remember(video.id) { mutableStateOf(false) }
    var commentsState by remember(video.id) { mutableStateOf<ListState>(if (offline != null) (if (offline.comments.isEmpty()) ListState.Empty("No saved comments.") else ListState.Ready) else if (cached?.comments?.isNotEmpty() == true) ListState.Ready else ListState.Loading) }
    var commentsLoading by remember(video.id) { mutableStateOf(false) }
    var sort by remember(video.id) { mutableStateOf(CommentSort.TOP) }
    var replies by remember(video.id) { mutableStateOf<Map<String, List<Comment>>>(emptyMap()) }
    var repliesLoading by remember(video.id) { mutableStateOf<Set<String>>(emptySet()) }
    var descriptionExpanded by remember(video.id) { mutableStateOf(false) }
    var downloadDialog by remember(video.id) { mutableStateOf(false) }
    var subscribed by remember(video.id, video.channelId) { mutableStateOf(services.subscriptions.isSubscribed(video.channelId)) }
    LaunchedEffect(info?.channelId) { info?.channelId?.takeIf { it.startsWith("UC") }?.let { subscribed = services.subscriptions.isSubscribed(it) } }
    var liked by remember(video.id) { mutableStateOf(false) }
    var disliked by remember(video.id) { mutableStateOf(false) }
    var actionError by remember(video.id) { mutableStateOf<String?>(null) }
    var actionNote by remember(video.id) { mutableStateOf<String?>(null) }
    // Created before any early return so the scroll positions survive fullscreen and Picture-in-Picture.
    val detailsState = rememberLazyListState()
    val sideState = rememberLazyListState()
    // Pull-down state: how far the page has been dragged down (px). The page shrinks toward the mini player's corner as it grows.
    var pageHeightPx by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var dragPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var pageWidthPx by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Release after a pull: past a short distance (or a quick flick) the page is handed to the mini player; otherwise it springs back.
    val reveal = com.greenaltambe.vela.ui.nav.LocalPageReveal.current
    fun setDrag(v: Float) { dragPx = v; reveal?.floatValue = (v / (pageHeightPx * 0.5f)).coerceIn(0f, 1f) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { reveal?.floatValue = 0f } }
    suspend fun settlePage(velocityY: Float) {
        if (dragPx > pageHeightPx * 0.18f || velocityY > 1200f) {
            androidx.compose.animation.core.animate(dragPx, pageHeightPx * 0.5f, animationSpec = androidx.compose.animation.core.tween(170)) { x, _ -> setDrag(x) }
            navigator.back()
        } else androidx.compose.animation.core.animate(dragPx, 0f, animationSpec = androidx.compose.animation.core.spring(stiffness = 500f)) { x, _ -> setDrag(x) }
    }
    val seekStep = services.settings.resolve(com.greenaltambe.vela.core.settings.Settings.seekStepSeconds).toInt()
    val holdSpeedSetting = services.settings.resolve(com.greenaltambe.vela.core.settings.Settings.holdSpeed)
    val miniOnRight = services.settings.resolve(com.greenaltambe.vela.core.settings.LookSettings.miniLook) == com.greenaltambe.vela.core.settings.MiniLook.PIP

    fun loadComments(more: Boolean) {
        if (commentsLoading) return
        commentsLoading = true
        if (!more) commentsState = ListState.Loading
        scope.launch {
            when (val r = services.extractor.comments(video.id, sort, commentsNext.takeIf { more })) {
                is ExtractorResult.Success -> {
                    comments = if (more) (comments + r.value.comments).distinctBy { it.id } else r.value.comments
                    commentsNext = r.value.continuation
                    commentsState = if (comments.isEmpty()) ListState.Empty("No comments yet.") else ListState.Ready
                }
                is ExtractorResult.Failure -> if (comments.isEmpty()) commentsState = ListState.Error("Comments unavailable: ${r.error.message}") { loadComments(false) }
            }
            commentsLoading = false
        }
    }

    // Start (or keep) playback. Coming back from the mini player must not restart a video that is already playing.
    LaunchedEffect(video.id) {
        host ?: return@LaunchedEffect
        val current = host.session.state.value
        // Already on the right video and, when a download exists, already playing that file rather than a stream of it.
        val rightSource = current.videoIdOrNull() == video.id &&
            (offline == null || (current as? SessionState.Ready)?.client == PlaybackSession.OFFLINE_CLIENT)
        if (!rightSource) {
            host.onBeforePlayback()
            if (offline != null) host.session.startLocal(video.id, PlaybackPlan.Local(offline.uri, offline.hasVideo), offline.branding?.title ?: offline.download.title, offline.download.channelId)
            else host.session.start(video.id, video)
        }
        if (startSeconds != null) host.session.seekTo(startSeconds * 1000)
    }
    LaunchedEffect(video.id) {
        if (offline != null || cached?.info != null) return@LaunchedEffect
        when (val r = services.extractor.videoInfo(video.id)) {
            is ExtractorResult.Success -> info = r.value
            is ExtractorResult.Failure -> infoError = r.error.message
        }
    }
    LaunchedEffect(video.id) { if (offline == null && related.isEmpty()) (services.extractor.related(video.id) as? ExtractorResult.Success)?.let { related = it.value } }
    var skipFirstComments by remember(video.id) { mutableStateOf(cached?.comments?.isNotEmpty() == true) }
    LaunchedEffect(video.id, sort) {
        if (offline == null) { if (skipFirstComments) skipFirstComments = false else loadComments(more = false) }
    }
    // What this page loaded is kept for the next time it is opened (see VideoPageCache).
    androidx.compose.runtime.SideEffect {
        if (offline == null && pageCache != null && (info != null || related.isNotEmpty() || comments.isNotEmpty())) pageCache.put(video.id, VideoPageCache.Entry(info, related, comments, commentsNext, services.clock.now()))
    }

    val chapters: List<Chapter> = remember(info) { info?.let { effectiveChapters(it.chapters, it.description, it.durationSeconds) }.orEmpty() }

    // ---- modules: none of these may delay or break anything above; each fills in when its answer arrives ----
    val modules = services.modules
    val look = LocalLook.current
    val brandingRevision by modules.dearrow.revision.collectAsState()
    val votesRevision by modules.ryd.revision.collectAsState()
    val segments by modules.sponsorBlock.segments.collectAsState()
    val prompt by modules.sponsorBlock.prompt.collectAsState()
    var notice by remember(video.id) { mutableStateOf<SkipNotice?>(null) }
    var showOriginalTitle by remember(video.id) { mutableStateOf(false) }
    LaunchedEffect(video.id) { if (offline == null) modules.dearrow.prefetch(listOf(video.id)) }
    LaunchedEffect(video.id) { if (offline == null) modules.ryd.votes(video.id) }
    LaunchedEffect(video.id) { modules.sponsorBlock.notices.collectLatest { notice = it; delay(6_000); notice = null } }
    val markers = remember(segments, look) {
        segments.map { s ->
            val color = Color((look.colors.markers[s.category] ?: 0xFFFFFFFF).toInt())
            SeekMarker(s.startMs, if (s.isPoint) s.startMs + 1_500 else s.endMs, color)
        }
    }
    val originalTitle = info?.title ?: video.title
    // Offline, DeArrow's title is the one saved with the download; online it is looked up (and cached).
    val brandedTitle = if (offline != null) offline.branding?.title ?: originalTitle
    else remember(brandingRevision, originalTitle) { modules.dearrow.title(video.id, originalTitle, DeArrowSurface.VIDEO_PAGE) }
    val shownTitle = if (showOriginalTitle) originalTitle else brandedTitle
    val titleReplaced = brandedTitle != originalTitle
    val votes = remember(votesRevision) { modules.ryd.cached(video.id) }
    val relatedShown = rememberFiltered(services, related, DeArrowSurface.VIDEO_PAGE)
    // Previous: the video played before this one (none: starts this one over). Next: the queue's next, else the first related video.
    val nextRelated = relatedShown.firstOrNull { it.id != video.id && !it.isShort }
    val onNextVideo: (() -> Unit)? = if (navigator.queue.isNotEmpty() || nextRelated != null) ({ if (navigator.playNext() == null) nextRelated?.let { navigator.replaceWithVideo(it) } }) else null
    val onPreviousVideo: () -> Unit = { if (navigator.playPrevious() == null) host?.session?.seekTo(0) }
    var submitting by remember(video.id) { mutableStateOf<String?>(null) }

    PlatformBackHandler(enabled = window?.isFullscreen == true) { window?.toggleFullscreen() }

    if (host != null && (window?.isFullscreen == true || inPip)) {
        // Swipe down on the picture brings the video back to portrait: it follows the finger, then either leaves or springs back.
        var fsDrag by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
        val leaveAt = with(density) { 90.dp.toPx() }
        PlayerView(
            host, video, chapters, markers,
            seekStepSeconds = seekStep, holdSpeed = holdSpeedSetting, onPrevious = onPreviousVideo, onNext = onNextVideo,
            modifier = modifier.fillMaxSize()
                .then(if (inPip) Modifier else Modifier.playerVerticalSwipe(
                    onDelta = { dy -> fsDrag = (fsDrag + dy).coerceAtLeast(0f) },
                    onEnd = { total, v ->
                        if (total > leaveAt || v > 1200f) window?.toggleFullscreen()
                        else scope.launch { androidx.compose.animation.core.animate(fsDrag, 0f) { x, _ -> fsDrag = x } }
                    },
                ))
                .graphicsLayer { translationY = fsDrag; alpha = 1f - (fsDrag / (leaveAt * 4f)).coerceIn(0f, 0.4f) },
            fullscreen = true, controlsEnabled = !inPip, onBack = if (inPip) null else ({ window?.toggleFullscreen() }),
        )
        return
    }

    val player = host?.controllerState?.collectAsState()?.value
    val position = player?.positionMs ?: 0
    val current = currentChapter(chapters, position)
    // An ended stream is recognised by its chat, not by NewPipe's stream type (measured: a real stream replay came back as a normal video).
    // One anonymous request per long non-live video page; live streams show the panel at once.
    val liveNow = video.isLive || info?.isLive == true
    var replayChat by remember(video.id) { mutableStateOf<com.greenaltambe.vela.extractor.ChatChannel?>(null) }
    androidx.compose.runtime.LaunchedEffect(video.id, info) {
        val i = info
        if (services.liveChat != null && !liveNow && i != null && (i.wasLive || i.durationSeconds > 900)) replayChat = runCatching { services.liveChat.open(video.id) }.getOrNull()?.takeIf { it.isReplay }
    }
    val uriHandler = LocalUriHandler.current

    val attemptLog by services.log.attempts.collectAsState()
    val resolveStart = androidx.compose.runtime.remember(video.id) { kotlin.time.Clock.System.now() }
    val resolvingNote = resolveNote(attemptLog, video.id, resolveStart, services.accounts.active.value.isSignedIn, services.settings.resolve(com.greenaltambe.vela.core.settings.Settings.accountFallbackMode) != com.greenaltambe.vela.core.settings.AccountFallbackMode.NEVER)

    val playerBox: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).testTag("video.player")) {
            if (host != null) PlayerView(
                host, video, chapters, markers, Modifier.fillMaxWidth(), onBack = { navigator.back() }, seekStepSeconds = seekStep, holdSpeed = holdSpeedSetting, onPrevious = onPreviousVideo, onNext = onNextVideo,
                isLive = info?.isLive == true, resolvingNote = resolvingNote,
                openAgeSetting = { navigator.selectTab(com.greenaltambe.vela.ui.nav.Route.Settings) },
                tryAccountNow = services.accounts.active.value.takeIf { it.isSignedIn }?.let { a -> { services.governors.forAccount(a.id).tryNow() } }, showChapterTitle = size != WindowSizeClass.COMPACT,
                overlay = { SponsorBlockOverlay(prompt, notice, onAccept = modules.sponsorBlock::acceptPrompt, onUndo = { n -> modules.sponsorBlock.undo(n); notice = null }) },
            ) else Box(Modifier.fillMaxWidth().padding(Vela.gutter), contentAlignment = Alignment.Center) {
                Text("No video player is available on this system: nothing will play. (On Linux install mpv-libs.)", Modifier.testTag("video.noPlayer"), maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    val details: LazyListScope.() -> Unit = {
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
                // A long title is cut at three lines; tapping it shows all of it, tapping again folds it back.
                var titleOpen by remember(video.id) { mutableStateOf(false) }
                Text(shownTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, maxLines = if (titleOpen) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { titleOpen = !titleOpen }.testTag("video.title"))
                if (titleReplaced) LinkButton(if (showOriginalTitle) "Original title · use DeArrow" else "DeArrow title · show original", { showOriginalTitle = !showOriginalTitle }, Modifier.testTag("video.toggleOriginal"))
                val meta = listOfNotNull(formatViews(info?.viewCount ?: video.viewCount), info?.likeCount?.let { "${compact(it)} likes" }).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Vela.colors.onSurfaceVariant.toColor())
                infoError?.let { Text("Details unavailable: $it", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clip(Vela.mediumShape).clickableChannel { (info?.channelId?.takeIf { it.isNotBlank() } ?: video.channelId).let { id -> navigator.push(Route.Channel(id, info?.channelName?.takeIf { it.isNotBlank() } ?: video.channelName, if (id.isBlank()) video.id else "")) } }.padding(horizontal = Vela.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
                val channelName = info?.channelName?.takeIf { it.isNotBlank() } ?: video.channelName
                // The picture: the one the listing carried, else the video's own details, else the channel's picture from your subscriptions (a library or history entry has none).
                val avatarUrl = video.channelAvatarUrl ?: info?.channelAvatarUrl ?: remember(info?.channelId, video.channelId) { services.subscriptions.all().firstOrNull { it.channelId == (info?.channelId?.takeIf { c -> c.isNotBlank() } ?: video.channelId) }?.avatarUrl }
                Avatar(channelName, url = avatarUrl)
                Text(channelName, Modifier.weight(1f).testTag("video.channel"), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // The channel id the video's own details give (a real UC… id) beats what a listing carried, which can be empty or a @handle: the account
                // rejects those with HTTP 400.
                val chId = info?.channelId?.takeIf { it.startsWith("UC") } ?: video.channelId
                // Optimistic: the local table and the button change at once; the account call follows, and a failure puts both back and says why.
                if (subscribed) OutlinedButton(onClick = {
                    services.subscriptions.unsubscribe(chId); subscribed = false
                    scope.launch { (services.actions?.unsubscribe(chId) as? ActionResult.Failed)?.let { services.subscriptions.subscribe(chId, video.channelName, video.channelAvatarUrl ?: info?.channelAvatarUrl, services.clock.now()); subscribed = true; actionError = "Could not unsubscribe on the account: ${it.message}" } }
                }, modifier = Modifier.testTag("video.subscribe")) { Text("Subscribed", maxLines = 1) }
                else PrimaryButton("Subscribe", onClick = {
                    services.subscriptions.subscribe(chId, video.channelName, video.channelAvatarUrl ?: info?.channelAvatarUrl, services.clock.now()); subscribed = true
                    scope.launch { (services.actions?.subscribe(chId) as? ActionResult.Failed)?.let { services.subscriptions.unsubscribe(chId); subscribed = false; actionError = "Could not subscribe on the account: ${it.message}" } }
                }, modifier = Modifier.testTag("video.subscribe"))
            }
        }
        if (services.liveChat != null && (liveNow || replayChat != null)) item { LiveChatPanel(services.liveChat, video.id, replay = !liveNow, position = position, preopened = replayChat) }
        if (actionError != null || actionNote != null) item {
            Text(actionError ?: actionNote.orEmpty(), color = if (actionError != null) Vela.colors.error.toColor() else androidx.compose.ui.graphics.Color.Unspecified,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag(if (actionError != null) "video.actionError" else "video.actionNote"))
        }
        item {
            var saveMenu by remember { mutableStateOf(false) }
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            // Five actions do not fit one phone width at full label size: they keep their natural size and the row scrolls sideways when it must, never clipping one.
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Spread edge to edge when they fit (no wasted margin at the sides); when they do not, the row is as wide as it needs and scrolls.
            Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).widthIn(min = maxWidth).testTag("video.actions"), horizontalArrangement = Arrangement.SpaceBetween) {
                // The viewer's own like and dislike (account only); the counts next to them are Return YouTube Dislike's. With RYD off or
                // unreachable there is no count: the like says "Like", the dislike button stays and says "Dislike".
                val canRate = services.actions != null && services.accounts.active.value.isSignedIn
                fun rate(target: VideoRating) {
                    if (!canRate) { actionNote = "Sign in to rate videos"; return }
                    val (wasLiked, wasDisliked) = liked to disliked
                    liked = target == VideoRating.LIKE; disliked = target == VideoRating.DISLIKE
                    scope.launch { (services.actions!!.rate(video.id, target) as? ActionResult.Failed)?.let { liked = wasLiked; disliked = wasDisliked; actionError = "Could not rate on the account: ${it.message}" } }
                }
                ActionChip(Vela.icons.like, votes?.let { compact(it.likes) } ?: "Like", { rate(if (liked) VideoRating.NONE else VideoRating.LIKE) }, Modifier.testTag("video.votes"), selected = liked)
                ActionChip(Vela.icons.dislike, votes?.let { compact(it.dislikes) } ?: "Dislike", { rate(if (disliked) VideoRating.NONE else VideoRating.DISLIKE) }, Modifier.testTag("video.dislike"), selected = disliked)
                if (services.downloads != null) {
                    if (offline != null) ActionChip(Vela.icons.downloaded, "Saved", {}, Modifier.testTag("video.downloaded"), selected = true)
                    else ActionChip(Vela.icons.download, "Download", { downloadDialog = true }, Modifier.testTag("video.download"))
                }
                Box {
                    ActionChip(Vela.icons.save, "Save", { saveMenu = true }, Modifier.testTag("video.save"))
                    DropdownMenu(saveMenu, { saveMenu = false }) {
                        if (services.actions != null && services.accounts.active.value.isSignedIn) DropdownMenuItem(
                            text = { Text("Watch later (YouTube)", maxLines = 1) },
                            onClick = { saveMenu = false; scope.launch { when (val r = services.actions.addToPlaylist("WL", video.id)) { is ActionResult.Failed -> actionError = "Could not add to Watch later: ${r.message}"; else -> actionNote = "Added to Watch later" } } },
                            modifier = Modifier.testTag("video.saveWatchLater"),
                        )
                        services.playlists.all().forEach { list ->
                            val has = services.playlists.contains(list.id, video.id)
                            DropdownMenuItem(
                                text = { Text((if (has) "✓ " else "") + list.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = { if (has) services.playlists.remove(list.id, video.id) else services.playlists.add(list.id, listOf(video), services.clock.now()); saveMenu = false },
                            )
                        }
                    }
                }
                val share = com.greenaltambe.vela.ui.platform.LocalShare.current
                ActionChip(Vela.icons.share, "Share", {
                    val link = "https://youtu.be/${video.id}"
                    if (share != null) share(shownTitle, link) else clipboard.setText(AnnotatedString(link))
                }, Modifier.testTag("video.share"))
            }
            }
        }
        info?.takeIf { it.description.isNotBlank() }?.let { d ->
            item {
                val linkColor = Vela.colors.primary.toColor()
                val text = remember(d.description, linkColor) { linkedText(d.description, linkColor) { seconds -> host?.session?.seekTo(seconds * 1000) } }
                // The "more" button is a full touch target tall, so the card gets less padding under it.
                Column(Modifier.fillMaxWidth().clip(Vela.mediumShape).background(Vela.colors.surface.toColor()).padding(start = Vela.md, end = Vela.md, top = Vela.md, bottom = Vela.xs)) {
                    Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("video.description"))
                    LinkButton(if (descriptionExpanded) "Show less" else "more", { descriptionExpanded = !descriptionExpanded }, Modifier.testTag("video.descriptionToggle"))
                }
            }
        }
        if (host != null && services.settings.resolve(com.greenaltambe.vela.core.settings.Settings.showContribute)) item {
            ContributeSection(modules = modules, videoId = video.id, positionMs = player?.positionMs ?: 0, durationMs = player?.durationMs ?: 0, status = submitting, onStatus = { submitting = it })
        }
        // Comments are one wide button, right under the description (also in the wide layout, where related videos sit in the side column): it opens the panel here.
        item {
            Row(
                Modifier.fillMaxWidth().clip(Vela.mediumShape).background(Vela.colors.surface.toColor()).clickable { commentsOpen = true }.heightIn(min = Vela.minTouch).padding(horizontal = Vela.md).testTag("video.commentsButton"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm),
            ) {
                Text("Comments", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                if (comments.isNotEmpty()) Text("${comments.size}${if (commentsNext != null) "+" else ""}", style = MaterialTheme.typography.bodyMedium, color = Vela.colors.onSurfaceVariant.toColor())
                Icon(Vela.icons.chevronRight, null, tint = Vela.colors.onSurfaceVariant.toColor())
            }
        }
    }

    // Autoplay: off unless switched on (here or in Settings). When this video ends, the queue's next video plays, else the first related one.
    val settingsRevision by services.settings.revision.collectAsState()
    val autoplay = remember(settingsRevision) { services.settings.resolve(com.greenaltambe.vela.core.settings.Settings.autoplayNext) }
    var autoplayed by remember(video.id) { mutableStateOf(false) }
    LaunchedEffect(player?.status, autoplay) {
        if (!autoplay || autoplayed || offline != null || player?.status != com.greenaltambe.vela.player.PlaybackStatus.ENDED) return@LaunchedEffect
        autoplayed = true
        if (navigator.playNext() == null) relatedShown.firstOrNull { it.id != video.id && !it.isShort }?.let { navigator.replaceWithVideo(it) }
    }

    val secondary: LazyListScope.() -> Unit = {
        if (relatedShown.isNotEmpty()) {
            item {
                SectionHeader("Related") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
                        Text("Autoplay", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        com.greenaltambe.vela.ui.look.VelaSwitch(autoplay, { services.settings.set(com.greenaltambe.vela.core.settings.Settings.autoplayNext, it) }, Modifier.testTag("video.autoplay"))
                    }
                }
            }
            items(relatedShown, key = { "rel-${it.id}" }) { v ->
                // On a phone-sized MEDIUM window the column beside the player is narrow: a thumbnail across its width reads better than a tiny one.
                VideoCard(v, onClick = { navigator.replaceWithVideo(v) }, style = if (size == WindowSizeClass.MEDIUM) VideoCardStyle.VERTICAL else VideoCardStyle.HORIZONTAL, onChannelClick = { navigator.push(Route.Channel(v.channelId, v.channelName)) }, trailing = { VideoMenu(services, v) })
            }
        }
    }

    // The comments sit in the room under the picture (never over it); the same panel serves the phone layout and the wide one.
    PlatformBackHandler(enabled = commentsOpen) { commentsOpen = false }
    val commentsPanel: @Composable (Modifier) -> Unit = { m ->
        androidx.compose.animation.AnimatedVisibility(commentsOpen, m, enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut()) {
            CommentsPanel(
                comments, commentsState, hasMore = commentsNext != null, loadingMore = commentsLoading,
                canSort = services.extractor.capabilities.supportsCommentSort, newestFirst = sort == CommentSort.NEWEST,
                onToggleSort = { sort = if (sort == CommentSort.TOP) CommentSort.NEWEST else CommentSort.TOP },
                replies = replies, repliesLoading = repliesLoading,
                onToggleReplies = { c ->
                    if (replies.containsKey(c.id)) replies = replies - c.id
                    else {
                        repliesLoading = repliesLoading + c.id
                        scope.launch {
                            val r = services.extractor.replies(video.id, c.id, c.repliesContinuation)
                            replies = replies + (c.id to ((r as? ExtractorResult.Success)?.value?.comments ?: emptyList()))
                            repliesLoading = repliesLoading - c.id
                        }
                    }
                },
                onSeek = { seconds -> host?.session?.seekTo(seconds * 1000) },
                onLoadMore = { loadComments(more = true) }, onDismiss = { commentsOpen = false }, modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (downloadDialog) DownloadDialog(services, listOf(video), onDismiss = { downloadDialog = false })

    val gap = Arrangement.spacedBy(Vela.sm)
    // Two columns (related videos beside the player) only in a really wide window; narrower ones, desktop windows included, stack like the phone.
    val twoColumn = LocalWindowWidthDp.current >= 1000f
    var fromRest by remember { mutableStateOf(true) }
    var pulledDown by remember { mutableStateOf(false) }
    if (!twoColumn) {
        val corner = Vela.look.radii.largeDp
        val topInsetPx = WindowInsets.safeDrawing.getTop(density)
        Box(
            // The gesture sits on this untransformed wrapper (the page below shrinks as it is dragged), and only counts when it starts on the picture.
            modifier.fillMaxSize().onSizeChanged { pageHeightPx = it.height.toFloat().coerceAtLeast(1f); pageWidthPx = it.width.toFloat() }
                .playerVerticalSwipe(
                    // A swipe up opens fullscreen only when it starts on a page at full size and never pulled it down first: changing your mind
                    // halfway through minimising just puts the page back.
                    startsOn = { at -> fromRest = dragPx <= 0f; pulledDown = false; dragPx > 0f || at.y < topInsetPx + pageWidthPx * 9f / 16f },
                    onDelta = { dy -> if (dy > 0f) pulledDown = true; if (dragPx > 0f || dy > 0f) setDrag((dragPx + dy).coerceAtLeast(0f)) },
                    onEnd = { total, v ->
                        if (fromRest && !pulledDown && (total < -with(density) { 60.dp.toPx() } || v < -1000f)) { setDrag(0f); window?.toggleFullscreen() }   // swipe up: landscape fullscreen
                        else scope.launch { settlePage(v) }
                    },
                ),
        ) {
        // Only the picture shrinks, travelling to where the mini player's picture will be; the rest of the page fades away, uncovering the screen behind.
        val miniPip = miniOnRight
        val pipPos = com.greenaltambe.vela.ui.nav.LocalPipPosition.current
        val miniWidthPx = with(density) { (if (miniPip) com.greenaltambe.vela.ui.components.PIP_WIDTH else 64.dp).toPx() }
        val marginPx = with(density) { 16.dp.toPx() }
        val bottomClearPx = with(density) { 112.dp.toPx() }
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier.graphicsLayer {
                    val p = (dragPx / (pageHeightPx * 0.5f)).coerceIn(0f, 1f)
                    val k = miniWidthPx / pageWidthPx.coerceAtLeast(1f)               // the mini picture's size as a fraction of the page width
                    val s = 1f + (k - 1f) * p
                    val miniH = miniWidthPx * 9f / 16f
                    val pipAt = pipPos?.pos
                    val targetLeft = if (miniPip) (pipAt?.x ?: (pageWidthPx - marginPx - miniWidthPx)) else marginPx + with(density) { 8.dp.toPx() }
                    val targetTop = (if (miniPip) (pipAt?.y ?: (pageHeightPx - bottomClearPx - miniH)) else pageHeightPx - bottomClearPx - miniH) - k * topInsetPx
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    scaleX = s; scaleY = s
                    translationX = targetLeft * p; translationY = targetTop * p
                    if (p > 0f) { clip = true; shape = androidx.compose.foundation.shape.RoundedCornerShape((corner * p * 0.6f).dp) }
                },
            ) { playerBox() }
            Box(Modifier.weight(1f).fillMaxWidth().graphicsLayer { alpha = (1f - 1.8f * (dragPx / (pageHeightPx * 0.5f))).coerceIn(0f, 1f) }) {
                LazyColumn(Modifier.fillMaxSize().testTag("video.list"), state = detailsState, contentPadding = Vela.contentPadding, verticalArrangement = gap) { details(); secondary() }
                commentsPanel(Modifier.fillMaxSize())
            }
        }
        }
    } else {
        Row(modifier.fillMaxSize()) {
            Column(Modifier.weight(1.6f).fillMaxSize()) {
                playerBox()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(Modifier.fillMaxSize().testTag("video.list"), state = detailsState, contentPadding = Vela.contentPadding, verticalArrangement = gap) { details() }
                    commentsPanel(Modifier.fillMaxSize())
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxSize().testTag("video.side"), state = sideState, contentPadding = Vela.contentPadding, verticalArrangement = gap) { secondary() }
        }
    }
}

private fun Modifier.clickableChannel(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(onClick = onClick))

/** The stored page of a download as the [VideoInfo] the page works with. */
private fun storedVideoInfo(o: OfflineVideo): VideoInfo = VideoInfo(
    id = o.download.videoId,
    title = o.info?.title ?: o.download.title,
    description = o.description,
    channelId = o.download.channelId,
    channelName = o.info?.channelName.orEmpty(),
    durationSeconds = o.info?.durationSeconds ?: 0,
    viewCount = o.info?.viewCount,
    likeCount = o.info?.likeCount,
    chapters = o.chapters,
)

/**
 * Read-only chat under a live video: polled anonymously every few seconds only while the panel is open, stopped when it closes or the page
 * is left, at most the last 100 lines kept. If the video has no chat (or it cannot be reached) it says so and stays quiet. Replay chat of an
 * ended stream is read in order, NOT synchronised to the playback position.
 */
@Composable
private fun LiveChatPanel(source: com.greenaltambe.vela.extractor.LiveChatSource, videoId: String, replay: Boolean, position: Long, preopened: com.greenaltambe.vela.extractor.ChatChannel? = null) {
    var open by remember(videoId) { mutableStateOf(false) }
    var lines by remember(videoId) { mutableStateOf<List<com.greenaltambe.vela.extractor.ChatMessage>>(emptyList()) }
    var status by remember(videoId) { mutableStateOf<String?>(null) }
    val playhead = androidx.compose.runtime.rememberUpdatedState(position)
    androidx.compose.runtime.LaunchedEffect(videoId, open) {
        if (!open) return@LaunchedEffect
        status = "Connecting to chat…"
        val chat = preopened ?: source.open(videoId)
        if (chat == null) { status = if (replay) "Chat replay unavailable for this video." else "This video has no live chat."; return@LaunchedEffect }
        status = if (chat.isReplay) "Chat replay, synchronised with the video" else null
        var failures = 0
        if (chat.isReplay) {
            // Replay: messages carry the video time they appeared at. Fetch ahead of the playhead (at most one request a second, only when less than
            // 20 s are buffered), show what is at or before the playhead, and start again from the new position after a seek either way.
            var buffer = emptyList<com.greenaltambe.vela.extractor.ChatMessage>(); var fetchedUntil = -1L; var lastPos = playhead.value; var exhausted = false
            if (lastPos > 3_000) { chat.seek(lastPos); fetchedUntil = -1 }
            while (true) {
                val p = playhead.value
                if (p < lastPos - 2_000 || (fetchedUntil >= 0 && p > fetchedUntil + 15_000)) { chat.seek(p); buffer = emptyList(); fetchedUntil = -1; exhausted = false }
                lastPos = p
                if (!exhausted && fetchedUntil < p + 20_000) {
                    try {
                        val batch = chat.poll().also { failures = 0 }
                        buffer = (buffer + batch.messages.filter { it.offsetMs != null }).sortedBy { it.offsetMs }.takeLast(600)
                        fetchedUntil = maxOf(fetchedUntil, batch.messages.mapNotNull { it.offsetMs }.maxOrNull() ?: (p + 5_000))
                        if (batch.ended) exhausted = true
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                        if (++failures >= 3) { status = "Chat replay stopped: ${e.message?.take(80) ?: "unreachable"}"; return@LaunchedEffect }
                        kotlinx.coroutines.delay(4_000L * failures)
                    }
                }
                lines = buffer.filter { (it.offsetMs ?: Long.MAX_VALUE) <= p }.takeLast(100)
                kotlinx.coroutines.delay(1_000)
            }
        }
        while (true) {
            val batch = try { chat.poll().also { failures = 0 } } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                if (++failures >= 3) { status = "Chat stopped: ${e.message?.take(80) ?: "unreachable"}"; return@LaunchedEffect }
                kotlinx.coroutines.delay(5_000L * failures); continue
            }
            if (batch.messages.isNotEmpty()) lines = (lines + batch.messages).takeLast(100)
            if (batch.ended) { status = "Chat ended."; return@LaunchedEffect }
            kotlinx.coroutines.delay(batch.nextPollMs)
        }
    }
    Column(Modifier.fillMaxWidth().testTag("video.chat")) {
        OutlinedButton(onClick = { open = !open }, modifier = Modifier.testTag("video.chat.toggle")) { Text(if (open) "Hide chat" else if (replay) "Show chat replay" else "Show live chat", maxLines = 1) }
        if (open) {
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("video.chat.status")) }
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(androidx.compose.foundation.rememberScrollState(), reverseScrolling = true).testTag("video.chat.lines")) {
                lines.forEach { m ->
                    val paid = m.kind != com.greenaltambe.vela.extractor.ChatKind.TEXT
                    Text(buildString { append(m.author); if (m.amount != null) append(" (${m.amount})"); append(": "); append(m.text) },
                        style = MaterialTheme.typography.bodySmall, fontWeight = if (paid) androidx.compose.ui.text.font.FontWeight.Bold else null, modifier = Modifier.padding(vertical = 1.dp))
                }
            }
        }
    }
}
