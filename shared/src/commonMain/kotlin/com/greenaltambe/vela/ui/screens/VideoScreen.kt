package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.AppServices
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
import com.greenaltambe.vela.player.PlaybackPlan
import com.greenaltambe.vela.player.PlaybackSession
import com.greenaltambe.vela.player.SessionState
import com.greenaltambe.vela.downloads.OfflineVideo
import com.greenaltambe.vela.ui.components.ChapterRow
import com.greenaltambe.vela.ui.components.CommentItem
import com.greenaltambe.vela.ui.components.PrimaryButton
import com.greenaltambe.vela.ui.components.SectionHeader
import com.greenaltambe.vela.ui.components.VideoCard
import com.greenaltambe.vela.ui.components.formatViews
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import com.greenaltambe.vela.ui.platform.LocalWindowControls
import com.greenaltambe.vela.ui.platform.PlatformBackHandler
import com.greenaltambe.vela.ui.platform.LocalInPictureInPicture
import com.greenaltambe.vela.ui.player.PlayerOptions
import com.greenaltambe.vela.ui.player.PlayerPanel
import com.greenaltambe.vela.ui.player.SeekMarker
import androidx.compose.ui.graphics.Color
import com.greenaltambe.vela.modules.dearrow.DeArrowSurface
import com.greenaltambe.vela.modules.ryd.summary
import com.greenaltambe.vela.modules.sponsorblock.SkipNotice
import com.greenaltambe.vela.ui.look.LocalLook
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private fun SessionState.videoIdOrNull(): String? = when (this) {
    SessionState.Idle -> null
    is SessionState.Resolving -> videoId
    is SessionState.Ready -> videoId
    is SessionState.Failed -> videoId
}

/** Player on top; title, channel with Subscribe, description, chapters, related videos and comments below, all in one scrolling list. */
@Composable
fun VideoScreen(services: AppServices, navigator: Navigator, video: VideoSummary, startSeconds: Long?, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val host = services.player
    val window = LocalWindowControls.current
    val inPip = LocalInPictureInPicture.current

    // A finished download plays from its file and shows its stored page: no stream, info, comment or related request is made.
    val offline = remember(video.id) { services.downloads?.offline(video.id) }
    var info by remember(video.id) { mutableStateOf<VideoInfo?>(offline?.let(::storedVideoInfo)) }
    var infoError by remember(video.id) { mutableStateOf<String?>(null) }
    var related by remember(video.id) { mutableStateOf<List<VideoSummary>>(emptyList()) }
    var comments by remember(video.id) { mutableStateOf<List<Comment>>(offline?.comments.orEmpty()) }
    var commentsNext by remember(video.id) { mutableStateOf<String?>(null) }
    var commentsStatus by remember(video.id) { mutableStateOf(if (offline != null) (if (offline.comments.isEmpty()) "No saved comments." else "") else "Loading comments...") }
    var downloadDialog by remember(video.id) { mutableStateOf(false) }
    var commentsLoading by remember(video.id) { mutableStateOf(false) }
    var sort by remember(video.id) { mutableStateOf(CommentSort.TOP) }
    var replies by remember(video.id) { mutableStateOf<Map<String, List<Comment>>>(emptyMap()) }
    var repliesLoading by remember(video.id) { mutableStateOf<Set<String>>(emptySet()) }
    var descriptionExpanded by remember(video.id) { mutableStateOf(false) }
    var subscribed by remember(video.id, video.channelId) { mutableStateOf(services.subscriptions.isSubscribed(video.channelId)) }

    fun loadComments(more: Boolean) {
        if (commentsLoading) return
        commentsLoading = true
        scope.launch {
            when (val r = services.extractor.comments(video.id, sort, commentsNext.takeIf { more })) {
                is ExtractorResult.Success -> {
                    comments = if (more) (comments + r.value.comments).distinctBy { it.id } else r.value.comments
                    commentsNext = r.value.continuation
                    commentsStatus = if (comments.isEmpty()) "No comments." else ""
                }
                is ExtractorResult.Failure -> commentsStatus = "Comments unavailable: ${r.error.message}"
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
        PlayerPanel(host, chapters, markers, modifier.fillMaxSize(), fullscreen = true)
        return
    }

    val player = host?.controllerState?.collectAsState()?.value
    val position = player?.positionMs ?: 0
    val current = currentChapter(chapters, position)

    // The picture and transport stay pinned while everything below scrolls: a list item would be disposed (and the
    // picture stop being drawn) as soon as the viewer scrolled down to the comments.
    if (downloadDialog) DownloadDialog(services, listOf(video), onDismiss = { downloadDialog = false })
    Column(modifier.fillMaxSize()) {
    Box(Modifier.fillMaxWidth().padding(horizontal = Vela.md, vertical = Vela.xs), contentAlignment = Alignment.TopCenter) {
        if (host != null) PlayerPanel(host, chapters, markers, Modifier.widthIn(max = 960.dp), overlay = {
            SponsorBlockOverlay(prompt, notice, onAccept = modules.sponsorBlock::acceptPrompt, onUndo = { n -> modules.sponsorBlock.undo(n); notice = null })
        })
        else Text("No video player is available on this system: nothing will play. (On Linux install mpv-libs.)", Modifier.testTag("video.noPlayer"))
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("video.list"), verticalArrangement = Arrangement.spacedBy(Vela.sm), contentPadding = Vela.pagePadding) {
        if (host != null) item { PlayerOptions(host, Modifier.widthIn(max = 960.dp)) }
        item {
            Text(shownTitle, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("video.title"))
            if (titleReplaced) {
                TextButton(onClick = { showOriginalTitle = !showOriginalTitle }, modifier = Modifier.testTag("video.toggleOriginal")) {
                    Text(if (showOriginalTitle) "Showing the original title · use DeArrow title" else "DeArrow title · show original")
                }
            }
            val meta = listOfNotNull(formatViews(info?.viewCount ?: video.viewCount), info?.likeCount?.let { "$it likes" }).joinToString("  ·  ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall)
            votes?.let { Text(it.summary(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("video.votes")) }
            infoError?.let { Text("Details unavailable: $it", style = MaterialTheme.typography.bodySmall) }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
                Text(
                    info?.channelName ?: video.channelName,
                    Modifier.weight(1f).testTag("video.channel"),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = { navigator.push(Route.Channel(video.channelId, video.channelName)) }) { Text("Open channel") }
                if (subscribed) {
                    OutlinedButton(onClick = { services.subscriptions.unsubscribe(video.channelId); subscribed = false }, modifier = Modifier.testTag("video.subscribe")) { Text("Subscribed") }
                } else {
                    PrimaryButton("Subscribe", onClick = {
                        services.subscriptions.subscribe(video.channelId, video.channelName, null, services.clock.now())
                        subscribed = true
                    }, modifier = Modifier.testTag("video.subscribe"))
                }
                if (services.downloads != null) {
                    if (offline != null) Text("Downloaded", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("video.downloaded"))
                    else OutlinedButton(onClick = { downloadDialog = true }, modifier = Modifier.testTag("video.download")) { Text("Download") }
                }
                VideoMenu(services, video)
            }
        }
        info?.takeIf { it.description.isNotBlank() }?.let { details ->
            item {
                val uriHandler = LocalUriHandler.current
                val text = remember(details.description) { descriptionText(parseDescription(details.description)) { seconds -> host?.session?.seekTo(seconds * 1000) } }
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (descriptionExpanded) Int.MAX_VALUE else 5,
                    modifier = Modifier.testTag("video.description"),
                )
                TextButton(onClick = { descriptionExpanded = !descriptionExpanded }) { Text(if (descriptionExpanded) "Show less" else "Show more") }
            }
        }
        if (host != null) item {
            ContributeSection(
                modules = modules, videoId = video.id, positionMs = player?.positionMs ?: 0, durationMs = player?.durationMs ?: 0,
                status = submitting, onStatus = { submitting = it },
            )
        }
        if (chapters.isNotEmpty()) {
            item { SectionHeader("Chapters") }
            items(chapters, key = { "ch-${it.startSeconds}" }) { c ->
                ChapterRow(c, current = c == current, onClick = { host?.session?.seekTo(c.startSeconds * 1000) })
            }
        }
        if (relatedShown.isNotEmpty()) {
            item { SectionHeader("Related") }
            items(relatedShown, key = { "rel-${it.id}" }) { v ->
                VideoCard(v, badge = rydBadge(services, v.id), onClick = { navigator.replaceWithVideo(v) }, onChannelClick = { navigator.push(Route.Channel(v.channelId, v.channelName)) }, trailing = { VideoMenu(services, v) })
            }
        }
        item {
            SectionHeader("Comments") {
                // Only offered when the extractor can honour it; otherwise the control would silently do nothing.
                if (services.extractor.capabilities.supportsCommentSort) {
                    TextButton(onClick = { sort = if (sort == CommentSort.TOP) CommentSort.NEWEST else CommentSort.TOP }) { Text(if (sort == CommentSort.TOP) "Top" else "Newest") }
                }
            }
            if (commentsStatus.isNotEmpty()) Text(commentsStatus, Modifier.testTag("video.commentsStatus"))
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
        if (commentsNext != null) item { TextButton(onClick = { loadComments(more = true) }, enabled = !commentsLoading, modifier = Modifier.testTag("video.moreComments")) { Text("Load more comments") } }
    }
    }
}

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
