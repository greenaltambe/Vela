package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.core.settings.*
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.ui.components.*
import com.greenaltambe.vela.ui.fontFamilyFor
import androidx.compose.foundation.layout.width
import com.greenaltambe.vela.ui.look.*
import com.greenaltambe.vela.ui.components.drawProgress
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route

/** Which Customize page shows which look setting, and which pages exist. The ones marked [stubs] arrive in the next update. */
object CustomizePages {
    val stubs = mapOf(
        "looks" to "Looks", "home" to "Home & Subscriptions", "feeds" to "Feeds & cards", "video" to "Video page & Player",
    )
    val titles = mapOf("color" to "Colour palette", "progress" to "Progress bar", "text" to "Text", "icons" to "Icons & shapes", "glass" to "Glass & backgrounds", "tabs" to "Tabs & mini player") + stubs

    fun pageFor(s: Setting<*>): String = when {
        s.key == "look.palette" -> "color"
        s.key == "look.progressStyle" -> "progress"
        s.key in setOf("look.font", "look.titleWeight", "look.textScale", "look.density") -> "text"
        s.key in setOf("look.glass", "look.reduceMotion", "appearance.performanceMode") -> "glass"
        s.key.startsWith("look.nav.") || s.key.startsWith("look.mini.") -> "tabs"
        s.key in setOf("look.theme", "appearance.look") -> "hub"
        else -> "icons"
    }

    fun settingsOn(page: String): List<Setting<*>> = LookSettings.all.filter { pageFor(it) == page }

    private val sample = VideoSummary("preview", "A video title long enough to wrap onto two lines", "c", "A channel", null, 754, 1_200_000)

    /** All the look settings are shown by one of the pages or the hub: used by a test. */
    fun covered(): Set<String> = (titles.keys - stubs.keys).flatMap { settingsOn(it) }.map { it.key }.toSet() + setOf("look.theme")
    val previewVideo get() = sample
    val sampleMini = MiniState(sample.title, null, true, 0.4f, {}, {}, {})
}


@Composable
private fun CustomizeFrame(title: String, navigator: Navigator, modifier: Modifier, tag: String, content: @Composable (LazyColumnScopeAlias) -> Unit) {
    Column(modifier.fillMaxSize()) {
        VelaTopBar(title, onBack = { navigator.back() })
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize().testTag(tag), contentPadding = Vela.contentPadding, verticalArrangement = Arrangement.spacedBy(Vela.sm)) { item { content(Unit) } }
        }
    }
}
private typealias LazyColumnScopeAlias = Unit

/** The Customize hub: Theme + Pure black, and one row per part of the look. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomizeScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    val revision by services.settings.revision.collectAsState()
    val toasts = LocalUndoToasts.current
    CustomizeFrame("Customize Vela", navigator, modifier, "customize.list") {
        Column(verticalArrangement = Arrangement.spacedBy(Vela.md)) {
            SettingRow(services.settings, LookSettings.theme, revision)
            SettingRow(services.settings, Settings.look, revision)
            listOf("color", "progress", "text", "icons", "glass", "tabs").forEach { id -> CustomizeRow(id, navigator) }
            val all = LookSettings.all + Settings.look
            ResetPageButton(services.settings, all, toasts, "customize.reset")
        }
    }
}

@Composable
private fun CustomizeRow(id: String, navigator: Navigator) {
    val stub = id in CustomizePages.stubs
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(Vela.largeShape).background(Vela.colors.surface.toColor()).pressable { navigator.push(Route.CustomizePage(id)) }.padding(horizontal = Vela.md).testTag("customize.row.$id"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(CustomizePages.titles.getValue(id), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (stub) Text("Next update", style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor())
        }
        androidx.compose.material3.Icon(com.greenaltambe.vela.ui.look.Vela.icons.chevronRight, null, tint = com.greenaltambe.vela.ui.look.Vela.colors.onSurfaceVariant.toColor())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomizePageScreen(services: AppServices, navigator: Navigator, page: String, modifier: Modifier = Modifier) {
    val revision by services.settings.revision.collectAsState()
    val toasts = LocalUndoToasts.current
    val s = services.settings
    CustomizeFrame(CustomizePages.titles[page] ?: "Customize", navigator, modifier, "customize.page.$page") {
        Column(verticalArrangement = Arrangement.spacedBy(Vela.md)) {
            if (page in CustomizePages.stubs) {
                Text("This part arrives in the next update. Everything it will hold is already stored the same way as the other settings, so nothing you set now is lost.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("customize.stub"))
                return@Column
            }
            when (page) {
                "color" -> PaletteGrid(s.resolve(LookSettings.palette)) { p -> s.setWithUndo(LookSettings.palette, p.name, toasts) { LookSettings.palette.choices.first { c -> c.raw == it }.label } }
                "progress" -> ProgressGrid(s.resolve(LookSettings.progressStyle)) { st -> s.setWithUndo(LookSettings.progressStyle, st.name, toasts) { LookSettings.progressStyle.choices.first { c -> c.raw == it }.label } }
                "text" -> {
                    Text("Font", style = MaterialTheme.typography.bodyLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Vela.sm), verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
                        FontChoice.entries.forEach { f -> FontCard(f, s.resolve(LookSettings.font) == f) { s.setWithUndo(LookSettings.font, f.name, toasts) { LookSettings.font.choices.first { c -> c.raw == it }.label } } }
                    }
                    CustomizePages.settingsOn("text").filter { it.key != "look.font" }.forEach { SettingRow(s, it, revision) }
                }
                "icons" -> {
                    Text("Icon pack", style = MaterialTheme.typography.bodyLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Vela.sm), verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
                        IconStyle.entries.forEach { st -> IconPackCard(st, s.resolve(LookSettings.iconStyle) == st, s.resolve(LookSettings.iconWeight), s.resolve(LookSettings.iconRounded)) { s.setWithUndo(LookSettings.iconStyle, st.name, toasts) { LookSettings.iconStyle.choices.first { c -> c.raw == it }.label } } }
                    }
                    CustomizePages.settingsOn("icons").filter { it.key != "look.iconStyle" }.forEach { SettingRow(s, it, revision) }
                }
                "glass" -> {
                    CustomizePages.settingsOn("glass").forEach { SettingRow(s, it, revision) }
                    Text("Performance mode switches glass off and motion to the minimum whatever is set here.", style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor())
                }
                "tabs" -> {
                    TabsChecklist(s, toasts)
                    CustomizePages.settingsOn("tabs").filter { it.key != "look.nav.tabs" }.forEach { SettingRow(s, it, revision) }
                    Text("Swipe to skip, bar height and the progress style come in the next update.", style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor())
                }
            }
            ResetPageButton(s, CustomizePages.settingsOn(page), toasts, "customize.resetPage")
        }
    }
}

/** The seek-bar styles as cards, each with a live preview drawn by the real scrubber's own drawing code, 40 % played. */
@Composable
private fun ProgressGrid(current: ProgressStyle, onPick: (ProgressStyle) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Vela.sm), modifier = Modifier.testTag("progress.styles")) {
        ProgressStyle.entries.forEach { st ->
            val label = LookSettings.progressStyle.choices.first { it.raw == st.name }.label
            val selected = st == current
            val border = androidx.compose.ui.graphics.Brush.horizontalGradient(Vela.look.accent.wake.map { it.toColor() })
            Column(
                Modifier.fillMaxWidth().clip(Vela.mediumShape).background(Vela.colors.surface.toColor())
                    .then(if (selected) Modifier.border(2.dp, border, Vela.mediumShape) else Modifier)
                    .semantics { contentDescription = label; this.selected = selected }.pressable(role = androidx.compose.ui.semantics.Role.RadioButton, onClick = { onPick(st) }).padding(Vela.md).testTag("progress.${st.name}"),
                verticalArrangement = Arrangement.spacedBy(Vela.xs),
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                ProgressPreview(st)
            }
        }
    }
}

@Composable
private fun ProgressPreview(style: ProgressStyle) {
    val wake = Vela.look.accent.wake.map { it.toColor() }
    val surface = Vela.colors.surface.toColor()
    val track = Vela.colors.outline.toColor()
    val phase = com.greenaltambe.vela.ui.components.rememberWavePhase(style == ProgressStyle.WAVE || style == ProgressStyle.RIBBON)
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(40.dp)) {
        drawProgress(style, 0.4f, wake, surface, track, track, track, wavePhase = phase)
    }
}

/** The palettes as a grid of cards: the gradient, its name, a ring on the chosen one. One tap applies it everywhere. */
@Composable
private fun PaletteGrid(current: Palette, onPick: (Palette) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
        Palette.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
                row.forEach { p -> PaletteCard(p, p == current, Modifier.weight(1f)) { onPick(p) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PaletteCard(p: Palette, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val brush = androidx.compose.ui.graphics.Brush.horizontalGradient(Palettes.stops(p).map { it.toColor() })
    val label = LookSettings.palette.choices.first { it.raw == p.name }.label
    Column(
        modifier.clip(Vela.mediumShape).background(Vela.colors.surface.toColor())
            .then(if (selected) Modifier.border(2.dp, brush, Vela.mediumShape) else Modifier)
            .semantics { contentDescription = label; this.selected = selected }.pressable(role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick).padding(Vela.sm).testTag("palette.${p.name}"),
        verticalArrangement = Arrangement.spacedBy(Vela.xs),
    ) {
        Box(Modifier.fillMaxWidth().height(40.dp).clip(Vela.smallShape).background(brush))
        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

@Composable
private fun FontCard(f: FontChoice, selected: Boolean, onClick: () -> Unit) {
    val label = LookSettings.font.choices.first { it.raw == f.name }.label
    Column(
        Modifier.size(96.dp, 88.dp).clip(Vela.mediumShape).background(Vela.colors.surface.toColor()).then(if (selected) Modifier.border(2.dp, androidx.compose.ui.graphics.Brush.horizontalGradient(Vela.look.accent.wake.map { it.toColor() }), Vela.mediumShape) else Modifier).pressable(onClick = onClick).testTag("font.${f.name}"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text("Aa", style = MaterialTheme.typography.titleLarge.copy(fontFamily = fontFamilyFor(f.name.lowercase())))
        Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

/** One icon pack as a card: its name and four of its real icons, drawn with the weight and end style currently chosen. */
@Composable
private fun IconPackCard(style: IconStyle, selected: Boolean, weight: IconWeight, rounded: Boolean, onClick: () -> Unit) {
    val set = VelaIcons.forTokens(IconTokens(style, weight, rounded))
    val label = LookSettings.iconStyle.choices.first { it.raw == style.name }.label
    Column(
        Modifier.width(152.dp).clip(Vela.mediumShape).background(Vela.colors.surface.toColor()).then(if (selected) Modifier.border(2.dp, androidx.compose.ui.graphics.Brush.horizontalGradient(Vela.look.accent.wake.map { it.toColor() }), Vela.mediumShape) else Modifier)
            .pressable(onClick = onClick).padding(vertical = Vela.sm).testTag("iconpack.${style.name}"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Vela.xs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Vela.sm)) { listOf(set.home, set.search, set.play, set.download).forEach { androidx.compose.material3.Icon(it, null, tint = Vela.colors.onSurface.toColor()) } }
        Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

/** The tab list as a checklist with order arrows. Fewer than two tabs is refused with a message instead of being saved. */
@Composable
private fun TabsChecklist(s: SettingsResolver, toasts: UndoToasts) {
    val revision by s.revision.collectAsState()
    val on = remember(revision) { NavTabs.parse(s.resolve(LookSettings.navTabs)) }
    val ordered = on + NavTab.entries.filter { it !in on }
    fun save(tabs: List<NavTab>) {
        if (tabs.size < 2) { toasts.show("Keep at least two tabs"); return }
        s.setWithUndo(LookSettings.navTabs, NavTabs.encode(tabs.take(NavTabs.MAX)), toasts) { r -> "${NavTabs.parse(r).size} tabs" }
    }
    Column(Modifier.testTag("tabs.checklist"), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
        Text("Tabs in the bar", style = MaterialTheme.typography.bodyLarge)
        ordered.forEach { tab ->
            val enabled = tab in on
            Row(Modifier.fillMaxWidth().heightIn(min = Vela.minTouch).testTag("tabs.row.${tab.name}"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
                // Shorts cannot be a tab while Shorts are hidden in Settings.
                val unavailable = tab == NavTab.SHORTS && !s.shortsEnabled()
                VelaSwitch(enabled && !unavailable, { checked -> if (!unavailable) save(if (checked) on + tab else on - tab) }, Modifier.testTag("tabs.toggle.${tab.name}"), enabled = !unavailable)
                Column(Modifier.weight(1f)) {
                    Text(navEntries(listOf(tab)).first().label, maxLines = 1)
                    if (unavailable) Text("Hidden in Settings > Shorts", style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor(), maxLines = 1)
                }
                if (enabled) {
                    val i = on.indexOf(tab)
                    TextButton(onClick = { save(on.toMutableList().also { it.add(i - 1, it.removeAt(i)) }) }, enabled = i > 0, modifier = Modifier.testTag("tabs.up.${tab.name}")) { androidx.compose.material3.Icon(com.greenaltambe.vela.ui.look.Vela.icons.arrowUp, "Move up") }
                    TextButton(onClick = { save(on.toMutableList().also { it.add(i + 1, it.removeAt(i)) }) }, enabled = i < on.lastIndex, modifier = Modifier.testTag("tabs.down.${tab.name}")) { androidx.compose.material3.Icon(com.greenaltambe.vela.ui.look.Vela.icons.arrowDown, "Move down") }
                }
            }
        }
    }
}
