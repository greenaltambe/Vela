@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.greenaltambe.vela.ui.screens

import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.settings.segmentedItemColors
import com.greenaltambe.vela.ui.settings.settingsSection
import com.greenaltambe.vela.ui.settings.SettingsScaffold
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Surface
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import com.greenaltambe.vela.ui.theme.Spacing
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
import com.greenaltambe.vela.ui.nav.Navigator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Health: whether each way of getting video is working (a row per source with its latest checks), updates of the parts that go out of date, and a button to run the checks now. */
@Composable
fun HealthScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    val health = services.health
    val scope = rememberCoroutineScope()
    if (health == null) {
        SettingsScaffold("Health", modifier, onBack = { navigator.back() }, listTag = "health.list") { item { EmptyState("Health checks are not available on this platform.") } }
        return
    }
    val running by health.running.collectAsState()
    val results by health.results.collectAsState()
    val open = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val statuses = services.releases.map { w -> w to w.check.status.collectAsState().value }
    val ytdlpMessage = services.managedYtDlp?.status?.collectAsState()?.value.orEmpty()
    val summary = health.summary()
    SettingsScaffold("Health", modifier, onBack = { navigator.back() }, listTag = "health.list") {
        if (statuses.isNotEmpty()) settingsSection("updates", "Updates", statuses, { it.second.name }) { (w, st), shape ->
            Column(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(st.describe(), color = if (st.newerAvailable) MaterialTheme.colorScheme.error else Color.Unspecified, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("health.release.${st.name}"))
                w.copyLines().forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("health.copy")) }
                if (st.name == "yt-dlp" && services.managedYtDlp != null) {
                    if (st.newerAvailable && services.settings.resolve(com.greenaltambe.vela.core.settings.Settings.ytDlpAutoUpdate) != com.greenaltambe.vela.core.settings.YtDlpAutoUpdate.OFF)
                        PrimaryButton("Install yt-dlp ${st.latest}", { scope.launch { services.managedYtDlp.install(st.latest!!) } }, modifier = Modifier.fillMaxWidth().testTag("health.installYtDlp"))
                    // Also when nothing is newer: the last update (or rollback) stays visible.
                    if (ytdlpMessage.isNotEmpty()) Text("Last yt-dlp update: $ytdlpMessage", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("health.ytdlpStatus"))
                }
            }
        }
        item(key = "run") {
            Column(Modifier.fillMaxWidth().padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                PrimaryButton(if (running) "Checking…" else "Run checks now", { scope.launch { services.releases.forEach { runCatching { it.check.check(force = true) } }; health.run() } }, enabled = !running, modifier = Modifier.fillMaxWidth().testTag("health.run"))
                Text("Checks use only the sources that need no account. Last run: ${health.lastRun()?.toString()?.substring(0, 16)?.replace('T', ' ') ?: "never"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.md))
            }
        }
        if (results.isEmpty()) item(key = "none") { EmptyState("No results yet. Press Run checks now.") }
        summary.forEach { t ->
            val key = t.tier.name
            val title = t.tier.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
            val rows = buildList {
                add("summary")
                if (open[key] == true) { t.latest.forEachIndexed { i, _ -> add("check-$i") }; if (t.runs.isNotEmpty()) add("history") }
                if (open["$key.history"] == true) t.runs.forEachIndexed { i, _ -> add("run-$i") }
            }
            settingsSection("tier-$key", title, rows, { it }) { row, shape ->
                val at = t.latestRunAt?.toString()?.substring(0, 16)?.replace('T', ' ')
                when {
                    row == "summary" -> SegmentedListItem(
                        onClick = { open[key] = open[key] != true }, shapes = ListItemShapes(shape, shape, shape, shape, shape, shape), colors = segmentedItemColors(),
                        // One sentence from the latest run only: older runs can never contradict it.
                        supportingContent = {
                            Text(
                                when {
                                    t.latest.isEmpty() -> "Not checked yet."
                                    t.healthy -> "Working (run $at, ${t.latest.size} checks passed)."
                                    else -> "Failing in the latest run ($at): ${t.failures.size} of ${t.latest.size} checks. ${t.lastFailureReason ?: "unknown"}" +
                                        (t.lastGood?.let { " Last time a check passed: ${it.toString().substring(0, 16).replace('T', ' ')}." } ?: "")
                                },
                                maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("health.tier.$key"),
                            )
                        },
                        trailingContent = {
                            val (label, container, content) = when {
                                t.latest.isEmpty() -> Triple("Unknown", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                                t.healthy -> Triple("OK", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                                else -> Triple("Failing", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                            }
                            Surface(shape = CircleShape, color = container, contentColor = content) { Text(label, Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs), style = MaterialTheme.typography.labelLarge) }
                        },
                    ) { Text(title) }
                    row.startsWith("check-") -> HealthRow(t.latest[row.removePrefix("check-").toInt()], shape)
                    row == "history" -> SegmentedListItem(
                        onClick = { open["$key.history"] = open["$key.history"] != true }, shapes = ListItemShapes(shape, shape, shape, shape, shape, shape), colors = segmentedItemColors(),
                        trailingContent = { Icon(if (open["$key.history"] == true) AppIcons.chevronUp else AppIcons.chevronDown, null) },
                        modifier = Modifier.testTag("health.history.$key"),
                    ) { Text(if (open["$key.history"] == true) "Hide history" else "History (${t.runs.size} earlier runs)") }
                    else -> {
                        val run = t.runs[row.removePrefix("run-").toInt()]
                        InfoRow("Run ${run.first().at.toString().substring(0, 16).replace('T', ' ')}: ${run.count { it.ok }} ok, ${run.count { !it.ok }} failed" + run.filter { !it.ok }.joinToString("") { "\n${it.check}${it.detail?.let { d -> " - $d" } ?: ""}" }, shape)
                    }
                }
            }
        }
        settingsSection("developer", "Developer", listOf("diagnostics", "simulate"), { it }) { row, shape ->
            val active = services.accounts.active.value
            if (row == "diagnostics") InfoActionRow("Diagnostics", "The tier log and simulated failures.", AppIcons.info, shape, enabled = true, tag = "health.diagnostics", onClick = { navigator.push(com.greenaltambe.vela.ui.nav.Route.Diagnostics) })
            else InfoActionRow("Simulate a rate limit", "Tests the account banner and the automatic pause. Only for a signed-in Google account.", AppIcons.timer, shape, enabled = active.kind == com.greenaltambe.vela.auth.AccountKind.GOOGLE, tag = "health.simulateRateLimit",
                onClick = { if (active.kind == com.greenaltambe.vela.auth.AccountKind.GOOGLE) services.governors.forAccount(active.id).trip(com.greenaltambe.vela.auth.BlockSignal.HTTP_429) })
        }
    }
}

/** Runs the canaries shortly after start when the last run is older than a week, then re-checks every six hours while the app is open. */
@Composable
fun HealthStartEffect(services: AppServices) {
    val health = services.health ?: return
    LaunchedEffect(Unit) {
        delay(15_000)
        var verifiedMinimumDone = false
        while (true) {
            // A fresh install ships a yt-dlp too old for age-restricted videos: install the verified release without anyone pressing a button (checksum, version check, canary and rollback as always).
            services.managedYtDlp?.let { up ->
                val installed = services.releases.firstOrNull { it.check.status.value.name == "yt-dlp" }?.check?.status?.value?.bundled
                if (!verifiedMinimumDone) com.greenaltambe.vela.health.verifiedMinimumToInstall(installed, up, services.settings.resolve(com.greenaltambe.vela.core.settings.Settings.ytDlpAutoUpdate))
                    ?.let { tag -> verifiedMinimumDone = runCatching { up.install(tag) }.getOrDefault(false) }
            }
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
private fun HealthRow(r: com.greenaltambe.vela.health.CanaryResult, shape: androidx.compose.ui.graphics.Shape) {
    SegmentedListItem(
        onClick = {}, enabled = false, shapes = ListItemShapes(shape, shape, shape, shape, shape, shape), colors = segmentedItemColors(),
        leadingContent = { Icon(if (r.ok) AppIcons.check else AppIcons.close, if (r.ok) "ok" else "failed", tint = if (r.ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error) },
        supportingContent = { Text("${r.millis} ms${r.detail?.let { " · $it" } ?: ""}", maxLines = 3, overflow = TextOverflow.Ellipsis) },
    ) { Text(r.check, maxLines = 2, overflow = TextOverflow.Ellipsis) }
}
