package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.extractor.ChannelPage
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.ui.components.Avatar
import com.greenaltambe.vela.ui.components.PrimaryButton
import com.greenaltambe.vela.ui.components.SectionHeader
import com.greenaltambe.vela.ui.components.formatCount
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.toColor
import com.greenaltambe.vela.ui.nav.Navigator
import kotlinx.coroutines.launch

@Composable
fun ChannelScreen(services: AppServices, navigator: Navigator, channelId: String, channelName: String, modifier: Modifier = Modifier) {
    var page by remember(channelId) { mutableStateOf<ChannelPage?>(null) }
    var videos by remember(channelId) { mutableStateOf<List<VideoSummary>>(emptyList()) }
    var next by remember(channelId) { mutableStateOf<String?>(null) }
    var state by remember(channelId) { mutableStateOf<ListState>(ListState.Loading) }
    var loading by remember(channelId) { mutableStateOf(false) }
    var subscribed by remember(channelId) { mutableStateOf(services.subscriptions.isSubscribed(channelId)) }
    val scope = rememberCoroutineScope()

    fun first() {
        state = ListState.Loading
        scope.launch {
            when (val r = services.extractor.channel(channelId)) {
                is ExtractorResult.Success -> {
                    page = r.value; videos = r.value.videos; next = r.value.continuation
                    state = if (videos.isEmpty()) ListState.Empty("This channel has no videos.") else ListState.Ready
                }
                is ExtractorResult.Failure -> state = ListState.Error("Channel unavailable: ${r.error.message}") { first() }
            }
        }
    }
    LaunchedEffect(channelId) { first() }

    fun more() {
        if (loading) return
        loading = true
        scope.launch {
            when (val r = services.extractor.channelVideos(channelId, next)) {
                is ExtractorResult.Success -> { videos = (videos + r.value.items).distinctBy { it.id }; next = r.value.continuation }
                is ExtractorResult.Failure -> state = ListState.Error("Could not load more: ${r.error.message}") { state = ListState.Ready; more() }
            }
            loading = false
        }
    }

    val name = page?.name ?: channelName
    VideoCollection(
        services, navigator, videos, state, testTag = "channel.list",
        header = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
                page?.bannerUrl?.let { banner ->
                    coil3.compose.AsyncImage(banner, null, Modifier.fillMaxWidth().aspectRatio(16f / 4.5f).clip(Vela.thumbnailShape).testTag("channel.banner"), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.md)) {
                    Avatar(name, size = 64.dp, url = page?.avatarUrl)
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("channel.name"))
                        formatCount(page?.subscriberCount)?.let { Text("$it subscribers", style = MaterialTheme.typography.bodySmall, maxLines = 1, color = Vela.colors.onSurfaceVariant.toColor()) }
                    }
                }
                if (subscribed) OutlinedButton(onClick = { services.subscriptions.unsubscribe(channelId); services.feed.reload(); subscribed = false }, modifier = Modifier.testTag("channel.subscribe")) { Text("Subscribed", maxLines = 1) }
                else PrimaryButton("Subscribe", onClick = { services.subscriptions.subscribe(channelId, name, page?.avatarUrl, services.clock.now()); subscribed = true }, modifier = Modifier.testTag("channel.subscribe"))
                if (services.downloads != null) {
                    var auto by remember(channelId) { mutableStateOf(services.autoDownloadRules.ruleFor(channelId)?.enabled == true) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
                        androidx.compose.material3.Switch(auto, {
                            auto = it
                            if (it) services.autoDownloadRules.upsert(com.greenaltambe.vela.downloads.AutoDownloadRule(channelId, name, sinceMs = services.clock.now().toEpochMilliseconds()))
                            else services.autoDownloadRules.ruleFor(channelId)?.let { r -> services.autoDownloadRules.upsert(r.copy(enabled = false)) }
                        }, modifier = Modifier.testTag("channel.autoDownload"))
                        Text("Download new videos automatically", style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    }
                }
                page?.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                SectionHeader("Videos")
            }
        },
        footer = if (next != null) ({ TextButton(onClick = ::more, enabled = !loading) { Text("Load more", maxLines = 1) } }) else null,
    )
}
