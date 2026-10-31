package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.PagedList
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.extractor.videoPagedList
import com.greenaltambe.vela.modules.dearrow.DeArrowSurface
import com.greenaltambe.vela.ui.components.HScrollRow
import com.greenaltambe.vela.ui.nav.Navigator
import kotlinx.coroutines.launch
import kotlin.time.Instant

/** What Explore offers besides Trending: each is a search for the topic. */
val ExploreTopics = listOf("Music", "Gaming", "News", "Live", "Sports", "Podcasts", "Movies", "Learning", "Cooking", "Tech")
private const val TRENDING = "Trending"

/** Explore's last state, kept while the shell lives: the open topic, each topic's loaded videos and scroll position. */
class ExploreStore {
    class Entry(var videos: PagedList<VideoSummary> = videoPagedList(), var at: Instant? = null, val scroll: ScrollMemory = ScrollMemory())
    var topic: String = TRENDING
    private val entries = HashMap<String, Entry>()
    fun entry(topic: String) = entries.getOrPut(topic) { Entry() }
    fun fresh(topic: String, now: Instant) = entry(topic).takeIf { e -> e.videos.items.isNotEmpty() && e.at?.let { (now - it).inWholeMilliseconds < 30 * 60 * 1000L } == true }
}

val LocalExploreStore = staticCompositionLocalOf<ExploreStore?> { null }

/** Explore: Trending and topic pills (Music, Gaming, ...) above the matching videos. Switching pills keeps what each already loaded. */
@Composable
fun ExploreScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    val store = LocalExploreStore.current ?: remember { ExploreStore() }
    var topic by remember { mutableStateOf(store.topic) }
    val entry = store.entry(topic)
    var videos by remember(topic) { mutableStateOf(entry.videos) }
    var state by remember(topic) { mutableStateOf<ListState>(if (store.fresh(topic, services.clock.now()) != null) ListState.Ready else ListState.Loading) }
    var loading by remember(topic) { mutableStateOf(false) }
    var pulling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load(more: Boolean) {
        if (loading) return
        loading = true
        if (!more && videos.items.isEmpty()) state = ListState.Loading
        val t = topic
        scope.launch {
            val token = videos.continuation.takeIf { more }
            val result = if (t == TRENDING) services.extractor.trending(token) else services.extractor.search(t, token)
            when (result) {
                is ExtractorResult.Success -> {
                    videos = if (more) videos.append(result.value) else videos.replace(result.value)
                    state = if (videos.items.isEmpty()) ListState.Empty("Nothing found for $t.", "Refresh") { load(false) } else ListState.Ready
                    entry.videos = videos; if (!more) entry.at = services.clock.now()
                }
                is ExtractorResult.Failure -> if (videos.items.isEmpty()) state = ListState.Error("Could not load $t: ${result.error.message}") { load(false) }
            }
            loading = false
        }
    }
    LaunchedEffect(loading) { if (!loading) pulling = false }
    LaunchedEffect(topic) { store.topic = topic; if (store.fresh(topic, services.clock.now()) == null) load(more = false) }

    val shown = rememberFiltered(services, videos.items, DeArrowSurface.FEED)
    com.greenaltambe.vela.ui.components.RefreshBox(isRefreshing = pulling && loading, onRefresh = { pulling = true; load(more = false) }, modifier = modifier) {
        VideoCollection(
            services, navigator, shown, state, Modifier, testTag = "explore.list", scroll = entry.scroll,
            header = {
                HScrollRow(Modifier.testTag("explore.topics")) {
                    items(listOf(TRENDING) + ExploreTopics) { t -> FilterChip(selected = topic == t, onClick = { topic = t }, label = { Text(t, maxLines = 1) }, modifier = Modifier.testTag("explore.topic.$t")) }
                }
            },
            onEndReached = { if (videos.continuation != null && !loading && state == ListState.Ready) load(more = true) },
            footer = if (videos.continuation != null) ({ TextButton(onClick = { load(more = true) }, enabled = !loading) { Text("Load more", maxLines = 1) } }) else null,
        )
    }
}
