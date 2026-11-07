@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.greenaltambe.vela.ui.player

import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.library.chapterProgress
import com.greenaltambe.vela.library.chapterEndSeconds
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.shape.CircleShape
import com.greenaltambe.vela.ui.theme.ExtraColors
import com.greenaltambe.vela.ui.theme.Spacing
import com.greenaltambe.vela.ui.theme.Accent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.greenaltambe.vela.extractor.Chapter
import com.greenaltambe.vela.library.currentChapter
import com.greenaltambe.vela.library.formatTimestamp
import kotlinx.coroutines.launch

/**
 * The chapter list as an in-page bottom panel matching [CommentsPanel]: rounded top, raised surface,
 * top drag-to-dismiss handle with close button, video frame/thumbnail previews with timestamp badges,
 * and active chapter indicators. It sits in the page under the video so playback is never obscured.
 */
@Composable
fun ChaptersPanel(
    chapters: List<Chapter>,
    positionMs: Long,
    videoThumbnailUrl: String? = null,
    /** The video's length, so the last chapter has an end (0 when unknown). */
    durationMs: Long = 0L,
    /** The exact playhead, read only while drawing the current chapter's bar (positionMs may be rounded). */
    livePositionMs: () -> Long = { positionMs },
    playing: Boolean = true,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = currentChapter(chapters, positionMs)
    var drag by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissAt = with(density) { 72.dp.toPx() }

    Column(
        modifier
            .graphicsLayer { translationY = drag }
            .clip(MaterialTheme.shapes.large.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .testTag("chapters.panel"),
    ) {
        Column(
            Modifier.fillMaxWidth().playerVerticalSwipe(
                onDelta = { dy -> drag = (drag + dy).coerceAtLeast(0f) },
                onEnd = { _, v ->
                    if (drag > dismissAt || v > 900f) onDismiss()
                    else scope.launch { androidx.compose.animation.core.animate(drag, 0f) { x, _ -> drag = x } }
                },
            ),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally)) { com.greenaltambe.vela.ui.components.SheetHandle() }
            Row(
                Modifier.fillMaxWidth().padding(start = Spacing.gutter, end = Spacing.xs, top = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text("Chapters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (chapters.isNotEmpty()) {
                    Text(
                        "${chapters.size}",
                        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = Spacing.sm, vertical = 2.dp).testTag("chapters.count"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                androidx.compose.material3.FilledTonalIconButton(onClick = onDismiss, shapes = androidx.compose.material3.IconButtonDefaults.shapes(), modifier = Modifier.testTag("chapters.close")) {
                    Icon(AppIcons.close, "Close chapters")
                }
            }
        }

        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        val currentIndex = chapters.indexOf(current).coerceAtLeast(0)
        // Opens at the chapter that is playing.
        LaunchedEffect(chapters.size) { if (current != null) listState.scrollToItem(currentIndex.coerceAtLeast(0)) }
        val currentVisible by remember(listState, currentIndex) { androidx.compose.runtime.derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.index == currentIndex } } }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize(), state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.sm, bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(chapters, key = { _, it -> "chapter-${it.startSeconds}" }) { index, c ->
                    val isCurrent = c == current
                    val played = !isCurrent && current != null && index < currentIndex
                    val length = (chapterEndSeconds(chapters, index, durationMs) - c.startSeconds).coerceAtLeast(0)
                    Column(
                        Modifier.fillMaxWidth().clip(com.greenaltambe.vela.ui.settings.segmentedShape(index, chapters.size))
                            .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { onSeek(c.startSeconds * 1000) }
                            .padding(Spacing.sm)
                            .testTag("chapters.row"),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            Box(Modifier.width(96.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                                val thumb = c.thumbnailUrl ?: videoThumbnailUrl
                                if (thumb != null) AsyncImage(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                Text(
                                    formatTimestamp(c.startSeconds), style = MaterialTheme.typography.labelSmall, color = ExtraColors.onMedia,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(ExtraColors.mediaBackground.copy(alpha = 0.75f), CircleShape).padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                if (isCurrent) Text("Now playing", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text(
                                    c.title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                                if (length > 0) Text(formatTimestamp(length), style = MaterialTheme.typography.bodySmall, color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (played) Icon(AppIcons.check, "Played", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isCurrent) {
                            // How far into this chapter: Material's wavy bar, flat while paused, with the time inside the chapter under it.
                            androidx.compose.material3.LinearWavyProgressIndicator(
                                progress = { chapterProgress(chapters, index, livePositionMs(), durationMs) },
                                modifier = Modifier.fillMaxWidth().testTag("chapters.progress"),
                                amplitude = { if (playing) 1f else 0f },
                                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                            )
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    formatTimestamp(((livePositionMs() - c.startSeconds * 1000) / 1000).coerceAtLeast(0)),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Spacer(Modifier.weight(1f))
                                if (length > 0) Text(formatTimestamp(length), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
            }
            if (!currentVisible && current != null) androidx.compose.material3.FilledTonalButton(
                onClick = { scope.launch { listState.animateScrollToItem(currentIndex) } }, shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.md).testTag("chapters.jump"),
            ) { Text("Now playing", maxLines = 1) }
        }
    }
}

/** Legacy / modal compatibility wrapper */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersSheet(
    chapters: List<Chapter>,
    positionMs: Long,
    keepClearTopPx: Float = 0f,
    videoThumbnailUrl: String? = null,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ChaptersPanel(
        chapters = chapters,
        positionMs = positionMs,
        videoThumbnailUrl = videoThumbnailUrl,
        onSeek = onSeek,
        onDismiss = onDismiss,
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.6f)
    )
}
