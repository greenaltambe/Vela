package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.feed.FeedContext
import com.greenaltambe.vela.feed.WatchedLookup
import com.greenaltambe.vela.modules.dearrow.DeArrowStep
import com.greenaltambe.vela.modules.dearrow.DeArrowSurface
import com.greenaltambe.vela.modules.ryd.summary
import kotlinx.coroutines.launch
import com.greenaltambe.vela.feed.standardFeedPipeline

/** The "⋮" on a video row: Watch later and the user's playlists. */
@Composable
fun VideoMenu(services: AppServices, video: VideoSummary, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    var revision by remember { mutableStateOf(0) }
    var download by remember { mutableStateOf(false) }
    if (download) DownloadDialog(services, listOf(video), onDismiss = { download = false })
    Box(modifier) {
        androidx.compose.material3.IconButton(onClick = { open = true }, modifier = Modifier.testTag("video.menu")) { androidx.compose.material3.Icon(com.greenaltambe.vela.ui.look.Vela.icons.more, "More options for ${video.title}") }
        // Composed only while open: a list of 60 cards used to build 60 popup menus it never showed.
        if (open) DropdownMenu(true, { open = false }) {
            if (services.downloads != null) DropdownMenuItem(
                text = { Text(if (services.downloads.isDownloaded(video.id)) "Downloaded" else "Download...") },
                leadingIcon = { androidx.compose.material3.Icon(if (services.downloads.isDownloaded(video.id)) com.greenaltambe.vela.ui.look.Vela.icons.downloaded else com.greenaltambe.vela.ui.look.Vela.icons.download, null) },
                onClick = { open = false; download = true },
            )
            ShareMenuItem(video, onDone = { open = false })
            // Re-read on every open so the checkmarks reflect the database.
            val lists = remember(open, revision) { services.playlists.all() }
            lists.forEach { list ->
                val has = remember(open, revision, list.id) { services.playlists.contains(list.id, video.id) }
                DropdownMenuItem(
                    text = { Text(list.name) },
                    // Watch later gets its clock, other playlists the list icon; a tick on the right says it is already in there.
                    leadingIcon = { androidx.compose.material3.Icon(if (list.isWatchLater) com.greenaltambe.vela.ui.look.Vela.icons.watchLater else com.greenaltambe.vela.ui.look.Vela.icons.playlist, null) },
                    trailingIcon = if (has) ({ androidx.compose.material3.Icon(com.greenaltambe.vela.ui.look.Vela.icons.check, "In this playlist") }) else null,
                    onClick = {
                        if (has) services.playlists.remove(list.id, video.id) else services.playlists.add(list.id, listOf(video), services.clock.now())
                        revision++
                        open = false
                    },
                )
            }
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
        leadingIcon = { androidx.compose.material3.Icon(com.greenaltambe.vela.ui.look.Vela.icons.share, null) },
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
    standardFeedPipeline(WatchedLookup.of(history.watchedVideoIds()))
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
