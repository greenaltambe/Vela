package com.greenaltambe.vela.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeShelvesTest {
    @Test fun anEmptyOrDamagedValueMeansTheDefault() {
        assertEquals(HomeShelves.default, HomeShelves.parse(""))
        assertEquals(HomeShelves.default, HomeShelves.parse("nonsense,other"))
    }

    @Test fun theOrderAndTheChoiceAreKept() {
        val chosen = listOf(HomeShelf.SHORTS, HomeShelf.CONTINUE)
        assertEquals(chosen, HomeShelves.parse(HomeShelves.encode(chosen)))
        assertEquals(listOf(HomeShelf.BECAUSE), HomeShelves.parse("because,BECAUSE,unknown"))
    }

    @Test fun allShelvesCanBeSwitchedOff() {
        assertEquals(emptyList(), HomeShelves.parse(HomeShelves.encode(emptyList())))
    }

    @Test fun theCustomizeListShowsEnabledShelvesFirst() {
        val ordered = HomeShelves.ordered(listOf(HomeShelf.SHORTS))
        assertEquals(HomeShelf.SHORTS, ordered.first()); assertEquals(HomeShelf.entries.size, ordered.size)
    }
}
