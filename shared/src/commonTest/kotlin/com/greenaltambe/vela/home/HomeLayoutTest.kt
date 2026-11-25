package com.greenaltambe.vela.home

import com.greenaltambe.vela.auth.FeedGroup
import com.greenaltambe.vela.auth.FeedGroupKind
import com.greenaltambe.vela.core.settings.HomeShelf
import com.greenaltambe.vela.extractor.VideoSummary
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

    // ---- YouTube's groups ----

    private fun video(id: String, short: Boolean = false) = VideoSummary(id, "T $id", "", "", isShort = short)
    private fun group(prefix: String, after: String?, kind: FeedGroupKind = FeedGroupKind.SUGGESTED, size: Int = 3) =
        FeedGroup(kind, prefix, null, (1..size).map { video("$prefix$it", kind == FeedGroupKind.SHORTS) }, after)
    private fun passed(vararg groups: FeedGroup) = groups.flatMap { it.videos }.associateBy { it.id }

    @Test fun theGroupsSwitchDecidesAboutOrdinaryRowsAndTheShortsSettingAboutRowsOfShorts() {
        val games = group("g", null); val shorts = group("s", null, FeedGroupKind.SHORTS)
        fun seen(groupsOn: Boolean, shortsOn: Boolean) = visibleGroups(listOf(games, shorts), passed(games, shorts), groupsOn, shortsOn, false).map { it.group.title }
        assertEquals(listOf("g", "s"), seen(true, true))
        assertEquals(listOf("s"), seen(false, true), "switching groups off leaves the Shorts rows")
        assertEquals(listOf("g"), seen(true, false), "hiding Shorts leaves the other rows")
        assertEquals(emptyList(), seen(false, false))
    }

    @Test fun aShortInsideAnotherRowAlsoNeedsShortsOn() {
        val mixed = FeedGroup(FeedGroupKind.SUGGESTED, "m", null, listOf(video("a"), video("b"), video("c"), video("d", short = true)), null)
        assertEquals(4, visibleGroups(listOf(mixed), passed(mixed), true, true, false).single().group.videos.size)
        assertEquals(listOf("a", "b", "c"), visibleGroups(listOf(mixed), passed(mixed), true, false, false).single().group.videos.map { it.id })
    }

    @Test fun aRowLeftWithFewerThanThreeIsNotDrawnAndItsKeyDoesNotChangeAsVideosAreFilteredOut() {
        val g = group("g", null, size = 4)
        assertEquals("g1", visibleGroups(listOf(g), passed(g) - "g1", true, true, false).single().key, "the key is the row's, not the first video left")
        assertEquals(emptyList(), visibleGroups(listOf(g), passed(g) - "g1" - "g2", true, true, false))
    }

    @Test fun aHomeFilterHidesTheRows() {
        val g = group("g", null)
        assertEquals(emptyList(), visibleGroups(listOf(g), passed(g), true, true, filtering = true))
    }

    private fun ids(n: Int) = (1..n).map { "v$it" }
    private fun place(groups: List<FeedGroup>, shelves: List<ShelfSlot> = emptyList(), order: List<String> = ids(20), shown: List<String> = order) =
        placeHomeBlocks(shelves, groups.map { ShownGroup(it.videos.first().id, it) }, order, shown).map {
            when (it) { is HomeBlock.Shelf -> it.shelf.name to it.afterVideos; is HomeBlock.Group -> it.shown.group.title to it.afterVideos }
        }

    @Test fun aRowStandsAfterTheVideoItFollowed() {
        assertEquals(listOf("top" to 0, "g" to 2, "h" to 7), place(listOf(group("top", null), group("g", "v2"), group("h", "v7"))))
    }

    @Test fun whenItsVideoIsFilteredOutARowStandsAfterTheOneBefore() {
        val shown = ids(20) - "v4" - "v5"
        assertEquals(listOf("g" to 3), place(listOf(group("g", "v5")), shown = shown))
        assertEquals(listOf("g" to 0), place(listOf(group("g", "v2")), shown = ids(20) - "v1" - "v2"))
    }

    @Test fun aShelfNeverTakesTheSamePlaceAsARow() {
        val shelves = planSlots(listOf(CONTINUE, SUBSCRIPTIONS, BECAUSE), 5)
        assertEquals(
            listOf("CONTINUE" to 0, "top" to 0, "g" to 3, "SUBSCRIPTIONS" to 5, "h" to 4, "BECAUSE" to 8).sortedBy { it.second },
            place(listOf(group("top", null), group("g", "v3"), group("h", "v4")), shelves),
        )
    }

    @Test fun twoShelvesNeverLandOnOnePlaceAfterMoving() {
        val placed = place(listOf(group("g", "v3"), group("h", "v4")), listOf(ShelfSlot(SUBSCRIPTIONS, 3), ShelfSlot(BECAUSE, 5)))
        assertEquals(listOf("g" to 3, "h" to 4, "SUBSCRIPTIONS" to 5, "BECAUSE" to 6), placed)
    }

    @Test fun thePlacesDoNotDependOnHowManyVideosHaveLoaded() {
        // The same call is made while the list loads and after it has grown; there is nothing to vary, so a shelf never moves.
        assertEquals(plan(listOf(SUBSCRIPTIONS, BECAUSE)), plan(listOf(SUBSCRIPTIONS, BECAUSE)))
        assertEquals(listOf(SUBSCRIPTIONS to 3, BECAUSE to 8), plan(listOf(SUBSCRIPTIONS, BECAUSE)))
    }
}
