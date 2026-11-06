package com.greenaltambe.vela.home

import com.greenaltambe.vela.core.settings.HomeShelf
import com.greenaltambe.vela.core.settings.HomeShelf.*
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeLayoutTest {
    private fun plan(shelves: List<HomeShelf>, step: Int = 5) = planSlots(shelves, step).map { it.shelf to it.afterVideos }

    @Test fun continueWatchingGoesFirstAndTheOthersAreSpreadThroughTheList() {
        assertEquals(listOf(CONTINUE to 0, SUBSCRIPTIONS to 3, BECAUSE to 8, SHORTS to 13), plan(listOf(SUBSCRIPTIONS, CONTINUE, BECAUSE, SHORTS)))
    }

    @Test fun theViewersOrderIsKept() {
        assertEquals(listOf(SHORTS to 3, INTERESTS to 8), plan(listOf(SHORTS, INTERESTS)))
    }

    @Test fun theStepFollowsTheDensity() {
        assertEquals(listOf(BECAUSE to 3, CHANNELS to 11), plan(listOf(BECAUSE, CHANNELS), step = 8))
        assertEquals(listOf(BECAUSE to 3, CHANNELS to 6), plan(listOf(BECAUSE, CHANNELS), step = 3))
    }

    @Test fun aShelfAppearsOnceAndNoShelvesMeansNoSlots() {
        assertEquals(listOf(BECAUSE to 3), plan(listOf(BECAUSE, BECAUSE)))
        assertEquals(emptyList(), plan(emptyList()))
    }

    @Test fun thePlacesDoNotDependOnHowManyVideosHaveLoaded() {
        // The same call is made while the list loads and after it has grown; there is nothing to vary, so a shelf never moves.
        assertEquals(plan(listOf(SUBSCRIPTIONS, BECAUSE)), plan(listOf(SUBSCRIPTIONS, BECAUSE)))
        assertEquals(listOf(SUBSCRIPTIONS to 3, BECAUSE to 8), plan(listOf(SUBSCRIPTIONS, BECAUSE)))
    }
}
