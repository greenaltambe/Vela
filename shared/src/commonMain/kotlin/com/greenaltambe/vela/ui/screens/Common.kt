package com.greenaltambe.vela.ui.screens

import com.greenaltambe.vela.ui.theme.AppIcons
import androidx.compose.foundation.shape.ZeroCornerSize
import com.greenaltambe.vela.ui.theme.Spacing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.feed.FeedContext
import com.greenaltambe.vela.feed.WatchedLookup
import com.greenaltambe.vela.feed.standardFeedPipeline
import com.greenaltambe.vela.modules.dearrow.DeArrowStep
import com.greenaltambe.vela.modules.dearrow.DeArrowSurface
import com.greenaltambe.vela.modules.ryd.summary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The "⋮" on a video row: Watch later and the user's playlists in a bottom sheet. */
@Composable
fun VideoMenu(services: AppServices, video: VideoSummary, modifier: Modifier = Modifier, onNotInterested: (() -> Unit)? = null, onHideChannel: (() -> Unit)? = null) {
    var open by remember { mutableStateOf(false) }
    var download by remember { mutableStateOf(false) }
    if (download) DownloadDialog(services, listOf(video), onDismiss = { download = false })
    Box(modifier) {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("video.menu")) {
            // Three horizontal dots, drawn a little smaller and in the quieter text colour so they read thin beside the title.
            Icon(AppIcons.more, "More options for ${video.title}", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (open) {
            VideoMenuSheet(
                services = services,
                video = video,
                onDismiss = { open = false },
                onDownload = { download = true },
                onNotInterested = onNotInterested,
                onHideChannel = onHideChannel,
            )
        }
    }
}

/** Bottom sheet with video actions: preview header, download, share/copy link, and playlists. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoMenuSheet(
    services: AppServices,
    video: VideoSummary,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    /** Home only: send this video, or its whole channel, away from Home (with an Undo there). */
    onNotInterested: (() -> Unit)? = null,
    onHideChannel: (() -> Unit)? = null,
) {
    val share = com.greenaltambe.vela.ui.platform.LocalShare.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var revision by remember { mutableStateOf(0) }
    val isDownloaded = remember(video.id, revision) { services.downloads?.isDownloaded(video.id) == true }
    val lists = remember(revision) { services.playlists.all() }

    com.greenaltambe.vela.ui.components.VelaSheet(onDismiss, Modifier.testTag("video.menu.sheet")) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = Spacing.md)
        ) {
            // Header with thumbnail, title, and channel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                val thumbUrl = video.thumbnailUrl ?: "https://i.ytimg.com/vi/${video.id}/mqdefault.jpg"
                coil3.compose.AsyncImage(
                    model = thumbUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .width(72.dp)
                        .aspectRatio(16f / 9f)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (video.channelName.isNotBlank()) {
                        Text(
                            text = video.channelName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = Spacing.sm),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (onNotInterested != null) VideoMenuSheetItem(
                    icon = AppIcons.close, title = "Not interested", onClick = { onDismiss(); onNotInterested() }, modifier = Modifier.testTag("video.menu.notInterested"),
                )
                if (onHideChannel != null && video.channelId.isNotBlank()) VideoMenuSheetItem(
                    icon = AppIcons.close, title = "Hide ${video.channelName.ifBlank { "this channel" }} from Home", onClick = { onDismiss(); onHideChannel() }, modifier = Modifier.testTag("video.menu.hideChannel"),
                )
                if (services.downloads != null) {
                    VideoMenuSheetItem(
                        icon = if (isDownloaded) AppIcons.downloaded else AppIcons.download,
                        title = if (isDownloaded) "Downloaded" else "Download video",
                        onClick = {
                            onDismiss()
                            onDownload()
                        },
                        modifier = Modifier.testTag("video.menu.download")
                    )
                }

                VideoMenuSheetItem(
                    icon = AppIcons.share,
                    title = if (share != null) "Share" else "Copy link",
                    onClick = {
                        onDismiss()
                        val link = "https://youtu.be/${video.id}"
                        if (share != null) share(video.title, link)
                        else clipboard.setText(androidx.compose.ui.text.AnnotatedString(link))
                    },
                    modifier = Modifier.testTag("video.menu.share")
                )

                lists.forEach { list ->
                    val has = remember(revision, list.id) { services.playlists.contains(list.id, video.id) }
                    VideoMenuSheetItem(
                        icon = if (list.isWatchLater) AppIcons.watchLater else AppIcons.playlist,
                        title = if (list.isWatchLater && !has) "Save to Watch later" else list.name,
                        trailingIcon = if (has) AppIcons.check else null,
                        trailingContentDescription = if (has) "In this playlist" else null,
                        highlightTrailing = true,
                        onClick = {
                            if (has) services.playlists.remove(list.id, video.id)
                            else services.playlists.add(list.id, listOf(video), services.clock.now())
                            revision++
                            onDismiss()
                        },
                        modifier = Modifier.testTag("video.menu.playlist.${list.id}")
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoMenuSheetItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailingContentDescription: String? = null,
    highlightTrailing: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = trailingContentDescription,
                tint = if (highlightTrailing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** A menu entry that opens the share sheet where the platform has one; on desktop (none) it copies the video's link instead. */
@Composable
fun ShareMenuItem(video: VideoSummary, onDone: () -> Unit) {
    val share = com.greenaltambe.vela.ui.platform.LocalShare.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    DropdownMenuItem(
        text = { Text(if (share != null) "Share" else "Copy link") },
        leadingIcon = { androidx.compose.material3.Icon(AppIcons.share, null) },
        onClick = {
            onDone()
            val link = "https://youtu.be/${video.id}"
            if (share != null) share(video.title, link) else clipboard.setText(androidx.compose.ui.text.AnnotatedString(link))
        },
        modifier = Modifier.testTag("video.menu.share"),
    )
}

/** The standard feed pipeline (Shorts, watched, length, keywords) and then DeArrow for [surface], applied to a list. */
fun AppServices.filterFeed(videos: List<VideoSummary>, surface: DeArrowSurface = DeArrowSurface.FEED): List<VideoSummary> =
    standardFeedPipeline(watchedLookup())
        .with(DeArrowStep(modules.dearrow, surface))
        .run(videos, FeedContext(settings))

/** The same pipeline without asking the database which videos were watched: cheap enough for the first frame. */
fun AppServices.filterFeedQuick(videos: List<VideoSummary>, surface: DeArrowSurface = DeArrowSurface.FEED): List<VideoSummary> =
    standardFeedPipeline(WatchedLookup.None).with(DeArrowStep(modules.dearrow, surface)).run(videos, FeedContext(settings))

/**
 * Shows [first] at once and replaces it with [compute] (run on [Dispatchers.Default], never on the main thread) when it is ready.
 * With [keepWhileLoading] a change of [keys] keeps showing the previous result until the new one arrives (stale-while-revalidate:
 * a refreshed feed does not blink back to a skeleton); without it a change of [keys] starts again from [first].
 */
@Composable
fun <T> rememberAsync(vararg keys: Any?, keepWhileLoading: Boolean = false, first: () -> T, compute: () -> T): T {
    var value by (if (keepWhileLoading) remember { mutableStateOf(first()) } else remember(*keys) { mutableStateOf(first()) })
    LaunchedEffect(*keys) { value = withContext(Dispatchers.Default) { compute() } }
    return value
}

/**
 * [videos] through the feed pipeline for [surface]. Lists show at once with what is known; DeArrow (and, if enabled,
 * Return YouTube Dislike) are fetched afterwards for the ids on screen and the list recomputes when they arrive.
 * The watched-history lookup and the full pass run off the main thread; the first frame uses [filterFeedQuick].
 */
@Composable
fun rememberFiltered(services: AppServices, videos: List<VideoSummary>, surface: DeArrowSurface): List<VideoSummary> {
    val branding by services.modules.dearrow.revision.collectAsState()
    val ids = remember(videos) { videos.take(PREFETCH_LIMIT).map { it.id } }
    LaunchedEffect(ids) {
        launch { services.modules.dearrow.prefetch(ids) }
        if (services.settings.resolve(Settings.rydInFeed)) launch { services.modules.ryd.prefetch(ids) }
    }
    return rememberAsync(videos, branding, keepWhileLoading = true, first = { services.filterFeedQuick(videos, surface) }, compute = { services.filterFeed(videos, surface) })
}

/** Only the ids this many deep are prefetched (DeArrow, RYD): the rest are fetched when the list is scrolled that far. */
const val PREFETCH_LIMIT = 60

/** "👍 1.2K  👎 12" for a card when the setting is on and the numbers are known; null otherwise. */
@Composable
fun rydBadge(services: AppServices, videoId: String): String? {
    val revision by services.modules.ryd.revision.collectAsState()
    if (!services.settings.resolve(Settings.rydInFeed) || !services.modules.ryd.isEnabled(services.settings)) return null
    return remember(revision, videoId) { services.modules.ryd.cached(videoId)?.summary() }
}

fun <T> ExtractorResult<T>.errorText(): String? =
    (this as? ExtractorResult.Failure)?.error?.let { "${it.reason}: ${it.message}" }
