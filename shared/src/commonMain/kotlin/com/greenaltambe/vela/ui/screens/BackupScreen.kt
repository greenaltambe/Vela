@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
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
import com.greenaltambe.vela.backup.AutoBackups
import com.greenaltambe.vela.backup.BackupException
import com.greenaltambe.vela.backup.BackupItem
import com.greenaltambe.vela.backup.BackupKind
import com.greenaltambe.vela.backup.BackupService
import com.greenaltambe.vela.ui.components.LinkButton
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
    val auto = services.autoBackups
    var autoOn by remember { mutableStateOf(auto.enabled) }
    var externalLabel by remember { mutableStateOf(auto.externalLabel) }
    var items by remember { mutableStateOf<List<BackupItem>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    var openItem by remember { mutableStateOf<BackupItem?>(null) }
    androidx.compose.runtime.LaunchedEffect(reload, externalLabel) { items = runCatching { auto.list() }.getOrDefault(emptyList()) }
    /** The restore of a file goes to the account it was made for (the local profile for older files). */
    fun serviceFor(text: String): BackupService? {
        val acct = runCatching { service.parse(text).first.account }.getOrNull()
        return if (acct == null) service else if (services.accounts.get(acct) != null) BackupService(services.database, services.clock, acct) else null
    }
    fun whenText(ms: Long): String = AutoBackups.stamp(ms + services.utcOffsetMinutes() * 60_000L).let { "${it.substring(0, 4)}-${it.substring(4, 6)}-${it.substring(6, 8)} ${it.substring(9, 11)}:${it.substring(11, 13)}" }

    SettingsScaffold("Backup and restore", modifier, onBack = { navigator.back() }, listTag = "backup.list") {
        settingsSection("about", "", listOf("about"), { it }) { _, shape ->
            InfoRow("A backup holds your settings (including per-channel and per-video overrides), subscriptions, local playlists and Watch later, and optionally history and search history. It never holds accounts, passwords, cookies or downloads.", shape)
        }
        if (auto.isAvailable) {
            settingsSection("auto", "Automatic backups", listOf("on", "folder", "now"), { it }) { r, shape ->
                when (r) {
                    "on" -> SwitchRow("Back up automatically", "A copy every day, one before the first sync and one before a large sync change. The newest 7 daily copies are kept.", autoOn, { autoOn = it; auto.enabled = it }, shape, "backup.auto")
                    "folder" -> InfoActionRow(
                        if (externalLabel == null) "Also copy to a folder" else "Copies also go to: $externalLabel",
                        if (externalLabel == null) "Recommended. Backups kept only inside the app are lost if you uninstall it." else "Tap to choose a different folder.",
                        AppIcons.folder, shape, auto.foldersAvailable, "backup.folder", {
                            scope.launch { services.backupFolders?.choose()?.let { auto.setExternalFolder(it); externalLabel = auto.externalLabel; reload++ } }
                        })
                    else -> InfoActionRow("Back up now", "Makes a backup right away.", AppIcons.save, shape, true, "backup.now", {
                        scope.launch { message = runCatching { auto.create(BackupKind.MANUAL); "Backup made." }.getOrElse { "Could not make the backup: ${it.message}" }; reload++ }
                    })
                }
            }
            if (externalLabel != null) item(key = "stop-folder") { LinkButton("Stop copying to that folder", { auto.setExternalFolder(null); externalLabel = null; reload++ }, Modifier.testTag("backup.folder.stop")) }
            if (items.isNotEmpty()) settingsSection("backups", "Your backups (tap to restore or delete)", items, { it.storage + it.name }) { it, shape ->
                InfoActionRow((it.kind?.title ?: "Backup") + (it.account?.let { a -> " · account …$a" } ?: ""), "${whenText(it.modifiedMs)} · ${it.sizeBytes / 1024 + 1} KB · ${it.storage}", AppIcons.history, shape, true, "backup.item.${it.name}", { openItem = it })
            }
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
                    try { val svc = serviceFor(text); if (svc == null) message = "That backup belongs to an account that is not on this device. Sign in to it first." else { preview = svc.preview(text).describe(); pending = text } } catch (e: BackupException) { message = e.message }
                }
            })
        }
        preview?.let { p ->
            item(key = "preview") { Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) { Text("This restore would: $p", Modifier.padding(Spacing.md).testTag("backup.preview")) } }
        }
        if (pending != null) item(key = "confirm") {
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
                PrimaryButton("Restore (merge)", { message = runCatching { (serviceFor(pending!!) ?: service).restore(pending!!).describe() }.getOrElse { "Restore failed: ${it.message}" }.let { "Done. $it" }; pending = null; preview = null }, modifier = Modifier.weight(1f).testTag("backup.confirm"))
                SecondaryButton("Cancel", { pending = null; preview = null }, modifier = Modifier.weight(1f))
            }
        }
        message?.let { m -> item(key = "message") { Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) { Text(m, Modifier.padding(Spacing.md).testTag("backup.message")) } } }
    }
    openItem?.let { it0 ->
        AlertDialog(
            onDismissRequest = { openItem = null }, title = { Text((it0.kind?.title ?: "Backup")) },
            text = { Text("Made ${whenText(it0.modifiedMs)}. Restoring only adds what is missing and shows you what would change first; nothing you have now is deleted.") },
            confirmButton = {
                TextButton({
                    openItem = null
                    scope.launch {
                        val text = runCatching { auto.read(it0) }.getOrNull()
                        if (text == null) { message = "Could not read that backup."; return@launch }
                        try { val svc = serviceFor(text); if (svc == null) message = "That backup belongs to an account that is not on this device. Sign in to it first." else { preview = svc.preview(text).describe(); pending = text } } catch (e: BackupException) { message = e.message }
                    }
                }, Modifier.testTag("backup.item.restore")) { Text("Restore…") }
            },
            dismissButton = {
                Row {
                    TextButton({ val i = it0; openItem = null; scope.launch { auto.delete(i); reload++ } }, Modifier.testTag("backup.item.delete")) { Text("Delete") }
                    TextButton({ openItem = null }) { Text("Cancel") }
                }
            },
        )
    }
}
