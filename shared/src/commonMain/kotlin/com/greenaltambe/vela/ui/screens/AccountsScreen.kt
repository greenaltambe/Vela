package com.greenaltambe.vela.ui.screens

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
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.toColor
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

    Column(modifier.fillMaxSize()) {
        VelaTopBar("Accounts", onBack = { navigator.back() })
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = Vela.gutter, end = Vela.gutter, top = Vela.sm, bottom = Vela.sm + com.greenaltambe.vela.ui.nav.LocalChromeInset.current), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
                if (active.kind == AccountKind.GOOGLE && services.profiles != null && services.settings.resolve(com.greenaltambe.vela.core.settings.Settings.accountProfilesExperimental)) ProfileSection(services)
                if (active.kind == AccountKind.GOOGLE && active.authState != AuthState.OK) {
                    Text("Signed out: ${active.displayName}'s session no longer works. Add the account again to sign in.", color = Vela.colors.error.toColor(), modifier = Modifier.testTag("accounts.banner"))
                }
                SectionHeader("Accounts")
                all.forEach { a ->
                    AccountRow(a, selected = a.id == active.id, onSwitch = { accounts.switchTo(a.id) }, onSignOut = { accounts.signOut(a.id) }, onRemove = { removing = a })
                }
                if (active.isSignedIn && services.sync != null) {
                    SectionHeader("Sync")
                    Text("Adds the channels you subscribe to on Google to this account's subscriptions. Nothing is removed.", style = MaterialTheme.typography.bodySmall)
                    PrimaryButton(if (busy) "Syncing…" else "Sync subscriptions now", {
                        busy = true; message = "Syncing…"
                        scope.launch {
                            val r = services.sync!!.pullSubscriptions()
                            message = r.failure?.let { "Sync failed: $it" } ?: "Added ${r.added} new channel(s); ${r.alreadyHad} were already here."
                            services.feed.reload(); busy = false
                        }
                    }, enabled = !busy, modifier = Modifier.testTag("accounts.sync"))
                }
                SectionHeader("Add a Google account")
                if (login == null) {
                    Text("Signing in is not available on this platform yet.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "Export your YouTube cookies from a PRIVATE (incognito) window where you are signed in, close that window right away, then pick the cookies.txt file. " +
                            "Browsers rotate the cookies of windows that stay open, which would invalidate the export. A \"cookies.txt\" export extension for your browser does this.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    services.webLogin?.let { start -> PrimaryButton("Sign in with Google", { onboarding = start }, modifier = Modifier.testTag("accounts.webLogin")) }
                    PrimaryButton(if (busy) "Checking…" else "Import cookies.txt", { onboarding = pick }, enabled = !busy, modifier = Modifier.testTag("accounts.import"))
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("accounts.message")) }
                SectionHeader("Privacy")
                Text(
                    "Cookies are kept in ${services.accounts.credentialStoreDescription}, never in Vela's database, logs or backups. " +
                        "Signing out everywhere deletes them from this device; it does not sign you out of Google elsewhere.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { wipingAll = true }, modifier = Modifier.testTag("accounts.signOutAll")) { Text("Sign out everywhere") }
                SectionHeader("Safety")
                Text("Account requests: ${services.accounts.credentialStoreDescription.let { "credentials in $it" }}. See Settings > Account activity for every request. Pausing the account is automatic; the button below only tests the banner and the pause.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { if (active.kind == AccountKind.GOOGLE) services.governors.forAccount(active.id).trip(com.greenaltambe.vela.auth.BlockSignal.HTTP_429) }, enabled = active.kind == AccountKind.GOOGLE, modifier = Modifier.testTag("accounts.simulate429")) { Text("Test: simulate a rate limit") }
            }
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
private fun AccountRow(a: Account, selected: Boolean, onSwitch: () -> Unit, onSignOut: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = Vela.minTouch).clickable(onClick = onSwitch).testTag("accounts.row.${a.id}"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
        Avatar(a.displayName, url = a.avatarUrl)
        Column(Modifier.weight(1f)) {
            Text(a.displayName + if (selected) "  (active)" else "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            val detail = when {
                a.kind == AccountKind.LOCAL -> "On this device only"
                a.authState != AuthState.OK -> "Needs sign-in"
                else -> a.maskedEmail ?: "Google account"
            }
            Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor())
        }
        if (a.kind == AccountKind.GOOGLE) {
            var open by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { open = true }, modifier = Modifier.testTag("accounts.menu.${a.id}")) { Icon(Vela.icons.more, "Account options") }
                DropdownMenu(open, { open = false }) {
                    if (a.authState == AuthState.OK) DropdownMenuItem(text = { Text("Sign out") }, onClick = { open = false; onSignOut() })
                    DropdownMenuItem(text = { Text("Remove") }, onClick = { open = false; onRemove() })
                }
            }
        }
    }
}


/** Experimental (Settings > Account switch, off by default): which channel of the account is used. Choosing one is checked with a read first and reverted if YouTube does not confirm it. */
@Composable
private fun ProfileSection(services: AppServices) {
    val profiles = services.profiles ?: return
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var options by remember { mutableStateOf<List<com.greenaltambe.vela.auth.AccountProfile>?>(null) }
    var current by remember { mutableStateOf(profiles.current()) }
    Column(Modifier.testTag("accounts.profile"), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
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
