package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.extractor.ChannelPage
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.ui.components.PrimaryButton
import com.greenaltambe.vela.ui.components.SectionHeader
import com.greenaltambe.vela.ui.components.VideoCard
import com.greenaltambe.vela.ui.components.formatCount
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.toColor
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import kotlinx.coroutines.launch

@Composable
fun ChannelScreen(services: AppServices, navigator: Navigator, channelId: String, channelName: String, modifier: Modifier = Modifier) {
    var page by remember(channelId) { mutableStateOf<ChannelPage?>(null) }
    var videos by remember(channelId) { mutableStateOf<List<VideoSummary>>(emptyList()) }
    var next by remember(channelId) { mutableStateOf<String?>(null) }
    var status by remember(channelId) { mutableStateOf("Loading...") }
    var loading by remember(channelId) { mutableStateOf(false) }
    var subscribed by remember(channelId) { mutableStateOf(services.subscriptions.isSubscribed(channelId)) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(channelId) {
        when (val r = services.extractor.channel(channelId)) {
            is ExtractorResult.Success -> {
                page = r.value
                videos = r.value.videos
                next = r.value.continuation
                status = if (videos.isEmpty()) "No videos." else ""
            }
            is ExtractorResult.Failure -> status = "Channel unavailable: ${r.error.message}"
        }
    }

    fun more() {
        if (loading) return
        loading = true
        scope.launch {
            when (val r = services.extractor.channelVideos(channelId, next)) {
                is ExtractorResult.Success -> { videos = (videos + r.value.items).distinctBy { it.id }; next = r.value.continuation }
                is ExtractorResult.Failure -> status = "Could not load more: ${r.error.message}"
            }
            loading = false
        }
    }

    LazyColumn(modifier.fillMaxSize().testTag("channel.list"), verticalArrangement = Arrangement.spacedBy(Vela.sm), contentPadding = Vela.pagePadding) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.md)) {
                Box(Modifier.size(72.dp).clip(Vela.pillShape).background(Vela.colors.surfaceVariant.toColor())) {
                    page?.avatarUrl?.let { AsyncImage(it, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
                }
                Row(Modifier.weight(1f)) {
                    androidx.compose.foundation.layout.Column {
                        Text(page?.name ?: channelName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("channel.name"))
                        formatCount(page?.subscriberCount)?.let { Text("$it subscribers", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                val name = page?.name ?: channelName
                if (subscribed) {
                    OutlinedButton(onClick = { services.subscriptions.unsubscribe(channelId); services.feed.reload(); subscribed = false }, modifier = Modifier.testTag("channel.subscribe")) { Text("Subscribed") }
                } else {
                    PrimaryButton("Subscribe", onClick = {
                        services.subscriptions.subscribe(channelId, name, page?.avatarUrl, services.clock.now())
                        subscribed = true
                    }, modifier = Modifier.testTag("channel.subscribe"))
                }
            }
        }
        page?.description?.takeIf { it.isNotBlank() }?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 4) } }
        item { SectionHeader("Videos") }
        if (status.isNotEmpty()) item { Text(status) }
        items(videos, key = { it.id }) { v ->
            VideoCard(v, onClick = { navigator.push(Route.Video(v)) }, trailing = { VideoMenu(services, v) })
        }
        if (next != null) item { TextButton(onClick = ::more, enabled = !loading) { Text("Load more") } }
    }
}
