package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.auth.Account
import com.greenaltambe.vela.auth.AccountKind
import com.greenaltambe.vela.ui.components.Avatar
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.pressable
import com.greenaltambe.vela.ui.look.toColor
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route

/** The avatar button in the top bar: the active account's picture, or "Local" when nobody is signed in. */
@Composable
fun ProfileButton(services: AppServices, onClick: () -> Unit) {
    val account by services.accounts.active.collectAsState()
    // A ring in the palette gradient around the picture.
    androidx.compose.foundation.layout.Box(Modifier.size(40.dp).clip(Vela.pillShape).clickable(onClick = onClick).border(2.dp, Vela.wake, Vela.pillShape).padding(4.dp).testTag("profile.button"), contentAlignment = Alignment.Center) {
        Avatar(if (account.kind == AccountKind.LOCAL) "Local" else account.displayName, size = 30.dp, url = account.avatarUrl.takeIf { account.kind != AccountKind.LOCAL })
    }
}

/**
 * The Profile menu: the account card (name, masked handle, avatar, switch, add, sign out), then Settings and About. The rest used to be repeated
 * here (Customize, Downloads, History, Backup, Health); they live in Settings and the Library. Anonymous / local mode shows a
 * "Local" card with "Sign in". Each line closes the menu and opens its page on top of the current one (Back returns).
 */
@Composable
fun ProfileMenu(services: AppServices, navigator: Navigator, onDismiss: () -> Unit) {
    val account by services.accounts.active.collectAsState()
    val all by services.accounts.all.collectAsState()
    var showAbout by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var switching by remember { mutableStateOf(false) }
    fun go(route: Route) { onDismiss(); navigator.push(route) }
    val local = account.kind == AccountKind.LOCAL

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 420.dp).clip(Vela.largeShape).background(Vela.colors.surface.toColor()).verticalScroll(rememberScrollState()).padding(Vela.md).testTag("profile.menu"),
            verticalArrangement = Arrangement.spacedBy(Vela.xs),
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = Vela.sm).testTag("profile.card"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.md)) {
                Avatar(if (local) "Local" else account.displayName, size = 48.dp, url = account.avatarUrl.takeIf { !local })
                Column(Modifier.weight(1f)) {
                    Text(if (local) "Local" else account.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("profile.name"))
                    Text(if (local) "Nothing is shared: everything stays on this device." else account.maskedEmail ?: account.label, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Vela.colors.onSurfaceVariant.toColor())
                }
            }
            // Account actions only. Everything else (Customize, Downloads, History, Backup, Health) has its own place: the Settings page and the Library tab.
            if (local) MenuLine("Sign in", "profile.signIn", Vela.icons.account) { go(Route.Accounts) }
            // Switching is offered whenever there is another account to switch to, including from the local profile.
            if (all.size > 1) MenuLine(if (switching) "Switch account ▴" else "Switch account ▾", "profile.switch", Vela.icons.refresh) { switching = !switching }
            if (switching) all.filter { it.id != account.id }.forEach { a ->
                MenuLine("   ${if (a.kind == AccountKind.LOCAL) "Local" else a.displayName}", "profile.switchTo", null) { services.accounts.switchTo(a.id); onDismiss() }
            }
            if (!local) {
                MenuLine("Add account", "profile.add", Vela.icons.add) { go(Route.Accounts) }
                MenuLine("Sign out", "profile.signOut", Vela.icons.close) { confirmSignOut = true }
            }
            Divider()
            MenuLine("Settings", "profile.settings", Vela.icons.settings) { go(Route.Settings) }
            MenuLine("About", "profile.about", Vela.icons.info) { showAbout = true }
        }
    }
    if (confirmSignOut) AlertDialog(
        onDismissRequest = { confirmSignOut = false },
        title = { Text("Sign out of ${account.displayName}?") },
        text = { Text("The sign-in is removed from this device. Subscriptions and history already copied here stay.") },
        confirmButton = { TextButton(onClick = { services.accounts.signOut(account.id); confirmSignOut = false; onDismiss() }, modifier = Modifier.testTag("profile.signOut.confirm")) { Text("Sign out") } },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
    )
    if (showAbout) AboutDialog { showAbout = false }
}

@Composable
private fun MenuLine(label: String, tag: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = Vela.minTouch).clip(Vela.mediumShape).pressable(onClick = onClick).padding(horizontal = Vela.sm).testTag(tag), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.md)) {
        if (icon != null) androidx.compose.material3.Icon(icon, null, tint = Vela.colors.onSurfaceVariant.toColor())
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Divider() {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(vertical = Vela.xs).androidxHeight1().background(Vela.colors.outline.toColor()))
}

private fun Modifier.androidxHeight1() = this.then(Modifier.height(1.dp))

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About Vela") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Vela.xs), modifier = Modifier.testTag("about.text")) {
                Text("A YouTube client that keeps working: several sources for every video, your data on your device.", style = MaterialTheme.typography.bodyMedium)
                Text("NewPipeExtractor ${com.greenaltambe.vela.health.NewPipeVersion.VERSION}; yt-dlp (checked ${com.greenaltambe.vela.health.GeneratedVersions.YTDLP_VERIFIED}).", style = MaterialTheme.typography.bodySmall)
                Text("Open-source parts include NewPipeExtractor and youtubedl-android (GPL-3.0), yt-dlp (Unlicense), Python and QuickJS. A full list of licences is not written yet.", style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor())
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
