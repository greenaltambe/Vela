package com.greenaltambe.vela.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.toColor

/** A grey placeholder shaped like a video card, shown while a list loads (no animation: nothing shimmers on slow phones). */
@Composable
fun SkeletonCard(vertical: Boolean, modifier: Modifier = Modifier) {
    val block = Vela.colors.surfaceVariant.toColor()
    if (vertical) {
        Column(modifier.fillMaxWidth().testTag("skeleton"), verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(Vela.mediumShape).background(block))
            Row(horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
                Box(Modifier.size(36.dp).clip(Vela.pillShape).background(block))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
                    Box(Modifier.fillMaxWidth().height(14.dp).clip(Vela.smallShape).background(block))
                    Box(Modifier.fillMaxWidth(0.6f).height(12.dp).clip(Vela.smallShape).background(block))
                }
            }
        }
    } else {
        Row(modifier.fillMaxWidth().testTag("skeleton"), horizontalArrangement = Arrangement.spacedBy(Vela.md)) {
            Box(Modifier.fillMaxWidth(0.4f).aspectRatio(16f / 9f).clip(Vela.mediumShape).background(block))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
                Box(Modifier.fillMaxWidth().height(14.dp).clip(Vela.smallShape).background(block))
                Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(Vela.smallShape).background(block))
                Box(Modifier.fillMaxWidth(0.4f).height(12.dp).clip(Vela.smallShape).background(block))
            }
        }
    }
}

/** What to show where a list would be but is empty: a short sentence and, when there is something to do, the action. */
@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(vertical = Vela.lg, horizontal = Vela.md).testTag("empty"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Vela.sm),
    ) {
        Icon(Vela.icons.info, null, Modifier.size(32.dp), tint = Vela.colors.onSurfaceVariant.toColor())
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
        if (actionLabel != null && onAction != null) OutlinedButton(onClick = onAction) { Text(actionLabel, maxLines = 1) }
    }
}

/** A failed load, in place: what went wrong in one or two lines and a Retry. */
@Composable
fun ErrorRow(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(Vela.mediumShape).background(Vela.colors.surfaceVariant.toColor()).padding(Vela.sm).testTag("error"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm),
    ) {
        Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, color = Vela.colors.error.toColor())
        if (onRetry != null) OutlinedButton(onClick = onRetry, modifier = Modifier.testTag("error.retry")) { Text("Retry", maxLines = 1) }
    }
}
