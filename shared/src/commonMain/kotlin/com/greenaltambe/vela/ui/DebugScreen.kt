package com.greenaltambe.vela.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.core.http.ModuleLog
import com.greenaltambe.vela.extractor.Extractor
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.StreamSet
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.extractor.chain.DiagnosticsLog
import com.greenaltambe.vela.extractor.chain.TierAttempt
import com.greenaltambe.vela.extractor.videoPagedList
import com.greenaltambe.vela.player.PlaybackPlan
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Settings > Diagnostics: exercise the extractor chain by hand (search, list a result's streams) and see which tier served what and why others failed. */
@Composable
fun DiagnosticsScreen(extractor: Extractor, log: DiagnosticsLog, moduleLog: ModuleLog, downloads: com.greenaltambe.vela.downloads.Downloads?, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(videoPagedList()) }
    var searchedQuery by remember { mutableStateOf("") }
    var streamSummary by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val attempts by log.attempts.collectAsState()
    val moduleEvents by moduleLog.events.collectAsState()

    fun search(more: Boolean = false) {
        job?.cancel()
        status = "Searching..."
        if (!more) streamSummary = null
        val q = if (more) searchedQuery else query
        job = scope.launch {
            when (val result = extractor.search(q, results.continuation.takeIf { more })) {
                is ExtractorResult.Success -> {
                    searchedQuery = q
                    results = if (more) results.append(result.value) else results.replace(result.value)
                    status = "${results.items.size} results" + if (results.continuation != null) " (more available)" else ""
                }
                is ExtractorResult.Failure -> {
                    if (!more) results = videoPagedList()
                    status = "Search failed: ${result.error.reason} ${result.error.message}"
                }
            }
        }
    }

    fun load(video: VideoSummary) {
        job?.cancel()
        streamSummary = "Loading streams for ${video.title}..."
        job = scope.launch {
            streamSummary = when (val result = extractor.streams(video.id)) {
                is ExtractorResult.Success -> "${video.title}: ${result.value.describe()}"
                is ExtractorResult.Failure -> "${video.title}: failed: ${result.error.reason} ${result.error.message}"
            }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Diagnostics", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text("Search (lists streams on click)") }, singleLine = true)
            Button(onClick = { search() }, enabled = query.isNotBlank()) { Text("Search") }
            if (results.continuation != null) Button(onClick = { search(more = true) }) { Text("Load more") }
        }
        Text(status)
        streamSummary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(results.items) { video -> Text("${video.title}  (${video.channelName})", Modifier.clickable { load(video) }.fillMaxWidth()) }
        }
        if (downloads != null) {
            Text("Downloads", style = MaterialTheme.typography.titleMedium)
            Text("Merging: ${downloads.merger.description}")
            downloads.diagnostics().forEach { Text(it) }
        }
        Text("Modules (SponsorBlock, Return YouTube Dislike, DeArrow)", style = MaterialTheme.typography.titleMedium)
        if (moduleEvents.isEmpty()) Text("No module activity yet.")
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(moduleEvents.asReversed()) { Text("${if (it.ok) "ok      " else "INACTIVE"}  ${it.moduleId}  ${it.message}") }
        }
        Text("Tier attempts", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(attempts.asReversed()) { Text(it.describe()) }
        }
    }
}

internal fun StreamSet.describe(): String {
    val muxed = videoStreams.count { it.hasAudio }
    return "${videoStreams.size} video ($muxed muxed), ${audioStreams.size} audio, hls=${hlsUrl != null}, " +
        "client=$client, expires=${expiresAt ?: "unknown"}"
}

internal fun TierAttempt.describe() = buildString {
    append("$subject  $tier  $outcome")
    reason?.let { append("  $it") }
    detail?.let { append("  ($it)") }
    append("  $duration")
}

internal fun PlaybackPlan.describe(): String = when (this) {
    is PlaybackPlan.Muxed -> "muxed ${stream.height}p ${stream.codec}"
    is PlaybackPlan.Separate -> "${video.height}p ${video.codec} + ${audio.codec} ${audio.bitrate / 1000}k${if (audio.isDrc) " DRC" else ""}"
    is PlaybackPlan.Hls -> "HLS"
    is PlaybackPlan.AudioOnly -> "audio only ${audio.codec} ${audio.bitrate / 1000}k"
    is PlaybackPlan.Local -> if (hasVideo) "downloaded file" else "downloaded audio"
    is PlaybackPlan.NothingPlayable -> "nothing playable: $reason"
}
