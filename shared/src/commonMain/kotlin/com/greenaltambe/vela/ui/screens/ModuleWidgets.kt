package com.greenaltambe.vela.ui.screens

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
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.toColor
import kotlinx.coroutines.launch

/** Under the picture: the Skip button of a "prompt" segment, and the notice of an automatic skip with Undo. */
@Composable
fun SponsorBlockOverlay(prompt: Segment?, notice: SkipNotice?, onAccept: () -> Unit, onUndo: (SkipNotice) -> Unit) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
        if (prompt != null) {
            val label = SegmentCategory.fromId(prompt.category)?.label ?: prompt.category
            OutlinedButton(onClick = onAccept, modifier = Modifier.background(Vela.colors.mediaScrim.toColor(), Vela.mediumShape).testTag("sponsorblock.prompt")) {
                Text(if (prompt.isPoint) "Jump to highlight" else "Skip $label", maxLines = 1, color = Vela.colors.onMedia.toColor())
            }
        }
        if (notice != null) {
            Row(Modifier.background(Vela.colors.mediaScrim.toColor(), Vela.mediumShape).padding(start = Vela.md).testTag("sponsorblock.notice"), verticalAlignment = Alignment.CenterVertically) {
                Text("Skipped: ${notice.label}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, color = Vela.colors.onMedia.toColor())
                TextButton(onClick = { onUndo(notice) }, modifier = Modifier.testTag("sponsorblock.undo")) { Text("Undo") }
            }
        }
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
    Column(Modifier.fillMaxWidth().clip(Vela.mediumShape).background(Vela.colors.surface.toColor())) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.heightIn(min = Vela.minTouch).padding(horizontal = Vela.md).testTag("contribute.toggle"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm),
        ) {
            Text("Contribute", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Icon(if (expanded) Vela.icons.chevronUp else Vela.icons.chevronDown, null, tint = Vela.colors.onSurfaceVariant.toColor())
        }
        if (expanded) Column(Modifier.fillMaxWidth().padding(start = Vela.md, end = Vela.md, bottom = Vela.md), verticalArrangement = Arrangement.spacedBy(Vela.md)) {
            Column(verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
                Text("SponsorBlock segment", style = MaterialTheme.typography.bodyMedium, color = Vela.colors.onSurfaceVariant.toColor())
                Row(horizontalArrangement = Arrangement.spacedBy(Vela.sm), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton("Mark start" + (start?.let { " (${formatTimestamp(it / 1000)})" } ?: ""), { start = positionMs }, Modifier.weight(1f).testTag("contribute.markStart"))
                    SecondaryButton("Mark end" + (end?.let { " (${formatTimestamp(it / 1000)})" } ?: ""), { end = positionMs }, Modifier.weight(1f).testTag("contribute.markEnd"))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Vela.sm), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
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
            Column(verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
                Text("DeArrow thumbnail", style = MaterialTheme.typography.bodyMedium, color = Vela.colors.onSurfaceVariant.toColor())
                SecondaryButton("Use the frame at ${formatTimestamp(positionMs / 1000)}", { confirmFrame = true }, Modifier.fillMaxWidth().testTag("contribute.frame"), enabled = durationMs > 0)
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor(), modifier = Modifier.testTag("contribute.status")) }
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
