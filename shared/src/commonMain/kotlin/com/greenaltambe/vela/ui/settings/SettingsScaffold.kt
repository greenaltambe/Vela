@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.greenaltambe.vela.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.ui.components.VelaTopBar
import com.greenaltambe.vela.ui.theme.Spacing

/**
 * The frame of every settings screen: Material's large flexible top bar whose big title collapses as the list scrolls, then one centred column
 * (at most 720 dp) with the same side margins everywhere. [content] builds the lazy list; [listTag] names it for tests.
 */
@Composable
fun SettingsScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    listTag: String = "settings.list",
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Column(modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection)) {
        VelaTopBar(title, onBack = onBack, actions = actions, large = true, scrollBehavior = behavior)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 720.dp).fillMaxSize().testTag(listTag), state = state, contentPadding = Spacing.contentPadding,
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap), content = content,
            )
        }
    }
}

/** The shape of row [index] of [count] in a segmented stack: large corners at the ends of the stack, small ones where rows meet. */
@Composable
fun segmentedShape(index: Int, count: Int): Shape = ListItemDefaults.segmentedShapes(index, count).shape

/** The fill of a segmented row: a step above the page, so each row reads as its own rounded card. */
@Composable
fun segmentedItemColors() = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

/** A section: a small primary-coloured title, then [rows] as a segmented stack; [row] draws one row with the shape it was given. */
fun <T> LazyListScope.settingsSection(key: String, title: String, rows: List<T>, rowKey: (T) -> Any, row: @Composable (T, Shape) -> Unit) {
    if (title.isNotBlank()) item(key = "h-$key", contentType = "header") {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, modifier = Modifier.padding(start = Spacing.md, top = Spacing.md, bottom = Spacing.xs))
    }
    itemsIndexed(rows, key = { _, r -> "$key-${rowKey(r)}" }, contentType = { _, _ -> "row" }) { i, r -> row(r, segmentedShape(i, rows.size)) }
}
