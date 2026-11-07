package com.greenaltambe.vela.library

import com.greenaltambe.vela.extractor.Chapter

/** A "1:23" / "01:02:03" mention found in text. [start] and [endExclusive] index into the text it was found in. */
data class TimestampMatch(val seconds: Long, val start: Int, val endExclusive: Int)

private val timestamp = Regex("""(?<![\d:])(?:(\d{1,2}):)?(\d{1,2}):(\d{2})(?![\d:])""")

/** Every timestamp in [text], in order. Minutes and seconds past 59 are not timestamps ("1:75" is skipped). */
fun findTimestamps(text: String): List<TimestampMatch> = timestamp.findAll(text).mapNotNull { m ->
    val hours = m.groupValues[1].toLongOrNull() ?: 0L
    val minutes = m.groupValues[2].toLong()
    val seconds = m.groupValues[3].toLong()
    if (seconds > 59 || (m.groupValues[1].isNotEmpty() && minutes > 59)) null
    else TimestampMatch(hours * 3600 + minutes * 60 + seconds, m.range.first, m.range.last + 1)
}.toList()

/**
 * Chapters the way YouTube reads them from a description: a run of lines that start with a timestamp, the first at
 * 0:00, in increasing order, at least three of them. Anything less is just a mention of times, not a chapter list.
 */
fun chaptersFromDescription(description: String, durationSeconds: Long = Long.MAX_VALUE): List<Chapter> {
    val parsed = description.lineSequence().mapNotNull { raw ->
        val line = raw.trim().trimStart('-', '*', '•', ' ', '>', '▶', '►')
        val m = findTimestamps(line).firstOrNull() ?: return@mapNotNull null
        if (m.start != 0) return@mapNotNull null
        val title = line.substring(m.endExclusive).trim().trimStart('-', '–', '—', ':', ')', ']', ' ').trim()
        if (title.isEmpty()) null else Chapter(title, m.seconds)
    }.toList()
    if (parsed.firstOrNull()?.startSeconds != 0L) return emptyList()
    val increasing = buildList<Chapter> {
        for (c in parsed) if (isEmpty() || c.startSeconds > last().startSeconds) add(c)
    }.filter { it.startSeconds < durationSeconds }
    return if (increasing.size >= 3) increasing else emptyList()
}

/** The chapters to show: those the extractor gave, else any found in the description. */
fun effectiveChapters(fromSource: List<Chapter>, description: String, durationSeconds: Long): List<Chapter> =
    fromSource.ifEmpty { chaptersFromDescription(description, durationSeconds) }

/** The chapter playing at [positionMs], or null before the first (or with no chapters). */
fun currentChapter(chapters: List<Chapter>, positionMs: Long): Chapter? =
    chapters.lastOrNull { it.startSeconds * 1000 <= positionMs }

fun formatTimestamp(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}" else "$m:${sec.toString().padStart(2, '0')}"
}

/** Where chapter [index] ends, in seconds: where the next one starts, or the end of the video (0 when that is not known). */
fun chapterEndSeconds(chapters: List<Chapter>, index: Int, durationMs: Long): Long =
    chapters.getOrNull(index + 1)?.startSeconds ?: (durationMs / 1000)

/** How far through chapter [index] the playhead is, 0..1 (0 before it, 1 after it). */
fun chapterProgress(chapters: List<Chapter>, index: Int, positionMs: Long, durationMs: Long): Float {
    val start = chapters.getOrNull(index)?.startSeconds?.times(1000) ?: return 0f
    val end = chapterEndSeconds(chapters, index, durationMs) * 1000
    if (end <= start) return 0f
    return ((positionMs - start).toFloat() / (end - start)).coerceIn(0f, 1f)
}
