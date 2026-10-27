package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.greenaltambe.vela.modules.dearrow.DeArrowSurface
import com.greenaltambe.vela.extractor.videoPagedList
import com.greenaltambe.vela.ui.components.SectionHeader
import com.greenaltambe.vela.ui.components.VideoCard
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import kotlinx.coroutines.launch

/** Trending, through the same feed pipeline as the subscription feed. */
@Composable
fun HomeScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    var videos by remember { mutableStateOf(videoPagedList()) }
    var status by remember { mutableStateOf("Loading...") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load(more: Boolean) {
        if (loading) return
        loading = true
        scope.launch {
            when (val result = services.extractor.trending(videos.continuation.takeIf { more })) {
                is ExtractorResult.Success -> {
                    videos = if (more) videos.append(result.value) else videos.replace(result.value)
                    status = if (videos.items.isEmpty()) "Nothing trending right now." else ""
                }
                is ExtractorResult.Failure -> status = "Could not load trending: ${result.error.message}"
            }
            loading = false
        }
    }
    LaunchedEffect(Unit) { if (videos.items.isEmpty()) load(more = false) }

    val shown = rememberFiltered(services, videos.items, DeArrowSurface.FEED)
    LazyColumn(modifier.fillMaxSize().testTag("home.list"), verticalArrangement = Arrangement.spacedBy(Vela.sm), contentPadding = Vela.pagePadding) {
        item { SectionHeader("Trending") { TextButton(onClick = { load(more = false) }) { Text("Refresh") } } }
        if (status.isNotEmpty()) item { Text(status) }
        items(shown, key = { it.id }) { v ->
            VideoCard(v, badge = rydBadge(services, v.id), onClick = { navigator.push(Route.Video(v)) }, onChannelClick = { navigator.push(Route.Channel(v.channelId, v.channelName)) }, trailing = { VideoMenu(services, v) })
        }
        if (videos.continuation != null) item { TextButton(onClick = { load(more = true) }, enabled = !loading) { Text("Load more") } }
    }
}
