@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.auth.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/** The profile menu switches accounts, and nothing one account holds is visible from the other. */
class AccountSwitcherUiTest {
    private val jar = CookieJar(listOf(Cookie("SID", "s", ".google.com"), Cookie("SAPISID", "p", ".youtube.com")))

    @Test fun theMenuListsAccountsSwitchesAndEachAccountKeepsItsOwnData() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(withDownloads = false)
        val accounts = f.services.accounts
        val g = accounts.addGoogle(GoogleIdentity("Second Person", "second@example.com", null, "UCsecond"), jar)
        assertEquals(g.id, accounts.active.value.id)
        // Data of the Google account only.
        f.services.subscriptions.subscribe("UCg", "Only on the Google account", null, Clock.System.now())
        setContent { AppUnderTest(f) }
        waitForIdle()
        onNodeWithTag("profile.button").performClick(); waitForIdle()
        onNodeWithTag("profile.switch").performClick(); waitForIdle()
        onNodeWithTag("profile.switchTo").performClick(); waitForIdle()
        assertEquals("local", accounts.active.value.id)
        assertTrue(!f.services.subscriptions.isSubscribed("UCg"), "the local account cannot see the Google account's subscription")
        accounts.switchTo(g.id)
        assertTrue(f.services.subscriptions.isSubscribed("UCg"), "switching back finds it")
    }
}
