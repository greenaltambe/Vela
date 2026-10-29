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
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route

/** Which Customize page shows which look setting, and which pages exist. The ones marked [stubs] arrive in the next update. */
object CustomizePages {
    val stubs = mapOf(
        "looks" to "Looks", "home" to "Home & Subscriptions", "feeds" to "Feeds & cards", "video" to "Video page & Player",
    )
    val titles = mapOf("color" to "Colour palette", "text" to "Text", "icons" to "Icons & shapes", "glass" to "Glass & backgrounds", "tabs" to "Tabs & mini player") + stubs

    fun pageFor(s: Setting<*>): String = when {
        s.key == "look.palette" -> "color"
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

/** Three small previews built from the REAL components at about 40 % scale; they update the instant a setting changes. */
@Composable
fun PreviewStrip(services: AppServices, modifier: Modifier = Modifier) {
    val revision by services.settings.revision.collectAsState()
    val nav = remember(revision) { com.greenaltambe.vela.ui.nav.NavConfig.of(services.settings) }
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("customize.preview"), horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
        MiniScreen("Home") {
            val scroll = remember { com.greenaltambe.vela.ui.nav.ScrollChrome() }
            Box(Modifier.fillMaxSize().background(Vela.colors.background.toColor())) {
                Column(Modifier.padding(Vela.md), verticalArrangement = Arrangement.spacedBy(Vela.md)) {
                    Text("Vela", style = MaterialTheme.typography.titleLarge)
                    VideoCard(CustomizePages.previewVideo, {}, style = VideoCardStyle.VERTICAL)
                    VideoCard(CustomizePages.previewVideo.copy(title = "Another video"), {}, style = VideoCardStyle.HORIZONTAL)
                }
                // The real bar and mini player, so the bar style, labels, mini look, position and distance show as they are chosen.
                com.greenaltambe.vela.ui.nav.BottomChrome(nav, navEntries(nav.tabs), nav.tabs.first(), scroll, CustomizePages.sampleMini, {}, Modifier.align(Alignment.BottomCenter))
            }
        }
        MiniScreen("Video page") {
            Column(Modifier.fillMaxSize().background(Vela.colors.background.toColor())) {
                Box(Modifier.fillMaxWidth().height(200.dp).background(Vela.colors.mediaBackground.toColor()), contentAlignment = Alignment.Center) { androidx.compose.material3.Icon(Vela.icons.play, null, tint = Vela.colors.onMedia.toColor()) }
                Column(Modifier.padding(Vela.md), verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
                    Text(CustomizePages.previewVideo.title, style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(Vela.sm)) { ActionChip(Vela.icons.like, "Like", {}, selected = true); ActionChip(Vela.icons.save, "Save", {}); ActionChip(Vela.icons.share, "Share", {}) }
                }
            }
        }
        MiniScreen("Player") {
            Column(Modifier.fillMaxSize().background(Vela.colors.background.toColor()), verticalArrangement = Arrangement.Bottom) {
                Box(Modifier.fillMaxWidth().weight(1f).background(Vela.colors.mediaBackground.toColor()))
                MiniPlayer(CustomizePages.sampleMini, MiniLook.CARD, Vela.largeShape)
            }
        }
    }
}

@Composable
private fun MiniScreen(label: String, content: @Composable () -> Unit) {
    val scale = 0.4f
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
        // The same components, not pictures of them: laid out at 360 x 640 dp, then drawn at 40 % from the top-left corner.
        Box(
            Modifier.clip(Vela.largeShape).background(Vela.colors.surface.toColor()).testTag("customize.preview.$label")
                .layout { measurable, _ ->
                    val full = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(360.dp.roundToPx(), 640.dp.roundToPx()))
                    layout((full.width * scale).toInt(), (full.height * scale).toInt()) { full.placeWithLayer(0, 0) { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f) } }
                }.clearAndSetSemantics { },
        ) { content() }
        Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

/** The Customize hub: the live preview, Theme + Pure black, and one row per part of the look. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomizeScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    val revision by services.settings.revision.collectAsState()
    val toasts = LocalUndoToasts.current
    CustomizeFrame("Customize Vela", navigator, modifier, "customize.list") {
        Column(verticalArrangement = Arrangement.spacedBy(Vela.md)) {
            PreviewStrip(services)
            SettingRow(services.settings, LookSettings.theme, revision)
            SettingRow(services.settings, Settings.look, revision)
            listOf("color", "text", "icons", "glass", "tabs").forEach { id -> CustomizeRow(id, navigator) }
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
            PreviewStrip(services)
            if (page in CustomizePages.stubs) {
                Text("This part arrives in the next update. Everything it will hold is already stored the same way as the other settings, so nothing you set now is lost.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("customize.stub"))
                return@Column
            }
            when (page) {
                "color" -> PaletteGrid(s.resolve(LookSettings.palette)) { p -> s.setWithUndo(LookSettings.palette, p.name, toasts) { LookSettings.palette.choices.first { c -> c.raw == it }.label } }
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
                VelaSwitch(enabled, { checked -> save(if (checked) on + tab else on - tab) }, Modifier.testTag("tabs.toggle.${tab.name}"))
                Text(navEntries(listOf(tab)).first().label, Modifier.weight(1f), maxLines = 1)
                if (enabled) {
                    val i = on.indexOf(tab)
                    TextButton(onClick = { save(on.toMutableList().also { it.add(i - 1, it.removeAt(i)) }) }, enabled = i > 0, modifier = Modifier.testTag("tabs.up.${tab.name}")) { androidx.compose.material3.Icon(com.greenaltambe.vela.ui.look.Vela.icons.arrowUp, "Move up") }
                    TextButton(onClick = { save(on.toMutableList().also { it.add(i + 1, it.removeAt(i)) }) }, enabled = i < on.lastIndex, modifier = Modifier.testTag("tabs.down.${tab.name}")) { androidx.compose.material3.Icon(com.greenaltambe.vela.ui.look.Vela.icons.arrowDown, "Move down") }
                }
            }
        }
    }
}
