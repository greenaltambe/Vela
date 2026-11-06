@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.greenaltambe.vela.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.data.SubscribedChannel
import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.Spacing

/**
 * The channels you follow, as a row of expressive tiles: each picture is cut to one of Material's shapes, a primary ring marks a channel with a new
 * video you have not seen in Notifications, and the last tile is "All" (the full list). The header's "View all" is a tonal button.
 */
@Composable
fun ChannelStrip(
    channels: List<SubscribedChannel>,
    unread: Set<String>,
    onOpen: (SubscribedChannel) -> Unit,
    onAll: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "subs.channelsRow",
) {
    val shapes = remember {
        listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Pentagon, MaterialShapes.Sunny, MaterialShapes.Cookie6Sided, MaterialShapes.Gem, MaterialShapes.Flower)
    }.map { it.toShape() }
    val gutter = Spacing.gutter
    Column(modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Following", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            FilledTonalButton(onClick = onAll, shapes = ButtonDefaults.shapes(), modifier = Modifier.testTag("$tag.all")) { Text("View all", maxLines = 1) }
        }
        HScrollRow(Modifier.bleed(gutter), Arrangement.spacedBy(Spacing.md), PaddingValues(horizontal = gutter)) {
            items(channels.size, key = { channels[it].channelId }) { i ->
                val c = channels[i]
                val shape = shapes[i % shapes.size]
                val hasNew = c.channelId in unread
                Column(
                    Modifier.width(76.dp).clip(MaterialTheme.shapes.large).clickable { onOpen(c) }.padding(vertical = Spacing.xs).testTag("$tag.item"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Box(Modifier.size(68.dp).then(if (hasNew) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier).padding(if (hasNew) 5.dp else 2.dp)) {
                        Avatar(c.name, size = 60.dp, url = c.avatarUrl, shape = shape)
                    }
                    Text(c.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, color = if (hasNew) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "all") {
                Column(
                    Modifier.width(76.dp).clip(MaterialTheme.shapes.large).clickable(onClick = onAll).padding(vertical = Spacing.xs).testTag("$tag.allTile"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Box(Modifier.size(68.dp).padding(4.dp).clip(MaterialShapes.Square.toShape()).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                        Icon(AppIcons.subscriptions, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Text("All", style = MaterialTheme.typography.labelMedium, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
