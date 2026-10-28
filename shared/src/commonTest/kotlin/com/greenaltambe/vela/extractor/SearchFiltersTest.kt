package com.greenaltambe.vela.extractor

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchFiltersTest {
    private fun v(id: String, seconds: Long?, views: Long?) = VideoSummary(id, id, "UC", "C", durationSeconds = seconds, viewCount = views)
    private val items = listOf(v("short", 100, 5), v("mid", 600, 50), v("long", 3600, 10), v("unknown", null, null))

    @Test fun durationNarrowsAndUnknownLengthsOnlyPassWhenNothingIsAsked() {
        assertEquals(4, SearchFilters().apply(items).size)
        assertEquals(listOf("short"), SearchFilters(DurationFilter.SHORT).apply(items).map { it.id })
        assertEquals(listOf("mid"), SearchFilters(DurationFilter.MEDIUM).apply(items).map { it.id })
        assertEquals(listOf("long"), SearchFilters(DurationFilter.LONG).apply(items).map { it.id })
    }

    @Test fun sortsOrderLoadedResultsAndPutUnknownsLast() {
        assertEquals(listOf("mid", "long", "short", "unknown"), SearchFilters(sort = SearchSort.VIEWS).apply(items).map { it.id })
        assertEquals(listOf("short", "mid", "long", "unknown"), SearchFilters(sort = SearchSort.SHORTEST).apply(items).map { it.id })
        assertEquals(listOf("long", "mid", "short", "unknown"), SearchFilters(sort = SearchSort.LONGEST).apply(items).map { it.id })
        assertEquals(items.map { it.id }, SearchFilters().apply(items).map { it.id }, "relevance keeps the source order")
    }
}
