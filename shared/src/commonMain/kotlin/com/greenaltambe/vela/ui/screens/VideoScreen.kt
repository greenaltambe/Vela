package com.greenaltambe.vela.ui.screens

import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.Spacing
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.animation.animateContentSize
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
import com.greenaltambe.vela.ui.components.HScrollableRow
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
import com.greenaltambe.vela.ui.components.bleed
import com.greenaltambe.vela.ui.components.SubscribeButton
import com.greenaltambe.vela.ui.components.formatAge
import com.greenaltambe.vela.ui.components.formatCount
import com.greenaltambe.vela.ui.look.pressable
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import com.greenaltambe.vela.ui.platform.LocalInPictureInPicture
import com.greenaltambe.vela.ui.platform.LocalWindowControls
import com.greenaltambe.vela.ui.platform.PlatformBackHandler
import com.greenaltambe.vela.ui.player.PlayerView
import com.greenaltambe.vela.ui.player.playerVerticalSwipe
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.graphicsLayer
import com.greenaltambe.vela.ui.player.SeekMarker
import androidx.compose.animation.togetherWith
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
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
fun VideoScreen(
    services: AppServices,
    navigator: Navigator,
    video: VideoSummary,
    startSeconds: Long?,
    /** Where the page is between mini player (0) and full (1). A provider, so each animation frame is read in layout/draw only and never recomposes this page. */
    sheetProgress: () -> Float = { 1f },
    onBack: (() -> Unit)? = null,
    onExpand: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** The width the open page has, in pixels, when it is not the whole window (beside the desktop rail). Null: the window's width. */
    pageWidthPx: Float? = null,
) {
    val scope = rememberCoroutineScope()
    val host = services.player
    val window = LocalWindowControls.current
    val inPip = LocalInPictureInPicture.current
    val size = LocalWindowSize.current
    val progressNow = { sheetProgress().coerceIn(0f, 1f) }
    // Coarse flags for composition: they flip at a threshold, so the page recomposes then and not on every frame in between.
    val fullyExpanded by remember { derivedStateOf { progressNow() > 0.85f } }
    val notFullyOpen by remember { derivedStateOf { progressNow() < 0.99f } }
    val showDetailsPane by remember { derivedStateOf { progressNow() > 0.15f } }
    val detailsScrollable by remember { derivedStateOf { progressNow() > 0.95f } }
    val detailsKept = remember { mutableStateOf(false) }
    val tapToExpand by remember { derivedStateOf { progressNow() < 0.5f } }

    // A finished download plays from its file and shows its stored page: no stream, info, comment or related request is made.
    val offline = remember(video.id) { services.downloads?.offline(video.id) }
    val pageCache = com.greenaltambe.vela.ui.screens.LocalVideoPageCache.current
    val cached = remember(video.id) { if (offline == null) pageCache?.get(video.id, services.clock.now()) else null }
    var info by remember(video.id) { mutableStateOf<VideoInfo?>(offline?.let(::storedVideoInfo) ?: cached?.info) }
    var infoError by remember(video.id) { mutableStateOf<String?>(null) }
    var related by remember(video.id) { mutableStateOf<List<VideoSummary>>(cached?.related.orEmpty()) }
    var comments by remember(video.id) { mutableStateOf<List<Comment>>(offline?.comments ?: cached?.comments.orEmpty()) }
    var commentsNext by remember(video.id) { mutableStateOf<String?>(cached?.commentsNext) }
    var commentTotal by remember(video.id) { mutableStateOf<Long?>(cached?.commentTotal) }
    var commentsOpen by remember(video.id) { mutableStateOf(false) }
    var chaptersOpen by remember(video.id) { mutableStateOf(false) }
    var commentsState by remember(video.id) { mutableStateOf<ListState>(if (offline != null) (if (offline.comments.isEmpty()) ListState.Empty("No saved comments.") else ListState.Ready) else if (cached?.comments?.isNotEmpty() == true) ListState.Ready else ListState.Loading) }
    var commentsLoading by remember(video.id) { mutableStateOf(false) }
    var sort by remember(video.id) { mutableStateOf(cached?.commentSort ?: CommentSort.TOP) }
    // The order the shown list really has (a tier can fall back to Top), and what to tell the viewer when it differs from what they asked for.
    var appliedSort by remember(video.id) { mutableStateOf(cached?.commentSort ?: CommentSort.TOP) }
    var sortNote by remember(video.id) { mutableStateOf<String?>(null) }
    var moreCommentsFailed by remember(video.id) { mutableStateOf(false) }
    var skipFirstComments by remember(video.id) { mutableStateOf(cached?.comments?.isNotEmpty() == true) }
    val replies = remember(video.id) { RepliesModel(services, scope, video.id) }
    var descriptionOpen by remember(video.id) { mutableStateOf(false) }
    var downloadDialog by remember(video.id) { mutableStateOf(false) }
    var shareOpen by remember(video.id) { mutableStateOf(false) }
    var saveMenu by remember(video.id) { mutableStateOf(false) }
    var saveRevision by remember(video.id) { mutableStateOf(0) }
    var subscribed by remember(video.id, video.channelId) { mutableStateOf(services.subscriptions.isSubscribed(video.channelId)) }
    LaunchedEffect(info?.channelId) { info?.channelId?.takeIf { it.startsWith("UC") }?.let { subscribed = services.subscriptions.isSubscribed(it) } }
    var liked by remember(video.id) { mutableStateOf(false) }
    var disliked by remember(video.id) { mutableStateOf(false) }
    var actionError by remember(video.id) { mutableStateOf<String?>(null) }
    var actionNote by remember(video.id) { mutableStateOf<String?>(null) }
    // Messages about what you did (queued, saved to the account, could not rate...) come as a toast over the page, not as a line that pushes the buttons down.
    val actionToasts = com.greenaltambe.vela.ui.look.LocalUndoToasts.current
    LaunchedEffect(actionError) { actionError?.let { actionToasts.show(it) } }
    LaunchedEffect(actionNote) { actionNote?.let { actionToasts.show(it) } }
    LaunchedEffect(actionNote) {
        if (actionNote != null) {
            delay(4_000)
            actionNote = null
        }
    }
    LaunchedEffect(actionError) {
        if (actionError != null) {
            delay(5_000)
            actionError = null
        }
    }
    // Created before any early return so the scroll positions survive fullscreen and Picture-in-Picture.
    val detailsState = rememberLazyListState()
    val sideState = rememberLazyListState()
    val containerSize = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val density = androidx.compose.ui.platform.LocalDensity.current

    val seekStep = services.settings.resolve(com.greenaltambe.vela.core.settings.Settings.seekStepSeconds).toInt()
    val holdSpeedSetting = services.settings.resolve(com.greenaltambe.vela.core.settings.Settings.holdSpeed)

    fun loadComments(more: Boolean) {
        if (commentsLoading) return
        commentsLoading = true
        if (!more) commentsState = ListState.Loading
        scope.launch {
            when (val r = services.extractor.comments(video.id, sort, commentsNext.takeIf { more })) {
                is ExtractorResult.Success -> {
                    comments = if (more) (comments + r.value.comments).distinctBy { it.id } else r.value.comments
                    commentsNext = r.value.continuation
                    if (!more) r.value.totalCount?.let { commentTotal = it }
                    if (!more) {
                        appliedSort = r.value.appliedSort ?: sort
                        sortNote = if (appliedSort != sort) "Newest first is not available right now. Showing top comments." else null
                    }
                    moreCommentsFailed = false
                    commentsState = if (comments.isEmpty()) ListState.Empty("No comments yet.") else ListState.Ready
                }
                is ExtractorResult.Failure -> when {
                    more -> moreCommentsFailed = true
                    comments.isEmpty() -> commentsState = ListState.Error("Comments unavailable: ${r.error.message}") { loadComments(false) }
                    else -> {
                        // The new order could not be loaded: keep the list that is on screen and say so, so the order shown and the chip agree.
                        commentsState = ListState.Ready
                        sortNote = "Could not load that order. Showing ${if (appliedSort == CommentSort.NEWEST) "newest" else "top"} comments."
                        skipFirstComments = true; sort = appliedSort
                    }
                }
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
            if (offline != null) {
                val saved = offline.info?.subtitles.orEmpty().map { sub ->
                    val address = com.greenaltambe.vela.ui.platform.OfflineSubtitles.address(video.id, sub.languageCode)
                    com.greenaltambe.vela.ui.platform.OfflineSubtitles.put(address, sub.vtt)
                    com.greenaltambe.vela.extractor.CaptionTrack(sub.languageCode, sub.label, address, sub.autoGenerated)
                }
                host.session.startLocal(video.id, PlaybackPlan.Local(offline.uri, offline.hasVideo), offline.branding?.title ?: offline.download.title, offline.download.channelId, saved)
            }
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
    LaunchedEffect(video.id, sort) {
        if (offline == null) { if (skipFirstComments) skipFirstComments = false else loadComments(more = false) }
    }
    // What this page loaded is kept for the next time it is opened (see VideoPageCache).
    androidx.compose.runtime.SideEffect {
        if (offline == null && pageCache != null && (info != null || related.isNotEmpty() || comments.isNotEmpty())) pageCache.put(video.id, VideoPageCache.Entry(info, related, comments, commentsNext, services.clock.now(), commentTotal, appliedSort))
    }

    val chapters: List<Chapter> = remember(info) { info?.let { effectiveChapters(it.chapters, com.greenaltambe.vela.extractor.plainDescription(it.description), it.durationSeconds) }.orEmpty() }

    // ---- modules: none of these may delay or break anything above; each fills in when its answer arrives ----
    val modules = services.modules
    val markerColors = com.greenaltambe.vela.ui.theme.ExtraColors.markers
    val markerFallback = com.greenaltambe.vela.ui.theme.ExtraColors.onMedia
    val brandingRevision by modules.dearrow.revision.collectAsState()
    val votesRevision by modules.ryd.revision.collectAsState()
    val segments by modules.sponsorBlock.segments.collectAsState()
    val prompt by modules.sponsorBlock.prompt.collectAsState()
    var notice by remember(video.id) { mutableStateOf<SkipNotice?>(null) }
    var showOriginalTitle by remember(video.id) { mutableStateOf(false) }
    LaunchedEffect(video.id) { if (offline == null) modules.dearrow.prefetch(listOf(video.id)) }
    LaunchedEffect(video.id) { if (offline == null) modules.ryd.votes(video.id) }
    LaunchedEffect(video.id) { modules.sponsorBlock.notices.collectLatest { notice = it; delay(6_000); notice = null } }
    val markers = remember(segments, markerColors) {
        segments.map { s ->
            val color = markerColors[s.category] ?: markerFallback
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

    // Fullscreen belongs to the expanded page. Turning the phone sideways while the video plays in the mini player used to switch the window to
    // fullscreen and draw the fullscreen player into the little card (a stretched poster across the bar): leave fullscreen when the page is not open.
    val fullscreenNow = window?.isFullscreen == true && fullyExpanded
    // Fullscreen is one thing, whichever way it is left (back arrow, exit button, swipe down, the Back key): the window turns upright and you are on the
    // video page. Minimising to the mini player is a separate, portrait-only move (swipe the page down).
    val minimised by remember { derivedStateOf { progressNow() < 0.05f } }
    LaunchedEffect(window?.isFullscreen, minimised) { if (window?.isFullscreen == true && minimised && !inPip) window.toggleFullscreen() }
    val leaveFullscreen: () -> Unit = { window?.toggleFullscreen() }
    PlatformBackHandler(enabled = fullscreenNow) { leaveFullscreen() }

    if (host != null && (fullscreenNow || inPip)) {
        // Swipe down on the picture brings the video back to portrait: it follows the finger, then either leaves or springs back.
        var fsDrag by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
        val leaveAt = with(density) { 90.dp.toPx() }
        // The seek bar's gesture lock: a swipe may not start on the bar or within 12 dp of it, nor continue while a finger is on it.
        val gestureLock = com.greenaltambe.vela.ui.components.LocalPlayerGestureLock.current
        var swipeRoot by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        val bandPx = with(density) { com.greenaltambe.vela.ui.components.PlayerGestureLock.BAND_DP.dp.toPx() }
        PlayerView(
            host, video, chapters, markers,
            seekStepSeconds = seekStep, holdSpeed = holdSpeedSetting, onPrevious = onPreviousVideo, onNext = onNextVideo,
            modifier = modifier.fillMaxSize()
                .onGloballyPositioned { swipeRoot = it.positionInRoot() }
                .then(if (inPip) Modifier else Modifier.playerVerticalSwipe(
                    startsOn = { at -> gestureLock?.onBar(swipeRoot + at, bandPx) != true },
                    lock = gestureLock,
                    onDelta = { dy -> fsDrag = (fsDrag + dy).coerceAtLeast(0f) },
                    onEnd = { total, v ->
                        if (total > leaveAt || v > 1200f) leaveFullscreen()
                        else scope.launch { androidx.compose.animation.core.animate(fsDrag, 0f) { x, _ -> fsDrag = x } }
                    },
                ))
                .graphicsLayer { translationY = fsDrag; alpha = 1f - (fsDrag / (leaveAt * 4f)).coerceIn(0f, 0.4f) },
            fullscreen = true, controlsEnabled = !inPip, onBack = if (inPip) null else ({ leaveFullscreen() }),
        )
        return
    }

    // The page reads the player state rounded to 2 s: the seek bar and the time have their own live reads, and the rest of this big page
    // (lists, cards, panels) is not rebuilt twice a second while a video plays.
    val playerRaw = host?.controllerState?.collectAsState()
    val player by remember(playerRaw) { androidx.compose.runtime.derivedStateOf { playerRaw?.value?.let { it.copy(positionMs = it.positionMs / 2000 * 2000) } } }
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

    val settingsRevision by services.settings.revision.collectAsState()
    val autoplay = remember(settingsRevision) { services.settings.resolve(com.greenaltambe.vela.core.settings.Settings.autoplayNext) }
    val playerBox: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().testTag("video.player")) {
            if (host != null) PlayerView(
                host, video, chapters, markers, Modifier.fillMaxSize(), onBack = onBack ?: { navigator.back(); Unit }, seekStepSeconds = seekStep, holdSpeed = holdSpeedSetting, onPrevious = onPreviousVideo, onNext = onNextVideo,
                onOpenChapters = if (chapters.isNotEmpty()) ({ chaptersOpen = true; commentsOpen = false; descriptionOpen = false }) else null,
                autoplay = autoplay, onAutoplayChange = { services.settings.set(com.greenaltambe.vela.core.settings.Settings.autoplayNext, it) },
                controlsEnabled = (fullyExpanded && !inPip),
                isLive = info?.isLive == true, resolvingNote = resolvingNote,
                openAgeSetting = { navigator.selectTab(com.greenaltambe.vela.ui.nav.Route.Settings) },
                tryAccountNow = services.accounts.active.value.takeIf { it.isSignedIn }?.let { a -> { services.governors.forAccount(a.id).tryNow() } }, showChapterTitle = size != WindowSizeClass.COMPACT,
                overlay = { SponsorBlockOverlay(prompt, notice, onAccept = modules.sponsorBlock::acceptPrompt, onUndo = { n -> modules.sponsorBlock.undo(n); notice = null }) },
            ) else Box(Modifier.fillMaxSize().padding(Spacing.gutter), contentAlignment = Alignment.Center) {
                Text("No video player is available on this system: nothing will play. (On Linux install mpv-libs.)", Modifier.testTag("video.noPlayer"), maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    val details: LazyListScope.() -> Unit = {
        item {
            // The title (with its numbers) is the way to everything else about the video: tapping it opens the description sheet.
            // The press area reaches a little past the text on every side (and its corners are small), so the rounded highlight never cuts into the words.
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.surfaceContainerLow).pressable(role = androidx.compose.ui.semantics.Role.Button) { descriptionOpen = true; commentsOpen = false; chaptersOpen = false }
                    .padding(horizontal = Spacing.md, vertical = Spacing.md).testTag("video.titleBlock"),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(shownTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("video.title"))
                if (titleReplaced) LinkButton(if (showOriginalTitle) "Original title · use DeArrow" else "DeArrow title · show original", { showOriginalTitle = !showOriginalTitle }, Modifier.testTag("video.toggleOriginal"))
                val meta = listOfNotNull(formatViews(info?.viewCount ?: video.viewCount), formatAge(info?.uploadDate ?: video.uploadedAt, services.clock.now()) ?: video.publishedText?.takeIf { it.isNotBlank() }).joinToString(" · ")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (meta.isNotEmpty()) Text(meta, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // A taste of the description, so the card says what is inside before it is opened.
                val teaser = info?.description?.let { com.greenaltambe.vela.extractor.plainDescription(it) }?.trim()?.takeIf { it.isNotEmpty() }?.lineSequence()?.firstOrNull { it.isNotBlank() }
                if (teaser != null) Text(teaser, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // "…more" comes last, after the description line (or after the views line when there is no description).
                Text("…more", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("video.descriptionMore"))
                // A failed details request is a passing notice, not text on the page.
                val toasts = com.greenaltambe.vela.ui.look.LocalUndoToasts.current
                LaunchedEffect(infoError) { if (infoError != null) toasts.show("Some details couldn't be loaded right now.") }
            }
        }
        item {
            val channelName = info?.channelName?.takeIf { it.isNotBlank() } ?: video.channelName
            // The picture: the one the listing carried, else the video's own details, else the channel's picture from your subscriptions (a library or history entry has none).
            val avatarUrl = video.channelAvatarUrl ?: info?.channelAvatarUrl ?: remember(info?.channelId, video.channelId) { services.subscriptions.all().firstOrNull { it.channelId == (info?.channelId?.takeIf { c -> c.isNotBlank() } ?: video.channelId) }?.avatarUrl }
            // The channel id the video's own details give (a real UC… id) beats what a listing carried, which can be empty or a @handle: the account
            // rejects those with HTTP 400.
            val chId = info?.channelId?.takeIf { it.startsWith("UC") } ?: video.channelId
            // Optimistic: the local table and the button change at once; the account call follows, and a failure puts both back and says why.
            val toggleSubscription: () -> Unit = {
                    if (subscribed) {
                        services.subscriptions.unsubscribe(chId); subscribed = false
                        scope.launch { val r = services.actions?.unsubscribe(chId); (r as? ActionResult.Queued)?.let { actionNote = it.message }; (r as? ActionResult.Failed)?.let { services.subscriptions.subscribe(chId, video.channelName, video.channelAvatarUrl ?: info?.channelAvatarUrl, services.clock.now()); subscribed = true; actionError = "Could not unsubscribe on the account: ${it.message}" } }
                    } else {
                        services.subscriptions.subscribe(chId, video.channelName, video.channelAvatarUrl ?: info?.channelAvatarUrl, services.clock.now()); subscribed = true
                        scope.launch { val r = services.actions?.subscribe(chId); (r as? ActionResult.Queued)?.let { actionNote = it.message }; (r as? ActionResult.Failed)?.let { services.subscriptions.unsubscribe(chId); subscribed = false; actionError = "Could not subscribe on the account: ${it.message}" } }
                    }
            }
            // The name has priority: up to two lines beside ONE small control. Subscribe is a word button; once subscribed it becomes a bell + chevron
            // pill whose menu holds the notification level and Unsubscribe (see SubscribedPill).
            ChannelRow(
                name = channelName,
                subscribersText = formatCount(info?.subscriberCount)?.let { "$it subscribers" },
                avatar = { Avatar(channelName, url = avatarUrl) },
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickableChannel { (info?.channelId?.takeIf { it.isNotBlank() } ?: video.channelId).let { id -> navigator.push(Route.Channel(id, info?.channelName?.takeIf { it.isNotBlank() } ?: video.channelName, if (id.isBlank()) video.id else "")) } },
                actions = {
                    androidx.compose.animation.AnimatedContent(subscribed, transitionSpec = { (androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 380f), initialScale = 0.8f)) togetherWith androidx.compose.animation.fadeOut() }, label = "subscribeState") { isSubscribed ->
                        if (isSubscribed) SubscribedPill(services, chId, onUnsubscribe = toggleSubscription)
                        else SubscribeButton(false, onClick = toggleSubscription, modifier = Modifier.testTag("video.subscribe"))
                    }
                },
            )
        }
        if (services.liveChat != null && (liveNow || replayChat != null)) item { LiveChatPanel(services.liveChat, video.id, replay = !liveNow, position = position, preopened = replayChat) }
        item {
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            HScrollableRow(
                modifier = Modifier.fillMaxWidth(),
                rowModifier = Modifier.testTag("video.actions"),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                // The viewer's own like and dislike (account only); the counts next to them are Return YouTube Dislike's. With RYD off or
                // unreachable there is no count: the like says "Like", the dislike button stays and says "Dislike".
                val canRate = services.actions != null && services.accounts.active.value.isSignedIn
                fun rate(target: VideoRating) {
                    if (!canRate) { actionNote = "Sign in to rate videos"; return }
                    val (wasLiked, wasDisliked) = liked to disliked
                    liked = target == VideoRating.LIKE; disliked = target == VideoRating.DISLIKE
                    scope.launch { val r = services.actions!!.rate(video.id, target); (r as? ActionResult.Queued)?.let { actionNote = it.message }; (r as? ActionResult.Failed)?.let { liked = wasLiked; disliked = wasDisliked; actionError = "Could not rate on the account: ${it.message}" } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(androidx.compose.material3.ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    ActionChip(AppIcons.like, votes?.let { compact(it.likes) } ?: "Like", { rate(if (liked) VideoRating.NONE else VideoRating.LIKE) }, Modifier.testTag("video.votes"), selected = liked, selectedIcon = AppIcons.likeFilled, position = com.greenaltambe.vela.ui.components.ActionPosition.LEADING)
                    ActionChip(AppIcons.dislike, votes?.let { compact(it.dislikes) } ?: "Dislike", { rate(if (disliked) VideoRating.NONE else VideoRating.DISLIKE) }, Modifier.testTag("video.dislike"), selected = disliked, selectedIcon = AppIcons.dislikeFilled, position = com.greenaltambe.vela.ui.components.ActionPosition.TRAILING)
                }
                if (services.downloads != null) {
                    // The button follows the download itself: idle (download now / choose), in progress (a loader with the percentage, and a cancel), failed (retry),
                    // finished ("Saved": tap it to delete the download, after a question).
                    val manager = services.downloads.manager
                    val allDownloads by manager.items.collectAsState()
                    val dl = allDownloads.firstOrNull { it.videoId == video.id }
                    var askDelete by remember(video.id) { mutableStateOf(false) }
                    // Read again whenever the downloads change (a delete or a finished download must show here at once, not only after reopening the page).
                    val offlineNow = remember(video.id, allDownloads) { services.downloads.offline(video.id) }
                    val finished = dl?.state == com.greenaltambe.vela.data.DownloadState.COMPLETED || (dl == null && offlineNow != null)
                    when {
                        finished -> ActionChip(AppIcons.downloaded, "Saved", { askDelete = true }, Modifier.testTag("video.downloaded"), selected = true)
                        dl != null -> androidx.compose.material3.SplitButtonLayout(
                            leadingButton = {
                                androidx.compose.material3.SplitButtonDefaults.TonalLeadingButton(
                                    onClick = {
                                        when (dl.state) {
                                            com.greenaltambe.vela.data.DownloadState.PAUSED -> manager.resume(dl.id)
                                            com.greenaltambe.vela.data.DownloadState.FAILED -> manager.retry(dl.id)
                                            else -> manager.pause(dl.id)
                                        }
                                    },
                                    modifier = Modifier.testTag("video.download.progress"),
                                ) {
                                    val p = dl.progress
                                    if (dl.state == com.greenaltambe.vela.data.DownloadState.FAILED) Icon(AppIcons.refresh, null, Modifier.size(androidx.compose.material3.SplitButtonDefaults.LeadingIconSize))
                                    else if (p != null && dl.state == com.greenaltambe.vela.data.DownloadState.DOWNLOADING) androidx.compose.material3.CircularWavyProgressIndicator(progress = { p }, modifier = Modifier.size(24.dp))
                                    else androidx.compose.material3.CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(androidx.compose.material3.ButtonDefaults.IconSpacing))
                                    Text(
                                        when (dl.state) {
                                            com.greenaltambe.vela.data.DownloadState.FAILED -> "Retry"
                                            com.greenaltambe.vela.data.DownloadState.PAUSED -> "Paused" + (dl.progress?.let { " ${(it * 100).toInt()}%" } ?: "")
                                            com.greenaltambe.vela.data.DownloadState.QUEUED -> "Queued"
                                            com.greenaltambe.vela.data.DownloadState.MERGING -> "Finishing"
                                            else -> "Downloading" + (dl.progress?.let { " ${(it * 100).toInt()}%" } ?: "")
                                        }, maxLines = 1, softWrap = false,
                                    )
                                }
                            },
                            trailingButton = {
                                androidx.compose.material3.SplitButtonDefaults.TonalTrailingButton(checked = false, onCheckedChange = { manager.remove(dl.id) }, modifier = Modifier.testTag("video.download.cancel")) {
                                    Icon(AppIcons.close, "Cancel download", Modifier.size(androidx.compose.material3.SplitButtonDefaults.TrailingIconSize))
                                }
                            },
                        )
                        else -> androidx.compose.material3.SplitButtonLayout(
                        // Download starts at once with the download settings; the arrow opens the choices (quality, audio only).
                        leadingButton = {
                            androidx.compose.material3.SplitButtonDefaults.TonalLeadingButton(
                                onClick = {
                                    val st = services.settings
                                    services.player?.onBeforePlayback()
                                    services.downloads.manager.enqueue(com.greenaltambe.vela.downloads.DownloadRequest(video, audioOnly = st.resolve(com.greenaltambe.vela.core.settings.Settings.downloadsAudioOnlyDefault), maxHeight = st.resolve(com.greenaltambe.vela.core.settings.Settings.downloadsDefaultQuality).height, preferAvc = st.resolve(com.greenaltambe.vela.core.settings.Settings.downloadsPreferAvc)))
                                    actionNote = "Download queued"
                                },
                                modifier = Modifier.testTag("video.download"),
                            ) {
                                Icon(AppIcons.download, null, Modifier.size(androidx.compose.material3.SplitButtonDefaults.LeadingIconSize))
                                Spacer(Modifier.width(androidx.compose.material3.ButtonDefaults.IconSpacing))
                                Text("Download", maxLines = 1, softWrap = false)
                            }
                        },
                        trailingButton = {
                            androidx.compose.material3.SplitButtonDefaults.TonalTrailingButton(checked = false, onCheckedChange = { downloadDialog = true }, modifier = Modifier.testTag("video.download.options")) {
                                Icon(AppIcons.arrowDown, "Download options", Modifier.size(androidx.compose.material3.SplitButtonDefaults.TrailingIconSize))
                            }
                        },
                    )

                    }
                    if (askDelete) androidx.compose.material3.AlertDialog(
                        onDismissRequest = { askDelete = false },
                        title = { Text("Delete this download?") },
                        text = { Text("The saved copy is removed from this device. You can download it again later.") },
                        confirmButton = { androidx.compose.material3.TextButton(onClick = { dl?.let { manager.remove(it.id) }; askDelete = false; actionNote = "Download deleted" }, modifier = Modifier.testTag("video.download.deleteConfirm")) { Text("Delete") } },
                        dismissButton = { androidx.compose.material3.TextButton(onClick = { askDelete = false }) { Text("Keep") } },
                    )
                }
                val isSaved = rememberAsync(video.id, saveRevision, keepWhileLoading = true, first = { false }, compute = { services.playlists.all().any { services.playlists.contains(it.id, video.id) } })
                ActionChip(AppIcons.save, "Save", { saveMenu = true }, Modifier.testTag("video.save"), selected = isSaved, selectedIcon = AppIcons.saveFilled)
                ActionChip(AppIcons.share, "Share", { shareOpen = true }, Modifier.testTag("video.share"))
            }
        }
        if (host != null && services.settings.resolve(com.greenaltambe.vela.core.settings.Settings.showContribute)) item {
            ContributeSection(modules = modules, videoId = video.id, positionMs = player?.positionMs ?: 0, durationMs = player?.durationMs ?: 0, status = submitting, onStatus = { submitting = it })
        }
        // Comments are one wide button, right under the description (also in the wide layout, where related videos sit in the side column): it opens the panel here.
        item {
            // The comments card: the count, and a taste of the top comment (so the card says what is inside before it is opened).
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.surfaceContainerLow).clickable { commentsOpen = true; chaptersOpen = false; descriptionOpen = false }
                    .padding(horizontal = Spacing.md, vertical = Spacing.md).testTag("video.commentsButton"),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("Comments", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (comments.isNotEmpty()) Text(commentTotal?.let { formatCount(it) } ?: "${comments.size}${if (commentsNext != null) "+" else ""}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                comments.firstOrNull()?.let { top ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Avatar(top.author.removePrefix("@"), size = 28.dp, url = top.authorAvatarUrl)
                        Text(top.text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    // Autoplay: off unless switched on (here or in Settings). When this video ends, the queue's next video plays, else the first related one.
    var autoplayed by remember(video.id) { mutableStateOf(false) }
    LaunchedEffect(player?.status, autoplay) {
        if (!autoplay || autoplayed || offline != null || player?.status != com.greenaltambe.vela.player.PlaybackStatus.ENDED) return@LaunchedEffect
        autoplayed = true
        if (navigator.playNext() == null) relatedShown.firstOrNull { it.id != video.id && !it.isShort }?.let { navigator.replaceWithVideo(it) }
    }

    val secondary: LazyListScope.() -> Unit = {
        if (relatedShown.isNotEmpty()) {
            item {
                SectionHeader("Related")
            }
            items(relatedShown, key = { "rel-${it.id}" }) { v ->
                // On a phone-sized MEDIUM window the column beside the player is narrow: a thumbnail across its width reads better than a tiny one.
                VideoCard(v, onClick = { navigator.replaceWithVideo(v) }, style = if (size == WindowSizeClass.MEDIUM || size == WindowSizeClass.COMPACT) VideoCardStyle.VERTICAL else VideoCardStyle.HORIZONTAL, onChannelClick = { navigator.push(Route.Channel(v.channelId, v.channelName)) }, trailing = { VideoMenu(services, v) })
            }
        }
    }

    // The comments sit in the room under the picture (never over it); the same panel serves the phone layout and the wide one.
    PlatformBackHandler(enabled = commentsOpen) { commentsOpen = false }
    val commentsPanel: @Composable (Modifier) -> Unit = { m ->
        androidx.compose.animation.AnimatedVisibility(commentsOpen, m, enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut()) {
            CommentsPanel(
                comments, commentsState, hasMore = commentsNext != null, loadingMore = commentsLoading, totalCount = commentTotal,
                canSort = services.extractor.capabilities.supportsCommentSort && offline == null, sort = appliedSort, onSort = { sortNote = null; sort = it }, sortNote = sortNote, loadMoreFailed = moreCommentsFailed,
                replies = replies, creatorName = info?.channelName?.takeIf { it.isNotBlank() } ?: video.channelName, creatorAvatarUrl = video.channelAvatarUrl ?: info?.channelAvatarUrl,
                onSeek = { seconds -> host?.session?.seekTo(seconds * 1000) },
                onLoadMore = { loadComments(more = true) }, onDismiss = { commentsOpen = false }, modifier = Modifier.fillMaxSize(),
            )
        }
    }

    PlatformBackHandler(enabled = chaptersOpen) { chaptersOpen = false }
    val chaptersPanel: @Composable (Modifier) -> Unit = { m ->
        androidx.compose.animation.AnimatedVisibility(chaptersOpen, m, enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut()) {
            com.greenaltambe.vela.ui.player.ChaptersPanel(
                chapters = chapters,
                positionMs = position,
                videoThumbnailUrl = video.thumbnailUrl ?: info?.thumbnailUrl,
                durationMs = (playerRaw?.value?.durationMs ?: 0L).takeIf { it > 0 } ?: ((info?.durationSeconds ?: 0L) * 1000),
                livePositionMs = { playerRaw?.value?.positionMs ?: position },
                playing = player?.status == com.greenaltambe.vela.player.PlaybackStatus.PLAYING,
                onSeek = { secondsMs -> host?.session?.seekTo(secondsMs) },
                onDismiss = { chaptersOpen = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (downloadDialog) DownloadDialog(services, listOf(video), onDismiss = { downloadDialog = false })
    if (saveMenu) SaveSheet(
        services, video, onMessage = { note, error -> actionNote = note; actionError = error },
        onChanged = { saveRevision++ }, onDismiss = { saveMenu = false },
    )
    if (shareOpen) ShareSheet(
        videoId = video.id, title = shownTitle, positionMs = host?.controllerState?.value?.positionMs ?: position,
        onDone = { message -> actionError = null; if (message != null) actionNote = message; shareOpen = false }, onDismiss = { shareOpen = false },
    )
    // The description sits in the room under the picture too (never over it), like the comments.
    PlatformBackHandler(enabled = descriptionOpen) { descriptionOpen = false }
    val descriptionPanel: @Composable (Modifier) -> Unit = { m ->
        androidx.compose.animation.AnimatedVisibility(descriptionOpen, m, enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut()) {
            val captionTracks by (host?.session?.captions ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.greenaltambe.vela.extractor.CaptionTrack>()) }).collectAsState()
            DescriptionPanel(
                title = shownTitle, info = info,
                viewCount = info?.viewCount ?: video.viewCount, likeCount = info?.likeCount, uploadedAt = info?.uploadDate ?: video.uploadedAt,
                channelName = info?.channelName?.takeIf { it.isNotBlank() } ?: video.channelName,
                channelAvatarUrl = video.channelAvatarUrl ?: info?.channelAvatarUrl,
                chapters = chapters, captions = captionTracks, positionMs = position,
                onSeek = { ms: Long -> host?.session?.seekTo(ms) },
                onOpenChapters = { chaptersOpen = true; commentsOpen = false; descriptionOpen = false },
                onOpenChannel = { navigator.push(Route.Channel((info?.channelId?.takeIf { it.isNotBlank() } ?: video.channelId), info?.channelName?.takeIf { it.isNotBlank() } ?: video.channelName)) },
                onSearchTag = { tag: String -> navigator.push(Route.Search(tag)) },
                onDismiss = { descriptionOpen = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    val gap = Arrangement.spacedBy(Spacing.sm)
    // Two columns (related videos beside the player) only in a really wide window; narrower ones, desktop windows included, stack like the phone.
    val twoColumn = LocalWindowWidthDp.current >= 1000f
    if (!twoColumn || notFullyOpen) {
        val topInsetPx = WindowInsets.safeDrawing.getTop(density).toFloat()
        val topInsetDp = with(density) { topInsetPx.toDp() }
        val bottomInsetPx = WindowInsets.safeDrawing.getBottom(density).toFloat()
        val bg = MaterialTheme.colorScheme.background
        // The page is laid out once at its open size (the sheet clips and moves it): the width the sheet will have, which beside a rail is less than the window.
        val winW = (pageWidthPx ?: containerSize.width.toFloat()).coerceAtLeast(100f)
        val winH = containerSize.height.toFloat().coerceAtLeast(100f)

        val fullWidthDp = with(density) { winW.toDp() }
        val fullHeightDp = with(density) { (winW * 9f / 16f).toDp() }

        val targetLeftDp: androidx.compose.ui.unit.Dp
        val targetTopDp: androidx.compose.ui.unit.Dp
        val targetWidthDp: androidx.compose.ui.unit.Dp
        val targetHeightDp: androidx.compose.ui.unit.Dp

        // In the mini player the picture is a small thumbnail at the left of the 64 dp bar.
        targetLeftDp = 12.dp
        targetTopDp = 14.dp
        targetWidthDp = 64.dp
        targetHeightDp = 36.dp

        val cornerBaseDp = 8f
        // Position and size of the video box as functions of the progress, evaluated in the layout/draw lambdas below.
        val fullWidthPx = with(density) { fullWidthDp.roundToPx() }
        val fullHeightPx = with(density) { fullHeightDp.roundToPx() }
        val miniWidthPx = with(density) { targetWidthDp.roundToPx() }
        val miniHeightPx = with(density) { targetHeightDp.roundToPx() }
        val miniLeftPx = with(density) { targetLeftDp.roundToPx() }
        val miniTopPx = with(density) { targetTopDp.roundToPx() }
        val fullTopPx = with(density) { topInsetDp.roundToPx() }

        Box(
            modifier.fillMaxSize()
                .drawBehind { val p = progressNow(); if (p > 0.05f) drawRect(bg, alpha = ((p - 0.15f) / 0.85f).coerceIn(0f, 1f)) }
        ) {
            // Once the details have been shown they stay composed (hidden, and clipped away by the sheet while it is a card): re-composing the
            // whole details/related list every time the page is expanded was a ~100 ms stall in the middle of the animation.
            if (showDetailsPane && !detailsKept.value) detailsKept.value = true
            if (showDetailsPane || detailsKept.value) {
                val detailsTopPx = with(density) { (topInsetDp + fullHeightDp).roundToPx() }
                val detailsHeightPx = (with(density) { winH.roundToInt() } - detailsTopPx).coerceAtLeast(0)
                Box(
                    Modifier
                        // Always laid out at the full page size, whatever the sheet's current size, so the list is not re-measured on every frame of the morph.
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(fullWidthPx, detailsHeightPx))
                            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, detailsTopPx) }
                        }
                        .graphicsLayer {
                            val p = progressNow()
                            alpha = ((p - 0.25f) / 0.75f).coerceIn(0f, 1f)
                            translationY = (1f - p) * 48.dp.toPx()
                        }
                ) {
                    LazyColumn(
                        Modifier.fillMaxSize().testTag("video.list"),
                        state = detailsState,
                        userScrollEnabled = detailsScrollable,
                        contentPadding = Spacing.contentPadding,
                        verticalArrangement = gap
                    ) {
                        details()
                        secondary()
                    }
                    commentsPanel(Modifier.fillMaxSize())
                    chaptersPanel(Modifier.fillMaxSize())
                    descriptionPanel(Modifier.fillMaxSize())
                }
            }

            Box(
                Modifier
                    .layout { measurable, _ ->
                        val p = progressNow()
                        val w = (miniWidthPx + (fullWidthPx - miniWidthPx) * p).roundToInt()
                        val h = (miniHeightPx + (fullHeightPx - miniHeightPx) * p).roundToInt()
                        // The picture is always measured at its full size (so the player, its controls and the video surface are laid out once) and
                        // drawn scaled down to the current size: the morph costs a transform per frame, not a re-measure of everything inside.
                        val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(fullWidthPx, fullHeightPx))
                        layout(w, h) {
                            placeable.placeWithLayer((miniLeftPx * (1f - p)).roundToInt(), (fullTopPx * p + miniTopPx * (1f - p)).roundToInt()) {
                                val q = progressNow()
                                val scale = (miniWidthPx + (fullWidthPx - miniWidthPx) * q) / fullWidthPx
                                scaleX = scale; scaleY = scale
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                                shape = RoundedCornerShape((cornerBaseDp * (1f - q)).dp.toPx() / scale)
                                clip = true
                            }
                        }
                    }
                    .then(if (tapToExpand && onExpand != null) Modifier.clickable { onExpand() } else Modifier)
            ) {
                playerBox()
            }
        }
    } else {
        val bg = MaterialTheme.colorScheme.background
        val topInsetDp = with(density) { WindowInsets.safeDrawing.getTop(density).toDp() }
        Row(modifier.fillMaxSize().drawBehind { drawRect(bg) }) {
            Column(Modifier.weight(1.6f).fillMaxSize()) {
                Spacer(Modifier.height(topInsetDp))
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                    playerBox()
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(Modifier.fillMaxSize().testTag("video.list"), state = detailsState, contentPadding = Spacing.contentPadding, verticalArrangement = gap) { details() }
                    commentsPanel(Modifier.fillMaxSize())
                    chaptersPanel(Modifier.fillMaxSize())
                    descriptionPanel(Modifier.fillMaxSize())
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxSize().testTag("video.side"), state = sideState, contentPadding = Spacing.contentPadding, verticalArrangement = gap) { secondary() }
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
    ChatPanelUi(open, replay, status, lines, onToggle = { open = !open })
}
