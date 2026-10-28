package com.greenaltambe.vela.extractor

/**
 * What the search screen can narrow and order by. NewPipeExtractor's YouTube search takes only a content type (measured from its source, v0.26.5:
 * no sort, no duration, no upload date), so these are applied HERE, to the results that were loaded; they work the same whichever tier answered.
 */
enum class DurationFilter(val label: String, private val range: LongRange?) {
    ANY("Any length", null), SHORT("Under 4 min", 0L..239L), MEDIUM("4-20 min", 240L..1200L), LONG("Over 20 min", 1201L..Long.MAX_VALUE);
    fun matches(seconds: Long?) = range == null || (seconds != null && seconds in range)
}

enum class SearchSort(val label: String) { RELEVANCE("Relevance"), VIEWS("Most viewed"), SHORTEST("Shortest"), LONGEST("Longest") }

data class SearchFilters(val duration: DurationFilter = DurationFilter.ANY, val sort: SearchSort = SearchSort.RELEVANCE) {
    val isDefault get() = duration == DurationFilter.ANY && sort == SearchSort.RELEVANCE

    /** Narrows and reorders [items]; "relevance" keeps the source's order. Unknown lengths or view counts sort last. */
    fun apply(items: List<VideoSummary>): List<VideoSummary> {
        val kept = items.filter { duration.matches(it.durationSeconds) || (duration == DurationFilter.ANY) }
        return when (sort) {
            SearchSort.RELEVANCE -> kept
            SearchSort.VIEWS -> kept.sortedByDescending { it.viewCount ?: -1 }
            SearchSort.SHORTEST -> kept.sortedBy { it.durationSeconds ?: Long.MAX_VALUE }
            SearchSort.LONGEST -> kept.sortedByDescending { it.durationSeconds ?: -1 }
        }
    }
}
