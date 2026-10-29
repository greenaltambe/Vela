package com.greenaltambe.vela.ui.screens

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.videoPagedList
import com.greenaltambe.vela.modules.dearrow.DeArrowSurface
import com.greenaltambe.vela.ui.nav.Navigator
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
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(isRefreshing = pulling && loading, onRefresh = { pulling = true; load(more = false) }, modifier = modifier) {
    VideoCollection(
        services, navigator, shown, state, Modifier, testTag = "home.list", scroll = store?.scroll,
        header = {
            note?.let { Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("home.note")) }
            (videos.items.size - shown.size).takeIf { it > 0 }?.let { Text("$it hidden by your feed filters (Settings > Feed).", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("home.hiddenByFeed")) }
        },
        // More is fetched by itself as the last card comes into view; the button stays as a fallback if that fetch failed.
        onEndReached = { if (videos.continuation != null && !loading && state == ListState.Ready) load(more = true) },
        footer = if (videos.continuation != null) ({ TextButton(onClick = { load(more = true) }, enabled = !loading) { Text("Load more", maxLines = 1) } }) else null,
    )
    }
}
