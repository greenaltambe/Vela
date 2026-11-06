@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.greenaltambe.vela.ui.screens

import com.greenaltambe.vela.ui.nav.Route
import com.greenaltambe.vela.ui.settings.segmentedItemColors
import com.greenaltambe.vela.ui.settings.settingsSection
import com.greenaltambe.vela.ui.settings.SettingsScaffold
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.material3.Surface
import androidx.compose.material3.SegmentedListItem
import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.Spacing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.auth.Account
import com.greenaltambe.vela.auth.AccountKind
import com.greenaltambe.vela.auth.AuthState
import com.greenaltambe.vela.auth.LoginResult
import com.greenaltambe.vela.ui.components.Avatar
import com.greenaltambe.vela.ui.components.PrimaryButton
import com.greenaltambe.vela.ui.components.SectionHeader
import com.greenaltambe.vela.ui.components.VelaTopBar
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.platform.rememberTextFilePicker
import kotlinx.coroutines.launch

/** Accounts on this device: switch, add a Google account from a cookies.txt export, sign out, remove, sign out everywhere. */
@Composable
fun AccountsScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    val accounts = services.accounts
    val all by accounts.all.collectAsState()
    val active by accounts.active.collectAsState()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Account?>(null) }
    var wipingAll by remember { mutableStateOf(false) }
    val login = services.login
    var onboarding by remember { mutableStateOf<(() -> Unit)?>(null) }

    val pick = rememberTextFilePicker { text ->
        if (text == null) { message = "No file was read."; return@rememberTextFilePicker }
        val l = login ?: return@rememberTextFilePicker
        busy = true; message = "Checking the cookies with Google…"
        scope.launch {
            message = when (val r = l.importCookies(text)) {
                is LoginResult.Success -> "Signed in as ${r.account.displayName}."
                is LoginResult.Failure -> r.message
            }
            busy = false
        }
    }

    SettingsScaffold("Accounts", modifier, onBack = { navigator.back() }, listTag = "accounts.list") {
        if (active.kind == AccountKind.GOOGLE && services.profiles != null && services.settings.resolve(com.greenaltambe.vela.core.settings.Settings.accountProfilesExperimental)) item(key = "profile") { ProfileSection(services) }
        settingsSection("accounts", "Accounts", all, { it.id }) { a, shape ->
            AccountRow(a, selected = a.id == active.id, shape = shape, onSwitch = { accounts.switchTo(a.id) }, onSignOut = { accounts.signOut(a.id) }, onRemove = { removing = a })
        }
        if (active.kind == AccountKind.GOOGLE && active.authState != AuthState.OK) item(key = "signedout") {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
                Text("Signed out: ${active.displayName}'s session no longer works. Add the account again to sign in.", Modifier.padding(Spacing.md).testTag("accounts.banner"))
            }
        }
        if (active.isSignedIn && services.sync != null) settingsSection("sync", "Sync", listOf("sync"), { it }) { _, shape ->
            InfoActionRow(
                title = if (busy) "Syncing…" else "Sync subscriptions now", description = "Adds the channels you subscribe to on Google to this account's subscriptions. Nothing is removed.",
                icon = AppIcons.refresh, shape = shape, enabled = !busy, tag = "accounts.sync",
                onClick = {
                    busy = true; message = "Syncing…"
                    scope.launch {
                        val r = services.sync!!.pullSubscriptions()
                        message = r.failure?.let { "Sync failed: $it" } ?: "Added ${r.added} new channel(s); ${r.alreadyHad} were already here."
                        services.feed.reload(); busy = false
                    }
                },
            )
        }
        val add = buildList { if (login != null) { services.webLogin?.let { add("web") }; add("cookies") } }
        settingsSection("add", "Add a Google account", if (login == null) listOf("unavailable") else listOf("how") + add, { it }) { row, shape ->
            when (row) {
                "unavailable" -> InfoRow("Signing in is not available here: this platform has no safe place for account credentials yet.", shape)
                "how" -> InfoRow(
                    "Export your YouTube cookies from a PRIVATE (incognito) window where you are signed in, close that window right away, then pick the cookies.txt file. " +
                        "Browsers rotate the cookies of windows that stay open, which would invalidate the export. A \"cookies.txt\" export extension for your browser does this.", shape,
                )
                "web" -> InfoActionRow("Sign in with Google", "Opens Google's own sign-in page.", AppIcons.account, shape, enabled = true, tag = "accounts.webLogin", onClick = { onboarding = services.webLogin })
                else -> InfoActionRow(if (busy) "Checking…" else "Import cookies.txt", "Pick the exported cookies file.", AppIcons.folder, shape, enabled = !busy, tag = "accounts.import", onClick = { onboarding = pick })
            }
        }
        message?.let { m -> item(key = "message") { Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) { Text(m, Modifier.padding(Spacing.md).testTag("accounts.message")) } } }
        settingsSection("privacy", "Privacy", listOf("text", "signout"), { it }) { row, shape ->
            if (row == "text") InfoRow("Cookies are kept in ${services.accounts.credentialStoreDescription}, never in Vela's database, logs or backups. Signing out everywhere deletes them from this device; it does not sign you out of Google elsewhere.", shape)
            else InfoActionRow("Sign out everywhere", "Deletes every account's stored credentials from this device.", AppIcons.lock, shape, enabled = true, tag = "accounts.signOutAll", danger = true, onClick = { wipingAll = true })
        }
        settingsSection("safety", "Safety", listOf("text", "activity"), { it }) { row, shape ->
            if (row == "text") InfoRow("Account requests use credentials in ${services.accounts.credentialStoreDescription}. Pausing the account is automatic.", shape)
            else InfoActionRow("Account activity", "Every request the account made, and what was decided.", AppIcons.history, shape, enabled = true, tag = "accounts.activity", onClick = { navigator.push(Route.AccountActivity) })
        }
    }

    onboarding?.let { go -> AccountOnboardingDialog(services, onDismiss = { onboarding = null }, onAccepted = { onboarding = null; go() }) }
    removing?.let { a ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${a.displayName}?", maxLines = 2) },
            text = { Text("Deletes this account's credentials and its subscriptions, history, playlists and search history on this device. Your Google account is not affected.") },
            confirmButton = { TextButton(onClick = { accounts.remove(a.id); removing = null }, modifier = Modifier.testTag("accounts.removeConfirm")) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
    if (wipingAll) {
        AlertDialog(
            onDismissRequest = { wipingAll = false },
            title = { Text("Sign out everywhere?") },
            text = { Text("Deletes the stored credentials of every account from this device. The accounts and their local data stay; you will have to add them again to sign in.") },
            confirmButton = { TextButton(onClick = { accounts.signOutEverywhere(); wipingAll = false }, modifier = Modifier.testTag("accounts.signOutAllConfirm")) { Text("Sign out everywhere") } },
            dismissButton = { TextButton(onClick = { wipingAll = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AccountRow(a: Account, selected: Boolean, shape: androidx.compose.ui.graphics.Shape, onSwitch: () -> Unit, onSignOut: () -> Unit, onRemove: () -> Unit) {
    val detail = when {
        a.kind == AccountKind.LOCAL -> "On this device only"
        a.authState != AuthState.OK -> "Needs sign-in"
        else -> a.maskedEmail ?: "Google account"
    }
    SegmentedListItem(
        onClick = onSwitch, selected = selected, shapes = androidx.compose.material3.ListItemShapes(shape, shape, shape, shape, shape, shape), colors = segmentedItemColors(),
        leadingContent = { Avatar(a.displayName, url = a.avatarUrl) },
        supportingContent = { Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) androidx.compose.material3.AssistChip(onClick = {}, label = { Text("Active") }, leadingIcon = { Icon(AppIcons.check, null, Modifier.size(18.dp)) })
                if (a.kind == AccountKind.GOOGLE) {
                    var open by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { open = true }, modifier = Modifier.testTag("accounts.menu.${a.id}")) { Icon(AppIcons.more, "Account options") }
                        DropdownMenu(open, { open = false }) {
                            if (a.authState == AuthState.OK) DropdownMenuItem(text = { Text("Sign out") }, onClick = { open = false; onSignOut() })
                            DropdownMenuItem(text = { Text("Remove") }, onClick = { open = false; onRemove() })
                        }
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth().testTag("accounts.row.${a.id}"),
    ) { Text(a.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge) }
}

/** A plain explanation inside a segmented stack. */
@Composable
internal fun InfoRow(text: String, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer).padding(Spacing.md)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A tappable row of a segmented stack: icon, title, one line of description. [danger] colours the title as a destructive action. */
@Composable
internal fun InfoActionRow(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, shape: androidx.compose.ui.graphics.Shape, enabled: Boolean, tag: String, onClick: () -> Unit, danger: Boolean = false) {
    SegmentedListItem(
        onClick = onClick, enabled = enabled, shapes = androidx.compose.material3.ListItemShapes(shape, shape, shape, shape, shape, shape), colors = segmentedItemColors(),
        leadingContent = { Icon(icon, null, tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) },
        supportingContent = { Text(description, maxLines = 3, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier.fillMaxWidth().testTag(tag),
    ) { Text(title, color = if (danger) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified, maxLines = 2, overflow = TextOverflow.Ellipsis) }
}

/** Experimental (Settings > Account switch, off by default): which channel of the account is used. Choosing one is checked with a read first and reverted if YouTube does not confirm it. */
@Composable
private fun ProfileSection(services: AppServices) {
    val profiles = services.profiles ?: return
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var options by remember { mutableStateOf<List<com.greenaltambe.vela.auth.AccountProfile>?>(null) }
    var current by remember { mutableStateOf(profiles.current()) }
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(Spacing.md).testTag("accounts.profile"), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("Profile (experimental)", style = MaterialTheme.typography.titleMedium)
        Text(current?.let { "${it.profile.name}${if (it.verified) " (confirmed by YouTube)" else ""}" } ?: "Main channel (default)", modifier = Modifier.testTag("accounts.profile.current"))
        OutlinedButton(onClick = { message = "Reading your profiles…"; scope.launch { options = profiles.list(); message = if (options == null) "Could not read the profiles." else null } }, modifier = Modifier.testTag("accounts.profile.change")) { Text("Change profile", maxLines = 1) }
        message?.let { Text(it, modifier = Modifier.testTag("accounts.profile.message"), style = MaterialTheme.typography.bodySmall) }
        Text("Only reads use another profile until YouTube confirms it; nothing is written to an unconfirmed profile.", style = MaterialTheme.typography.bodySmall)
    }
    options?.let { list ->
        androidx.compose.material3.AlertDialog(onDismissRequest = { options = null }, title = { Text("Use which profile?") },
            text = { Column { list.forEach { p -> TextButton(onClick = {
                options = null; message = "Checking with YouTube…"
                scope.launch { val r = profiles.select(p); current = profiles.current(); message = if (r is com.greenaltambe.vela.auth.ActionResult.Failed) r.message else "Using ${p.name}." }
            }, modifier = Modifier.testTag("accounts.profile.option.${p.name}")) { Text(p.name + if (p.pageId == null) " (default)" else "", maxLines = 1) } } } },
            confirmButton = {}, dismissButton = { TextButton(onClick = { options = null }) { Text("Cancel") } })
    }
}
