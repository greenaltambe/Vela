package com.greenaltambe.vela.modules.sponsorblock

/** What the engine asks the player to do for a position. */
sealed interface SegmentEffect {
    /** Seek to [toMs], past [segments]. [fromMs] is where the first of them starts: what Undo returns to. */
    data class Skip(val toMs: Long, val fromMs: Long, val segments: List<Segment>) : SegmentEffect

    data class Mute(val muted: Boolean) : SegmentEffect

    /** Offer a "Skip" button for [segment] (null: nothing to offer any more). */
    data class Prompt(val segment: Segment?) : SegmentEffect
}

/**
 * Decides, from the playback position alone, when to skip, mute or prompt. Pure and deterministic: it makes no calls,
 * so its rules are testable without a player.
 *
 * - **Skip-once.** A segment that has been skipped, or whose skip was undone, is never skipped again for this video.
 * - **Seeks into segments** are handled like playing into one: any position inside an unhandled SKIP segment skips.
 * - **Overlap and chains.** Overlapping or touching skip segments are skipped as one jump to the furthest end.
 * - **Minimum duration.** Segments shorter than [minDurationMs] are ignored (highlight points are exempt: they have no length).
 * - **Prompt** stays offered for as long as the position is inside the segment, or is at/before a highlight point
 *   within [pointWindowMs].
 */
class SegmentEngine(
    private val actionFor: (SegmentCategory) -> SegmentAction,
    private val minDurationMs: Long = 0,
    private val pointWindowMs: Long = 5_000,
) {
    private var segments: List<Segment> = emptyList()
    private val handled = HashSet<String>()
    private var muted = false
    private var prompted: String? = null

    fun load(list: List<Segment>) {
        segments = list.filter { !it.isFullVideoLabel && (it.isPoint || it.durationMs >= minDurationMs) }.sortedBy { it.startMs }
        handled.clear(); muted = false; prompted = null
    }

    /** Segments to show as markers: everything that would do something (not IGNORE). */
    fun visible(): List<Segment> = segments.filter { actionOf(it) != SegmentAction.IGNORE }

    /** The user said "undo" or acted on a prompt: do not touch these again. */
    fun markHandled(ids: Collection<String>) { handled.addAll(ids) }

    fun onPosition(positionMs: Long): List<SegmentEffect> {
        val effects = mutableListOf<SegmentEffect>()

        // Skip.
        val entered = segments.filter { s ->
            !s.isPoint && s.uuid !in handled && actionOf(s) == SegmentAction.SKIP && positionMs >= s.startMs && positionMs < s.endMs
        }
        if (entered.isNotEmpty()) {
            val group = mutableListOf<Segment>()
            var end = entered.maxOf { it.endMs }
            group += entered
            // Chain: a following skip segment that starts at or before the current end extends the jump.
            var extended = true
            while (extended) {
                extended = false
                for (s in segments) {
                    if (s !in group && !s.isPoint && s.uuid !in handled && actionOf(s) == SegmentAction.SKIP && s.startMs <= end && s.endMs > end) {
                        group += s; end = s.endMs; extended = true
                    }
                }
            }
            handled.addAll(group.map { it.uuid })
            effects += SegmentEffect.Skip(toMs = end, fromMs = group.minOf { it.startMs }, segments = group.sortedBy { it.startMs })
            return effects + stateEffects(end) // evaluate mute/prompt at the landing point
        }
        return stateEffects(positionMs)
    }

    private fun stateEffects(positionMs: Long): List<SegmentEffect> {
        val effects = mutableListOf<SegmentEffect>()
        val shouldMute = segments.any { s -> !s.isPoint && actionOf(s) == SegmentAction.MUTE && positionMs >= s.startMs && positionMs < s.endMs }
        if (shouldMute != muted) { muted = shouldMute; effects += SegmentEffect.Mute(shouldMute) }

        val prompt = segments.firstOrNull { s ->
            s.uuid !in handled && actionOf(s) == SegmentAction.PROMPT &&
                if (s.isPoint) positionMs >= s.startMs - pointWindowMs && positionMs <= s.startMs + pointWindowMs
                else positionMs >= s.startMs && positionMs < s.endMs
        }
        if (prompt?.uuid != prompted) { prompted = prompt?.uuid; effects += SegmentEffect.Prompt(prompt) }
        return effects
    }

    private fun actionOf(s: Segment): SegmentAction {
        val category = SegmentCategory.fromId(s.category) ?: return SegmentAction.IGNORE
        val configured = actionFor(category)
        // A "mute" segment can only be muted or ignored; a highlight point can only be prompted (jump to) or ignored.
        return when {
            s.actionType == "mute" && configured == SegmentAction.SKIP -> SegmentAction.MUTE
            s.isPoint && configured != SegmentAction.IGNORE -> SegmentAction.PROMPT
            else -> configured
        }
    }
}
