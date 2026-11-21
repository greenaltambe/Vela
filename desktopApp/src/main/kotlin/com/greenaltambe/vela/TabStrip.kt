@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.greenaltambe.vela

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.onClick
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.greenaltambe.vela.ui.nav.Route
import com.greenaltambe.vela.ui.routeTitle
import com.greenaltambe.vela.ui.theme.AppIcons

private val MinTab = 120.dp
private val MaxTab = 240.dp
private val NewTabButton = 40.dp

/** What a tab is called: the video's title, the search, or the page's name. */
fun tabTitle(route: Route): String = when (route) {
    is Route.Video -> route.video.title.ifBlank { "Video" }
    is Route.Search -> if (route.query.isBlank()) "Search" else "Search: ${route.query}"
    else -> routeTitle(route)
}

/**
 * The browser-style row of tabs at the top of a window: click to switch, the cross (or a middle click) closes, "+" opens a new one, and a tab can be dragged:
 * along the row to reorder, onto another Vela window's tab row to move there, or away from the row to open in a window of its own.
 */
@Composable
fun TabStrip(window: WindowModel, workspace: Workspace, onNoWindowsLeft: () -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    var draggedId by remember { mutableStateOf<Long?>(null) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(modifier.fillMaxWidth().height(window.stripHeight.dp).background(MaterialTheme.colorScheme.surfaceContainerLow).testTag("tabs.strip")) {
        val tabWidth: Dp = ((maxWidth - NewTabButton) / window.tabs.size.coerceAtLeast(1)).coerceIn(MinTab, MaxTab)
        window.tabWidthDp = tabWidth.value
        val scroll = rememberScrollState()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Row(Modifier.weight(1f, fill = false).horizontalScroll(scroll).padding(start = 6.dp, top = 6.dp)) {
                for (tab in window.tabs.toList()) androidx.compose.runtime.key(tab.id) {
                    val selected = tab === window.selected
                    val dragging = draggedId == tab.id
                    val title by remember(tab) { derivedStateOf { tabTitle(tab.navigator.current) } }
                    val widthPx = with(density) { tabWidth.toPx() }
                    Row(
                        Modifier.width(tabWidth).height(window.stripHeight.dp - 6.dp)
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer { if (dragging) { translationX = dragX; translationY = dragY.coerceIn(-8f, 80f) } }
                            .alpha(if (dragging && kotlin.math.abs(dragY) > 40f) 0.75f else 1f)
                            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                            .background(if (selected) MaterialTheme.colorScheme.background else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { window.select(tab) }
                            .onClick(matcher = PointerMatcher.mouse(PointerButton.Tertiary)) { if (workspace.closeTab(window, tab)) onNoWindowsLeft() }
                            .pointerInput(tab) {
                                detectDragGestures(
                                    onDragStart = { draggedId = tab.id; dragX = 0f; dragY = 0f; window.select(tab) },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        dragX += delta.x; dragY += delta.y
                                        val i = window.indexOf(tab)
                                        // Past half a tab's width: swap places with the neighbour (the tab keeps following the pointer).
                                        if (dragX > widthPx / 2 && i < window.tabs.lastIndex) { workspace.move(window, i, i + 1); dragX -= widthPx }
                                        else if (dragX < -widthPx / 2 && i > 0) { workspace.move(window, i, i - 1); dragX += widthPx }
                                    },
                                    onDragEnd = { finishDrag(window, workspace, tab, dragY); draggedId = null; dragX = 0f; dragY = 0f },
                                    onDragCancel = { draggedId = null; dragX = 0f; dragY = 0f },
                                )
                            }
                            .testTag("tab.${tab.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(title, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = { if (workspace.closeTab(window, tab)) onNoWindowsLeft() }, modifier = Modifier.size(28.dp).padding(end = 4.dp).testTag("tab.close")) {
                            Icon(AppIcons.close, "Close tab", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            IconButton(onClick = { workspace.newTab(window) }, modifier = Modifier.size(NewTabButton).testTag("tabs.new")) { Icon(AppIcons.add, "New tab (Ctrl+T)") }
        }
    }
}

/** A tab was let go: over another window's tab row it moves there; far from its own row it becomes a window; otherwise it stays (already reordered). */
private fun finishDrag(window: WindowModel, workspace: Workspace, tab: TabModel, dragY: Float) {
    val p = runCatching { java.awt.MouseInfo.getPointerInfo()?.location }.getOrNull() ?: return
    val target = workspace.windows.firstOrNull { w -> w !== window && w.stripOnScreen()?.let { r -> p.x in r.x..(r.x + r.width) && p.y in (r.y - 10)..(r.y + r.height + 10) } == true }
    if (target != null) {
        val r = target.stripOnScreen()!!
        val slot = (((p.x - r.x) / target.tabWidthDp).toInt()).coerceIn(0, target.tabs.size)
        workspace.moveTab(tab, window, target, slot)
        return
    }
    val own = window.stripOnScreen()
    val away = own == null || p.y < own.y - 20 || p.y > own.y + own.height + 40 || p.x < own.x - 40 || p.x > own.x + own.width + 40
    if (away && window.tabs.size > 1) workspace.tearOff(tab, window, p.x - 90, p.y - 20)
}
