package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.*
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.createDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

/** The hourly limit of account actions: readable words, and a write that waits instead of being thrown away. */
class WriteBudgetTest {
    private class FixedClock(val at: Instant = Instant.fromEpochSeconds(1_700_000_000)) : Clock { override fun now() = at }
    private val db = createDatabase(JvmDatabaseDriverFactory())
    private val accounts = AccountManager(db, CredentialStore(InMemorySecretStore()))
    private val account = accounts.addGoogle(GoogleIdentity("T", "t@example.com", null, "UCx"), CookieJar(listOf(Cookie("SID", "s", ".google.com"), Cookie("SAPISID", "AbCdEfGhIjKlMnOp/12345", ".youtube.com"))))
    private val registry = GovernorRegistry(FixedClock(), { GovernorLimits(minSpacingMs = 0, browsePerMinute = 100, writesPerHour = 2) })
    private val transport = FakeTransport { _, _, _, _ -> RawResponse(200, """{"responseContext":{}}""", emptyMap(), "") }
    private val actions = InnertubeAccountActions(accounts, registry, transport, WebClient("WEB", "2.0", "en", "US", "UA"), FixedClock())

    @Test fun theWriteAfterTheLimitIsQueuedAndKeptLocalNotRolledBack() = runBlocking<Unit> {
        assertIs<ActionResult.Ok>(actions.rate("a", VideoRating.LIKE))
        assertIs<ActionResult.Ok>(actions.rate("b", VideoRating.LIKE))
        val third = actions.rate("c", VideoRating.LIKE)
        assertIs<ActionResult.Queued>(third)
        assertTrue("after" in third.message && "UTC" !in third.message, third.message)
        assertEquals(1, actions.queuedCount)
    }

    @Test fun aLaterWriteOnTheSameThingReplacesTheQueuedOne() = runBlocking<Unit> {
        actions.rate("a", VideoRating.LIKE); actions.rate("b", VideoRating.LIKE)
        assertIs<ActionResult.Queued>(actions.rate("c", VideoRating.LIKE))
        assertIs<ActionResult.Queued>(actions.rate("c", VideoRating.NONE))   // like, then un-like: only the last is sent
        assertEquals(1, actions.queuedCount)
        assertIs<ActionResult.Queued>(actions.subscribe("UC1"))
        assertEquals(2, actions.queuedCount)
    }

    @Test fun anActionThatIsNotQueuedSaysItInPlainLocalTime() = runBlocking<Unit> {
        actions.rate("a", VideoRating.LIKE); actions.rate("b", VideoRating.LIKE)
        val (result, _) = actions.createPlaylist("Mix")
        assertIs<ActionResult.Failed>(result)
        assertTrue(result.message.startsWith("Hourly account-action limit reached. Try again after "), result.message)
        assertTrue("budget" !in result.message && "UTC" !in result.message)
    }
}
