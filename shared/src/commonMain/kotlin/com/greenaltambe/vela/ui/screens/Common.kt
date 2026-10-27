package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
        TextButton(onClick = { open = true }, modifier = Modifier.testTag("video.menu")) { Text("⋮") }
        DropdownMenu(open, { open = false }) {
            if (services.downloads != null) DropdownMenuItem(
                text = { Text(if (services.downloads.isDownloaded(video.id)) "✓ Downloaded" else "Download...") },
                onClick = { open = false; download = true },
            )
            // Re-read on every open so the checkmarks reflect the database.
            val lists = remember(open, revision) { services.playlists.all() }
            lists.forEach { list ->
                val has = remember(open, revision, list.id) { services.playlists.contains(list.id, video.id) }
                DropdownMenuItem(
                    text = { Text((if (has) "✓ " else "") + list.name) },
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

/** The standard feed pipeline (Shorts, watched, length, keywords) and then DeArrow for [surface], applied to a list. */
fun AppServices.filterFeed(videos: List<VideoSummary>, surface: DeArrowSurface = DeArrowSurface.FEED): List<VideoSummary> =
    standardFeedPipeline(WatchedLookup.of(history.watchedVideoIds()))
        .with(DeArrowStep(modules.dearrow, surface))
        .run(videos, FeedContext(settings))

/**
 * [videos] through the feed pipeline for [surface]. Lists show at once with what is known; DeArrow (and, if enabled,
 * Return YouTube Dislike) are fetched afterwards for the ids on screen and the list recomputes when they arrive.
 */
@Composable
fun rememberFiltered(services: AppServices, videos: List<VideoSummary>, surface: DeArrowSurface): List<VideoSummary> {
    val branding by services.modules.dearrow.revision.collectAsState()
    val ids = remember(videos) { videos.map { it.id } }
    LaunchedEffect(ids) {
        launch { services.modules.dearrow.prefetch(ids) }
        if (services.settings.resolve(Settings.rydInFeed)) launch { services.modules.ryd.prefetch(ids) }
    }
    return remember(videos, branding) { services.filterFeed(videos, surface) }
}

/** "👍 1.2K  👎 12" for a card when the setting is on and the numbers are known; null otherwise. */
@Composable
fun rydBadge(services: AppServices, videoId: String): String? {
    val revision by services.modules.ryd.revision.collectAsState()
    if (!services.settings.resolve(Settings.rydInFeed) || !services.modules.ryd.isEnabled(services.settings)) return null
    return remember(revision, videoId) { services.modules.ryd.cached(videoId)?.summary() }
}

fun <T> ExtractorResult<T>.errorText(): String? =
    (this as? ExtractorResult.Failure)?.error?.let { "${it.reason}: ${it.message}" }
