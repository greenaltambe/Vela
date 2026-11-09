package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.greenaltambe.vela.ui.theme.Spacing

/** Lines the channel name may use before it is cut with an ellipsis. */
const val CHANNEL_NAME_MAX_LINES = 2

/**
 * The channel row of the video page: avatar, name (up to [CHANNEL_NAME_MAX_LINES] lines) with the subscriber count under it, and ONE small
 * control at the end ([actions]: Subscribe, or the subscribed pill). It is always a single row; the name gets all the width the control does not
 * need. If even two lines are not enough the name ends in an ellipsis; its full text is in the content description and the row still opens the
 * channel page, which shows the whole name. Laid out by measuring, so it follows the window width and the font scale.
 */
@Composable
fun ChannelRow(
    name: String,
    subscribersText: String?,
    avatar: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nameStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    val subsStyle = MaterialTheme.typography.bodySmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val gap = Spacing.sm
    SubcomposeLayout(modifier.semantics(mergeDescendants = false) { contentDescription = name }.testTag("video.channelRow")) { constraints ->
        val maxW = constraints.maxWidth
        val gapPx = gap.roundToPx()
        val loose = Constraints(maxWidth = maxW)
        val avatarP = subcompose("avatar") { avatar() }.map { it.measure(loose) }
        val actionsP = subcompose("actions") { actions() }.map { it.measure(loose) }
        val avatarW = avatarP.maxOfOrNull { it.width } ?: 0
        val avatarH = avatarP.maxOfOrNull { it.height } ?: 0
        val actionsW = actionsP.maxOfOrNull { it.width } ?: 0
        val actionsH = actionsP.maxOfOrNull { it.height } ?: 0
        val textW = (maxW - avatarW - actionsW - 2 * gapPx).coerceAtLeast(0)
        val textP = subcompose("text") {
            Column {
                Text(name, Modifier.testTag("video.channel"), style = nameStyle, maxLines = CHANNEL_NAME_MAX_LINES, overflow = TextOverflow.Ellipsis)
                if (subscribersText != null) Text(subscribersText, Modifier.testTag("video.subscribers"), style = subsStyle, color = muted, maxLines = 1)
            }
        }.map { it.measure(Constraints(maxWidth = textW)) }
        val textH = textP.maxOfOrNull { it.height } ?: 0
        val height = maxOf(avatarH, textH, actionsH)
        layout(maxW, height) {
            avatarP.forEach { it.place(0, 0) }
            textP.forEach { it.place(avatarW + gapPx, (height - textH) / 2) }
            actionsP.forEach { it.place(maxW - actionsW, (height - actionsH) / 2) }
        }
    }
}
