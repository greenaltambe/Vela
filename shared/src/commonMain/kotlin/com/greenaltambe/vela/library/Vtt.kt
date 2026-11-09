package com.greenaltambe.vela.library

/** A stretch of caption text with one look. [color] is ARGB, null for "the viewer's colour". */
data class CaptionRun(val text: String, val color: Int? = null, val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false)

enum class CueAlign { START, CENTER, END }

/**
 * Where the creator put a cue, as fractions of the picture (0..1): [line] is the box's top edge (a percentage `line:` setting; null = at the bottom as usual),
 * [position] and [size] place it across, [align] says how the text sits in its box. Only set when the file asked for something other than the default.
 */
data class CuePlacement(val line: Float? = null, val position: Float? = null, val size: Float? = null, val align: CueAlign = CueAlign.CENTER)

/** One subtitle to show from [startMs] until [endMs]: [text] is the plain words (lines joined with newlines), [lines] the same with each run's look. */
data class Cue(
    val startMs: Long, val endMs: Long, val text: String,
    val lines: List<List<CaptionRun>> = emptyList(),
    val placement: CuePlacement? = null,
)

/**
 * WebVTT as cues: timing lines (`00:01.000 --> 00:03.500`, hours optional) with their settings (`line`, `position`, `size`, `align`), the `STYLE` block's
 * `::cue(c.name) { color: ... }` colours, and the text with its `<c.name>`, `<b>`, `<i>` and `<u>` looks kept as runs (word timestamps and unknown tags are dropped).
 * YouTube's automatic captions roll: each cue repeats the previous cue's last line first, which is dropped so a line is not shown twice.
 */
object Vtt {
    private val timing = Regex("""((?:\d+:)?\d{1,2}:\d{2}[.,]\d{3})\s*-->\s*((?:\d+:)?\d{1,2}:\d{2}[.,]\d{3})([^\n]*)""")
    private val styleRule = Regex("""::cue\(\s*c\.([A-Za-z0-9_-]+)\s*\)\s*\{([^}]*)\}""")
    private val cssColor = Regex("""(?<![-\w])color\s*:\s*([^;}]+)""")
    private val stamp = Regex("""\d+:\d{2}(:\d{2})?[.,]\d{3}""")

    fun parse(text: String): List<Cue> {
        val all = text.replace("\r\n", "\n")
        val firstCue = timing.find(all)?.range?.first ?: return emptyList()
        val styles = styleColors(all.substring(0, firstCue))
        val cues = mutableListOf<Cue>()
        var previousLast: String? = null
        for (block in all.split("\n\n")) {
            val lines = block.lines()
            val at = lines.indexOfFirst { timing.containsMatchIn(it) }
            if (at < 0) continue
            val m = timing.find(lines[at])!!
            var body = toLines(lines.drop(at + 1).joinToString("\n"), styles)
            if (body.size > 1 && plain(body.first()) == previousLast) body = body.drop(1)
            if (body.isEmpty()) continue
            previousLast = plain(body.last())
            cues += Cue(millis(m.groupValues[1]), millis(m.groupValues[2]), body.joinToString("\n") { plain(it) }, body, placement(m.groupValues[3]))
        }
        return cues.sortedBy { it.startMs }
    }

    /** The cue showing at [positionMs], or null (the latest one that has started and not ended). */
    fun at(cues: List<Cue>, positionMs: Long): Cue? {
        val found = lastStarted(cues, positionMs)
        // Cues may overlap slightly: look back a few for one still on.
        for (i in found downTo maxOf(0, found - 3)) if (positionMs < cues[i].endMs) return cues[i]
        return null
    }

    /**
     * Every cue showing at [positionMs]: the creator-placed ones all (a chat overlay and the speech can be on at once), and of the ordinary bottom ones only
     * the latest, as [at] (automatic captions overlap while they roll). Placed cues sharing one box show the latest of them.
     */
    fun active(cues: List<Cue>, positionMs: Long): List<Cue> {
        val found = lastStarted(cues, positionMs)
        if (found < 0) return emptyList()
        val on = (found downTo maxOf(0, found - LOOK_BACK)).map { cues[it] }.filter { positionMs < it.endMs }
        val placed = on.filter { it.placement != null }.groupBy { it.placement }.map { (_, same) -> same.maxBy { it.startMs } }
        val plain = on.firstOrNull { it.placement == null }
        return listOfNotNull(plain) + placed
    }

    private fun lastStarted(cues: List<Cue>, positionMs: Long): Int {
        var lo = 0; var hi = cues.size - 1; var found = -1
        while (lo <= hi) { val mid = (lo + hi) / 2; if (cues[mid].startMs <= positionMs) { found = mid; lo = mid + 1 } else hi = mid - 1 }
        return found
    }

    private fun plain(line: List<CaptionRun>) = line.joinToString("") { it.text }.trim()

    private fun styleColors(header: String): Map<String, Int> =
        styleRule.findAll(header).mapNotNull { r -> cssColor.find(r.groupValues[2])?.let { c -> CaptionColors.parse(c.groupValues[1])?.let { r.groupValues[1] to it } } }.toMap()

    private class Look(val color: Int?, val bold: Boolean, val italic: Boolean, val underline: Boolean)

    /** The payload as lines of runs. Tags may open on one line and close on another (a chat message split over lines), so it is read as a whole. */
    private fun toLines(payload: String, styles: Map<String, Int>): List<List<CaptionRun>> {
        val stack = ArrayList<Look>()
        val runs = ArrayList<CaptionRun>()
        val buf = StringBuilder()
        fun current() = stack.lastOrNull()
        fun flush() {
            if (buf.isEmpty()) return
            val l = current()
            runs += CaptionRun(decodeEntities(buf.toString()), l?.color, l?.bold == true, l?.italic == true, l?.underline == true)
            buf.clear()
        }
        var i = 0
        while (i < payload.length) {
            val ch = payload[i]
            if (ch != '<') { buf.append(ch); i++; continue }
            val end = payload.indexOf('>', i)
            if (end < 0) { buf.append(ch); i++; continue }
            val tag = payload.substring(i + 1, end).trim()
            i = end + 1
            when {
                stamp.matches(tag) -> {}
                tag.startsWith("/") -> { flush(); if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
                else -> {
                    flush()
                    val name = tag.takeWhile { it != '.' && !it.isWhitespace() }
                    val classes = tag.substring(name.length).split('.', ' ').filter { it.isNotEmpty() }
                    val parent = current()
                    val color = if (name == "c") classes.firstNotNullOfOrNull { styles[it] ?: CaptionColors.fromClass(it) } else null
                    stack += Look(color ?: parent?.color, parent?.bold == true || name == "b", parent?.italic == true || name == "i", parent?.underline == true || name == "u")
                }
            }
        }
        flush()
        // Split the runs at line breaks, trim the ends of each line, and drop lines with nothing to read.
        val lines = ArrayList<List<CaptionRun>>()
        var line = ArrayList<CaptionRun>()
        fun endLine() {
            if (line.isNotEmpty()) {
                line[0] = line[0].copy(text = line[0].text.trimStart())
                line[line.lastIndex] = line.last().copy(text = line.last().text.trimEnd())
                val kept = line.filter { it.text.isNotEmpty() }
                if (kept.any { it.text.isNotBlank() }) lines += kept
            }
            line = ArrayList()
        }
        for (r in runs) {
            val parts = r.text.split('\n')
            parts.forEachIndexed { k, part ->
                if (k > 0) endLine()
                if (part.isNotEmpty()) line += r.copy(text = part)
            }
        }
        endLine()
        return lines
    }

    private fun placement(settings: String): CuePlacement? {
        if (settings.isBlank()) return null
        val map = settings.trim().split(Regex("\\s+")).mapNotNull { s -> s.substringBefore(':', "").takeIf { it.isNotEmpty() && ':' in s }?.let { it to s.substringAfter(':') } }.toMap()
        fun pct(key: String): Float? = map[key]?.substringBefore(',')?.takeIf { it.endsWith("%") }?.dropLast(1)?.toFloatOrNull()?.let { (it / 100f).coerceIn(0f, 1f) }
        val line = pct("line"); val position = pct("position"); val size = pct("size")
        val align = when (map["align"]) { "start", "left" -> CueAlign.START; "end", "right" -> CueAlign.END; else -> CueAlign.CENTER }
        // Only a cue the creator really placed counts: a line (top of the picture) or a box narrower than the picture. YouTube writes `align:start position:0%`
        // on every automatic caption (measured on 43 files, 2026-11); that is not a placement, and those captions stay centred at the bottom.
        if (line == null && (size == null || size >= 0.99f)) return null
        return CuePlacement(line, position, size, align)
    }

    private fun millis(t: String): Long {
        val parts = t.replace(',', '.').split(':')
        val seconds = parts.last().toDouble()
        val minutes = parts[parts.size - 2].toLong()
        val hours = if (parts.size == 3) parts[0].toLong() else 0L
        return ((hours * 3600 + minutes * 60) * 1000 + seconds * 1000).toLong()
    }

    private const val LOOK_BACK = 12
}
