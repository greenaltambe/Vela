package com.greenaltambe.vela.extractor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PagedListTest {
    private fun v(id: String) = VideoSummary(id, "t$id", "c", "C")
    private fun page(vararg ids: String, next: String? = null) = Page(ids.map(::v), next)

    @Test fun appendsNewItemsInOrderAndTracksTheContinuation() {
        val list = videoPagedList().append(page("a", "b", next = "t1")).append(page("c", next = null))
        assertEquals(listOf("a", "b", "c"), list.items.map { it.id })
        assertNull(list.continuation)
    }

    @Test fun repeatsAcrossPagesAreDroppedAndTheFirstOccurrenceKeepsItsPlace() {
        val first = videoPagedList().append(page("a", "b", next = "t1"))
        val second = first.append(page("b", "c", "a", "d", next = "t2"))
        assertEquals(listOf("a", "b", "c", "d"), second.items.map { it.id })
        assertEquals(2, second.lastDuplicates)
        assertEquals("t2", second.continuation, "token still advances")
    }

    @Test fun repeatsWithinOnePageAreDropped() {
        assertEquals(listOf("a", "b"), videoPagedList().append(page("a", "a", "b")).items.map { it.id })
    }

    @Test fun aPageOfOnlyRepeatsStillAdvancesSoPagingDoesNotStall() {
        val list = videoPagedList().append(page("a", next = "t1")).append(page("a", next = "t2"))
        assertEquals(listOf("a"), list.items.map { it.id })
        assertEquals("t2", list.continuation)
    }

    @Test fun appendDoesNotMutateTheOriginal() {
        val first = videoPagedList().append(page("a", next = "t1"))
        first.append(page("b"))
        assertEquals(listOf("a"), first.items.map { it.id })
        assertEquals("t1", first.continuation)
    }

    @Test fun replaceStartsOver() {
        val list = videoPagedList().append(page("a", "b", next = "t1")).replace(page("b", "z"))
        assertEquals(listOf("b", "z"), list.items.map { it.id })
    }
}
