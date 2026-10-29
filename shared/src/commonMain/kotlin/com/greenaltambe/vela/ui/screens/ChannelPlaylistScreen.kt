package com.greenaltambe.vela.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.videoPagedList
import com.greenaltambe.vela.ui.nav.Navigator
import kotlinx.coroutines.launch

/** The videos of a YouTube playlist from a channel's Playlists tab, paged as you reach the end. */
@Composable
fun ChannelPlaylistScreen(services: AppServices, navigator: Navigator, playlistId: String, modifier: Modifier = Modifier) {
    var list by remember(playlistId) { mutableStateOf(videoPagedList()) }
    var state by remember(playlistId) { mutableStateOf<ListState>(ListState.Loading) }
    var loading by remember(playlistId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun load(more: Boolean) {
        if (loading) return
        loading = true
        if (!more) state = ListState.Loading
        scope.launch {
            when (val r = services.extractor.playlistVideos(playlistId, list.continuation.takeIf { more })) {
                is ExtractorResult.Success -> {
                    list = if (more) list.append(r.value) else list.replace(r.value)
                    state = if (list.items.isEmpty()) ListState.Empty("This playlist is empty.") else ListState.Ready
                }
                is ExtractorResult.Failure -> if (list.items.isEmpty()) state = ListState.Error("Playlist unavailable: ${r.error.message}") { load(false) }
            }
            loading = false
        }
    }
    LaunchedEffect(playlistId) { load(false) }
    val shown = rememberFiltered(services, list.items, com.greenaltambe.vela.modules.dearrow.DeArrowSurface.FEED)
    VideoCollection(
        services, navigator, shown, state, modifier, grid = false, testTag = "channelPlaylist.list",
        onEndReached = if (list.continuation != null) ({ if (!loading && state == ListState.Ready) load(true) }) else null,
        footer = if (list.continuation != null) ({ TextButton(onClick = { load(true) }, enabled = !loading) { Text("Load more", maxLines = 1) } }) else null,
    )
}
