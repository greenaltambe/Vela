@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.greenaltambe.vela.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.greenaltambe.vela.ui.look.ComponentIds
import com.greenaltambe.vela.ui.look.CoverWash
import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.Spacing

/** From this width on, a playlist page puts its header beside the list instead of above it. */
val PLAYLIST_SIDE_BY_SIDE = 840.dp
/** The width of the header when it stands beside the list. */
val PLAYLIST_HEADER_WIDTH = 360.dp

/** One button of a playlist header. */
class HeaderAction(val enabled: Boolean = true, val onClick: () -> Unit)

/** What the header can do with the whole playlist; a null action is not offered at all. */
class PlaylistActions(
    val playAll: HeaderAction,
    val shuffle: HeaderAction? = null,
    val download: HeaderAction? = null,
    val share: HeaderAction? = null,
    val menu: List<PageAction> = emptyList(),
)

/**
 * The head of a playlist page, for YouTube playlists and the library's own: cover, title, owner, facts, Play all and the other actions on the whole
 * list, and the description. Anything not given is left out.
 */
@Composable
fun PlaylistHeader(
    coverUrl: String?,
    actions: PlaylistActions,
    modifier: Modifier = Modifier,
    title: String? = null,
    owner: String? = null,
    ownerAvatarUrl: String? = null,
    onOwner: (() -> Unit)? = null,
    facts: String? = null,
    description: String? = null,
) {
    Box(modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).testTag(ComponentIds.PLAYLIST_HEADER)) {
        CoverWash(coverUrl, Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (coverUrl != null) AsyncImage(
                coverUrl, null, Modifier.align(Alignment.CenterHorizontally).widthIn(max = 420.dp).fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.large)
                    // A plain frame stands in until the picture is there (or when it cannot be had).
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).testTag("playlist.cover"),
                contentScale = ContentScale.Crop,
            )
            if (title != null) Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("playlist.title"))
            if (owner != null) Row(
                Modifier.clip(MaterialTheme.shapes.medium).let { if (onOwner != null) it.clickable(onClick = onOwner) else it }.heightIn(min = Spacing.minTouch).testTag("playlist.owner"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Avatar(owner, size = 28.dp, url = ownerAvatarUrl)
                Text(owner, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = Spacing.sm))
            }
            if (facts != null) Text(facts, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("playlist.facts"))
            PrimaryButton("Play all", onClick = actions.playAll.onClick, enabled = actions.playAll.enabled, modifier = Modifier.fillMaxWidth().testTag("playlist.playAll"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                actions.shuffle?.let { HeaderButton(it, AppIcons.shuffle, "Shuffle", "playlist.shuffle") }
                actions.download?.let { HeaderButton(it, AppIcons.download, "Download all", "playlist.downloadAllButton") }
                actions.share?.let { HeaderButton(it, AppIcons.share, "Share playlist", "playlist.share") }
                if (actions.menu.isNotEmpty()) Box {
                    var open by remember { mutableStateOf(false) }
                    HeaderButton(HeaderAction { open = true }, AppIcons.more, "More playlist actions", "playlist.headerMenu")
                    DropdownMenu(open, { open = false }) {
                        actions.menu.forEach { a -> DropdownMenuItem(text = { Text(a.label, maxLines = 1) }, onClick = { open = false; a.onClick() }, modifier = Modifier.testTag(a.tag)) }
                    }
                }
            }
            if (description != null) {
                var whole by remember(description) { mutableStateOf(false) }
                Text(
                    description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (whole) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { whole = !whole }.testTag("playlist.description"),
                )
            }
        }
    }
}

@Composable
private fun HeaderButton(action: HeaderAction, icon: ImageVector, label: String, tag: String) {
    FilledTonalIconButton(onClick = action.onClick, enabled = action.enabled, shapes = IconButtonDefaults.shapes(), modifier = Modifier.testTag(tag)) { Icon(icon, label) }
}
