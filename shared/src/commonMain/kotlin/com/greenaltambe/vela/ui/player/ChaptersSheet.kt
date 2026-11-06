package com.greenaltambe.vela.ui.player

import com.greenaltambe.vela.ui.theme.AppIcons
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
                Text("Chapters", style = MaterialTheme.typography.titleMedium)
                if (chapters.isNotEmpty()) {
                    Text(
                        "${chapters.size}",
                        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = Spacing.sm, vertical = 2.dp).testTag("chapters.count"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("chapters.close")) {
                    Icon(AppIcons.close, "Close chapters")
                }
            }
        }

        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.sm, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(chapters, key = { "chapter-${it.startSeconds}" }) { c ->
                val isCurrent = c == current
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                        .then(if (isCurrent) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh) else Modifier)
                        .clickable { onSeek(c.startSeconds * 1000) }
                        .padding(Spacing.xs)
                        .testTag("chapters.row"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    // 16:9 Thumbnail frame box
                    Box(
                        Modifier
                            .width(88.dp)
                            .aspectRatio(16f / 9f)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        val thumb = c.thumbnailUrl ?: videoThumbnailUrl
                        if (thumb != null) {
                            AsyncImage(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        // Timestamp badge overlay on bottom-right of thumbnail
                        Text(
                            formatTimestamp(c.startSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = ExtraColors.onMedia,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(2.dp)
                                .background(ExtraColors.mediaBackground.copy(alpha = 0.75f), MaterialTheme.shapes.small)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                        if (isCurrent) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .border(2.dp, Brush.horizontalGradient(Accent.colors), MaterialTheme.shapes.medium)
                            )
                        }
                    }

                    // Chapter title and start timestamp
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            c.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            formatTimestamp(c.startSeconds),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Wake indicator pill for active chapter
                    if (isCurrent) {
                        Box(
                            Modifier
                                .width(4.dp)
                                .height(28.dp)
                                .clip(CircleShape)
                                .background(Accent.brush)
                        )
                    }
                }
            }
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
