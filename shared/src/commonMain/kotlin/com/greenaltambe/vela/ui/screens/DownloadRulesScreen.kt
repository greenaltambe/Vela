package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.downloads.AutoDownloadRule
import com.greenaltambe.vela.ui.components.EmptyState
import com.greenaltambe.vela.ui.components.PrimaryButton
import com.greenaltambe.vela.ui.components.VelaTopBar
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.nav.Navigator
import kotlinx.coroutines.launch

/** Library > Downloads > Automatic downloads: one rule per channel (or Watch later), each with quality, audio-only and Wi-Fi-only. */
@Composable
fun DownloadRulesScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var revision by remember { mutableStateOf(0) }
    val rules = remember(revision) { services.autoDownloadRules.rules() }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AutoDownloadRule?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().testTag("rules.list"), contentPadding = Vela.contentPadding, verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
            item {
                Text("New videos are downloaded in the background (about every 6 hours on Android, 30 minutes on Linux while the app runs), always without your account. Watched automatic downloads are removed after the number of days set in Settings > Downloads; the storage cap there is enforced for them, oldest-watched first.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
                    PrimaryButton("Add rule", { adding = true }, modifier = Modifier.testTag("rules.add"))
                    if (services.autoDownloader != null) OutlinedButton(onClick = { running = true; scope.launch { result = runCatching { services.autoDownloader.run().describe() }.getOrElse { "Failed: ${it.message}" }; running = false } }, enabled = !running, modifier = Modifier.testTag("rules.runNow")) { Text(if (running) "Checking…" else "Check now", maxLines = 1) }
                }
                result?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("rules.result")) }
            }
            if (rules.isEmpty()) item { EmptyState("No rules yet. Add one for Watch later or a channel (or use the switch on a channel page).") }
            items(rules, key = { it.target }) { r ->
                Row(Modifier.fillMaxWidth().testTag("rules.row.${r.target}"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
                    Switch(r.enabled, { services.autoDownloadRules.upsert(r.copy(enabled = it)); revision++ }, modifier = Modifier.testTag("rules.toggle.${r.target}"))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(summary(r), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                    TextButton({ editing = r }) { Text("Edit", maxLines = 1) }
                    TextButton({ services.autoDownloadRules.remove(r.target); revision++ }, modifier = Modifier.testTag("rules.delete.${r.target}")) { Text("Delete", maxLines = 1) }
                }
            }
        }
    }

    if (adding) {
        val taken = rules.map { it.target }.toSet()
        val options = buildList {
            if (AutoDownloadRule.WATCH_LATER !in taken) add(AutoDownloadRule.WATCH_LATER to "Watch later")
            services.subscriptions.all().filter { it.channelId !in taken }.forEach { add(it.channelId to it.name) }
        }
        AlertDialog(onDismissRequest = { adding = false }, title = { Text("Add a rule for…") },
            text = { if (options.isEmpty()) Text("Everything already has a rule.") else LazyColumn(Modifier.heightIn(max = 360.dp)) { items(options, key = { it.first }) { (t, n) ->
                TextButton({ services.autoDownloadRules.upsert(AutoDownloadRule(t, n, sinceMs = services.clock.now().toEpochMilliseconds())); revision++; adding = false }, modifier = Modifier.fillMaxWidth().testTag("rules.option.$t")) { Text(n, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            } } },
            confirmButton = {}, dismissButton = { TextButton({ adding = false }) { Text("Cancel") } })
    }
    editing?.let { r ->
        var height by remember(r) { mutableStateOf(r.maxHeight) }; var audio by remember(r) { mutableStateOf(r.audioOnly) }; var wifi by remember(r) { mutableStateOf(r.wifiOnly) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text(r.name, maxLines = 1) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Vela.xs)) { listOf(360, 480, 720, 1080, null).forEach { h -> FilterChip(height == h, { height = h }, label = { Text(h?.let { "${it}p" } ?: "Best") }) } }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(audio, { audio = it }); Text("Audio only") }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(wifi, { wifi = it }, modifier = Modifier.testTag("rules.wifi")); Text("Only on Wi-Fi") }
            } },
            confirmButton = { TextButton({ services.autoDownloadRules.upsert(r.copy(maxHeight = height, audioOnly = audio, wifiOnly = wifi)); revision++; editing = null }, modifier = Modifier.testTag("rules.save")) { Text("Save") } },
            dismissButton = { TextButton({ editing = null }) { Text("Cancel") } })
    }
}

private fun summary(r: AutoDownloadRule) = buildString {
    append(if (r.audioOnly) "Audio only" else r.maxHeight?.let { "up to ${it}p" } ?: "best quality")
    append(if (r.wifiOnly) " · Wi-Fi only" else " · any network")
    if (!r.enabled) append(" · off")
}
