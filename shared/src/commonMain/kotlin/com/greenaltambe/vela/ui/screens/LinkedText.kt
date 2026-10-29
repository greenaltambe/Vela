package com.greenaltambe.vela.ui.screens

import androidx.compose.ui.graphics.Color
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
