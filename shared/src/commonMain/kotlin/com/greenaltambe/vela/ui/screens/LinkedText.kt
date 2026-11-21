package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.unit.em
import androidx.compose.material3.MaterialTheme
import com.greenaltambe.vela.library.Social
import com.greenaltambe.vela.library.socialHandle
import com.greenaltambe.vela.library.socialOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.greenaltambe.vela.library.DescriptionPart
import com.greenaltambe.vela.library.parseDescription

/**
 * A description or comment as text with working links: links open externally, timestamps call [onSeek] with the second. Links and times wear
 * [linkColor] (the palette's accent), underlined.
 */
fun linkedText(raw: String, linkColor: Color, onSeek: (Long) -> Unit): AnnotatedString = buildAnnotatedString {
    val style = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    parseDescription(raw).forEach { part ->
        when (part) {
            is DescriptionPart.Text -> append(part.text)
            is DescriptionPart.Link -> withLink(LinkAnnotation.Url(part.url, style)) { append(part.text) }
            is DescriptionPart.Time -> withLink(LinkAnnotation.Clickable("t=${part.seconds}", style) { onSeek(part.seconds) }) { append(part.text) }
        }
    }
}

/** A description's text and the inline badges (one per social network it links to) that go with it. */
class DescriptionText(val text: AnnotatedString, val inline: Map<String, InlineTextContent>)

/**
 * Like [linkedText], but a link to a social network is YouTube's chip: the network's round badge, " / " and the account, on a tonal background, and still
 * a working link. Pass the result's text and [DescriptionText.inline] to `Text`. [raw] may be the description's HTML (anchors keep their addresses).
 */
fun descriptionText(raw: String, linkColor: Color, chipColor: Color, chipTextColor: Color, onSeek: (Long) -> Unit): DescriptionText {
    val inline = LinkedHashMap<String, InlineTextContent>()
    val style = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    val chipStyle = TextLinkStyles(SpanStyle(color = chipTextColor, background = chipColor))
    val text = buildAnnotatedString {
        parseDescription(raw).forEach { part ->
            when (part) {
                is DescriptionPart.Text -> append(part.text)
                is DescriptionPart.Time -> withLink(LinkAnnotation.Clickable("t=${part.seconds}", style) { onSeek(part.seconds) }) { append(part.text) }
                is DescriptionPart.Link -> {
                    val social = socialOf(part.url)
                    if (social == null) withLink(LinkAnnotation.Url(part.url, style)) { append(part.text) }
                    else withLink(LinkAnnotation.Url(part.url, chipStyle)) {
                        val id = "social.${social.name}"
                        inline.getOrPut(id) { socialBadge(social) }
                        append(" "); appendInlineContent(id, social.glyph); append(" / ${socialHandle(part.text, part.url)} ")
                    }
                }
            }
        }
    }
    return DescriptionText(text, inline)
}

private fun socialBadge(social: Social) = InlineTextContent(Placeholder(1.25.em, 1.25.em, PlaceholderVerticalAlign.Center)) {
    Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(social.argb)), contentAlignment = Alignment.Center) {
        Text(social.glyph, color = Color(com.greenaltambe.vela.library.SOCIAL_BADGE_TEXT), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
