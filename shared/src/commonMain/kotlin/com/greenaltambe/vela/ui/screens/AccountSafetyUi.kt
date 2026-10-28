package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.auth.AuthState
import com.greenaltambe.vela.auth.BreakerState
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.setRaw
import com.greenaltambe.vela.ui.components.EmptyState
import com.greenaltambe.vela.ui.components.VelaTopBar
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.toColor
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import kotlinx.coroutines.delay

/**
 * Shown on every screen: the active account signed out, or its breaker open ("paused"). The plain explanation says what
 * happened and that nothing is being sent; "Try now" asks first.
 */
@Composable
fun AccountBanner(services: AppServices, navigator: Navigator) {
    val account by services.accounts.active.collectAsState()
    if (account.kind != com.greenaltambe.vela.auth.AccountKind.GOOGLE) return
    val breaker by services.governors.forAccount(account.id).status.collectAsState()
    var confirming by remember { mutableStateOf(false) }
    val message: String? = when {
        account.authState != AuthState.OK -> "${account.displayName} is signed out. Add the account again to use it."
        breaker.state == BreakerState.OPEN -> "The account is paused" + (services.governors.forAccount(account.id).cooldownUntil()?.let { " until ${it.toString().substring(11, 16)} UTC" } ?: "") +
            " because ${breaker.lastSignal?.label ?: "Google pushed back"}. Vela sends nothing with it meanwhile; videos still play through the other sources."
        else -> null
    }
    if (message == null) return
    Surface(color = Vela.colors.surface.toColor(), modifier = Modifier.fillMaxWidth().testTag("account.banner")) {
        Row(Modifier.padding(horizontal = Vela.gutter, vertical = Vela.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.xs)) {
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Vela.colors.error.toColor(), maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (account.authState != AuthState.OK) TextButton(onClick = { navigator.push(Route.Accounts) }) { Text("Accounts") }
            else TextButton(onClick = { confirming = true }, modifier = Modifier.testTag("account.tryNow")) { Text("Try now") }
        }
    }
    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text("Try the account now?") },
        text = { Text("The pause exists to protect the account. Trying early sends one request; if Google pushes back again the pause gets longer.") },
        confirmButton = { TextButton(onClick = { services.governors.forAccount(account.id).tryNow(); confirming = false }, modifier = Modifier.testTag("account.tryNowConfirm")) { Text("Try now") } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("Keep paused") } },
    )
}

/** At start and then daily, ask Google (through the governor) whether the active account's session still works. A network error changes nothing. */
@Composable
fun SessionCheckEffect(services: AppServices) {
    val account by services.accounts.active.collectAsState()
    val login = services.login ?: return
    LaunchedEffect(account.id) {
        if (account.kind != com.greenaltambe.vela.auth.AccountKind.GOOGLE || account.authState != AuthState.OK) return@LaunchedEffect
        while (true) {
            runCatching { login.checkSession(account.id) }
            delay(24L * 60 * 60 * 1000)
        }
    }
}

/**
 * The gate before a Google account is added: what the app does, what it never does, the risk in plain words, and the
 * activity-reporting choice (off). [onAccepted] runs only after both acknowledgements are ticked.
 */
@Composable
fun AccountOnboardingDialog(services: AppServices, onDismiss: () -> Unit, onAccepted: () -> Unit) {
    var understandsTerms by remember { mutableStateOf(false) }
    var throwaway by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Before you add an account") },
        text = {
            Column(Modifier.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
                Text("Vela is not made by YouTube. Using a third-party app with your Google account goes against YouTube's terms, and Google can limit or close an account it dislikes. Use a throwaway account, not your main one.", style = MaterialTheme.typography.bodyMedium)
                Text("Vela uses the account only when a video cannot be opened without signing in (age limits, bot checks), keeps requests few and spaced out, and sends them only to YouTube and Google sign-in hosts. It never sends the account to search, trending, comments, downloads, SponsorBlock, or other sites. Every request is listed under Diagnostics > Account activity.", style = MaterialTheme.typography.bodyMedium)
                Text("Remove everything: Accounts > Remove, or Sign out everywhere. Both delete the stored cookies from this device.", style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(understandsTerms, { understandsTerms = it }, modifier = Modifier.testTag("onboarding.terms")); Text("I understand the risk to this account") }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(throwaway, { throwaway = it }, modifier = Modifier.testTag("onboarding.throwaway")); Text("This is a throwaway account, or I accept the risk for it") }
                // Reporting what you watch to the account is NOT implemented (it needs tracking URLs that only an own signed-in player call yields), so there is no switch: nothing here may promise it.
                Text("Not available: what you watch stays on this device; Vela never adds to your YouTube history.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("onboarding.noReporting"))
            }
        },
        confirmButton = {
            TextButton(onClick = { onAccepted() }, enabled = understandsTerms && throwaway, modifier = Modifier.testTag("onboarding.continue")) { Text("Continue") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Diagnostics > Account activity: everything the account did, newest first, with the governor's decision. */
@Composable
fun AccountActivityScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    val account by services.accounts.active.collectAsState()
    var revision by remember { mutableStateOf(0) }
    val rows = remember(revision, account.id) { services.audit.recent(account.id, 300) }
    Column(modifier.fillMaxSize()) {
        VelaTopBar("Account activity", onBack = { navigator.back() })
        Row(Modifier.padding(horizontal = Vela.gutter), verticalAlignment = Alignment.CenterVertically) {
            Text("${rows.size} request(s) with ${account.label}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { revision++ }) { Text("Refresh") }
            TextButton(onClick = { services.audit.purge(account.id); revision++ }, modifier = Modifier.testTag("activity.purge")) { Text("Clear") }
        }
        if (rows.isEmpty()) EmptyState("The account has not been used yet.")
        else LazyColumn(Modifier.fillMaxSize().padding(horizontal = Vela.gutter)) {
            items(rows) { r ->
                Text("${r.at.toString().substring(0, 19).replace('T', ' ')}  ${r.kind}  ${r.host}  ${r.result}  [${r.decision}]", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

/**
 * "Playback may be broken": shown when every source failed the health checks twice in a row. Not spammy: one line, dismissed
 * for as long as the failure streak stays at the length it had when dismissed (it returns if it gets worse after a recovery).
 */
@Composable
fun HealthBanner(services: AppServices, navigator: Navigator) {
    val health = services.health ?: return
    val alert by health.alert.collectAsState()
    var dismissedAt by remember { mutableStateOf(0) }
    val a = alert ?: return
    if (a.streak <= dismissedAt) return
    Surface(color = Vela.colors.surface.toColor(), modifier = Modifier.fillMaxWidth().testTag("health.banner")) {
        Row(Modifier.padding(horizontal = Vela.gutter, vertical = Vela.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.xs)) {
            Text(a.message(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Vela.colors.error.toColor(), maxLines = 4, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { navigator.push(Route.Health) }) { Text("Details") }
            TextButton(onClick = { dismissedAt = a.streak }, modifier = Modifier.testTag("health.banner.dismiss")) { Text("Dismiss") }
        }
    }
}

/** "YouTube wants to check you are human": shown while a request waits on a captcha; Solve opens the platform's solver, Not now gives up. */
@Composable
fun CaptchaBanner(services: AppServices) {
    val coordinator = services.captcha ?: return
    val url by coordinator.pending.collectAsState()
    val challenge = url ?: return
    Column(Modifier.fillMaxWidth().padding(8.dp).testTag("captcha.banner"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("YouTube wants to check that you are not a robot before it answers.", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (services.solveCaptcha != null) androidx.compose.material3.Button(onClick = { services.solveCaptcha(challenge) }, modifier = Modifier.testTag("captcha.solve")) { Text("Solve it", maxLines = 1) }
            androidx.compose.material3.OutlinedButton(onClick = { coordinator.dismiss() }, modifier = Modifier.testTag("captcha.dismiss")) { Text("Not now", maxLines = 1) }
        }
    }
}
