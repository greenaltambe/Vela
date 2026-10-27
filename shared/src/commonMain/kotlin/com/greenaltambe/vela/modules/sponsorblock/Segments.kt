package com.greenaltambe.vela.modules.sponsorblock

import kotlinx.serialization.Serializable

/** What SponsorBlock calls a kind of segment; [id] is its API name and the key of the Look's marker colours. */
enum class SegmentCategory(val id: String, val label: String) {
    SPONSOR("sponsor", "Sponsor"),
    SELFPROMO("selfpromo", "Self promotion"),
    INTERACTION("interaction", "Interaction reminder"),
    INTRO("intro", "Intro"),
    OUTRO("outro", "Outro / endcards"),
    PREVIEW("preview", "Preview / recap"),
    MUSIC_OFFTOPIC("music_offtopic", "Non-music section"),
    FILLER("filler", "Filler / tangent"),
    POI_HIGHLIGHT("poi_highlight", "Highlight"),
    EXCLUSIVE_ACCESS("exclusive_access", "Exclusive access");

    companion object {
        fun fromId(id: String): SegmentCategory? = entries.firstOrNull { it.id == id }
    }
}

/** What to do on entering a segment of a category. */
enum class SegmentAction { SKIP, MUTE, PROMPT, IGNORE }

@Serializable
data class Segment(
    val uuid: String,
    val category: String,
    /** The API's actionType: "skip", "mute", "poi" (a highlight point) or "full" (a label for the whole video). */
    val actionType: String,
    val startMs: Long,
    val endMs: Long,
    val votes: Int = 0,
    val locked: Boolean = false,
    val description: String = "",
) {
    val isPoint: Boolean get() = actionType == "poi" || endMs <= startMs
    val isFullVideoLabel: Boolean get() = actionType == "full"
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
}
