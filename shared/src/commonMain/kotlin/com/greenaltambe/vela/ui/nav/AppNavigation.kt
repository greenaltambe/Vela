package com.greenaltambe.vela.ui.nav

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.greenaltambe.vela.core.settings.NavTab
import com.greenaltambe.vela.ui.components.NavEntry
import com.greenaltambe.vela.ui.components.icon
import com.greenaltambe.vela.ui.look.ComponentIds
import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.Spacing

/** The bottom bar on a narrow window: Material's navigation bar, the selected tab's icon filled. The system's bottom inset is its own. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppNavigationBar(
    entries: List<NavEntry>, selected: NavTab?, onSelect: (NavTab) -> Unit, modifier: Modifier = Modifier, showLabels: Boolean = true,
    /** See-through: the bar paints no background of its own; its parent frosts the area behind it (the mini player and the bar are one surface). */
    translucent: Boolean = false, animateIcons: Boolean = true,
) {
    val cs = androidx.compose.material3.MaterialTheme.colorScheme
    val container = if (translucent) androidx.compose.ui.graphics.Color.Transparent else androidx.compose.material3.ShortNavigationBarDefaults.containerColor
    // Over a moving picture the quiet colours of a solid bar are hard to read, and a selection pill in a theme's accent can vanish (Classic's accent is near black), so
    // there is no pill here: the chosen tab is told by its filled icon and bold name at full strength, the others are the same colour a little dimmer.
    val dim = cs.onSurface.copy(alpha = 0.74f)
    val itemColors = if (translucent) androidx.compose.material3.ShortNavigationBarItemDefaults.colors(
        selectedIconColor = cs.onSurface, selectedTextColor = cs.onSurface, selectedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        unselectedIconColor = dim, unselectedTextColor = dim,
    ) else androidx.compose.material3.ShortNavigationBarItemDefaults.colors()
    ShortNavigationBar(modifier.testTag(ComponentIds.BOTTOM_NAV), containerColor = container) {
        entries.forEach { e ->
            val isSelected = e.tab == selected
            ShortNavigationBarItem(
                selected = isSelected, onClick = { onSelect(e.tab) },
                // Without names the icon carries the tab's name for screen readers.
                icon = { AnimatedNavIcon(isSelected, animateIcons, if (showLabels) null else e.label) { on -> e.icon(on) } },
                label = if (showLabels) ({ Text(e.label, maxLines = 1, overflow = TextOverflow.Clip, fontWeight = if (translucent && isSelected) androidx.compose.ui.text.font.FontWeight.Bold else null, modifier = Modifier.testTag("nav.label")) }) else null,
                colors = itemColors,
                modifier = Modifier.testTag("nav.tab.${e.tab.name}"),
            )
        }
    }
}

/**
 * The navigation of a wide window: Material's wide rail, collapsed to icons or expanded with labels (the menu button toggles it; the choice is kept
 * in the settings). The system's side and top insets are its own.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppNavigationRail(entries: List<NavEntry>, selected: NavTab?, collapsed: Boolean, onToggleCollapsed: () -> Unit, onSelect: (NavTab) -> Unit, modifier: Modifier = Modifier, showLabels: Boolean = true, animateIcons: Boolean = true) {
    val state = rememberWideNavigationRailState(if (collapsed) WideNavigationRailValue.Collapsed else WideNavigationRailValue.Expanded)
    LaunchedEffect(collapsed) { if (collapsed) state.collapse() else state.expand() }
    val expanded = !collapsed
    WideNavigationRail(
        modifier.testTag(ComponentIds.SIDE_RAIL), state = state,
        header = { IconButton(onClick = onToggleCollapsed, modifier = Modifier.padding(start = Spacing.sm).testTag("rail.toggle")) { Icon(AppIcons.menu, if (collapsed) "Expand the navigation" else "Collapse the navigation") } },
    ) {
        entries.forEach { e ->
            val isSelected = e.tab == selected
            WideNavigationRailItem(
                selected = isSelected, onClick = { onSelect(e.tab) },
                icon = { AnimatedNavIcon(isSelected, animateIcons, if (showLabels || expanded) null else e.label) { on -> e.icon(on) } },
                // The expanded rail is the "with names" form: it keeps its names, as the desktop side bar does.
                label = if (showLabels || expanded) ({ Text(e.label, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.testTag("nav.label")) }) else null,
                railExpanded = expanded,
                modifier = Modifier.testTag("rail.tab.${e.tab.name}"),
            )
        }
    }
}

/** The room the shell keeps for the mini player between the page and the bar while a video is open; it reports where it is to [slot]. */
@Composable
fun MiniPlayerSlotSpacer(slot: MiniSlot, modifier: Modifier = Modifier, floating: Boolean = false) {
    // The floating window takes no room of its own (it hovers over the page); the strip still tells the sheet where the page area is.
    Spacer(
        modifier.fillMaxWidth().height(if (floating) 0.dp else MiniPlayerHeight).onGloballyPositioned { c -> val p = c.positionInWindow(); slot.left = p.x; slot.top = p.y; slot.width = c.size.width.toFloat() },
    )
}

/**
 * The desktop's side bar, laid out like a website's: expanded, one row per page (icon and name, 44 dp tall); folded, a narrow strip with the
 * name under each icon. The menu button that folds it is in the top bar. Evenly spaced, starting right under the top bar.
 */
@Composable
fun DesktopSideBar(entries: List<NavEntry>, selected: NavTab?, collapsed: Boolean, onSelect: (NavTab) -> Unit, modifier: Modifier = Modifier, showLabels: Boolean = true, animateIcons: Boolean = true) {
    val cs = androidx.compose.material3.MaterialTheme.colorScheme
    androidx.compose.foundation.layout.Column(
        modifier.testTag(ComponentIds.SIDE_RAIL).fillMaxHeight().width(if (collapsed) 76.dp else 232.dp).padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
    ) {
        entries.forEach { e ->
            val isSelected = e.tab == selected
            val tint = if (isSelected) cs.onSecondaryContainer else cs.onSurface
            val shape = androidx.compose.material3.MaterialTheme.shapes.medium
            val base = Modifier.fillMaxWidth().clip(shape).background(if (isSelected) cs.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                .selectable(isSelected, role = androidx.compose.ui.semantics.Role.Tab, onClick = { onSelect(e.tab) }).testTag("rail.tab.${e.tab.name}")
            if (collapsed) androidx.compose.foundation.layout.Column(base.padding(vertical = 10.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                AnimatedNavIcon(isSelected, animateIcons, if (showLabels) null else e.label, tint) { on -> e.icon(on) }
                if (showLabels) Text(e.label, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = tint, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.testTag("nav.label"))
            } else androidx.compose.foundation.layout.Row(base.height(44.dp).padding(horizontal = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp)) {
                AnimatedNavIcon(isSelected, animateIcons, null, tint) { on -> e.icon(on) }
                Text(e.label, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("nav.label"))
            }
        }
    }
}
