package com.greenaltambe.vela.library

/** One run of a description: plain text, a link, or a timestamp the player can seek to. */
sealed interface DescriptionPart {
    data class Text(val text: String) : DescriptionPart
    data class Link(val text: String, val url: String) : DescriptionPart
    data class Time(val text: String, val seconds: Long) : DescriptionPart
}

private val urlRegex = Regex("""https?://[^\s<>"')\]]+""")
private val anchor = Regex("""<a\s[^>]*href="([^"]*)"[^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

/** Decodes the handful of entities descriptions use; anything else is left alone. */
internal fun decodeEntities(s: String): String = s
    .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
    .replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&")

/** Descriptions arrive as plain text from some tiers and light HTML from others; this reduces both to text with <a> kept. */
internal fun htmlToPlain(html: String): String {
    // Anchors become their visible text followed by the target when it differs, so links survive plain-text parsing.
    val withAnchors = anchor.replace(html) { m ->
        val href = decodeEntities(m.groupValues[1])
        val label = m.groupValues[2].replace(Regex("<[^>]*>"), "")
        if (label.trim() == href || label.contains("...") || label.isBlank()) href else "$label ($href)"
    }
    return decodeEntities(
        withAnchors.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]*>"), ""),
    )
}

/** Splits a description into text, links (opened externally) and timestamps (seek the player). */
fun parseDescription(raw: String): List<DescriptionPart> {
    val text = htmlToPlain(raw)
    val marks = buildList {
        urlRegex.findAll(text).forEach { m ->
            val trimmed = m.value.trimEnd('.', ',', ';', ':', '!', '?')
            add(Triple(m.range.first, m.range.first + trimmed.length, DescriptionPart.Link(trimmed, trimmed) as DescriptionPart))
        }
        findTimestamps(text).forEach { t ->
            // A timestamp inside a URL (a ?t=1:23 query, say) belongs to the link.
            if (none { it.first <= t.start && t.endExclusive <= it.second }) {
                add(Triple(t.start, t.endExclusive, DescriptionPart.Time(text.substring(t.start, t.endExclusive), t.seconds) as DescriptionPart))
            }
        }
    }.sortedBy { it.first }
    val parts = mutableListOf<DescriptionPart>()
    var cursor = 0
    for ((start, end, part) in marks) {
        if (start < cursor) continue
        if (start > cursor) parts += DescriptionPart.Text(text.substring(cursor, start))
        parts += part
        cursor = end
    }
    if (cursor < text.length) parts += DescriptionPart.Text(text.substring(cursor))
    return parts
}
