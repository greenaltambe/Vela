package com.greenaltambe.vela.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.greenaltambe.vela.extractor.Chapter
import com.greenaltambe.vela.library.formatTimestamp
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.toColor

/**
 * The chapter list as a bottom sheet in the app's look: rounded top (the corner-radius setting), the Look's raised surface, one row per
 * chapter with its start time, the current one marked with the palette gradient. Tapping a chapter seeks there and closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersSheet(chapters: List<Chapter>, positionMs: Long, keepClearTopPx: Float = 0f, onSeek: (Long) -> Unit, onDismiss: () -> Unit) {
    val current = com.greenaltambe.vela.library.currentChapter(chapters, positionMs)
    // Portrait: the sheet may only use the space below the video. (The drag handle takes about 36 dp.)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val windowPx = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height
    val maxContent = if (keepClearTopPx <= 0f) androidx.compose.ui.unit.Dp.Unspecified else with(density) { ((windowPx - keepClearTopPx).toDp() - 36.dp).coerceAtLeast(120.dp) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = Vela.look.radii.largeDp.dp, topEnd = Vela.look.radii.largeDp.dp),
        containerColor = Vela.colors.surface.toColor(),
        contentColor = Vela.colors.onSurface.toColor(),
        modifier = Modifier.testTag("chapters.sheet"),
    ) {
        Text("Chapters", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = Vela.gutter, vertical = Vela.xs))
        LazyColumn(Modifier.fillMaxWidth().then(if (maxContent.isSpecified) Modifier.heightIn(max = maxContent - 40.dp) else Modifier), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Vela.sm, vertical = Vela.xs), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
            items(chapters, key = { "sheet-${it.startSeconds}" }) { c ->
                val isCurrent = c == current
                Row(
                    Modifier.fillMaxWidth().clip(Vela.mediumShape).then(if (isCurrent) Modifier.background(Vela.colors.surfaceVariant.toColor()) else Modifier)
                        .clickable { onSeek(c.startSeconds * 1000); onDismiss() }.heightIn(min = Vela.minTouch).padding(horizontal = Vela.md).testTag("chapters.row"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.md),
                ) {
                    Box(Modifier.width(3.dp).heightIn(min = 20.dp).clip(Vela.pillShape).then(if (isCurrent) Modifier.background(Vela.wake) else Modifier))
                    Text(formatTimestamp(c.startSeconds), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, color = Vela.colors.onSurfaceVariant.toColor())
                    Text(c.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
