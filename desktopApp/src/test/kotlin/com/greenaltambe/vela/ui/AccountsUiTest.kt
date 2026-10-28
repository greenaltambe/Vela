@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.auth.*
import com.greenaltambe.vela.ui.nav.Route
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountsUiTest {
    private val jar = CookieJar(listOf(Cookie("SID", "s", ".google.com"), Cookie("SAPISID", "p", ".youtube.com")))
    private val identity = GoogleIdentity("Test Person", "test.person@example.com", null, "UCtest")

    private class NoLogin(val m: AccountManager) : AccountLogin {
        override suspend fun importCookies(netscapeText: String) = LoginResult.Failure("n/a")
        override suspend fun addFromJar(jar: CookieJar) = LoginResult.Failure("n/a")
        override suspend fun checkSession(accountId: String) = AuthState.OK
    }

    @Test fun switchSignOutAndRemoveFollowTheAccountsScreen() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(login = ::NoLogin)
        val accounts = f.services.accounts
        val g = accounts.addGoogle(identity, jar)
        f.navigator.selectTab(Route.Settings); f.navigator.push(Route.Accounts)
        setContent { AppUnderTest(f) }
        waitForIdle()

        // Active account shown, the e-mail masked, never in full.
        onNodeWithTag("accounts.row.${g.id}").assertIsDisplayed()
        onNodeWithText("t***@example.com").assertIsDisplayed()
        assertTrue(runCatching { onNodeWithText("test.person@example.com").assertIsDisplayed() }.isFailure, "full e-mail must not be on screen")

        // Switch to the local account.
        onNodeWithTag("accounts.row.local").performClick(); waitForIdle()
        assertEquals("local", accounts.active.value.id)
        onNodeWithTag("accounts.row.${g.id}").performClick(); waitForIdle()
        assertEquals(g.id, accounts.active.value.id)

        // Sign out: credentials gone, banner shown.
        onNodeWithTag("accounts.menu.${g.id}").performClick(); waitForIdle()
        onNodeWithText("Sign out").performClick(); waitForIdle()
        assertNull(accounts.cookies(g.id))
        onNodeWithTag("accounts.banner").assertIsDisplayed()

        // Remove needs the confirmation.
        onNodeWithTag("accounts.menu.${g.id}").performClick(); waitForIdle()
        onNodeWithText("Remove").performClick(); waitForIdle()
        assertTrue(accounts.get(g.id) != null, "still there until confirmed")
        onNodeWithTag("accounts.removeConfirm").performClick(); waitForIdle()
        assertNull(accounts.get(g.id))
        assertEquals("local", accounts.active.value.id)
    }

    @Test fun signOutEverywhereNeedsConfirmationAndWipesCredentials() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(login = ::NoLogin)
        val a = f.services.accounts.addGoogle(identity, jar)
        f.navigator.selectTab(Route.Settings); f.navigator.push(Route.Accounts)
        setContent { AppUnderTest(f) }
        waitForIdle()
        onNodeWithTag("accounts.signOutAll").performClick(); waitForIdle()
        assertTrue(f.services.accounts.cookies(a.id) != null, "not before confirming")
        onNodeWithTag("accounts.signOutAllConfirm").performClick(); waitForIdle()
        assertNull(f.services.accounts.cookies(a.id))
        assertEquals("local", f.services.accounts.active.value.id)
    }

    @Test fun switchingAccountsSwapsTheSubscriptionsTheScreensSee() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(login = ::NoLogin)
        val localCount = f.services.subscriptions.all().size
        val g = f.services.accounts.addGoogle(identity, jar)
        assertEquals(0, f.services.subscriptions.all().size, "a new account starts empty")
        f.services.subscriptions.subscribe("UC1", "Only on the Google account", null, f.services.clock.now())
        assertEquals(1, f.services.subscriptions.all().size)
        f.services.accounts.switchTo("local")
        assertEquals(localCount, f.services.subscriptions.all().size, "the local account keeps its own")
        f.services.accounts.switchTo(g.id)
        assertEquals(1, f.services.subscriptions.all().size)
    }

    @Test fun onboardingGateNeedsBothAcknowledgementsAndReportingDefaultsOff() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(login = ::NoLogin)
        f.navigator.selectTab(Route.Settings); f.navigator.push(Route.Accounts)
        setContent { AppUnderTest(f) }
        waitForIdle()
        onNodeWithTag("accounts.import").performClick(); waitForIdle()
        onNodeWithTag("onboarding.continue").assertIsNotEnabled()
        onNodeWithTag("onboarding.terms").performClick(); waitForIdle()
        onNodeWithTag("onboarding.continue").assertIsNotEnabled()
        onNodeWithTag("onboarding.throwaway").performClick(); waitForIdle()
        onNodeWithTag("onboarding.continue").assertIsEnabled()
        assertEquals(false, f.services.settings.resolve(com.greenaltambe.vela.core.settings.Settings.accountReportActivity), "reporting stays off unless switched on")
    }

    @Test fun anOpenBreakerShowsABannerOnEveryScreenAndTryNowAsksFirst() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(login = ::NoLogin)
        val g = f.services.accounts.addGoogle(identity, jar)
        val gov = f.services.governors.forAccount(g.id)
        gov.trip(BlockSignal.HTTP_429)
        setContent { AppUnderTest(f) }
        waitForIdle()
        onNodeWithTag("account.banner").assertIsDisplayed()          // on Home
        f.navigator.selectTab(Route.Library); waitForIdle()
        onNodeWithTag("account.banner").assertIsDisplayed()          // and on Library
        onNodeWithTag("account.tryNow").performClick(); waitForIdle()
        assertEquals(BreakerState.OPEN, gov.status.value.state, "nothing happens before confirming")
        onNodeWithTag("account.tryNowConfirm").performClick(); waitForIdle()
        assertEquals(BreakerState.HALF_OPEN, gov.status.value.state)
    }

    @Test fun signedOutAccountShowsTheBannerWithoutAnyNetwork() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(login = ::NoLogin)
        val g = f.services.accounts.addGoogle(identity, jar)
        f.services.accounts.setAuthState(g.id, AuthState.NEEDS_RELOGIN)
        setContent { AppUnderTest(f) }
        waitForIdle()
        onNodeWithTag("account.banner").assertIsDisplayed()
    }

    @Test fun activityScreenListsAuditedRequestsAndPurges() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(login = ::NoLogin)
        val g = f.services.accounts.addGoogle(identity, jar)
        f.services.audit.record(AuditEntry(g.id, f.services.clock.now(), "BROWSE", "www.youtube.com", "ok", "allowed"))
        f.navigator.selectTab(Route.Settings); f.navigator.push(Route.AccountActivity)
        setContent { AppUnderTest(f) }
        waitForIdle()
        onNodeWithText("www.youtube.com", substring = true).assertIsDisplayed()
        onNodeWithTag("activity.purge").performClick(); waitForIdle()
        assertTrue(f.services.audit.recent(g.id).isEmpty())
    }
}
