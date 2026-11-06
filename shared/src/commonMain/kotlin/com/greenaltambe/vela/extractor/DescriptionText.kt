package com.greenaltambe.vela.extractor

/**
 * A video description as plain text. Some sources hand back HTML (`<a href="https://example.com">https://exa…</a>`, `<br>`, `&amp;`); the page
 * shows text and finds the links itself, so tags are dropped: a link keeps its address (the visible text is often cut with "…"), line breaks stay.
 * Text with no tags is returned unchanged.
 */
fun plainDescription(raw: String): String {
    if ('<' !in raw && '&' !in raw) return raw
    var s = raw
    s = Regex("<a\\s[^>]*?href\\s*=\\s*[\"']([^\"']*)[\"'][^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).replace(s) { m ->
        val href = unescape(m.groupValues[1])
        val text = unescape(m.groupValues[2].replace(Regex("<[^>]*>"), ""))
        // YouTube's own redirect wraps the real address in ?q=; the text of a link to a hashtag or a timestamp is what the viewer sees.
        val target = Regex("[?&]q=([^&]+)").find(href)?.groupValues?.get(1)?.let { percentDecode(it) } ?: href
        if (text.startsWith("http") || text.endsWith("…") || text.endsWith("...")) target.takeIf { it.startsWith("http") } ?: text else text
    }
    s = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE).replace(s, "\n")
    s = Regex("</p\\s*>", RegexOption.IGNORE_CASE).replace(s, "\n")
    s = Regex("<[^>]*>").replace(s, "")
    return unescape(s)
}

private fun unescape(s: String): String = s
    .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&")

private fun percentDecode(s: String): String {
    val out = StringBuilder(); var i = 0
    val bytes = ArrayList<Byte>()
    fun flush() { if (bytes.isNotEmpty()) { out.append(bytes.toByteArray().decodeToString()); bytes.clear() } }
    while (i < s.length) {
        val c = s[i]
        if (c == '%' && i + 2 < s.length + 0 && i + 2 <= s.length - 1 + 0) {
            val v = s.substring(i + 1, i + 3).toIntOrNull(16)
            if (v != null) { bytes += v.toByte(); i += 3; continue }
        }
        flush(); out.append(if (c == '+') ' ' else c); i++
    }
    flush()
    return out.toString()
}
