package com.greenaltambe.vela.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.core.settings.CaptionBackground
import com.greenaltambe.vela.core.settings.CaptionStyle
import com.greenaltambe.vela.library.CaptionColors
import com.greenaltambe.vela.library.CaptionRun
import com.greenaltambe.vela.library.Cue
import com.greenaltambe.vela.library.CueAlign
import com.greenaltambe.vela.library.CuePlacement
import com.greenaltambe.vela.ui.theme.ExtraColors
import com.greenaltambe.vela.ui.theme.Spacing

/**
 * The captions over the picture. Ordinary cues sit at the bottom, above the controls while they show. A cue its creator placed (a chat overlay at the
 * top right, say) is drawn where it was authored while [CaptionStyle.creatorStyle] is on, in the creator's colours made readable on dark; with it off every
 * cue is plain text at the bottom. Size and background follow the viewer's [style].
 */
@Composable
fun CaptionOverlay(cues: List<Cue>, style: CaptionStyle, fullscreen: Boolean, bottomInset: Dp, modifier: Modifier = Modifier) {
    val margin = with(androidx.compose.ui.platform.LocalDensity.current) { Spacing.sm.roundToPx() }
    val bottom = with(androidx.compose.ui.platform.LocalDensity.current) { bottomInset.roundToPx() }
    Box(modifier.fillMaxSize()) {
        val (placed, plain) = if (style.creatorStyle) cues.partition { it.placement != null } else emptyList<Cue>() to listOfNotNull(cues.firstOrNull { it.placement == null } ?: cues.lastOrNull())
        plain.forEach { cue ->
            CaptionBubble(cue, style, fullscreen, placedInBox = false, Modifier.align(Alignment.BottomCenter).padding(start = Spacing.md, end = Spacing.md, bottom = bottomInset))
        }
        placed.forEach { cue ->
            CaptionBubble(cue, style, fullscreen, placedInBox = true, Modifier.placedIn(cue.placement!!, margin, bottom))
        }
    }
}

/** Puts the box at the place a cue asked for: [CuePlacement.line] is its top edge, [CuePlacement.position] and [CuePlacement.size] place it across; it never leaves the picture. */
private fun Modifier.placedIn(p: CuePlacement, margin: Int, bottomInset: Int): Modifier = layout { measurable, c ->
    val w = c.maxWidth; val h = c.maxHeight
    val size = (p.size ?: 1f).coerceIn(0.1f, 1f)
    val boxW = (w * size).toInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints(maxWidth = (boxW - 2 * margin).coerceAtLeast(1), maxHeight = h))
    val left = when (p.align) {
        CueAlign.START -> p.position ?: 0f
        CueAlign.CENTER -> (p.position ?: 0.5f) - size / 2f
        CueAlign.END -> (p.position ?: 1f) - size
    }.coerceIn(0f, 1f - size)
    val boxLeft = (w * left).toInt()
    val x = when (p.align) {
        CueAlign.START -> boxLeft + margin
        CueAlign.CENTER -> boxLeft + (boxW - placeable.width) / 2
        CueAlign.END -> boxLeft + boxW - placeable.width - margin
    }
    val y = if (p.line != null) (h * p.line).toInt() + margin else h - placeable.height - bottomInset
    layout(w, h) { placeable.place(x, y.coerceIn(0, (h - placeable.height).coerceAtLeast(0))) }
}

/** One cue's text with the viewer's size and background. [placedInBox]: align the text the way its creator asked; otherwise it is centred. */
@Composable
fun CaptionBubble(cue: Cue, style: CaptionStyle, fullscreen: Boolean, placedInBox: Boolean, modifier: Modifier = Modifier) {
    val base = MaterialTheme.typography.bodyMedium
    // A chat-like overlay is several lines at once: a little smaller than a single subtitle line, so it does not cover the picture.
    val scale = style.size.scale * (if (fullscreen) 1.15f else 1f) * (if (placedInBox) 0.85f else 1f)
    val onMedia = ExtraColors.onMedia
    val lines = cue.lines.ifEmpty { cue.text.split('\n').map { listOf(CaptionRun(it)) } }
    val text = remember(cue, style.creatorStyle) {
        buildAnnotatedString {
            lines.forEachIndexed { i, line ->
                if (i > 0) append('\n')
                line.forEach { r ->
                    val color = if (style.creatorStyle && r.color != null) Color(CaptionColors.readableOnDark(r.color)) else Color.Unspecified
                    withStyle(SpanStyle(
                        color = color, fontWeight = if (r.bold) FontWeight.Bold else null, fontStyle = if (r.italic) FontStyle.Italic else null,
                        textDecoration = if (r.underline) TextDecoration.Underline else null,
                    )) { append(r.text) }
                }
            }
        }
    }
    val box = when (style.background) {
        CaptionBackground.OUTLINE -> Modifier
        CaptionBackground.SHADE -> Modifier.clip(MaterialTheme.shapes.small).background(ExtraColors.mediaScrim.copy(alpha = 0.7f))
        CaptionBackground.SOLID -> Modifier.clip(MaterialTheme.shapes.small).background(ExtraColors.mediaBackground)
    }
    val shadow = if (style.background == CaptionBackground.OUTLINE) Shadow(ExtraColors.mediaBackground, Offset.Zero, blurRadius = 10f) else null
    Text(
        text, modifier.then(box).padding(horizontal = Spacing.sm, vertical = Spacing.xs).testTag("player.caption"),
        color = onMedia, textAlign = if (placedInBox) when (cue.placement?.align) { CueAlign.START -> TextAlign.Start; CueAlign.END -> TextAlign.End; else -> TextAlign.Center } else TextAlign.Center,
        style = base.copy(fontSize = base.fontSize * scale, lineHeight = base.lineHeight * scale, shadow = shadow), overflow = TextOverflow.Ellipsis,
        maxLines = if (placedInBox) 14 else 4,
    )
}
