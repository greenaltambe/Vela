package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.data.WatchRecord
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.extractor.videoPagedList
import com.greenaltambe.vela.modules.dearrow.DeArrowSurface
import com.greenaltambe.vela.ui.components.HScrollRow
import com.greenaltambe.vela.ui.components.SectionHeader
import com.greenaltambe.vela.ui.components.formatDuration
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.pressFeedback
import com.greenaltambe.vela.ui.look.toColor
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import kotlinx.coroutines.launch

/** Trending, through the same feed pipeline as the subscription feed. */
@Composable
fun HomeScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    val store = LocalHomeFeed.current
    val accountId = services.accounts.active.value.id
    val cached = store != null && store.usable(accountId, services.clock.now())
    var videos by remember { mutableStateOf(if (cached) store!!.videos else videoPagedList()) }
    var state by remember { mutableStateOf<ListState>(if (cached) ListState.Ready else ListState.Loading) }
    var loading by remember { mutableStateOf(false) }
    var pulling by remember { mutableStateOf(false) }       // the pull-to-refresh spinner, only for a pull (not for the first load or paging)
    // What the list shows: the account's Recommended feed, or Trending (signed out, setting, or the account feed failed or was empty).
    var showing by remember { mutableStateOf(if (cached) store!!.showing else "Trending") }
    var note by remember { mutableStateOf<String?>(if (cached) store!!.note else null) }
    val scope = rememberCoroutineScope()
    fun load(more: Boolean) {
        if (loading) return
        loading = true
        if (!more && videos.items.isEmpty()) state = ListState.Loading
        scope.launch {
            val wantRecommended = !more && services.reads != null && services.accounts.active.value.isSignedIn &&
                services.settings.resolve(com.greenaltambe.vela.core.settings.Settings.homeSource) == com.greenaltambe.vela.core.settings.HomeSource.RECOMMENDED
            if (wantRecommended) {
                val rec = services.reads!!.recommended()
                if (!rec.isNullOrEmpty()) {
                    videos = videos.replace(com.greenaltambe.vela.extractor.Page(rec)); showing = "Recommended"; note = null
                    state = ListState.Ready; loading = false
                    store?.loadedAt = services.clock.now()
                    return@launch
                }
                note = if (rec == null) "Recommended is unavailable right now; showing Trending." else "Your account has no recommendations yet; showing Trending."
            } else if (!more) { note = null }
            showing = "Trending"
            when (val result = services.extractor.trending(videos.continuation.takeIf { more })) {
                is ExtractorResult.Success -> {
                    videos = if (more) videos.append(result.value) else videos.replace(result.value)
                    state = if (videos.items.isEmpty()) ListState.Empty("Nothing is trending right now.", "Refresh") { load(false) } else ListState.Ready
                    if (!more) store?.loadedAt = services.clock.now()
                }
                is ExtractorResult.Failure -> if (videos.items.isEmpty()) state = ListState.Error("Could not load trending: ${result.error.message}") { load(false) }
            }
            loading = false
        }
    }
    LaunchedEffect(loading) { if (!loading) pulling = false }
    // Remember what is shown for the next visit; a good answer stamps the time.
    androidx.compose.runtime.SideEffect {
        if (store != null && videos.items.isNotEmpty() && state == ListState.Ready) {
            store.accountId = accountId; store.videos = videos; store.showing = showing; store.note = note
        }
    }
    LaunchedEffect(accountId) {
        // Back on Home with a recent feed: show it as it was. Otherwise (first visit, another account, too old, nothing loaded) fetch.
        if (cached && store!!.accountId == accountId) return@LaunchedEffect
        videos = videoPagedList(); load(more = false)
    }

    val shown = rememberFiltered(services, videos.items, DeArrowSurface.FEED)
    val continueWatching = rememberAsync(
        accountId,
        navigator.current,
        navigator.nowPlaying?.id,
        keepWhileLoading = true,
        first = { emptyList<WatchRecord>() },
        compute = { services.history.continueWatching() }
    )

    com.greenaltambe.vela.ui.components.RefreshBox(isRefreshing = pulling && loading, onRefresh = { pulling = true; load(more = false) }, modifier = modifier) {
    VideoCollection(
        services, navigator, shown, state, Modifier, testTag = "home.list", scroll = store?.scroll,
        header = {
            note?.let {
                Box(
                    Modifier.fillMaxWidth().padding(bottom = Vela.sm)
                        .clip(Vela.smallShape)
                        .background(Vela.colors.surfaceVariant.toColor().copy(alpha = 0.5f))
                        .padding(horizontal = Vela.sm, vertical = Vela.xs)
                ) {
                    Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor(), modifier = Modifier.testTag("home.note"))
                }
            }
            (videos.items.size - shown.size).takeIf { it > 0 }?.let {
                Text("$it hidden by your feed filters (Settings > Feed).", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor(), modifier = Modifier.padding(bottom = Vela.xs).testTag("home.hiddenByFeed"))
            }
        },
        injectedContent = if (continueWatching.isNotEmpty()) ({
            ContinueWatchingRow(continueWatching, navigator)
        }) else null,
        injectedIndex = 2,
        // More is fetched by itself as the last card comes into view; the button stays as a fallback if that fetch failed.
        // The account's recommended feed is one page with no continuation: at its end the list carries on with Trending (then Trending's own pages).
        onEndReached = { if ((videos.continuation != null || showing == "Recommended") && !loading && state == ListState.Ready) load(more = true) },
        footer = if (videos.continuation != null || showing == "Recommended") ({ TextButton(onClick = { load(more = true) }, enabled = !loading) { Text("Load more", maxLines = 1) } }) else null,
    )
    }
}

/** Horizontal scrolling row of started but unfinished videos from watch history. */
@Composable
internal fun ContinueWatchingRow(
    items: List<WatchRecord>,
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("home.continueWatching"),
        verticalArrangement = Arrangement.spacedBy(Vela.xs)
    ) {
        SectionHeader("Continue watching") {
            TextButton(
                onClick = { navigator.push(Route.History) },
                modifier = Modifier.testTag("home.continueWatchingAll")
            ) {
                Text("See all", maxLines = 1)
            }
        }
        HScrollRow(
            modifier = Modifier.testTag("home.continueWatchingRow"),
            horizontalArrangement = Arrangement.spacedBy(Vela.md)
        ) {
            items(items, key = { it.videoId }) { r ->
                ContinueWatchingCard(
                    record = r,
                    onClick = {
                        val v = VideoSummary(
                            id = r.videoId,
                            title = r.title,
                            channelId = r.channelId,
                            channelName = "",
                            durationSeconds = r.durationMs.takeIf { it > 0 }?.div(1000),
                        )
                        navigator.push(Route.Video(v))
                    }
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    record: WatchRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val hover = if (hovered) Modifier.border(2.dp, Vela.wake, Vela.mediumShape).background(Vela.colors.onSurface.toColor().copy(alpha = 0.05f), Vela.mediumShape) else Modifier
    val click = hover.clip(Vela.mediumShape).hoverable(source).pressFeedback(source).clickable(interactionSource = source, indication = null, onClick = onClick)

    val progress = if (record.durationMs > 0) (record.positionMs.toFloat() / record.durationMs).coerceIn(0f, 1f) else 0f
    val remainingSeconds = if (record.durationMs > 0 && record.positionMs < record.durationMs) {
        (record.durationMs - record.positionMs) / 1000
    } else null
    val remainingText = remainingSeconds?.let { formatDuration(it) }?.let { "$it left" }

    Column(
        modifier = modifier
            .width(176.dp)
            .then(click)
            .padding(bottom = Vela.xs)
            .testTag("home.continueWatchingItem"),
        verticalArrangement = Arrangement.spacedBy(Vela.xs)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .clip(Vela.thumbnailShape)
                .background(Vela.colors.surfaceVariant.toColor())
        ) {
            coil3.compose.AsyncImage(
                model = "https://i.ytimg.com/vi/${record.videoId}/mqdefault.jpg",
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            formatDuration(record.durationMs.takeIf { it > 0 }?.div(1000))?.let { dur ->
                Text(
                    text = dur,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 6.dp, end = Vela.xs)
                        .clip(Vela.smallShape)
                        .background(Vela.colors.background.toColor().copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Vela.colors.onBackground.toColor(),
                    maxLines = 1,
                )
            }
            if (progress > 0f) {
                Box(
                    Modifier.align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Vela.colors.background.toColor().copy(alpha = 0.5f))
                ) {
                    Box(
                        Modifier.fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(Vela.wake)
                    )
                }
            }
        }
        Text(
            text = record.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (remainingText != null) {
            Text(
                text = remainingText,
                style = MaterialTheme.typography.labelSmall,
                color = Vela.colors.onSurfaceVariant.toColor(),
                maxLines = 1,
            )
        }
    }
}
