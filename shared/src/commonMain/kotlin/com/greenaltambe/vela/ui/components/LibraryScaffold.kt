@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.greenaltambe.vela.ui.components

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.greenaltambe.vela.ui.platform.PlatformBackHandler
import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.Spacing

/** One entry of a page's overflow menu. */
class PageAction(val label: String, val tag: String, val onClick: () -> Unit)

/**
 * The frame of a library list (History, Downloads, a playlist): a medium collapsing top bar with a back arrow, the title (two lines are fine), an
 * optional subtitle and an overflow menu; when rows are selected the bar becomes a "N selected" bar with [selectionActions] and Back clears the
 * selection. The list is centred and capped in width, so it looks the same on a phone and on a wide desktop window.
 */
@Composable
fun LibraryScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: () -> Unit,
    menu: List<PageAction> = emptyList(),
    barActions: @Composable RowScope.() -> Unit = {},
    selectionCount: Int = 0,
    onClearSelection: () -> Unit = {},
    selectionActions: @Composable RowScope.() -> Unit = {},
    listTag: String,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    PlatformBackHandler(enabled = selectionCount > 0, onBack = onClearSelection)
    Column(modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection)) {
        // One bar for both states, so choosing a row does not change the bar's height and nothing under it jumps: the title, the back button and the
        // actions cross-fade between "History" and "2 selected".
        val selecting = selectionCount > 0
        MediumFlexibleTopAppBar(
            title = {
                androidx.compose.animation.AnimatedContent(selecting, transitionSpec = { androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut() }, label = "barTitle") { sel ->
                    Text(if (sel) "$selectionCount selected" else title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag(if (sel) "library.selectionTitle" else "library.title"))
                }
            },
            // The subtitle line stays while selecting (a hint in its place), so the bar keeps its height and the list does not move.
            subtitle = if (selecting) ({ Text("Tap videos to select more", maxLines = 1) }) else subtitle?.let { sub -> { Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            modifier = Modifier.testTag(if (selecting) "library.selectionBar" else "topbar"),
            navigationIcon = {
                androidx.compose.animation.AnimatedContent(selecting, transitionSpec = { androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut() }, label = "barNav") { sel ->
                    if (sel) IconButton(onClick = onClearSelection, modifier = Modifier.testTag("library.selection.close")) { Icon(AppIcons.close, "Clear selection") }
                    else IconButton(onClick = onBack, modifier = Modifier.testTag("nav.back")) { Icon(AppIcons.back, "Back") }
                }
            },
            actions = {
                androidx.compose.animation.AnimatedContent(selecting, transitionSpec = { androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut() }, label = "barActions") { sel ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (sel) selectionActions() else {
                            barActions()
                            if (menu.isNotEmpty()) {
                                var open by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { open = true }, modifier = Modifier.testTag("library.menu")) { Icon(AppIcons.more, "More options") }
                                    DropdownMenu(open, { open = false }) {
                                        menu.forEach { a -> DropdownMenuItem(text = { Text(a.label, maxLines = 1) }, onClick = { open = false; a.onClick() }, modifier = Modifier.testTag(a.tag)) }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            scrollBehavior = behavior,
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 760.dp).fillMaxSize().testTag(listTag), state = state,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.sm, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs), content = content,
            )
        }
    }
}

/**
 * Removing rows with a way back: the rows are hidden at once and an Undo toast shows; the real delete runs after the toast is gone, unless Undo
 * was pressed. Replaces swipe-to-delete and instant deletes.
 */
class DeferredRemoval<K>(private val scope: kotlinx.coroutines.CoroutineScope, private val toasts: com.greenaltambe.vela.ui.look.UndoToasts) {
    val hidden = androidx.compose.runtime.mutableStateListOf<K>()

    fun remove(keys: List<K>, message: String, commit: () -> Unit) {
        hidden.addAll(keys)
        toasts.show(message) { hidden.removeAll(keys.toSet()) }
        scope.launch {
            kotlinx.coroutines.delay(5_600)
            if (keys.all { it in hidden }) commit()
            hidden.removeAll(keys.toSet())
        }
    }
}
