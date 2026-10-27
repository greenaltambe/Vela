package com.greenaltambe.vela

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.extractor.chain.DiagnosticsLog
import com.greenaltambe.vela.extractor.chain.ExtractorTier
import com.greenaltambe.vela.extractor.chain.FallbackChain
import com.greenaltambe.vela.extractor.chain.stubTiers
import com.greenaltambe.vela.ui.DebugScreen
import com.greenaltambe.vela.ui.VelaTheme

/**
 * [tiers] come from the platform entry point so shared UI never depends on a concrete extractor; the entry point
 * also owns [settings] and [log] so it can read settings and write startup notes before the UI exists.
 */
@Composable
@Preview
fun App(
    tiers: List<ExtractorTier> = stubTiers(),
    settings: SettingsResolver = remember { SettingsResolver(InMemorySettingsStore()) },
    log: DiagnosticsLog = remember { DiagnosticsLog() },
) {
    val chain = remember(tiers) {
        FallbackChain(tiers, accountMode = { settings.resolve(Settings.accountFallbackMode) }, log = log)
    }

    VelaTheme {
        Column(Modifier.safeContentPadding().fillMaxSize()) {
            Text("Vela", style = MaterialTheme.typography.headlineMedium)
            DebugScreen(chain, log, Modifier.weight(1f))
        }
    }
}
