@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.backup.BackupException
import com.greenaltambe.vela.backup.BackupService
import com.greenaltambe.vela.ui.components.PrimaryButton
import com.greenaltambe.vela.ui.components.SecondaryButton
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.settings.SettingsScaffold
import com.greenaltambe.vela.ui.settings.SwitchRow
import com.greenaltambe.vela.ui.settings.settingsSection
import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.Spacing
import kotlinx.coroutines.launch

/** One file with settings, subscriptions, local playlists and (optionally) history; restore shows what would change first and only ever merges. */
@Composable
fun BackupScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    val files = services.backupFiles
    val scope = rememberCoroutineScope()
    val service = remember { BackupService(services.database, services.clock) }
    var includeHistory by remember { mutableStateOf(true) }
    var includeSearch by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }       // the picked file's text, until confirmed
    var preview by remember { mutableStateOf<String?>(null) }

    SettingsScaffold("Backup and restore", modifier, onBack = { navigator.back() }, listTag = "backup.list") {
        settingsSection("about", "", listOf("about"), { it }) { _, shape ->
            InfoRow("A backup holds your settings (including per-channel and per-video overrides), subscriptions, local playlists and Watch later, and optionally history and search history. It never holds accounts, passwords, cookies or downloads.", shape)
        }
        if (files == null) {
            settingsSection("unavailable", "", listOf("x"), { it }) { _, shape -> InfoRow("This platform has no file picker for backups yet.", shape, Modifier.testTag("backup.unavailable")) }
            return@SettingsScaffold
        }
        val options = listOf("history", "search")
        settingsSection("create", "Create", options, { it }) { o, shape ->
            if (o == "history") SwitchRow("Include watch history", "Everything you watched, with positions.", includeHistory, { includeHistory = it }, shape, "backup.history")
            else SwitchRow("Include search history", "What you searched for.", includeSearch, { includeSearch = it }, shape, "backup.searchHistory")
        }
        item(key = "create-button") {
            PrimaryButton("Create backup", {
                scope.launch { message = runCatching { files.save("vela-backup.json", service.create(includeHistory, includeSearch))?.let { "Saved to $it" } ?: "Cancelled." }.getOrElse { "Could not save: ${it.message}" } }
            }, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm).testTag("backup.create"))
        }
        settingsSection("restore", "Restore", listOf("pick"), { it }) { _, shape ->
            InfoActionRow("Restore from a file…", "Restoring only adds: nothing you have now is deleted. Settings in the file replace the ones you have.", AppIcons.folder, shape, enabled = true, tag = "backup.pick", onClick = {
                scope.launch {
                    message = null; preview = null; pending = null
                    val text = runCatching { files.pick() }.getOrElse { message = "Could not read the file: ${it.message}"; null } ?: return@launch
                    try { preview = service.preview(text).describe(); pending = text } catch (e: BackupException) { message = e.message }
                }
            })
        }
        preview?.let { p ->
            item(key = "preview") { Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) { Text("This restore would: $p", Modifier.padding(Spacing.md).testTag("backup.preview")) } }
        }
        if (pending != null) item(key = "confirm") {
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
                PrimaryButton("Restore (merge)", { message = runCatching { service.restore(pending!!).describe() }.getOrElse { "Restore failed: ${it.message}" }.let { "Done. $it" }; pending = null; preview = null }, modifier = Modifier.weight(1f).testTag("backup.confirm"))
                SecondaryButton("Cancel", { pending = null; preview = null }, modifier = Modifier.weight(1f))
            }
        }
        message?.let { m -> item(key = "message") { Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) { Text(m, Modifier.padding(Spacing.md).testTag("backup.message")) } } }
    }
}
