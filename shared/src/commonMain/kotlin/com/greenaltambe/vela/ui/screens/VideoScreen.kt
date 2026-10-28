package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
    var info by remember(video.id) { mutableStateOf<VideoInfo?>(offline?.let(::storedVideoInfo)) }
    var infoError by remember(video.id) { mutableStateOf<String?>(null) }
    var related by remember(video.id) { mutableStateOf<List<VideoSummary>>(emptyList()) }
    var comments by remember(video.id) { mutableStateOf<List<Comment>>(offline?.comments.orEmpty()) }
    var commentsNext by remember(video.id) { mutableStateOf<String?>(null) }
    var commentsState by remember(video.id) { mutableStateOf<ListState>(if (offline != null) (if (offline.comments.isEmpty()) ListState.Empty("No saved comments.") else ListState.Ready) else ListState.Loading) }
    var commentsLoading by remember(video.id) { mutableStateOf(false) }
    var sort by remember(video.id) { mutableStateOf(CommentSort.TOP) }
    var replies by remember(video.id) { mutableStateOf<Map<String, List<Comment>>>(emptyMap()) }
    var repliesLoading by remember(video.id) { mutableStateOf<Set<String>>(emptySet()) }
    var descriptionExpanded by remember(video.id) { mutableStateOf(false) }
    var downloadDialog by remember(video.id) { mutableStateOf(false) }
    var subscribed by remember(video.id, video.channelId) { mutableStateOf(services.subscriptions.isSubscribed(video.channelId)) }
    // Created before any early return so the scroll positions survive fullscreen and Picture-in-Picture.
    val detailsState = rememberLazyListState()
    val sideState = rememberLazyListState()

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
        if (offline != null) return@LaunchedEffect
        when (val r = services.extractor.videoInfo(video.id)) {
            is ExtractorResult.Success -> info = r.value
            is ExtractorResult.Failure -> infoError = r.error.message
        }
    }
    LaunchedEffect(video.id) { if (offline == null) (services.extractor.related(video.id) as? ExtractorResult.Success)?.let { related = it.value } }
    LaunchedEffect(video.id, sort) { if (offline == null) loadComments(more = false) }

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
    var submitting by remember(video.id) { mutableStateOf<String?>(null) }

    PlatformBackHandler(enabled = window?.isFullscreen == true) { window?.toggleFullscreen() }

    if (host != null && (window?.isFullscreen == true || inPip)) {
        PlayerView(host, video, chapters, markers, modifier.fillMaxSize(), fullscreen = true, controlsEnabled = !inPip, onBack = if (inPip) null else ({ window?.toggleFullscreen() }))
        return
    }

    val player = host?.controllerState?.collectAsState()?.value
    val position = player?.positionMs ?: 0
    val current = currentChapter(chapters, position)
    val uriHandler = LocalUriHandler.current

    val playerBox: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).testTag("video.player")) {
            if (host != null) PlayerView(
                host, video, chapters, markers, Modifier.fillMaxWidth(), onBack = { navigator.back() },
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
                Text(shownTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("video.title"))
                if (titleReplaced) LinkButton(if (showOriginalTitle) "Original title · use DeArrow" else "DeArrow title · show original", { showOriginalTitle = !showOriginalTitle }, Modifier.testTag("video.toggleOriginal"))
                val meta = listOfNotNull(formatViews(info?.viewCount ?: video.viewCount), info?.likeCount?.let { "${compact(it)} likes" }).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Vela.colors.onSurfaceVariant.toColor())
                if (size == WindowSizeClass.COMPACT) current?.let { Text("Chapter: ${it.title}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Vela.colors.primary.toColor(), modifier = Modifier.testTag("video.chapterNow")) }
                infoError?.let { Text("Details unavailable: $it", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clickableChannel { navigator.push(Route.Channel(video.channelId, video.channelName)) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
                val channelName = info?.channelName?.takeIf { it.isNotBlank() } ?: video.channelName
                Avatar(channelName)
                Text(channelName, Modifier.weight(1f).testTag("video.channel"), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subscribed) OutlinedButton(onClick = { services.subscriptions.unsubscribe(video.channelId); subscribed = false }, modifier = Modifier.testTag("video.subscribe")) { Text("Subscribed", maxLines = 1) }
                else PrimaryButton("Subscribe", onClick = { services.subscriptions.subscribe(video.channelId, video.channelName, null, services.clock.now()); subscribed = true }, modifier = Modifier.testTag("video.subscribe"))
            }
        }
        item {
            var saveMenu by remember { mutableStateOf(false) }
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            Row(Modifier.fillMaxWidth().testTag("video.actions"), horizontalArrangement = Arrangement.SpaceEvenly) {
                votes?.let { ActionChip(Vela.icons.like, compact(it.likes), {}, Modifier.weight(1f).testTag("video.votes")) }
                if (services.downloads != null) {
                    if (offline != null) ActionChip(Vela.icons.downloaded, "Saved", {}, Modifier.weight(1f).testTag("video.downloaded"), selected = true)
                    else ActionChip(Vela.icons.download, "Download", { downloadDialog = true }, Modifier.weight(1f).testTag("video.download"))
                }
                Box(Modifier.weight(1f)) {
                    ActionChip(Vela.icons.save, "Save", { saveMenu = true }, Modifier.fillMaxWidth().testTag("video.save"))
                    DropdownMenu(saveMenu, { saveMenu = false }) {
                        services.playlists.all().forEach { list ->
                            val has = services.playlists.contains(list.id, video.id)
                            DropdownMenuItem(
                                text = { Text((if (has) "✓ " else "") + list.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = { if (has) services.playlists.remove(list.id, video.id) else services.playlists.add(list.id, listOf(video), services.clock.now()); saveMenu = false },
                            )
                        }
                    }
                }
                ActionChip(Vela.icons.share, "Share", { clipboard.setText(AnnotatedString("https://youtu.be/${video.id}")) }, Modifier.weight(1f).testTag("video.share"))
            }
        }
        info?.takeIf { it.description.isNotBlank() }?.let { d ->
            item {
                val text = remember(d.description) { descriptionText(parseDescription(d.description)) { seconds -> host?.session?.seekTo(seconds * 1000) } }
                Column(Modifier.fillMaxWidth()) {
                    Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("video.description"))
                    LinkButton(if (descriptionExpanded) "Show less" else "more", { descriptionExpanded = !descriptionExpanded }, Modifier.testTag("video.descriptionToggle"))
                }
            }
        }
        if (host != null) item {
            ContributeSection(modules = modules, videoId = video.id, positionMs = player?.positionMs ?: 0, durationMs = player?.durationMs ?: 0, status = submitting, onStatus = { submitting = it })
        }
        if (chapters.isNotEmpty()) {
            item { SectionHeader("Chapters") }
            items(chapters, key = { "ch-${it.startSeconds}" }) { c -> ChapterRow(c, current = c == current, onClick = { host?.session?.seekTo(c.startSeconds * 1000) }) }
        }
    }

    val secondary: LazyListScope.() -> Unit = {
        if (relatedShown.isNotEmpty()) {
            item { SectionHeader("Related") }
            items(relatedShown, key = { "rel-${it.id}" }) { v ->
                // On a phone-sized MEDIUM window the column beside the player is narrow: a thumbnail across its width reads better than a tiny one.
                VideoCard(v, onClick = { navigator.replaceWithVideo(v) }, style = if (size == WindowSizeClass.MEDIUM) VideoCardStyle.VERTICAL else VideoCardStyle.HORIZONTAL, onChannelClick = { navigator.push(Route.Channel(v.channelId, v.channelName)) }, trailing = { VideoMenu(services, v) })
            }
        }
        item {
            SectionHeader("Comments") {
                // Only offered when the extractor can honour it; otherwise the control would silently do nothing.
                if (services.extractor.capabilities.supportsCommentSort) TextButton(onClick = { sort = if (sort == CommentSort.TOP) CommentSort.NEWEST else CommentSort.TOP }) { Text(if (sort == CommentSort.TOP) "Top" else "Newest", maxLines = 1) }
            }
        }
        when (val st = commentsState) {
            ListState.Loading -> items(3) { SkeletonCard(vertical = false) }
            is ListState.Empty -> item { Text(st.message, Modifier.testTag("video.commentsStatus"), style = MaterialTheme.typography.bodyMedium) }
            is ListState.Error -> item { ErrorRow(st.message, st.onRetry) }
            ListState.Ready -> {}
        }
        items(comments, key = { "c-${it.id}" }) { c ->
            CommentItem(
                c, replies = replies[c.id], repliesLoading = c.id in repliesLoading,
                onToggleReplies = {
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
            )
        }
        if (commentsNext != null) item { TextButton(onClick = { loadComments(more = true) }, enabled = !commentsLoading, modifier = Modifier.testTag("video.moreComments")) { Text("Load more comments", maxLines = 1) } }
    }

    if (downloadDialog) DownloadDialog(services, listOf(video), onDismiss = { downloadDialog = false })

    val gap = Arrangement.spacedBy(Vela.sm)
    if (size == WindowSizeClass.COMPACT) {
        Column(modifier.fillMaxSize()) {
            playerBox()
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("video.list"), state = detailsState, contentPadding = Vela.contentPadding, verticalArrangement = gap) { details(); secondary() }
        }
    } else {
        Row(modifier.fillMaxSize()) {
            Column(Modifier.weight(1.6f).fillMaxSize()) {
                playerBox()
                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("video.list"), state = detailsState, contentPadding = Vela.contentPadding, verticalArrangement = gap) { details() }
            }
            LazyColumn(Modifier.weight(1f).fillMaxSize().testTag("video.side"), state = sideState, contentPadding = Vela.contentPadding, verticalArrangement = gap) { secondary() }
        }
    }
}

private fun Modifier.clickableChannel(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(onClick = onClick))

/** Links open externally; timestamps seek the player. */
private fun descriptionText(parts: List<DescriptionPart>, onSeek: (Long) -> Unit): AnnotatedString = buildAnnotatedString {
    val style = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
    parts.forEach { part ->
        when (part) {
            is DescriptionPart.Text -> append(part.text)
            is DescriptionPart.Link -> withLink(LinkAnnotation.Url(part.url, style)) { append(part.text) }
            is DescriptionPart.Time -> withLink(LinkAnnotation.Clickable("t=${part.seconds}", style) { onSeek(part.seconds) }) { append(part.text) }
        }
    }
}

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
