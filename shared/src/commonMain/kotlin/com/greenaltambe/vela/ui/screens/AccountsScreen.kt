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
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Vela.gutter, vertical = Vela.sm), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
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
                    services.webLogin?.let { PrimaryButton("Sign in with Google", it, modifier = Modifier.testTag("accounts.webLogin")) }
                    PrimaryButton(if (busy) "Checking…" else "Import cookies.txt", pick, enabled = !busy, modifier = Modifier.testTag("accounts.import"))
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("accounts.message")) }
                SectionHeader("Privacy")
                Text(
                    "Cookies are kept in ${services.accounts.credentialStoreDescription}, never in Vela's database, logs or backups. " +
                        "Signing out everywhere deletes them from this device; it does not sign you out of Google elsewhere.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { wipingAll = true }, modifier = Modifier.testTag("accounts.signOutAll")) { Text("Sign out everywhere") }
            }
        }
    }

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
