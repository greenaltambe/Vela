package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.backup.BackupException
import com.greenaltambe.vela.backup.BackupService
import com.greenaltambe.vela.ui.components.PrimaryButton
import com.greenaltambe.vela.ui.components.SectionHeader
import com.greenaltambe.vela.ui.components.VelaTopBar
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.nav.Navigator
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

    Column(modifier.fillMaxSize()) {
        VelaTopBar("Backup and restore", onBack = { navigator.back() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = Vela.gutter, end = Vela.gutter, bottom = com.greenaltambe.vela.ui.nav.LocalChromeInset.current), verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
            Text("A backup holds your settings (including per-channel and per-video overrides), subscriptions, local playlists and Watch later, and optionally history and search history. It never holds accounts, passwords, cookies or downloads.", style = MaterialTheme.typography.bodyMedium)
            if (files == null) { Text("This platform has no file picker for backups yet.", modifier = Modifier.testTag("backup.unavailable")); return@Column }
            SectionHeader("Create")
            Row(horizontalArrangement = Arrangement.spacedBy(Vela.sm), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(includeHistory, { includeHistory = it }, modifier = Modifier.testTag("backup.history")); Text("Include watch history") }
            Row(horizontalArrangement = Arrangement.spacedBy(Vela.sm), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(includeSearch, { includeSearch = it }); Text("Include search history") }
            PrimaryButton("Create backup", {
                scope.launch { message = runCatching { files.save("vela-backup.json", service.create(includeHistory, includeSearch))?.let { "Saved to $it" } ?: "Cancelled." }.getOrElse { "Could not save: ${it.message}" } }
            }, modifier = Modifier.testTag("backup.create"))
            SectionHeader("Restore")
            OutlinedButton(onClick = {
                scope.launch {
                    message = null; preview = null; pending = null
                    val text = runCatching { files.pick() }.getOrElse { message = "Could not read the file: ${it.message}"; null } ?: return@launch
                    try { preview = service.preview(text).describe(); pending = text } catch (e: BackupException) { message = e.message }
                }
            }, modifier = Modifier.testTag("backup.pick")) { Text("Restore from a file…") }
            preview?.let { Text("This restore would: $it", modifier = Modifier.testTag("backup.preview"), style = MaterialTheme.typography.bodyMedium) }
            if (pending != null) Row(horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
                PrimaryButton("Restore (merge)", { message = runCatching { service.restore(pending!!).describe() }.getOrElse { "Restore failed: ${it.message}" }.let { "Done. $it" }; pending = null; preview = null }, modifier = Modifier.testTag("backup.confirm"))
                OutlinedButton(onClick = { pending = null; preview = null }) { Text("Cancel") }
            }
            message?.let { Text(it, modifier = Modifier.testTag("backup.message"), style = MaterialTheme.typography.bodyMedium) }
            Text("Restoring only adds: nothing you have now is deleted. Settings in the file replace the ones you have.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
