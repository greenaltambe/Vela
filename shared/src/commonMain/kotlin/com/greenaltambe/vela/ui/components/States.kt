package com.greenaltambe.vela.ui.components

import com.greenaltambe.vela.ui.theme.AppIcons
import androidx.compose.foundation.shape.CircleShape
import com.greenaltambe.vela.ui.theme.Spacing
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
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
import com.greenaltambe.vela.ui.look.shimmer

/** A placeholder shaped like a video card, shown while a list loads. The blocks shimmer, unless motion is reduced or the app is in the background (then they are flat). */
@Composable
fun SkeletonCard(vertical: Boolean, modifier: Modifier = Modifier, fullBleed: Boolean = false) {
    if (vertical) {
        val gutter = Spacing.gutter
        Column(modifier.then(if (fullBleed) Modifier.bleed(gutter) else Modifier).fillMaxWidth().testTag("skeleton"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.large).shimmer())
            Row(Modifier.padding(horizontal = if (fullBleed) gutter + Spacing.xs else Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box(Modifier.size(36.dp).clip(CircleShape).shimmer())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Box(Modifier.fillMaxWidth().height(14.dp).clip(MaterialTheme.shapes.small).shimmer())
                    Box(Modifier.fillMaxWidth(0.6f).height(12.dp).clip(MaterialTheme.shapes.small).shimmer())
                }
            }
        }
    } else {
        // Same geometry as the horizontal VideoCard: the card's inner padding, a 0.36 thumbnail capped at 168 dp, the same gap.
        Row(modifier.fillMaxWidth().testTag("skeleton").padding(Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(Modifier.fillMaxWidth(0.36f).widthIn(max = 168.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium).shimmer())
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Box(Modifier.fillMaxWidth().height(14.dp).clip(MaterialTheme.shapes.small).shimmer())
                Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(MaterialTheme.shapes.small).shimmer())
                Box(Modifier.fillMaxWidth(0.4f).height(12.dp).clip(MaterialTheme.shapes.small).shimmer())
            }
        }
    }
}

/**
 * A placeholder shaped like a comment: the round picture, a short name line, two lines of text and the likes line. [avatar] is the picture's size
 * (a reply's is smaller than a comment's); the blocks shimmer like the video placeholders.
 */
@Composable
fun CommentSkeleton(modifier: Modifier = Modifier, avatar: androidx.compose.ui.unit.Dp = 40.dp, lastLineWidth: Float = 0.6f) {
    Row(modifier.fillMaxWidth().testTag("skeleton.comment"), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Box(Modifier.size(avatar).clip(CircleShape).shimmer())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Box(Modifier.fillMaxWidth(0.35f).height(12.dp).clip(MaterialTheme.shapes.small).shimmer())
            Box(Modifier.fillMaxWidth().height(14.dp).clip(MaterialTheme.shapes.small).shimmer())
            Box(Modifier.fillMaxWidth(lastLineWidth).height(14.dp).clip(MaterialTheme.shapes.small).shimmer())
            Box(Modifier.padding(top = Spacing.xs).width(72.dp).height(12.dp).clip(MaterialTheme.shapes.small).shimmer())
        }
    }
}

/** What to show where a list would be but is empty: a short sentence and, when there is something to do, the action. */
@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(vertical = Spacing.lg, horizontal = Spacing.md).testTag("empty"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(AppIcons.info, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
        if (actionLabel != null && onAction != null) androidx.compose.material3.FilledTonalButton(onClick = onAction) { Text(actionLabel, maxLines = 1) }
    }
}

/** A failed load, in place: what went wrong in one or two lines and a Retry. */
@Composable
fun ErrorRow(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = Spacing.md, vertical = Spacing.sm).testTag("error"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(AppIcons.info, null, tint = MaterialTheme.colorScheme.onErrorContainer)
        Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onErrorContainer)
        if (onRetry != null) androidx.compose.material3.FilledTonalButton(onClick = onRetry, modifier = Modifier.testTag("error.retry")) { Text("Retry", maxLines = 1) }
    }
}

/** The wait indicator: Material's expressive loading indicator (a morphing shape), for places where something is being fetched with nothing else to show. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingSpinner(modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    androidx.compose.material3.LoadingIndicator(modifier, color = color)
}
