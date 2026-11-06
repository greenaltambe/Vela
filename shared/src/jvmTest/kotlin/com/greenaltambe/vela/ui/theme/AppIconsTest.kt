package com.greenaltambe.vela.ui.theme

import kotlin.test.*

class AppIconsTest {
    private val set = MaterialIconSet.Default

    @Test fun everyIconSlotIsCoveredByTheMaterialSet() {
        for (n in IconNames.all) assertNotNull(set[n], n)
        assertEquals(IconNames.all.toSet(), set.names, "the set has exactly the slots the app names")
    }

    @Test fun whatCanBeSelectedHasADifferentFilledForm() {
        for (n in IconNames.withFilledForm) assertNotSame(set[n], set.filled(n), "$n: the filled form is a different icon")
        assertEquals(IconNames.withFilledForm.toSet(), set.filledNames)
    }

    @Test fun onePieceIconsHaveNoFilledFormAndFallBackToTheOrdinaryOne() {
        for (n in listOf("search", "close", "back", "share", "settings", "more")) assertSame(set[n], set.filled(n), n)
        assertSame(set["home"], set["home", false]); assertSame(set.filled("home"), set["home", true])
    }
}
