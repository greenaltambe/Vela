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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
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
            services.releases.forEach { w ->
                val st by w.check.status.collectAsState()
                Text(st.describe(), color = if (st.newerAvailable) Vela.colors.error.toColor() else Color.Unspecified, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("health.release.${st.name}"))
                if (st.name == "yt-dlp" && st.newerAvailable && services.managedYtDlp != null && services.settings.resolve(com.greenaltambe.vela.core.settings.Settings.ytDlpAutoUpdate) != com.greenaltambe.vela.core.settings.YtDlpAutoUpdate.OFF) {
                    val msg by services.managedYtDlp.status.collectAsState()
                    PrimaryButton("Install yt-dlp ${st.latest} in the app's folder", { scope.launch { services.managedYtDlp.install(st.latest!!) } }, modifier = Modifier.testTag("health.installYtDlp"))
                    if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("health.ytdlpStatus"))
                }
                w.copyLines().forEach { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("health.copy")) }
            }
            PrimaryButton(if (running) "Checking…" else "Run checks now", { scope.launch { services.releases.forEach { runCatching { it.check.check(force = true) } }; health.run() } }, enabled = !running, modifier = Modifier.testTag("health.run"))
            Text("Checks use only the sources that need no account. Last run: ${health.lastRun()?.toString()?.substring(0, 16)?.replace('T', ' ') ?: "never"}", style = MaterialTheme.typography.bodySmall)
            health.summary().forEach { t ->
                SectionHeader(t.tier.name.lowercase().replace('_', ' '))
                val at = t.latestRunAt?.toString()?.substring(0, 16)?.replace('T', ' ')
                // One sentence from the latest run only: older runs can never contradict it.
                Text(
                    when {
                        t.latest.isEmpty() -> "Not checked yet."
                        t.healthy -> "Working (run $at, ${t.latest.size} checks passed)."
                        else -> "Failing in the latest run ($at): ${t.failures.size} of ${t.latest.size} checks. ${t.lastFailureReason ?: "unknown"}" +
                            (t.lastGood?.let { " Last time a check passed: ${it.toString().substring(0, 16).replace('T', ' ')}." } ?: "")
                    },
                    style = MaterialTheme.typography.bodyMedium, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("health.tier.${t.tier.name}"),
                )
                t.latest.forEach { r -> HealthRow(r) }
                if (t.runs.isNotEmpty()) {
                    var open by remember { mutableStateOf(false) }
                    TextButton({ open = !open }, modifier = Modifier.testTag("health.history.${t.tier.name}")) { Text(if (open) "Hide history" else "History (${t.runs.size} earlier runs)") }
                    if (open) t.runs.forEach { run ->
                        Text("Run ${run.first().at.toString().substring(0, 16).replace('T', ' ')}: ${run.count { it.ok }} ok, ${run.count { !it.ok }} failed", style = MaterialTheme.typography.labelMedium)
                        run.filter { !it.ok }.forEach { r -> HealthRow(r) }
                    }
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
            services.releases.forEach { runCatching { it.check.check() } } // at most once a day, see ReleaseCheck
            // AUTO: install a newer yt-dlp into the app's own folder; the updater rolls back if the yt-dlp source regresses.
            services.managedYtDlp?.let { up ->
                val st = services.releases.firstOrNull { it.check.status.value.name == "yt-dlp" }?.check?.status?.value
                if (st != null && st.newerAvailable && services.settings.resolve(com.greenaltambe.vela.core.settings.Settings.ytDlpAutoUpdate) == com.greenaltambe.vela.core.settings.YtDlpAutoUpdate.AUTO) runCatching { up.install(st.latest!!) }
            }
            if (health.isStale()) runCatching { health.run() }
            delay(6L * 60 * 60 * 1000)
        }
    }
}

@Composable
private fun HealthRow(r: com.greenaltambe.vela.health.CanaryResult) {
    Text("${if (r.ok) "ok  " else "FAIL"} ${r.check} ${r.millis}ms${r.detail?.let { " - $it" } ?: ""}", style = MaterialTheme.typography.bodySmall,
        maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
}
