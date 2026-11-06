package com.greenaltambe.vela.ui.nav

import androidx.compose.foundation.layout.Spacer
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
fun AppNavigationBar(entries: List<NavEntry>, selected: NavTab?, onSelect: (NavTab) -> Unit, modifier: Modifier = Modifier) {
    ShortNavigationBar(modifier.testTag(ComponentIds.BOTTOM_NAV)) {
        entries.forEach { e ->
            val isSelected = e.tab == selected
            ShortNavigationBarItem(
                selected = isSelected, onClick = { onSelect(e.tab) },
                icon = { Icon(e.icon(isSelected), null) },
                label = { Text(e.label, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.testTag("nav.label")) },
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
fun AppNavigationRail(entries: List<NavEntry>, selected: NavTab?, collapsed: Boolean, onToggleCollapsed: () -> Unit, onSelect: (NavTab) -> Unit, modifier: Modifier = Modifier) {
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
                icon = { Icon(e.icon(isSelected), null) },
                label = { Text(e.label, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.testTag("nav.label")) },
                railExpanded = expanded,
                modifier = Modifier.testTag("rail.tab.${e.tab.name}"),
            )
        }
    }
}

/** The room the shell keeps for the mini player between the page and the bar while a video is open; it reports where it is to [slot]. */
@Composable
fun MiniPlayerSlotSpacer(slot: MiniSlot, modifier: Modifier = Modifier) {
    Spacer(
        modifier.fillMaxWidth().height(MiniPlayerHeight).onGloballyPositioned { c -> val p = c.positionInWindow(); slot.left = p.x; slot.top = p.y; slot.width = c.size.width.toFloat() },
    )
}
