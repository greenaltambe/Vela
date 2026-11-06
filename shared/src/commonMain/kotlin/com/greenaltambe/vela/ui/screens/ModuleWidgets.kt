package com.greenaltambe.vela.ui.screens

import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.Accent
import androidx.compose.foundation.shape.CircleShape
import com.greenaltambe.vela.ui.theme.ExtraColors
import com.greenaltambe.vela.ui.theme.Spacing
import com.greenaltambe.vela.ui.theme.LocalMotion
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import com.greenaltambe.vela.ui.components.PrimaryButton
import com.greenaltambe.vela.ui.components.SecondaryButton
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.greenaltambe.vela.core.http.ApiResult
import com.greenaltambe.vela.library.formatTimestamp
import com.greenaltambe.vela.modules.Modules
import com.greenaltambe.vela.modules.sponsorblock.Segment
import com.greenaltambe.vela.modules.sponsorblock.SegmentCategory
import com.greenaltambe.vela.modules.sponsorblock.SkipNotice
import com.greenaltambe.vela.modules.sponsorblock.SubmitOutcome
import com.greenaltambe.vela.ui.components.SectionHeader
import kotlinx.coroutines.launch

/**
 * Over the picture, bottom-right (the player places it): the Skip button of a "prompt" segment, and the notice of an automatic skip with Undo.
 * Both are quiet pills in the player's own language: a translucent scrim, a thin ring in the palette gradient, a squeeze and a buzz on press, and a
 * slide in from the edge.
 */
@Composable
fun SponsorBlockOverlay(prompt: Segment?, notice: SkipNotice?, onAccept: () -> Unit, onUndo: (SkipNotice) -> Unit) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AnimatedVisibility(prompt != null, enter = slideInHorizontally { it / 2 } + fadeIn(), exit = slideOutHorizontally { it / 2 } + fadeOut()) {
            // Keep the last prompt while the exit plays.
            val shown = remember { mutableStateOf(prompt) }
            if (prompt != null) shown.value = prompt
            shown.value?.let { seg ->
                val label = SegmentCategory.fromId(seg.category)?.label ?: seg.category
                SkipPill(if (seg.isPoint) "Jump to highlight" else "Skip ${label.lowercase()}", AppIcons.skipNext, onAccept, Modifier.testTag("sponsorblock.prompt"))
            }
        }
        AnimatedVisibility(notice != null, enter = slideInHorizontally { it / 2 } + fadeIn(), exit = slideOutHorizontally { it / 2 } + fadeOut()) {
            val shown = remember { mutableStateOf(notice) }
            if (notice != null) shown.value = notice
            shown.value?.let { n ->
                SkipPill("Skipped ${n.label.lowercase()}", null, { onUndo(n) }, Modifier.testTag("sponsorblock.notice"), action = "Undo", actionTag = "sponsorblock.undo")
            }
        }
    }
}

/** One pill of the overlay. A tap anywhere on it acts; with an [action] the pill reads "message, Undo" and the action word is the accent. */
@Composable
private fun SkipPill(message: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, action: String? = null, actionTag: String? = null) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val motion = LocalMotion.current
    val haptics = com.greenaltambe.vela.ui.player.rememberPlayerHaptics()
    val scale by animateFloatAsState(if (pressed && !motion.reduced) 0.93f else 1f, com.greenaltambe.vela.ui.look.VelaMotion.bouncy(motion), label = "skipPillScale")
    val ring by animateFloatAsState(if (pressed) 1f else 0.55f, com.greenaltambe.vela.ui.look.VelaMotion.tween(motion, com.greenaltambe.vela.ui.look.VelaMotion.Level.QUICK), label = "skipPillRing")
    val wake = Accent.colors
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(ExtraColors.mediaScrim.copy(alpha = 0.72f))
            .border(1.5.dp, Brush.horizontalGradient(wake.map { it.copy(alpha = ring) }), CircleShape)
            .clickable(interactionSource = source, indication = null, role = androidx.compose.ui.semantics.Role.Button) { haptics.confirm(); onClick() }
            .heightIn(min = 40.dp).padding(start = if (icon != null) Spacing.sm else Spacing.md, end = Spacing.md),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(22.dp), tint = ExtraColors.onMedia)
        Text(message, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, color = ExtraColors.onMedia)
        if (action != null) Text(action, Modifier.padding(start = Spacing.xs).testTag(actionTag.orEmpty()), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, color = wake.last())
    }
}

/**
 * Marking a sponsor segment and suggesting a DeArrow thumbnail. Everything is explicit: nothing is sent until the
 * viewer has seen what will be sent and confirmed it.
 */
@Composable
fun ContributeSection(
    modules: Modules,
    videoId: String,
    positionMs: Long,
    durationMs: Long,
    status: String?,
    onStatus: (String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var expanded by remember(videoId) { mutableStateOf(false) }
    var start by remember(videoId) { mutableStateOf<Long?>(null) }
    var end by remember(videoId) { mutableStateOf<Long?>(null) }
    var category by remember(videoId) { mutableStateOf(SegmentCategory.SPONSOR) }
    var menu by remember { mutableStateOf(false) }
    var confirmSegment by remember { mutableStateOf(false) }
    var confirmFrame by remember { mutableStateOf(false) }

    // One card: a header row that opens it, then the two kinds of contribution, each with its own small heading and the same button family.
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.heightIn(min = Spacing.minTouch).padding(horizontal = Spacing.md).testTag("contribute.toggle"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text("Contribute", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Icon(if (expanded) AppIcons.chevronUp else AppIcons.chevronDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) Column(Modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("SponsorBlock segment", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton("Mark start" + (start?.let { " (${formatTimestamp(it / 1000)})" } ?: ""), { start = positionMs }, Modifier.weight(1f).testTag("contribute.markStart"))
                    SecondaryButton("Mark end" + (end?.let { " (${formatTimestamp(it / 1000)})" } ?: ""), { end = positionMs }, Modifier.weight(1f).testTag("contribute.markEnd"))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                        SecondaryButton(category.label, { menu = true }, Modifier.fillMaxWidth())
                        DropdownMenu(menu, { menu = false }) {
                            SegmentCategory.entries.forEach { c -> DropdownMenuItem(text = { Text(c.label) }, onClick = { category = c; menu = false }) }
                        }
                    }
                    val s = start; val e = end
                    val valid = s != null && (category == SegmentCategory.POI_HIGHLIGHT || (e != null && e > s))
                    PrimaryButton("Review and submit", { confirmSegment = true }, Modifier.weight(1f).testTag("contribute.review"), enabled = valid)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("DeArrow thumbnail", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SecondaryButton("Use the frame at ${formatTimestamp(positionMs / 1000)}", { confirmFrame = true }, Modifier.fillMaxWidth().testTag("contribute.frame"), enabled = durationMs > 0)
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("contribute.status")) }
        }
    }

    if (confirmSegment) {
        val s = start ?: 0L
        val e = if (category == SegmentCategory.POI_HIGHLIGHT) s else (end ?: s)
        AlertDialog(
            onDismissRequest = { confirmSegment = false },
            title = { Text("Submit this segment?") },
            text = { Text("${category.label}: ${formatTimestamp(s / 1000)}${if (category == SegmentCategory.POI_HIGHLIGHT) "" else " to ${formatTimestamp(e / 1000)}"}\n\nIt is sent to the SponsorBlock server, where other users review it. Check the times first.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSegment = false
                    scope.launch {
                        onStatus("Sending...")
                        onStatus(when (val r = modules.sponsorBlock.submit(category, s, e, durationMs)) {
                            SubmitOutcome.Sent -> "Segment sent. Thank you."
                            is SubmitOutcome.Failed -> "Not sent: ${r.message}"
                        })
                    }
                }, modifier = Modifier.testTag("contribute.confirmSegment")) { Text("Send") }
            },
            dismissButton = { TextButton(onClick = { confirmSegment = false }) { Text("Cancel") } },
        )
    }
    if (confirmFrame) {
        AlertDialog(
            onDismissRequest = { confirmFrame = false },
            title = { Text("Suggest this frame?") },
            text = { Text("The frame at ${formatTimestamp(positionMs / 1000)} is suggested to DeArrow as this video's thumbnail. Others vote on it.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmFrame = false
                    scope.launch {
                        onStatus("Sending...")
                        onStatus(when (val r = modules.dearrow.submitThumbnail(videoId, positionMs, durationMs, modules.sponsorBlock.userId())) {
                            is ApiResult.Ok -> "Thumbnail suggestion sent."
                            is ApiResult.Failed -> "Not sent: ${r.message}"
                        })
                    }
                }, modifier = Modifier.testTag("contribute.confirmFrame")) { Text("Send") }
            },
            dismissButton = { TextButton(onClick = { confirmFrame = false }) { Text("Cancel") } },
        )
    }
}
