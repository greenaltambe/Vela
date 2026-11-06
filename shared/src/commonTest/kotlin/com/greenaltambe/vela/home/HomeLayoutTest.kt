package com.greenaltambe.vela.home

import com.greenaltambe.vela.core.settings.HomeShelf
import com.greenaltambe.vela.core.settings.HomeShelf.*
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeLayoutTest {
    private fun plan(shelves: List<HomeShelf>, videos: Int, step: Int = 5) = planSlots(shelves, videos, step).map { it.shelf to it.afterVideos }

    @Test fun continueWatchingGoesFirstAndTheOthersAreSpreadThroughTheList() {
        assertEquals(listOf(CONTINUE to 0, SUBSCRIPTIONS to 3, BECAUSE to 8, SHORTS to 13), plan(listOf(SUBSCRIPTIONS, CONTINUE, BECAUSE, SHORTS), 40))
    }

    @Test fun theViewersOrderIsKept() {
        assertEquals(listOf(SHORTS to 3, INTERESTS to 8), plan(listOf(SHORTS, INTERESTS), 30))
    }

    @Test fun theStepFollowsTheDensity() {
        assertEquals(listOf(BECAUSE to 3, CHANNELS to 11), plan(listOf(BECAUSE, CHANNELS), 50, step = 8))
        assertEquals(listOf(BECAUSE to 3, CHANNELS to 6), plan(listOf(BECAUSE, CHANNELS), 50, step = 3))
    }

    @Test fun aShortListHoldsTheShelvesAtItsEndSoHomeIsNeverBlank() {
        assertEquals(listOf(CONTINUE to 0, BECAUSE to 0, CHANNELS to 0), plan(listOf(CONTINUE, BECAUSE, CHANNELS), 0))
        assertEquals(listOf(BECAUSE to 3, CHANNELS to 4), plan(listOf(BECAUSE, CHANNELS), 4))
    }

    @Test fun aShelfAppearsOnceAndNoShelvesMeansNoSlots() {
        assertEquals(listOf(BECAUSE to 3), plan(listOf(BECAUSE, BECAUSE), 20))
        assertEquals(emptyList(), plan(emptyList(), 20))
    }

    @Test fun shelvesMoveToTheirPlacesAsTheListGrows() {
        assertEquals(listOf(SUBSCRIPTIONS to 3, BECAUSE to 5), plan(listOf(SUBSCRIPTIONS, BECAUSE), 5))
        assertEquals(listOf(SUBSCRIPTIONS to 3, BECAUSE to 8), plan(listOf(SUBSCRIPTIONS, BECAUSE), 20))
    }
}
