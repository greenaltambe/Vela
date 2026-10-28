package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.health.NewPipeVersion
import com.greenaltambe.vela.ui.components.EmptyState
import com.greenaltambe.vela.ui.components.PrimaryButton
import com.greenaltambe.vela.ui.components.SectionHeader
import com.greenaltambe.vela.ui.components.VelaTopBar
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.toColor
import com.greenaltambe.vela.ui.nav.Navigator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Diagnostics > Health: per-tier canary status, the NewPipe age warning, and a button to run the checks now. */
@Composable
fun HealthScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    val health = services.health
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxSize()) {
        VelaTopBar("Health", onBack = { navigator.back() })
        if (health == null) { EmptyState("Health checks are not available on this platform."); return@Column }
        val running by health.running.collectAsState()
        val results by health.results.collectAsState()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Vela.gutter), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
            NewPipeVersion.warning(services.clock.now())?.let { Text(it, color = Vela.colors.error.toColor(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("health.newpipe")) }
            Text("NewPipeExtractor ${NewPipeVersion.VERSION} (released ${NewPipeVersion.RELEASED})", style = MaterialTheme.typography.bodySmall)
            PrimaryButton(if (running) "Checking…" else "Run checks now", { scope.launch { health.run() } }, enabled = !running, modifier = Modifier.testTag("health.run"))
            Text("Checks use only the sources that need no account. Last run: ${health.lastRun()?.toString()?.substring(0, 16)?.replace('T', ' ') ?: "never"}", style = MaterialTheme.typography.bodySmall)
            health.summary().forEach { t ->
                SectionHeader(t.tier.name.lowercase().replace('_', ' '))
                Text(
                    when {
                        t.lastSuccess == null && t.lastFailure == null -> "Not checked yet."
                        t.healthy -> "Working. Last success ${t.lastSuccess.toString().substring(0, 16).replace('T', ' ')}."
                        else -> "Failing: ${t.lastFailureReason ?: "unknown"} (since ${t.lastFailure.toString().substring(0, 16).replace('T', ' ')})."
                    },
                    style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("health.tier.${t.tier.name}"),
                )
                t.history.take(8).forEach { r ->
                    Text("${if (r.ok) "ok  " else "FAIL"} ${r.check} ${r.millis}ms${r.detail?.let { " - $it" } ?: ""}", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                }
            }
            if (results.isEmpty()) EmptyState("No results yet. Press Run checks now.")
        }
    }
}

/** Runs the canaries shortly after start when the last run is older than a week, then re-checks every six hours while the app is open. */
@Composable
fun HealthStartEffect(services: AppServices) {
    val health = services.health ?: return
    LaunchedEffect(Unit) {
        delay(15_000)
        while (true) {
            if (health.isStale()) runCatching { health.run() }
            delay(6L * 60 * 60 * 1000)
        }
    }
}
