package com.greenaltambe.vela.ui.screens

import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.settings.settingsSection
import com.greenaltambe.vela.ui.settings.segmentedShape
import com.greenaltambe.vela.ui.settings.segmentedItemColors
import com.greenaltambe.vela.ui.settings.SwitchRow
import com.greenaltambe.vela.ui.settings.SettingsScaffold
import com.greenaltambe.vela.ui.components.SecondaryButton
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.clickable
import com.greenaltambe.vela.ui.theme.Spacing
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
import com.greenaltambe.vela.ui.nav.Navigator
import kotlinx.coroutines.launch

/** Library > Downloads > Automatic downloads: one rule per channel (or Watch later), each with quality, audio-only and Wi-Fi-only. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DownloadRulesScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var revision by remember { mutableStateOf(0) }
    val rules = remember(revision) { services.autoDownloadRules.rules() }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AutoDownloadRule?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }

    SettingsScaffold("Automatic downloads", modifier, onBack = { navigator.back() }, listTag = "rules.list") {
        settingsSection("about", "", listOf("about"), { it }) { _, shape ->
            InfoRow("New videos are downloaded in the background (about every 6 hours on Android, 30 minutes on Linux while the app runs), always without your account. Watched automatic downloads are removed after the number of days set in Settings > Downloads; the storage cap there is enforced too.", shape)
        }
        item(key = "actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                PrimaryButton("Add rule", { adding = true }, modifier = Modifier.weight(1f).testTag("rules.add"))
                if (services.autoDownloader != null) SecondaryButton(if (running) "Running…" else "Run now", { running = true; scope.launch { result = runCatching { services.autoDownloader.run().describe() }.getOrElse { "Failed: ${it.message}" }; running = false } }, enabled = !running, modifier = Modifier.weight(1f).testTag("rules.runNow"))
            }
        }
        result?.let { r -> item(key = "result") { Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) { Text(r, Modifier.padding(Spacing.md).testTag("rules.result")) } } }
        if (rules.isEmpty()) item(key = "empty") { EmptyState("No rules yet. Add one for Watch later or a channel (or use the switch on a channel page).") }
        if (rules.isNotEmpty()) settingsSection("rules", "Rules", rules, { it.target }) { r, shape ->
            SegmentedListItem(
                onClick = { editing = r }, shapes = ListItemShapes(shape, shape, shape, shape, shape, shape), colors = segmentedItemColors(),
                supportingContent = { Text(summary(r), maxLines = 2) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(r.enabled, { services.autoDownloadRules.upsert(r.copy(enabled = it)); revision++ }, modifier = Modifier.testTag("rules.toggle.${r.target}"))
                        IconButton(onClick = { services.autoDownloadRules.remove(r.target); revision++ }, modifier = Modifier.testTag("rules.delete.${r.target}")) { Icon(AppIcons.delete, "Delete the rule for ${r.name}") }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("rules.row.${r.target}"),
            ) { Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }

    if (adding) {
        val taken = rules.map { it.target }.toSet()
        val options = buildList {
            if (AutoDownloadRule.WATCH_LATER !in taken) add(AutoDownloadRule.WATCH_LATER to "Watch later")
            services.subscriptions.all().filter { it.channelId !in taken }.forEach { add(it.channelId to it.name) }
        }
        com.greenaltambe.vela.ui.components.VelaSheet({ adding = false }) {
            Text("Add a rule for…", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm))
            if (options.isEmpty()) Text("Everything already has a rule.", Modifier.padding(Spacing.md))
            else LazyColumn(Modifier.heightIn(max = 420.dp).navigationBarsPadding()) {
                items(options, key = { it.first }) { (t, n) ->
                    ListItem(
                        headlineContent = { Text(n, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.clickable { services.autoDownloadRules.upsert(AutoDownloadRule(t, n, sinceMs = services.clock.now().toEpochMilliseconds())); revision++; adding = false }.testTag("rules.option.$t"),
                    )
                }
            }
        }
    }
    editing?.let { r ->
        var height by remember(r) { mutableStateOf(r.maxHeight) }; var audio by remember(r) { mutableStateOf(r.audioOnly) }; var wifi by remember(r) { mutableStateOf(r.wifiOnly) }
        com.greenaltambe.vela.ui.components.VelaSheet({ editing = null }) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(r.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Quality", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) { listOf(360, 480, 720, 1080, null).forEach { h -> FilterChip(height == h, { height = h }, label = { Text(h?.let { "${it}p" } ?: "Best") }) } }
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    SwitchRow("Audio only", "Download just the sound.", audio, { audio = it }, segmentedShape(0, 2), "rules.audio")
                    SwitchRow("Only on Wi-Fi", "Wait for an unmetered connection.", wifi, { wifi = it }, segmentedShape(1, 2), "rules.wifi")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
                    SecondaryButton("Cancel", { editing = null }, modifier = Modifier.weight(1f))
                    PrimaryButton("Save", { services.autoDownloadRules.upsert(r.copy(maxHeight = height, audioOnly = audio, wifiOnly = wifi)); revision++; editing = null }, modifier = Modifier.weight(1f).testTag("rules.save"))
                }
            }
        }
    }
}

private fun summary(r: AutoDownloadRule) = buildString {
    append(if (r.audioOnly) "Audio only" else r.maxHeight?.let { "up to ${it}p" } ?: "best quality")
    append(if (r.wifiOnly) " · Wi-Fi only" else " · any network")
    if (!r.enabled) append(" · off")
}
