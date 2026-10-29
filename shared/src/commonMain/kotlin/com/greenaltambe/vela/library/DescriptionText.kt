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

/**
 * Splits a description (or comment) into text, links (opened externally) and timestamps (seek the player). Anchors are read as anchors: a link to a
 * moment of a YouTube video (`...watch?v=ID&t=83`, `youtu.be/ID?t=83`) becomes a timestamp, a YouTube `redirect?q=` link points at what it redirects to,
 * and a link whose label is cut short ("https://example.com/very/lo...") shows a tidy form of its address. The plain text between anchors is
 * searched for bare links and timestamps.
 */
fun parseDescription(raw: String): List<DescriptionPart> {
    val parts = mutableListOf<DescriptionPart>()
    var cursor = 0
    for (m in anchor.findAll(raw)) {
        if (m.range.first > cursor) parts += parsePlain(htmlToPlain(raw.substring(cursor, m.range.first)))
        val href = resolveHref(decodeEntities(m.groupValues[1]))
        val label = decodeEntities(m.groupValues[2].replace(Regex("<[^>]*>"), "")).trim()
        val seconds = timestampSeconds(href)
        parts += if (seconds != null) DescriptionPart.Time(label.ifBlank { formatTimestamp(seconds) }, seconds) else DescriptionPart.Link(linkLabel(label, href), href)
        cursor = m.range.last + 1
    }
    if (cursor < raw.length) parts += parsePlain(htmlToPlain(raw.substring(cursor)))
    return parts
}

/** A `youtube.com/redirect?q=` address is the address in its `q`; anything else is as given. */
internal fun resolveHref(href: String): String {
    if ("youtube.com/redirect" !in href) return href
    val q = Regex("""[?&]q=([^&]+)""").find(href)?.groupValues?.get(1) ?: return href
    return percentDecode(q).takeIf { it.startsWith("http") } ?: href
}

private fun percentDecode(s: String): String {
    val out = StringBuilder(); val bytes = ArrayList<Byte>()
    fun flush() { if (bytes.isNotEmpty()) { out.append(bytes.toByteArray().decodeToString()); bytes.clear() } }
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '%' && i + 2 < s.length + 0 && s.substring(i + 1, i + 3).toIntOrNull(16) != null) { bytes += s.substring(i + 1, i + 3).toInt(16).toByte(); i += 3 }
        else { flush(); out.append(if (c == '+') ' ' else c); i++ }
    }
    flush()
    return out.toString()
}

/** The moment a YouTube watch / youtu.be link points at ("t=83", "t=1m23s", "start=83"), or null if it is not such a link. */
internal fun timestampSeconds(href: String): Long? {
    if ("youtube.com/watch" !in href && "youtu.be/" !in href) return null
    val v = Regex("""[?&#](?:t|start)=([0-9hms]+)""").find(href)?.groupValues?.get(1) ?: return null
    v.toLongOrNull()?.let { return it }
    val m = Regex("""(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?""").matchEntire(v) ?: return null
    return (m.groupValues[1].toLongOrNull() ?: 0) * 3600 + (m.groupValues[2].toLongOrNull() ?: 0) * 60 + (m.groupValues[3].toLongOrNull() ?: 0)
}

/** What a link shows: its label, unless that is empty, cut short or the bare address, when it is the address without "https://www.", shortened. */
internal fun linkLabel(label: String, href: String): String {
    if (label.isNotBlank() && !label.endsWith("...") && !label.endsWith("…") && label != href) return label
    val bare = href.removePrefix("https://").removePrefix("http://").removePrefix("www.")
    return if (bare.length > 48) bare.take(47) + "…" else bare
}

private fun parsePlain(text: String): List<DescriptionPart> {
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
