package com.greenaltambe.vela.library

/** One subtitle line to show from [startMs] until [endMs]. */
data class Cue(val startMs: Long, val endMs: Long, val text: String)

/**
 * WebVTT as cues: timing lines (`00:01.000 --> 00:03.500`, hours optional, settings after the times ignored), the text under them with tags and
 * entities removed. YouTube's automatic captions roll: each cue repeats the previous cue's last line first, which is dropped here so a line is not shown twice.
 */
object Vtt {
    private val timing = Regex("""((?:\d+:)?\d{1,2}:\d{2}[.,]\d{3})\s*-->\s*((?:\d+:)?\d{1,2}:\d{2}[.,]\d{3})""")
    private val tag = Regex("<[^>]*>")

    fun parse(text: String): List<Cue> {
        val cues = mutableListOf<Cue>()
        var previousLast: String? = null
        for (block in text.replace("\r\n", "\n").split("\n\n")) {
            val lines = block.lines()
            val at = lines.indexOfFirst { timing.containsMatchIn(it) }
            if (at < 0) continue
            val m = timing.find(lines[at])!!
            var body = lines.drop(at + 1).map { clean(it) }.filter { it.isNotBlank() }
            if (body.size > 1 && body.first() == previousLast) body = body.drop(1)
            if (body.isEmpty()) continue
            previousLast = body.last()
            cues += Cue(millis(m.groupValues[1]), millis(m.groupValues[2]), body.joinToString("\n"))
        }
        return cues.sortedBy { it.startMs }
    }

    /** The cue showing at [positionMs], or null. */
    fun at(cues: List<Cue>, positionMs: Long): Cue? {
        var lo = 0; var hi = cues.size - 1; var found = -1
        while (lo <= hi) { val mid = (lo + hi) / 2; if (cues[mid].startMs <= positionMs) { found = mid; lo = mid + 1 } else hi = mid - 1 }
        // Cues may overlap slightly: look back a few for one still on.
        for (i in found downTo maxOf(0, found - 3)) if (positionMs < cues[i].endMs) return cues[i]
        return null
    }

    private fun clean(line: String) = decodeEntities(line.replace(tag, "")).trim()

    private fun millis(t: String): Long {
        val parts = t.replace(',', '.').split(':')
        val seconds = parts.last().toDouble()
        val minutes = parts[parts.size - 2].toLong()
        val hours = if (parts.size == 3) parts[0].toLong() else 0L
        return ((hours * 3600 + minutes * 60) * 1000 + seconds * 1000).toLong()
    }
}
