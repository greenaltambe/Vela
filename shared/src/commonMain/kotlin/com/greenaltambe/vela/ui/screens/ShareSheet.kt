package com.greenaltambe.vela.ui.screens

import com.greenaltambe.vela.ui.theme.AppIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.greenaltambe.vela.ui.theme.Spacing
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import com.greenaltambe.vela.library.formatTimestamp
import com.greenaltambe.vela.ui.player.PlayerMenuRow
import com.greenaltambe.vela.ui.player.PlayerMenuSection
import com.greenaltambe.vela.ui.player.PlayerMenuSheet
import com.greenaltambe.vela.ui.platform.LocalShare

/**
 * Everything you can do with a video's address, in one sheet: send it (the system share sheet, where there is one), copy it, either as it is or
 * at the moment you are watching, or open it in the browser. [onDone] gets a short message for the page to show ("Link copied: ...").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareSheet(videoId: String, title: String, positionMs: Long, onDone: (String?) -> Unit, onDismiss: () -> Unit) {
    val share = LocalShare.current
    val clipboard = LocalClipboardManager.current
    val uri = LocalUriHandler.current
    val link = "https://youtu.be/$videoId"
    val seconds = (positionMs / 1000).coerceAtLeast(0)
    val atTime = seconds >= 2
    val timed = "$link?t=$seconds"
    fun copy(text: String) { clipboard.setText(AnnotatedString(text)); onDone("Link copied: $text") }

    var withTime by remember { mutableStateOf(atTime) }
    val shown = if (withTime && atTime) timed else link
    PlayerMenuSheet("Share", onDismiss, subtitle = title) {
        // Large square tiles: what you can do with the address. Share only where the system has a share sheet.
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            maxItemsInEachRow = 3,
        ) {
            if (share != null) ShareTile(AppIcons.share, "Share", Modifier.testTag("video.share.send")) { share(title, shown); onDone(null) }
            ShareTile(AppIcons.link, "Copy link", Modifier.testTag("video.share.copy")) { copy(shown) }
            ShareTile(AppIcons.explore, "Browser", Modifier.testTag("video.share.browser")) {
                runCatching { uri.openUri("https://www.youtube.com/watch?v=$videoId" + if (withTime && atTime) "&t=${seconds}s" else "") }; onDone(null)
            }
        }
        Text(shown, Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm).testTag("video.share.link"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (atTime) Row(
            Modifier.padding(horizontal = Spacing.gutter).fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(AppIcons.timer, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Start at ${formatTimestamp(seconds)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            androidx.compose.material3.Switch(withTime, { withTime = it }, Modifier.testTag("video.share.atTime"))
        }
    }
}

/** One big square action: the icon in a tonal tile, the word under it. */
@Composable
private fun ShareTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.width(104.dp).clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.secondaryContainer).clickable(onClick = onClick).padding(vertical = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
    }
}
