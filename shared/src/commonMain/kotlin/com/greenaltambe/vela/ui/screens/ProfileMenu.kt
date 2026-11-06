package com.greenaltambe.vela.ui.screens

import androidx.compose.material3.toShape
import com.greenaltambe.vela.ui.theme.AppIcons
import androidx.compose.foundation.shape.CircleShape
import com.greenaltambe.vela.ui.theme.Spacing
import com.greenaltambe.vela.ui.theme.Accent
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
import com.greenaltambe.vela.ui.look.pressable
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route

/** The avatar button in the top bar: the active account's picture, or "Local" when nobody is signed in. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileButton(services: AppServices, onClick: () -> Unit) {
    val account by services.accounts.active.collectAsState()
    // The picture in one of Material's expressive shapes (a nine-sided cookie), on a tonal ring of the same shape.
    val cookie = androidx.compose.material3.MaterialShapes.Cookie9Sided.toShape()
    androidx.compose.foundation.layout.Box(Modifier.size(44.dp).clip(cookie).background(MaterialTheme.colorScheme.primaryContainer).clickable(onClick = onClick).padding(4.dp).testTag("profile.button"), contentAlignment = Alignment.Center) {
        Avatar(if (account.kind == AccountKind.LOCAL) "Local" else account.displayName, size = 36.dp, url = account.avatarUrl.takeIf { account.kind != AccountKind.LOCAL }, shape = cookie)
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
            Modifier.widthIn(max = 420.dp).clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.surfaceContainer).verticalScroll(rememberScrollState()).padding(Spacing.md).testTag("profile.menu"),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = Spacing.sm).testTag("profile.card"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Avatar(if (local) "Local" else account.displayName, size = 48.dp, url = account.avatarUrl.takeIf { !local })
                Column(Modifier.weight(1f)) {
                    Text(if (local) "Local" else account.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("profile.name"))
                    Text(if (local) "Nothing is shared: everything stays on this device." else account.maskedEmail ?: account.label, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Account actions only. Everything else (Customize, Downloads, History, Backup, Health) has its own place: the Settings page and the Library tab.
            if (local) MenuLine("Sign in", "profile.signIn", AppIcons.account) { go(Route.Accounts) }
            // Switching is offered whenever there is another account to switch to, including from the local profile.
            if (all.size > 1) MenuLine("Switch account", "profile.switch", if (switching) AppIcons.chevronUp else AppIcons.chevronDown) { switching = !switching }
            if (switching) all.filter { it.id != account.id }.forEach { a ->
                MenuLine(if (a.kind == AccountKind.LOCAL) "Local" else a.displayName, "profile.switchTo", null, leading = { Avatar(if (a.kind == AccountKind.LOCAL) "Local" else a.displayName, size = 32.dp, url = a.avatarUrl) }) { services.accounts.switchTo(a.id); onDismiss() }
            }
            if (!local) {
                MenuLine("Add account", "profile.add", AppIcons.add) { go(Route.Accounts) }
                MenuLine("Sign out", "profile.signOut", AppIcons.close) { confirmSignOut = true }
            }
            androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = Spacing.xs), color = MaterialTheme.colorScheme.outlineVariant)
            MenuLine("Settings", "profile.settings", AppIcons.settings) { go(Route.Settings) }
            MenuLine("About", "profile.about", AppIcons.info) { showAbout = true }
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

/** One line of the profile menu: a Material list item (icon, text) that closes the menu when it acts. */
@Composable
private fun MenuLine(label: String, tag: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    androidx.compose.material3.ListItem(
        headlineContent = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = leading ?: icon?.let { i -> { androidx.compose.material3.Icon(i, null) } },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).testTag(tag),
    )
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About Vela") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.testTag("about.text")) {
                Text("A YouTube client that keeps working: several sources for every video, your data on your device.", style = MaterialTheme.typography.bodyMedium)
                Text("NewPipeExtractor ${com.greenaltambe.vela.health.NewPipeVersion.VERSION}; yt-dlp (checked ${com.greenaltambe.vela.health.GeneratedVersions.YTDLP_VERIFIED}).", style = MaterialTheme.typography.bodySmall)
                Text("Vela is free software under the GNU General Public License v3.0 or later. It is an independent project, not affiliated with or endorsed by YouTube or Google.", style = MaterialTheme.typography.bodySmall)
                Text("Open-source parts include NewPipeExtractor and youtubedl-android (GPL-3.0), yt-dlp (Unlicense), Python and QuickJS, libmpv (GPL/LGPL), AndroidX, Compose, OkHttp, SQLDelight and Coil (Apache-2.0), Lucide icons (ISC). The full list with licences is THIRD_PARTY_NOTICES.md in the source repository.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
