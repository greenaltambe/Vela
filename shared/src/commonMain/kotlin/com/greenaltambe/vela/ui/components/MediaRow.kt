@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.greenaltambe.vela.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.ExtraColors
import com.greenaltambe.vela.ui.theme.Spacing

/**
 * The one row of the library lists (History, Downloads, a playlist): a 16:9 picture with a duration pill and an optional bar along its foot, a
 * two-line title, up to two supporting lines and a trailing slot (the overflow button). It sits on the page itself; a selected row fills with the
 * secondary container and shows a check on its picture. Long press selects, a tap while anything is selected toggles.
 */
@Composable
fun MediaRow(
    title: String,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    duration: String? = null,
    /** 0..1 along the foot of the picture (watched so far, or download progress); null for none. */
    progress: Float? = null,
    supporting: List<String> = emptyList(),
    supportingColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    selected: Boolean = false,
    dimmed: Boolean = false,
    overlay: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val container by androidx.compose.animation.animateColorAsState(if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent, label = "mediaRowBg")
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(container)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(Modifier.width(116.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh).testTag("card.thumbnail")) {
            if (thumbnailUrl != null) AsyncImage(thumbnailUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (dimmed) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.4f)))
            if (duration != null) Text(
                duration, style = MaterialTheme.typography.labelSmall, color = ExtraColors.onMedia,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(ExtraColors.mediaBackground.copy(alpha = 0.75f), CircleShape).padding(horizontal = 6.dp, vertical = 1.dp),
            )
            if (progress != null && progress > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(ExtraColors.mediaBackground.copy(alpha = 0.5f))) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
            }
            overlay?.invoke(this)
            if (selected) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.check, "Selected", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("card.title"))
            supporting.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = supportingColor, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        trailing()
    }
}

/** A row-shaped placeholder with the same geometry as [MediaRow], for while a list loads. */
@Composable
fun MediaRowSkeleton(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Box(Modifier.width(116.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Box(Modifier.fillMaxWidth().height(14.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHigh))
            Box(Modifier.fillMaxWidth(0.55f).height(12.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHigh))
        }
    }
}
