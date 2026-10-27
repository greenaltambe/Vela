package com.greenaltambe.vela

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.extractor.ExtractorError
import com.greenaltambe.vela.extractor.StubExtractor
import com.greenaltambe.vela.extractor.chain.DiagnosticsLog
import com.greenaltambe.vela.extractor.chain.ExtractorTier
import com.greenaltambe.vela.extractor.chain.FallbackChain
import com.greenaltambe.vela.extractor.chain.TierAttempt
import com.greenaltambe.vela.extractor.chain.TierId
import com.greenaltambe.vela.ui.VelaTheme

@Composable
@Preview
fun App() {
    val settings = remember { SettingsResolver(InMemorySettingsStore()) }
    val log = remember { DiagnosticsLog() }
    LaunchedEffect(Unit) { runFakeChain(settings, log) }
    val attempts by log.attempts.collectAsState()

    VelaTheme {
        Column(Modifier.safeContentPadding().fillMaxSize()) {
            Text("Vela", style = MaterialTheme.typography.headlineMedium)
            Text("${Settings.shortsMode.label}: ${settings.resolve(Settings.shortsMode)}")
            Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
            LazyColumn {
                items(attempts) { Text(it.describe()) }
            }
        }
    }
}

private fun TierAttempt.describe() = buildString {
    append("$subject  $tier  $outcome")
    reason?.let { append("  $it") }
    detail?.let { append("  ($it)") }
    append("  ${duration}")
}

/** Anonymous is blocked, the account tier serves it; a second call then skips anonymous via tier memory. */
private suspend fun runFakeChain(settings: SettingsResolver, log: DiagnosticsLog) {
    val chain = FallbackChain(
        tiers = listOf(
            ExtractorTier(TierId.ANONYMOUS, StubExtractor(ExtractorError.Blocked("IP blocked"))),
            ExtractorTier(TierId.AUTHENTICATED, StubExtractor()),
            ExtractorTier(TierId.ALT_CLIENT, StubExtractor()),
            ExtractorTier(TierId.YT_DLP, StubExtractor()),
        ),
        accountMode = { settings.resolve(Settings.accountFallbackMode) },
        log = log,
    )
    chain.streams("demo-video")
    chain.streams("demo-video-2")
}
