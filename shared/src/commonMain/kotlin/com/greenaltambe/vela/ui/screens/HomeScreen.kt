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
import com.greenaltambe.vela.ui.components.SectionHeader
import com.greenaltambe.vela.ui.nav.Navigator
import kotlinx.coroutines.launch

/** Trending, through the same feed pipeline as the subscription feed. */
@Composable
fun HomeScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    var videos by remember { mutableStateOf(videoPagedList()) }
    var state by remember { mutableStateOf<ListState>(ListState.Loading) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load(more: Boolean) {
        if (loading) return
        loading = true
        if (!more && videos.items.isEmpty()) state = ListState.Loading
        scope.launch {
            when (val result = services.extractor.trending(videos.continuation.takeIf { more })) {
                is ExtractorResult.Success -> {
                    videos = if (more) videos.append(result.value) else videos.replace(result.value)
                    state = if (videos.items.isEmpty()) ListState.Empty("Nothing is trending right now.", "Refresh") { load(false) } else ListState.Ready
                }
                is ExtractorResult.Failure -> if (videos.items.isEmpty()) state = ListState.Error("Could not load trending: ${result.error.message}") { load(false) }
            }
            loading = false
        }
    }
    LaunchedEffect(Unit) { if (videos.items.isEmpty()) load(more = false) }

    val shown = rememberFiltered(services, videos.items, DeArrowSurface.FEED)
    VideoCollection(
        services, navigator, shown, state, modifier, testTag = "home.list",
        header = { SectionHeader("Trending") { TextButton(onClick = { load(more = false) }, enabled = !loading) { Text("Refresh", maxLines = 1) } } },
        footer = if (videos.continuation != null) ({ TextButton(onClick = { load(more = true) }, enabled = !loading) { Text("Load more", maxLines = 1) } }) else null,
    )
}
