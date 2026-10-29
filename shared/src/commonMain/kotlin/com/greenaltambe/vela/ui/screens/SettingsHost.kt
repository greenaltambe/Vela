package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.core.settings.*
import com.greenaltambe.vela.ui.components.EmptyState
import com.greenaltambe.vela.ui.components.VelaTopBar
import com.greenaltambe.vela.ui.look.*
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route

/** The icon on a page's tile; the tile colour comes from the Look's wake colours, so a Look recolours the whole screen. */
@Composable
internal fun SettingsPage.tileIcon(): ImageVector = when (id) {
    "customize" -> Vela.icons.palette; "playback" -> Vela.icons.play; "extras" -> Vela.icons.timer; "home" -> Vela.icons.home; "feed" -> Vela.icons.subscriptions
    "shorts" -> Vela.icons.bolt; "accounts" -> Vela.icons.account; "safety" -> Vela.icons.shield; "privacy" -> Vela.icons.lock; "downloads" -> Vela.icons.download
    "auto" -> Vela.icons.refresh; "storage" -> Vela.icons.folder; "backup" -> Vela.icons.cloud; "updates" -> Vela.icons.speed; "health" -> Vela.icons.info
    else -> Vela.icons.settings
}

@Composable
internal fun IconTile(page: SettingsPage) {
    val wake = Vela.look.accent.wake
    Box(Modifier.size(40.dp).clip(Vela.mediumShape).background(wake[page.tile % wake.size].toColor().copy(alpha = 0.28f)), contentAlignment = Alignment.Center) {
        Icon(page.tileIcon(), null, tint = Vela.colors.onSurface.toColor())
    }
}

/** The screen a link row leads to (Accounts, the download rules, Backup, Health, the Customize hub). */
internal fun SettingsPage.linkRoute(): Route = when (link) {
    SettingsLink.CUSTOMIZE -> Route.Customize
    SettingsLink.ACCOUNTS -> Route.Accounts
    SettingsLink.DOWNLOAD_RULES -> Route.DownloadRules
    SettingsLink.BACKUP -> Route.Backup
    SettingsLink.HEALTH -> Route.Health
    SettingsLink.NONE -> Route.SettingsPage(id)
}

/** What tapping the row opens: a page of settings, or the linked screen directly when the row owns no settings (so no setting is only reachable through search). */
private fun SettingsPage.route(): Route =
    if (link == SettingsLink.NONE || link == SettingsLink.CUSTOMIZE || SettingsPages.settingsOf(this).isEmpty()) linkRoute() else Route.SettingsPage(id)

/**
 * Settings home: the account card, settings search, the Performance mode switch, then grouped cards with small-caps headers. Every row
 * is an icon tile, a title, a LIVE one-line summary of the current values and a chevron. Search results are single settings; picking
 * one opens its page and highlights it. Everything is generated from [SettingsPages] and the settings schema.
 */
@Composable
fun SettingsHost(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    val revision by services.settings.revision.collectAsState()
    val account by services.accounts.active.collectAsState()
    val results = remember(query) { if (query.isBlank()) emptyList() else Settings.search(query) }

    Column(modifier.fillMaxSize()) {
        VelaTopBar("Settings", menu = emptyList())
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            OutlinedTextField(
                query, { query = it }, singleLine = true, placeholder = { Text("Search settings", maxLines = 1) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }, modifier = Modifier.testTag("settings.clear")) { Icon(Vela.icons.close, "Clear") } },
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = Vela.gutter, vertical = Vela.xs).testTag("settings.search"),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("settings.list"), contentPadding = Vela.contentPadding, verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
                if (query.isNotBlank()) {
                    if (results.isEmpty()) item { EmptyState("No settings match \"$query\".") }
                    items(results, key = { it.key }) { s ->
                        val page = SettingsPages.pageOf(s)
                        Column(Modifier.fillMaxWidth().heightIn(min = Vela.minTouch).clip(Vela.mediumShape).pressable { page?.let { p -> navigator.push(if (p.link == SettingsLink.CUSTOMIZE) Route.CustomizePage(CustomizePages.pageFor(s)) else Route.SettingsPage(p.id, highlight = s.key)) } }.padding(Vela.sm).testTag("settings.result.${s.key}")) {
                            Text(s.label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(page?.title ?: "", style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor(), maxLines = 1)
                        }
                    }
                    return@LazyColumn
                }
                item(key = "account") {
                    Row(Modifier.fillMaxWidth().clip(Vela.largeShape).background(Vela.colors.surface.toColor()).pressable { navigator.push(Route.Accounts) }.padding(Vela.md).testTag("settings.account"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.md)) {
                        com.greenaltambe.vela.ui.components.Avatar(if (account.kind == com.greenaltambe.vela.auth.AccountKind.LOCAL) "Local" else account.displayName, size = 44.dp, url = account.avatarUrl)
                        Column(Modifier.weight(1f)) {
                            Text(if (account.kind == com.greenaltambe.vela.auth.AccountKind.LOCAL) "Local (not signed in)" else account.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (account.kind == com.greenaltambe.vela.auth.AccountKind.LOCAL) "Sign in to sync subscriptions and playlists" else account.maskedEmail ?: account.label, style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        androidx.compose.material3.Icon(com.greenaltambe.vela.ui.look.Vela.icons.chevronRight, null, tint = com.greenaltambe.vela.ui.look.Vela.colors.onSurfaceVariant.toColor())
                    }
                }
                item(key = "performance") {
                    Column(Modifier.fillMaxWidth().clip(Vela.largeShape).background(Vela.colors.surface.toColor()).padding(horizontal = Vela.md, vertical = Vela.xs)) {
                        SettingRow(services.settings, Settings.performanceMode, revision)
                        Text("Turns off glass, blur, staggered entrances and extra animation. Use it on a slow or hot device.", style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor(), modifier = Modifier.padding(bottom = Vela.xs))
                    }
                }
                SettingsGroup.entries.forEach { group ->
                    val pages = SettingsPages.all.filter { it.group == group }
                    if (pages.isEmpty()) return@forEach
                    item(key = "header-${group.name}") { Text(group.title, style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor(), modifier = Modifier.padding(top = Vela.sm, start = Vela.xs), maxLines = 1) }
                    item(key = "group-${group.name}") {
                        Column(Modifier.fillMaxWidth().clip(Vela.largeShape).background(Vela.colors.surface.toColor())) {
                            pages.forEach { page ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).pressable { navigator.push(page.route()) }.padding(horizontal = Vela.md, vertical = Vela.sm).testTag("settings.page.${page.id}"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.md)) {
                                    IconTile(page)
                                    Column(Modifier.weight(1f)) {
                                        Text(page.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(remember(revision) { SettingsSummaries.of(page, services.settings) }, style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor(), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("settings.summary.${page.id}"))
                                    }
                                    androidx.compose.material3.Icon(com.greenaltambe.vela.ui.look.Vela.icons.chevronRight, null, tint = com.greenaltambe.vela.ui.look.Vela.colors.onSurfaceVariant.toColor())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A page's own settings (generated rows), a link row to the screen it also leads to, and "Reset this page" at the end. */
@Composable
fun SettingsPageScreen(services: AppServices, navigator: Navigator, pageId: String, highlight: String?, modifier: Modifier = Modifier) {
    val page = SettingsPages.byId(pageId)
    val revision by services.settings.revision.collectAsState()
    val toasts = LocalUndoToasts.current
    Column(modifier.fillMaxSize()) {
        VelaTopBar(page?.title ?: "Settings", onBack = { navigator.back() })
        if (page == null) { EmptyState("This settings page does not exist."); return@Column }
        val settings = remember(page) { SettingsPages.settingsOf(page) }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("settings.pageList"), contentPadding = Vela.contentPadding, verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
                if (page.link != SettingsLink.NONE) item {
                    TextButton(onClick = { navigator.push(page.linkRoute()) }, modifier = Modifier.testTag("settings.openLinked")) { Text("Open ${page.title}", maxLines = 1) }
                }
                items(settings, key = { it.key }) { s -> SettingRow(services.settings, s, revision, highlighted = s.key == highlight) }
                item(key = "reset") { ResetPageButton(services.settings, settings, toasts, "settings.resetPage") }
            }
        }
    }
}

/** Puts every setting of a page back to its default in one change, with one Undo. */
@Composable
internal fun ResetPageButton(resolver: SettingsResolver, settings: List<Setting<*>>, toasts: UndoToasts, tag: String) {
    TextButton(onClick = {
        val before = settings.associateWith { if (resolver.isOverridden(it)) resolver.rawValue(it) else null }
        resolver.batch { settings.forEach { resolver.resetToDefault(it) } }
        toasts.show("Page reset to defaults") { resolver.batch { before.forEach { (s, raw) -> if (raw == null) resolver.resetToDefault(s) else resolver.setRaw(s, raw) } } }
    }, modifier = Modifier.testTag(tag)) { Text("Reset this page", maxLines = 1) }
}
