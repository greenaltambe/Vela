@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.greenaltambe.vela.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import com.greenaltambe.vela.ui.components.LocalEmbeddedInPane
import com.greenaltambe.vela.ui.layout.LocalWindowSize
import com.greenaltambe.vela.ui.layout.WindowSizeClass
import com.greenaltambe.vela.ui.look.LocalUndoToasts
import com.greenaltambe.vela.ui.look.UndoToasts
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import com.greenaltambe.vela.ui.settings.SettingItem
import com.greenaltambe.vela.ui.settings.SettingsScaffold
import com.greenaltambe.vela.ui.settings.segmentedItemColors
import com.greenaltambe.vela.ui.settings.segmentedShape
import com.greenaltambe.vela.ui.settings.settingsSection
import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.Spacing

/** The icon on a page's tile. */
@Composable
internal fun SettingsPage.tileIcon(): ImageVector = when (id) {
    "customize" -> AppIcons.palette; "playback" -> AppIcons.play; "extras" -> AppIcons.timer; "home" -> AppIcons.home; "feed" -> AppIcons.subscriptions
    "shorts" -> AppIcons.bolt; "accounts" -> AppIcons.account; "safety" -> AppIcons.shield; "privacy" -> AppIcons.lock; "downloads" -> AppIcons.download
    "auto" -> AppIcons.refresh; "storage" -> AppIcons.folder; "backup" -> AppIcons.cloud; "updates" -> AppIcons.speed; "health" -> AppIcons.info
    else -> AppIcons.settings
}

/** A page's tile: its icon on a tonal container in one of Material's expressive shapes; the container colour and the shape follow the page's tile slot. */
@Composable
internal fun IconTile(page: SettingsPage) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (page.tile % 3) {
        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    val polygons = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Pentagon, MaterialShapes.Sunny, MaterialShapes.Cookie6Sided, MaterialShapes.Pill)
    val shape = polygons[page.tile % polygons.size].toShape()
    Box(Modifier.size(44.dp).clip(shape).background(container), contentAlignment = Alignment.Center) { Icon(page.tileIcon(), null, tint = content) }
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
internal fun SettingsPage.route(): Route =
    if (link == SettingsLink.NONE || link == SettingsLink.CUSTOMIZE || SettingsPages.settingsOf(this).isEmpty()) linkRoute() else Route.SettingsPage(id)

/** The routes that live in the Settings area and share its two-pane layout on wide windows. */
internal fun Route.isSettingsPane(): Boolean = this == Route.Settings || this is Route.SettingsPage || this == Route.Customize || this is Route.CustomizePage || this == Route.Accounts || this == Route.Backup || this == Route.Health

/** The page of the settings list that is open for [route] (highlighted in the two-pane list). */
private fun selectedPageId(route: Route): String = when (route) {
    is Route.SettingsPage -> route.pageId
    Route.Customize, is Route.CustomizePage -> "customize"
    Route.Accounts -> "accounts"
    Route.Backup -> "backup"
    Route.Health -> "health"
    else -> SettingsPages.all.first().id
}

/**
 * One Settings route on its own: the home list, a page of settings, Customize, Accounts, Backup or Health. On a phone this is the whole screen; on a
 * wide window it is the right pane of [SettingsPaneHost].
 */
@Composable
internal fun SettingsRouteContent(services: AppServices, navigator: Navigator, route: Route, modifier: Modifier = Modifier) {
    when (route) {
        Route.Settings -> SettingsHost(services, navigator, modifier)
        is Route.SettingsPage -> SettingsPageScreen(services, navigator, route.pageId, route.highlight, modifier)
        Route.Customize -> CustomizeScreen(services, navigator, modifier)
        is Route.CustomizePage -> CustomizePageScreen(services, navigator, route.page, modifier)
        Route.Accounts -> AccountsScreen(services, navigator, modifier)
        Route.Backup -> BackupScreen(services, navigator, modifier)
        Route.Health -> HealthScreen(services, navigator, modifier)
        else -> Unit
    }
}

/**
 * Settings, on any window. Under 840 dp each screen is a push over the last. At 840 dp and above: the list of pages on the left (its Settings title and
 * search with it), the open page on the right with no Back arrow; picking a page replaces the right pane (so Back leaves Settings in one step).
 */
@Composable
internal fun SettingsPaneHost(services: AppServices, navigator: Navigator, route: Route, modifier: Modifier = Modifier) {
    val wide = LocalWindowSize.current == WindowSizeClass.EXPANDED
    if (!wide) { SettingsRouteContent(services, navigator, route, modifier); return }
    val detail = if (route == Route.Settings) SettingsPages.all.first().route() else route
    Row(modifier.fillMaxWidth().fillMaxHeight()) {
        Box(Modifier.width(400.dp).fillMaxHeight()) { SettingsHome(services, navigator, selectedId = selectedPageId(route), showBack = false) }
        VerticalDivider()
        Box(Modifier.weight(1f).fillMaxHeight()) {
            CompositionLocalProvider(LocalEmbeddedInPane provides true) {
                AnimatedContent(detail, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "settingsPane") { r -> SettingsRouteContent(services, navigator, r, Modifier) }
            }
        }
    }
}

/** Settings home on a phone or a narrow window. */
@Composable
fun SettingsHost(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) =
    SettingsHome(services, navigator, selectedId = null, showBack = navigator.canGoBack, modifier = modifier)

/**
 * The list of settings pages: a large collapsing title, the search field, the account row, then the groups of pages. Search results are single
 * settings in the same rows; picking one opens its page and highlights the setting. Everything is generated from [SettingsPages] and the schema.
 */
@Composable
internal fun SettingsHome(services: AppServices, navigator: Navigator, selectedId: String?, showBack: Boolean, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    val revision by services.settings.revision.collectAsState()
    val account by services.accounts.active.collectAsState()
    val results = remember(query) { if (query.isBlank()) emptyList() else Settings.search(query) }
    val wide = LocalWindowSize.current == WindowSizeClass.EXPANDED
    // On a wide window the right pane is replaced by the next page; on a phone the next page is pushed.
    val go: (Route) -> Unit = { r -> if (wide && navigator.current != Route.Settings && navigator.current.isSettingsPane()) navigator.replace(r) else navigator.push(r) }
    SettingsScaffold("Settings", modifier, onBack = if (showBack) ({ navigator.back() }) else null, listTag = "settings.list") {
        item(key = "search") {
            TextField(
                query, { query = it }, singleLine = true, shape = CircleShape, placeholder = { Text("Search settings", maxLines = 1) },
                leadingIcon = { Icon(AppIcons.search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }, modifier = Modifier.testTag("settings.clear")) { Icon(AppIcons.close, "Clear") } },
                colors = TextFieldDefaults.colors(focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent),
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm).testTag("settings.search"),
            )
        }
        if (query.isNotBlank()) {
            if (results.isEmpty()) item(key = "none") { EmptyState("No settings match \"$query\".") }
            settingsSection("results", "", results, { it.key }) { s, shape ->
                val page = SettingsPages.pageOf(s)
                SegmentedListItem(
                    onClick = { page?.let { p -> go(if (p.link == SettingsLink.CUSTOMIZE) Route.CustomizePage(CustomizePages.pageFor(s)) else Route.SettingsPage(p.id, highlight = s.key)) } },
                    shapes = androidx.compose.material3.ListItemShapes(shape, shape, shape, shape, shape, shape), colors = segmentedItemColors(),
                    supportingContent = { Text(page?.title ?: "", maxLines = 1) },
                    modifier = Modifier.testTag("settings.result.${s.key}"),
                ) { Text(s.title, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            return@SettingsScaffold
        }
        item(key = "account") {
            val local = account.kind == com.greenaltambe.vela.auth.AccountKind.LOCAL
            SegmentedListItem(
                onClick = { go(Route.Accounts) },
                shapes = ListItemDefaults.segmentedShapes(0, 1),
                leadingContent = { com.greenaltambe.vela.ui.components.Avatar(if (local) "Local" else account.displayName, size = 44.dp, url = account.avatarUrl) },
                trailingContent = { Icon(AppIcons.chevronRight, null) },
                supportingContent = { Text(if (local) "Sign in to sync subscriptions and playlists" else account.maskedEmail ?: account.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = segmentedItemColors(), selected = selectedId == "accounts",
                modifier = Modifier.fillMaxWidth().testTag("settings.account"),
            ) { Text(if (local) "Local (not signed in)" else account.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        SettingsGroup.entries.forEach { group ->
            val pages = SettingsPages.all.filter { it.group == group }
            if (pages.isEmpty()) return@forEach
            settingsSection(group.name, group.title, pages, { it.id }) { page, _ ->
                val index = pages.indexOf(page)
                SegmentedListItem(
                    onClick = { go(page.route()) },
                    selected = page.id == selectedId,
                    shapes = ListItemDefaults.segmentedShapes(index, pages.size),
                    leadingContent = { IconTile(page) },
                    trailingContent = { Icon(AppIcons.chevronRight, null) },
                    supportingContent = { Text(remember(revision) { SettingsSummaries.of(page, services.settings) }, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("settings.summary.${page.id}")) },
                    colors = segmentedItemColors(),
                    modifier = Modifier.testTag("settings.page.${page.id}"),
                ) { Text(page.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** One line of a page: a setting, or the SponsorBlock categories folded into one expandable row. */
private sealed interface PageRow {
    val key: String
    class One(val setting: Setting<*>) : PageRow { override val key get() = setting.key }
    class Group(val kids: List<Setting<*>>) : PageRow { override val key get() = "group-segments" }
}

private fun rowsOf(settings: List<Setting<*>>, expanded: Boolean): List<PageRow> = buildList {
    val kids = settings.filter { it.key.startsWith("sponsorblock.action.") }
    var groupAdded = false
    settings.forEach { s ->
        if (s in kids) { if (!groupAdded) { add(PageRow.Group(kids)); groupAdded = true; if (expanded) kids.forEach { add(PageRow.One(it)) } } }
        else add(PageRow.One(s))
    }
}

/**
 * A page's own settings in titled sections (see [SettingsPages.sectionsOf]), a row to the screen it also leads to, and "Reset this page" in the
 * top bar's menu. Rows are composed lazily, so opening a page costs what is on screen, not what the page holds.
 */
@Composable
fun SettingsPageScreen(services: AppServices, navigator: Navigator, pageId: String, highlight: String?, modifier: Modifier = Modifier) {
    val page = SettingsPages.byId(pageId)
    val revision by services.settings.revision.collectAsState()
    val toasts = LocalUndoToasts.current
    if (page == null) {
        SettingsScaffold("Settings", modifier, onBack = { navigator.back() }, listTag = "settings.pageList") { item { EmptyState("This settings page does not exist.") } }
        return
    }
    val sections = remember(page) { SettingsPages.sectionsOf(page) }
    val all = remember(sections) { sections.flatMap { it.settings } }
    var segmentsOpen by remember(pageId) { mutableStateOf(highlight?.startsWith("sponsorblock.action.") == true) }
    val rowsBySection = sections.map { it to rowsOf(it.settings, segmentsOpen) }
    val state = rememberLazyListState()
    // A search jump scrolls to the setting it found.
    LaunchedEffect(highlight, pageId) {
        if (highlight == null) return@LaunchedEffect
        var index = if (page.link != SettingsLink.NONE) 1 else 0
        for ((sec, rows) in rowsBySection) {
            if (sec.title.isNotBlank()) index++
            val at = rows.indexOfFirst { it.key == highlight }
            if (at >= 0) { state.animateScrollToItem(index + at); return@LaunchedEffect }
            index += rows.size
        }
    }
    SettingsScaffold(
        page.title, modifier, onBack = { navigator.back() }, listTag = "settings.pageList", state = state,
        actions = {
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.testTag("settings.pageMenu")) { Icon(AppIcons.more, "More options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Reset this page") }, onClick = { menu = false; resetPage(services.settings, all, toasts) }, modifier = Modifier.testTag("settings.resetPage"))
                }
            }
        },
    ) {
        if (page.link != SettingsLink.NONE) item(key = "linked") {
            SegmentedListItem(
                onClick = { navigator.push(page.linkRoute()) }, shapes = ListItemDefaults.segmentedShapes(0, 1),
                leadingContent = { IconTile(page) }, trailingContent = { Icon(AppIcons.chevronRight, null) }, colors = segmentedItemColors(),
                modifier = Modifier.testTag("settings.openLinked"),
            ) { Text("Manage ${page.title.replaceFirstChar { it.lowercase() }}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        rowsBySection.forEach { (sec, rows) ->
            settingsSection("sec-${sec.title.ifBlank { "main" }}", sec.title, rows, { it.key }) { row, shape ->
                when (row) {
                    is PageRow.One -> SettingItem(services.settings, row.setting, revision, shape, highlighted = row.setting.key == highlight)
                    is PageRow.Group -> SegmentedListItem(
                        onClick = { segmentsOpen = !segmentsOpen }, shapes = androidx.compose.material3.ListItemShapes(shape, shape, shape, shape, shape, shape), colors = segmentedItemColors(),
                        supportingContent = { Text("Skip, mute or ask for each kind of segment", maxLines = 2) },
                        trailingContent = { Icon(if (segmentsOpen) AppIcons.chevronUp else AppIcons.chevronDown, null) },
                        modifier = Modifier.testTag("settings.group.segments"),
                    ) { Text("Segments by category") }
                }
            }
        }
    }
}

/** Puts every setting of a page back to its default in one change, with one Undo. */
internal fun resetPage(resolver: SettingsResolver, settings: List<Setting<*>>, toasts: UndoToasts) {
    val before = settings.associateWith { if (resolver.isOverridden(it)) resolver.rawValue(it) else null }
    resolver.batch { settings.forEach { resolver.resetToDefault(it) } }
    toasts.show("Page reset to defaults") { resolver.batch { before.forEach { (s, raw) -> if (raw == null) resolver.resetToDefault(s) else resolver.setRaw(s, raw) } } }
}

/** A "Reset this page" text button (Customize, until its pages move to the same menu). */
@Composable
internal fun ResetPageButton(resolver: SettingsResolver, settings: List<Setting<*>>, toasts: UndoToasts, tag: String) {
    androidx.compose.material3.TextButton(onClick = { resetPage(resolver, settings, toasts) }, modifier = Modifier.testTag(tag)) { Text("Reset this page", maxLines = 1) }
}
