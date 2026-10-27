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
import com.greenaltambe.vela.extractor.Extractor
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.StreamSet
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.extractor.chain.DiagnosticsLog
import com.greenaltambe.vela.extractor.chain.TierAttempt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Throwaway screen for exercising the extractor chain by hand: search, pick a result, see its streams and the diagnostics. */
@Composable
fun DebugScreen(extractor: Extractor, log: DiagnosticsLog, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<VideoSummary>>(emptyList()) }
    var continuation by remember { mutableStateOf<String?>(null) }
    var searchedQuery by remember { mutableStateOf("") }
    var streamSummary by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val attempts by log.attempts.collectAsState()

    /** A fresh search, or the next page of the last one when [more] is set. */
    fun search(more: Boolean = false) {
        job?.cancel()
        status = "Searching..."
        if (!more) streamSummary = null
        val q = if (more) searchedQuery else query
        job = scope.launch {
            when (val result = extractor.search(q, continuation.takeIf { more })) {
                is ExtractorResult.Success -> {
                    searchedQuery = q
                    results = if (more) results + result.value.items else result.value.items
                    continuation = result.value.continuation
                    status = "${results.size} results" + if (continuation != null) " (more available)" else ""
                }
                is ExtractorResult.Failure -> {
                    if (!more) results = emptyList()
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text("Search") }, singleLine = true)
            Button(onClick = { search() }, enabled = query.isNotBlank()) { Text("Search") }
            if (continuation != null) Button(onClick = { search(more = true) }) { Text("Load more") }
        }
        Text(status)
        streamSummary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(results) { video ->
                Text("${video.title}  (${video.channelName})", Modifier.clickable { load(video) }.fillMaxWidth())
            }
        }
        Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(attempts.asReversed()) { Text(it.describe()) }
        }
    }
}

private fun StreamSet.describe(): String {
    val muxed = videoStreams.count { it.hasAudio }
    return "${videoStreams.size} video ($muxed muxed), ${audioStreams.size} audio, hls=${hlsUrl != null}, " +
        "client=$client, expires=${expiresAt ?: "unknown"}"
}

private fun TierAttempt.describe() = buildString {
    append("$subject  $tier  $outcome")
    reason?.let { append("  $it") }
    detail?.let { append("  ($it)") }
    append("  $duration")
}
