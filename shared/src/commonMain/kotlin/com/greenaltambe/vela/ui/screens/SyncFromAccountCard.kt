package com.greenaltambe.vela.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.ui.components.LoadingSpinner
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * "Sync from your account" on the Channels page: a primary-container card with a round icon badge, a short explanation and a large expressive button
 * (its shape morphs when pressed; a loading indicator replaces the icon while it works). Signed in: pulls the channels you follow on YouTube and adds
 * the new ones (nothing is removed). Signed out: the same card, with the button leading to Accounts. [onSynced] runs after a pull, to reload the list.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SyncFromAccountCard(services: AppServices, navigator: Navigator, onSynced: () -> Unit, modifier: Modifier = Modifier) {
    val account by services.accounts.active.collectAsState()
    val sync = services.sync
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val canSync = account.isSignedIn && sync != null

    Surface(modifier.fillMaxWidth().animateContentSize().testTag("subs.syncCard"), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(52.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(busy, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "syncBadge") { working ->
                            if (working) LoadingSpinner(Modifier.size(36.dp), color = MaterialTheme.colorScheme.onPrimary) else Icon(AppIcons.cloud, null, Modifier.size(26.dp))
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Sync from your account", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (canSync) "Adds the channels you follow on YouTube. Nothing is removed." else "Sign in to bring in the channels you follow on YouTube.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
            Button(
                onClick = {
                    if (!canSync) { navigator.push(Route.Accounts); return@Button }
                    busy = true; result = null
                    scope.launch {
                        val r = try { sync!!.pullSubscriptions() } catch (e: Exception) { null }
                        result = when {
                            r == null -> "Sync failed. Try again in a moment."
                            r.failure != null -> "Sync failed: ${r.failure}"
                            else -> "Added ${r.added} new channel${if (r.added == 1) "" else "s"}; ${r.alreadyHad} already here."
                        }
                        if (r?.failure == null && r != null) { services.feed.reload(); onSynced() }
                        busy = false
                    }
                },
                enabled = !busy, shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight).testTag("subs.syncButton"),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            ) {
                Icon(if (canSync) AppIcons.refresh else AppIcons.account, null, Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)))
                Text(if (busy) "Syncing…" else if (canSync) "Sync now" else "Sign in to sync", Modifier.padding(start = ButtonDefaults.iconSpacingFor(ButtonDefaults.MediumContainerHeight)), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
            AnimatedVisibility(result != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Text(result.orEmpty(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("subs.syncResult"))
            }
        }
    }
}
