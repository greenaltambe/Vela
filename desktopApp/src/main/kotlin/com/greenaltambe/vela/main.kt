package com.greenaltambe.vela

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.extractor.chain.DiagnosticsLog
import com.greenaltambe.vela.extractor.chain.stubTiers
import com.greenaltambe.vela.extractor.newpipe.NewPipeExtractor
import com.greenaltambe.vela.extractor.ytdlp.SystemProcessRunner
import com.greenaltambe.vela.extractor.ytdlp.createYtDlpTier
import kotlinx.coroutines.runBlocking

fun main() {
    val settings = SettingsResolver(InMemorySettingsStore())
    val log = DiagnosticsLog()
    // yt-dlp is probed once at startup (`yt-dlp --version`); if it isn't there the tier is simply not registered.
    val ytDlp = runBlocking {
        createYtDlpTier(SystemProcessRunner(), binary = { settings.resolve(Settings.ytDlpPath) }, log = log)
    }
    // Chain order: ANONYMOUS (NewPipe), AUTHENTICATED (stub), ALT_CLIENT (stub), YT_DLP (real, or absent).
    val tiers = stubTiers(anonymous = NewPipeExtractor(), ytDlp = ytDlp?.extractor)

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Vela",
        ) {
            App(tiers, settings, log)
        }
    }
}
