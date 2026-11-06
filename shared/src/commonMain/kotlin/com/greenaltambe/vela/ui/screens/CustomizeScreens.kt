@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.selection.toggleable
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.core.settings.*
import com.greenaltambe.vela.ui.components.drawProgress
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
import com.greenaltambe.vela.ui.theme.Accent
import com.greenaltambe.vela.ui.theme.Spacing

/** Which Customize page shows which setting. The hub shows the theme and the rail; the progress bar and Home have their own pages. */
object CustomizePages {
    val titles = mapOf("progress" to "Progress bar", "home" to "Home")

    fun pageFor(s: Setting<*>): String = when {
        s.key == "look.progressStyle" -> "progress"
        s.key.startsWith("look.home.") -> "home"
        else -> "hub"
    }

    fun settingsOn(page: String): List<Setting<*>> = LookSettings.all.filter { pageFor(it) == page }

    /** Every look setting has a visible row on the hub or on one of the pages: used by a test. */
    fun covered(): Set<String> = titles.keys.flatMap { settingsOn(it) }.map { it.key }.toSet() + setOf("look.theme", "look.nav.railCollapsed")
}

/** The top bar's menu of a Customize screen: "Reset this page" for the settings [settings] on it, with one Undo. */
@Composable
private fun CustomizeMenu(resolver: SettingsResolver, settings: List<Setting<*>>, toasts: UndoToasts, resetTag: String) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }, modifier = Modifier.testTag("customize.menu")) { Icon(AppIcons.more, "More options") }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text("Reset this page") }, onClick = { menu = false; resetPage(resolver, settings, toasts) }, modifier = Modifier.testTag(resetTag))
        }
    }
}

/** The Customize hub: the theme, one row per page (each with a live summary), and the rail. */
@Composable
fun CustomizeScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    val revision by services.settings.revision.collectAsState()
    val toasts = LocalUndoToasts.current
    val s = services.settings
    SettingsScaffold(
        "Customize Vela", modifier, onBack = { navigator.back() }, listTag = "customize.list",
        actions = { CustomizeMenu(s, LookSettings.all, toasts, "customize.reset") },
    ) {
        settingsSection("look", "Look", listOf(LookSettings.theme), { it.key }) { st, shape -> SettingItem(s, st, revision, shape) }
        val pages = CustomizePages.titles.keys.toList()
        settingsSection("pages", "Make it yours", pages, { it }) { id, _ ->
            val index = pages.indexOf(id)
            SegmentedListItem(
                onClick = { navigator.push(Route.CustomizePage(id)) }, shapes = ListItemDefaults.segmentedShapes(index, pages.size),
                trailingContent = { Icon(AppIcons.chevronRight, null) }, colors = segmentedItemColors(),
                supportingContent = { Text(remember(revision) { customizeSummary(id, s) }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.fillMaxWidth().testTag("customize.row.$id"),
            ) { Text(CustomizePages.titles.getValue(id), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        settingsSection("layout", "Layout", listOf(LookSettings.railCollapsed), { it.key }) { st, shape -> SettingItem(s, st, revision, shape) }
    }
}

private fun customizeSummary(id: String, s: SettingsResolver): String = when (id) {
    "progress" -> LookSettings.progressStyle.choices.first { it.raw == s.rawValue(LookSettings.progressStyle) }.label
    else -> "${HomeShelves.parse(s.resolve(LookSettings.homeShelves)).size} shelves · ${LookSettings.homeDensity.choices.first { it.raw == s.rawValue(LookSettings.homeDensity) }.label.substringBefore(" (")}"
}

@Composable
fun CustomizePageScreen(services: AppServices, navigator: Navigator, page: String, modifier: Modifier = Modifier) {
    val revision by services.settings.revision.collectAsState()
    val toasts = LocalUndoToasts.current
    val s = services.settings
    SettingsScaffold(
        CustomizePages.titles[page] ?: "Customize", modifier, onBack = { navigator.back() }, listTag = "customize.page.$page",
        actions = { CustomizeMenu(s, CustomizePages.settingsOn(page), toasts, "customize.resetPage") },
    ) {
        when (page) {
            "progress" -> progressItems(s, toasts)
            "home" -> homeItems(services, revision, toasts)
        }
    }
}

/** The seek-bar styles as a radio list: the chosen one animates its preview (40 % played, drawn by the scrubber's own code), the others are still. */
private fun LazyListScope.progressItems(s: SettingsResolver, toasts: UndoToasts) {
    item(key = "progress-intro") { Text("How the player's seek bar looks. The change shows in every video.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) }
    val styles = ProgressStyle.entries
    settingsSection("progress", "", styles, { it.name }) { st, _ ->
        val current = s.resolve(LookSettings.progressStyle)
        val label = LookSettings.progressStyle.choices.first { it.raw == st.name }.label
        SegmentedListItem(
            selected = st == current, onClick = { s.setWithUndo(LookSettings.progressStyle, st.name, toasts) { LookSettings.progressStyle.choices.first { c -> c.raw == it }.label } },
            shapes = ListItemDefaults.segmentedShapes(styles.indexOf(st), styles.size), colors = segmentedItemColors(),
            leadingContent = { RadioButton(selected = st == current, onClick = null) },
            supportingContent = { ProgressPreview(st, animate = st == current) },
            modifier = Modifier.fillMaxWidth().semantics { role = Role.RadioButton }.testTag("progress.${st.name}"),
        ) { Text(label, maxLines = 1) }
    }
}

@Composable
private fun ProgressPreview(style: ProgressStyle, animate: Boolean) {
    val wake = Accent.colors
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val track = MaterialTheme.colorScheme.outline
    val phase = com.greenaltambe.vela.ui.components.rememberWavePhase(animate && (style == ProgressStyle.WAVE || style == ProgressStyle.RIBBON || style == ProgressStyle.EXPRESSIVE))
    Canvas(Modifier.fillMaxWidth().height(40.dp)) { drawProgress(style, 0.4f, wake, surface, track, track, track, wavePhase = phase.value) }
}

/** Home: the shelves (switch each and move it up or down), then density and chips, then the way back for hidden videos and channels. */
private fun LazyListScope.homeItems(services: AppServices, revision: Int, toasts: UndoToasts) {
    val s = services.settings
    val on = HomeShelves.parse(s.resolve(LookSettings.homeShelves))
    val ordered = HomeShelves.ordered(on)
    fun save(shelves: List<HomeShelf>) = s.setWithUndo(LookSettings.homeShelves, HomeShelves.encode(shelves), toasts) { r -> "${HomeShelves.parse(r).size} shelves" }
    settingsSection("shelves", "Shelves on Home", ordered, { it.name }) { shelf, shape ->
        val enabled = shelf in on
        val i = on.indexOf(shelf)
        Box(Modifier.fillMaxWidth().clip(shape).testTag("home.shelfRow.${shelf.name}")) {
            ListItem(
                headlineContent = { Text(shelf.label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(shelf.hint, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                trailingContent = {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        // Always laid out (invisible while the shelf is off), so switching a shelf never moves the others.
                        IconButton(onClick = { save(on.toMutableList().also { it.add(i - 1, it.removeAt(i)) }) }, enabled = enabled && i > 0, modifier = Modifier.alpha(if (enabled) 1f else 0f).testTag("home.shelfUp.${shelf.name}")) { Icon(AppIcons.arrowUp, "Move up") }
                        IconButton(onClick = { save(on.toMutableList().also { it.add(i + 1, it.removeAt(i)) }) }, enabled = enabled && i < on.lastIndex, modifier = Modifier.alpha(if (enabled) 1f else 0f).testTag("home.shelfDown.${shelf.name}")) { Icon(AppIcons.arrowDown, "Move down") }
                        Switch(enabled, { checked -> save(if (checked) on + shelf else on - shelf) }, Modifier.testTag("home.shelfToggle.${shelf.name}"))
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        }
    }
    val feed = CustomizePages.settingsOn("home").filter { it.key != "look.home.shelves" }
    settingsSection("feed", "Feed", feed, { it.key }) { st, shape -> SettingItem(s, st, revision, shape) }
    settingsSection("hidden", "Hidden", listOf("hidden"), { it }) { _, shape ->
        SegmentedListItem(
            onClick = { services.homePrefs.clearDismissed(); toasts.show("Home shows everything again") },
            shapes = androidx.compose.material3.ListItemShapes(shape, shape, shape, shape, shape, shape), colors = segmentedItemColors(),
            leadingContent = { Icon(AppIcons.refresh, null) },
            supportingContent = { Text("Videos and channels you sent away with Not interested or Hide this channel come back.", maxLines = 3, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.fillMaxWidth().testTag("customize.home.clearDismissed"),
        ) { Text("Bring back hidden videos and channels", maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}
