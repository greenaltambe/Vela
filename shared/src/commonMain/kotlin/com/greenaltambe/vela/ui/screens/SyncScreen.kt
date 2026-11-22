@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.sync.*
import com.greenaltambe.vela.ui.components.PrimaryButton
import com.greenaltambe.vela.ui.components.SecondaryButton
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.settings.SettingsScaffold
import com.greenaltambe.vela.ui.settings.SwitchRow
import com.greenaltambe.vela.ui.settings.settingsSection
import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Sync between devices. Before it is set up: three short steps (folder, code, what to sync) and one button. After: a status card with "Sync now",
 * the other devices, what is synced, and a few options. Everything is explained where it is used, in plain words.
 */
@Composable
fun SyncScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    val sync = services.syncService
    val config by sync.config.collectAsState()
    val status by sync.status.collectAsState()
    SettingsScaffold("Sync between devices", modifier, onBack = { navigator.back() }, listTag = "sync.list") {
        if (config.isSetUp) syncedContent(services, sync, config, status) else setupContent(services, sync, config)
    }
}

// ---- first-time setup ----------------------------------------------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.setupContent(services: AppServices, sync: SyncService, config: SyncConfig) {
    item(key = "hero") {
        HeroCard("Keep your devices in step", "Your subscriptions, playlists, watch history and settings follow you between your phone and your computer. " +
            "No account needed, and everything is encrypted before it leaves this device.", AppIcons.cloud)
    }
    item(key = "setup") { SetupSteps(services, sync, config) }
}

@Composable
private fun SetupSteps(services: AppServices, sync: SyncService, config: SyncConfig) {
    val scope = rememberCoroutineScope()
    val folders = services.syncFolders
    var mode by remember { mutableStateOf(if (sync.lanAvailable) SyncMode.LAN else SyncMode.FOLDER) }
    var folder by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var haveCode by remember { mutableStateOf<Boolean?>(null) }       // null = not chosen yet
    var kinds by remember { mutableStateOf(config.kinds) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val codeOk = normalizeSyncCode(code).length == 16
    val ready = (mode == SyncMode.LAN || folder != null) && codeOk

    Column(Modifier.fillMaxWidth().padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StepCard(1, "How should your devices connect?", done = mode == SyncMode.LAN || folder != null) {
            if (sync.lanAvailable) {
                ChoiceCard("Same Wi-Fi", "Easiest. Your devices find each other automatically while Vela is open on both. No extra app and no account.", mode == SyncMode.LAN, "sync.mode.lan") { mode = SyncMode.LAN }
                ChoiceCard("A shared folder", "Use a folder that Dropbox, Google Drive, Syncthing or Nextcloud already keeps in step. Works when the devices are not on the same Wi-Fi.", mode == SyncMode.FOLDER, "sync.mode.folder") { mode = SyncMode.FOLDER }
            }
            if (mode == SyncMode.FOLDER) {
                if (folders == null) Text("This device cannot pick a folder yet.", color = MaterialTheme.colorScheme.error)
                else {
                    folder?.let { Text(folders.label(it), style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("sync.folderLabel")) }
                    SecondaryButton(if (folder == null) "Choose folder" else "Choose a different folder", {
                        scope.launch { folders.choose()?.let { folder = it; error = null } }
                    }, Modifier.fillMaxWidth().testTag("sync.chooseFolder"))
                }
            } else Text("Both devices need to be on the same Wi-Fi with Vela open the first time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StepCard(2, "Sync code", done = codeOk) {
            Text("The code is the key that locks your data. Every device must use the same one. Nobody without it can read the files, not even the service that stores them.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (haveCode) {
                null -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                    PrimaryButton("This is my first device", { haveCode = false; code = generateSyncCode() }, Modifier.weight(1f).testTag("sync.newCode"))
                    SecondaryButton("I already have a code", { haveCode = true; code = "" }, Modifier.weight(1f).testTag("sync.haveCode"))
                }
                false -> {
                    CodeDisplay(code)
                    Text("Write it down or save it in a password manager. You will type it on your other devices.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinkRow("Make a different code") { code = generateSyncCode() }
                }
                true -> {
                    OutlinedTextField(
                        code, { code = it.uppercase().take(24) }, Modifier.fillMaxWidth().testTag("sync.codeField"), singleLine = true,
                        label = { Text("Code from your other device") }, placeholder = { Text("XXXX-XXXX-XXXX-XXXX") },
                        supportingText = { Text("On your other device: Settings > Sync between devices > Sync code.") },
                        isError = code.isNotBlank() && !codeOk && normalizeSyncCode(code).length > 16,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    )
                    LinkRow("I don't have one yet: create a code") { haveCode = false; code = generateSyncCode() }
                }
            }
        }
        StepCard(3, "What to sync", done = kinds.isNotEmpty()) {
            Text("You can change this any time.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val all = SyncKind.entries
            Column(verticalArrangement = Arrangement.spacedBy(androidx.compose.material3.ListItemDefaults.SegmentedGap)) {
                all.forEachIndexed { i, k ->
                    SwitchRow(k.title, k.detail, k in kinds, { on -> kinds = if (on) kinds + k else kinds - k }, com.greenaltambe.vela.ui.settings.segmentedShape(i, all.size), "sync.setup.kind.${k.name}")
                }
            }
        }
        error?.let { ErrorCard(it) }
        PrimaryButton(if (busy) "Syncing…" else "Start syncing", {
            busy = true; error = null
            scope.launch {
                kinds.let { chosen -> SyncKind.entries.forEach { sync.setKind(it, it in chosen) } }
                sync.setMode(mode); if (mode == SyncMode.FOLDER) sync.setFolder(folder!!)
                sync.setCode(code)
                val st = sync.syncNow()
                busy = false
                if (st is SyncStatus.Failed) { error = st.message; sync.turnOff() }
            }
        }, Modifier.fillMaxWidth().testTag("sync.start"), enabled = ready && !busy && kinds.isNotEmpty())
        if (!ready) Text(
            when { mode == SyncMode.FOLDER && folder == null -> "Start with step 1: choose the folder."; !codeOk -> if (haveCode == null) "Now step 2: choose how to get a code." else "Step 2: enter all 16 characters of the code."; else -> "" },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        )
    }
}

// ---- after setup ---------------------------------------------------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.syncedContent(services: AppServices, sync: SyncService, config: SyncConfig, status: SyncStatus) {
    item(key = "status") { StatusCard(sync, config, status) }
    val others = (status as? SyncStatus.Idle)?.devices.orEmpty()
    item(key = "devices-h") { SectionTitle("Devices") }
    item(key = "devices") { DevicesCard(sync, config, others) }
    item(key = "what-h") { SectionTitle("What is synced") }
    settingsSection("what", "", SyncKind.entries, { it.name }) { k, shape ->
        SwitchRow(k.title, k.detail, k in config.kinds, { sync.setKind(k, it) }, shape, "sync.kind.${k.name}")
    }
    item(key = "opt-h") { SectionTitle("Options") }
    item(key = "options") { OptionsCard(services, sync, config) }
}

@Composable
private fun StatusCard(sync: SyncService, config: SyncConfig, status: SyncStatus) {
    val scope = rememberCoroutineScope()
    val nearby by sync.nearby.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val failed = status as? SyncStatus.Failed
    val (title, detail) = when (status) {
        SyncStatus.NotSetUp -> "Not set up" to ""
        SyncStatus.Syncing -> "Syncing…" to "Comparing with your other devices."
        is SyncStatus.Failed -> (if (status.wrongCode) "The code does not match" else "Couldn't sync") to status.message
        is SyncStatus.Idle -> (if (status.lastSyncAt == null) "Ready to sync" else "Up to date") to buildString {
            append(status.lastSyncAt?.let { "Last synced ${ago(it)}" } ?: "Not synced yet")
            append(if (config.mode == SyncMode.LAN && nearby.isEmpty()) " · no other device on this Wi-Fi right now"
                else if (config.mode == SyncMode.LAN) " · ${nearby.size} nearby"
                else if (status.devices.isEmpty()) " · no other device seen yet" else " · ${status.devices.size} other device${if (status.devices.size == 1) "" else "s"}")
        }
    }
    val container = if (failed != null) scheme.errorContainer else scheme.primaryContainer
    val onContainer = if (failed != null) scheme.onErrorContainer else scheme.onPrimaryContainer
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(container).padding(Spacing.lg).testTag("sync.status"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (status == SyncStatus.Syncing) CircularWavyProgressIndicator(Modifier.size(40.dp), color = onContainer, trackColor = container)
            else Icon(if (failed != null) AppIcons.info else AppIcons.check, null, tint = onContainer, modifier = Modifier.size(40.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = onContainer, modifier = Modifier.testTag("sync.statusTitle"))
                if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = onContainer)
            }
        }
        if (failed?.wrongCode == true) Text("Open Settings > Sync between devices on your other device to see its code, then turn sync off here and set it up again with that code.",
            style = MaterialTheme.typography.bodySmall, color = onContainer)
        if (config.mode == SyncMode.LAN && nearby.isEmpty() && status is SyncStatus.Idle) Text("Open Vela on your other device, on the same Wi-Fi. They connect by themselves within a few seconds.", style = MaterialTheme.typography.bodySmall, color = onContainer)
        PrimaryButton(if (status == SyncStatus.Syncing) "Syncing…" else "Sync now", { scope.launch { sync.syncNow() } }, Modifier.fillMaxWidth().testTag("sync.now"), enabled = status != SyncStatus.Syncing)
    }
}

@Composable
private fun DevicesCard(sync: SyncService, config: SyncConfig, others: List<SyncDevice>) {
    var rename by remember { mutableStateOf(false) }
    val nearby by sync.nearby.collectAsState()
    val nearbyIds = nearby.map { it.deviceId }.toSet()
    val known = others.map { it.id }.toSet()
    val rows = listOf<Pair<String, String>>(("${config.deviceName} (this device)") to "Tap to rename") +
        others.map { it.name to (if (it.id in nearbyIds) "On this Wi-Fi now · " else "") + "Synced ${ago(it.syncedAt)}" } +
        nearby.filter { it.deviceId !in known }.map { it.name to "On this Wi-Fi now · waiting for its data" }
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        rows.forEachIndexed { i, (name, sub) ->
            val shape = com.greenaltambe.vela.ui.settings.segmentedShape(i, rows.size)
            if (i == 0) InfoActionRow(name, sub, AppIcons.account, shape, true, "sync.thisDevice", { rename = true })
            else InfoActionRow(name, sub, AppIcons.cast, shape, false, "sync.device.$i", {})
        }
        if (rows.size == 1) InfoRow(if (config.mode == SyncMode.LAN) "Set up sync on your other device with the same code, on the same Wi-Fi. It will show up here." else "Set up sync on your other device with the same folder and code. It will show up here.", com.greenaltambe.vela.ui.settings.segmentedShape(rows.size, rows.size + 1))
    }
    if (rename) TextDialog("Name this device", "Shows on your other devices", config.deviceName, { rename = false }) { sync.setDeviceName(it); rename = false }
}

@Composable
private fun OptionsCard(services: AppServices, sync: SyncService, config: SyncConfig) {
    val scope = rememberCoroutineScope()
    var showCode by remember { mutableStateOf(false) }
    var confirmOff by remember { mutableStateOf(false) }
    val shapes = (0..3).map { com.greenaltambe.vela.ui.settings.segmentedShape(it, 4) }
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        SwitchRow("Sync automatically", "When Vela starts and every few minutes while it is open. Turn off to sync only with the button.", config.auto, { sync.setAuto(it) }, shapes[0], "sync.auto")
        if (config.mode == SyncMode.LAN) InfoActionRow("Connection: same Wi-Fi", "To switch to a shared folder, turn sync off and set it up again.", AppIcons.cast, shapes[1], false, "sync.connection", {})
        else InfoActionRow("Shared folder", sync.folderLabel() ?: "", AppIcons.folder, shapes[1], services.syncFolders != null, "sync.folder", {
            scope.launch { services.syncFolders?.choose()?.let { sync.setFolder(it); sync.syncNow() } }
        })
        InfoActionRow("Sync code", "Show the code to set up another device", AppIcons.lock, shapes[2], true, "sync.code", { showCode = true })
        InfoActionRow("Turn off sync on this device", "Your data stays here and in the folder; this device just stops syncing.", AppIcons.close, shapes[3], true, "sync.off", { confirmOff = true }, danger = true)
    }
    if (showCode) AlertDialog(
        onDismissRequest = { showCode = false }, title = { Text("Your sync code") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            CodeDisplay(sync.code().orEmpty().chunked(4).joinToString("-"))
            Text("Type this on your other device when you set up sync there. Keep it private: anyone with the code and the folder can read your synced data.", style = MaterialTheme.typography.bodyMedium)
        } },
        confirmButton = { TextButton({ showCode = false }) { Text("Done") } },
    )
    if (confirmOff) AlertDialog(
        onDismissRequest = { confirmOff = false }, title = { Text("Turn off sync?") },
        text = { Text("This device stops syncing and forgets the code. Nothing is deleted here or in the shared folder, and you can set it up again any time with the same code.") },
        confirmButton = { TextButton({ sync.turnOff(); confirmOff = false }, Modifier.testTag("sync.off.confirm")) { Text("Turn off") } },
        dismissButton = { TextButton({ confirmOff = false }) { Text("Cancel") } },
    )
}

// ---- pieces --------------------------------------------------------------------------------------------------------------------------

@Composable
private fun HeroCard(title: String, text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(scheme.primaryContainer).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Box(Modifier.size(56.dp).clip(MaterialTheme.shapes.large).background(scheme.primary), contentAlignment = Alignment.Center) { Icon(icon, null, tint = scheme.onPrimary, modifier = Modifier.size(32.dp)) }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = scheme.onPrimaryContainer)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = scheme.onPrimaryContainer)
    }
}

@Composable
private fun StepCard(number: Int, title: String, done: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(scheme.surfaceContainer).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(if (done) scheme.primary else scheme.secondaryContainer), contentAlignment = Alignment.Center) {
                if (done) Icon(AppIcons.check, null, tint = scheme.onPrimary, modifier = Modifier.size(18.dp)) else Text("$number", style = MaterialTheme.typography.labelLarge, color = scheme.onSecondaryContainer)
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        content()
    }
}

/** One of two ways to connect, as a tappable card with a radio button. */
@Composable
private fun ChoiceCard(title: String, detail: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(if (selected) scheme.secondaryContainer else scheme.surfaceContainerHigh)
            .selectable(selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick).padding(Spacing.md).testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, null)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (selected) scheme.onSecondaryContainer else scheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CodeDisplay(code: String) {
    Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(Spacing.md), contentAlignment = Alignment.Center) {
        Text(code, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace, modifier = Modifier.testTag("sync.codeText"))
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) { TextButton(onClick, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(label) } }

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = Spacing.md, top = Spacing.md, bottom = Spacing.xs))
}

@Composable
private fun ErrorCard(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.errorContainer).padding(Spacing.md).testTag("sync.error"))
}

@Composable
private fun TextDialog(title: String, hint: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(text, { text = it.take(40) }, singleLine = true, supportingText = { Text(hint) }) },
        confirmButton = { TextButton({ onSave(text) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

private fun ago(epochMs: Long): String {
    val s = (Clock.System.now().toEpochMilliseconds() - epochMs) / 1000
    return when {
        s < 45 -> "just now"
        s < 3600 -> "${(s / 60).coerceAtLeast(1)} min ago"
        s < 86_400 -> "${s / 3600} h ago"
        else -> "${s / 86_400} d ago"
    }
}
