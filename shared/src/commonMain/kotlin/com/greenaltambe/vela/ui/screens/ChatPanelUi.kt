@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.greenaltambe.vela.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.greenaltambe.vela.extractor.ChatKind
import com.greenaltambe.vela.extractor.ChatMessage
import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.Spacing

/**
 * The chat under a live video (or the replay of an ended one), as a Material 3 Expressive card: a tonal header that opens and closes it with a
 * spring (a pulsing LIVE dot while a live chat is open), and inside it the messages as small tonal bubbles. A message is a coloured initial, the
 * author's name in that colour and the text; a paid message is a raised tertiary card with the amount in a pill, a membership message a primary
 * one. The list follows the newest message while you are at the bottom; scroll up to read and a "Latest" pill brings you back.
 * Only the display is here: polling and replay sync stay with the caller.
 */
@Composable
fun ChatPanelUi(open: Boolean, replay: Boolean, status: String?, lines: List<ChatMessage>, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier.fillMaxWidth().animateContentSize(spring(dampingRatio = 0.8f, stiffness = 400f)).testTag("video.chat"),
        shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            // The header: tap anywhere on it.
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle).padding(horizontal = Spacing.md, vertical = Spacing.sm).testTag("video.chat.toggle"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.comment, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text(if (replay) "Chat replay" else "Live chat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(if (open) "${lines.size} message${if (lines.size == 1) "" else "s"}" else if (replay) "Follows the video" else "Tap to join the chat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                if (open && !replay && status == null) LivePill()
                Icon(AppIcons.chevronDown, if (open) "Hide chat" else "Show chat", Modifier.rotate(if (open) 180f else 0f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(open, enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(horizontal = Spacing.sm).padding(bottom = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    status?.let {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                            Text(it, Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs).testTag("video.chat.status"), style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    MessageList(lines)
                }
            }
        }
    }
}

/** A small red "LIVE" pill with a softly pulsing dot. */
@Composable
private fun LivePill() {
    val pulse by rememberInfiniteTransition(label = "livePulse").animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "liveDot")
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
        Row(Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(8.dp).graphicsLayer { alpha = pulse }.clip(CircleShape).background(MaterialTheme.colorScheme.error))
            Text("LIVE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MessageList(lines: List<ChatMessage>) {
    val state = rememberLazyListState()
    // Follow the newest message while the viewer is at the bottom; reading older ones (scrolled up) is left alone.
    val atBottom by remember { androidx.compose.runtime.derivedStateOf { !state.canScrollForward } }
    LaunchedEffect(lines.lastOrNull()?.id) { if (lines.isNotEmpty() && atBottom) state.animateScrollToItem(lines.lastIndex) }
    Box {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp).testTag("video.chat.lines"), state, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            items(lines, key = { it.id }) { m -> ChatBubble(m, Modifier.animateItem()) }
        }
        androidx.compose.animation.AnimatedVisibility(!atBottom && lines.isNotEmpty(), Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.xs), enter = fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.7f), exit = fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.7f)) {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            Surface(
                onClick = { scope.launch { state.animateScrollToItem(lines.lastIndex) } }, shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shadowElevation = 3.dp,
                modifier = Modifier.testTag("video.chat.latest"),
            ) {
                Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(AppIcons.arrowDown, null, Modifier.size(16.dp))
                    Text("Latest", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** The colour pairs an author's initial is drawn in: a name always gets the same one. */
@Composable
private fun authorColors(author: String): Pair<Color, Color> {
    val s = MaterialTheme.colorScheme
    val pairs = listOf(s.primaryContainer to s.onPrimaryContainer, s.secondaryContainer to s.onSecondaryContainer, s.tertiaryContainer to s.onTertiaryContainer)
    return pairs[(author.hashCode() and 0x7fffffff) % pairs.size]
}

@Composable
private fun ChatBubble(m: ChatMessage, modifier: Modifier = Modifier) {
    val (bg, fg) = authorColors(m.author)
    val initial = m.author.trimStart('@').firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    when (m.kind) {
        ChatKind.TEXT -> Row(modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { Text(initial, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1) }
            Column(Modifier.weight(1f)) {
                Text(m.author, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(m.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
        ChatKind.SUPERCHAT, ChatKind.MEMBER -> {
            val paid = m.kind == ChatKind.SUPERCHAT
            Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = if (paid) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer, contentColor = if (paid) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer) {
                Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(m.author, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val pill = m.amount ?: if (paid) null else "Member"
                        if (pill != null) Surface(shape = CircleShape, color = if (paid) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary, contentColor = if (paid) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary) {
                            Text(pill, Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    if (m.text.isNotBlank()) Text(m.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
