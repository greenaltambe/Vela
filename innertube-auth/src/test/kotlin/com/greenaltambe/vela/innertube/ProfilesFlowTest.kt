package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.*
import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.createDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

class ProfilesFlowTest {
    private class FixedClock(val at: Instant = Instant.fromEpochSeconds(1_700_000_000)) : Clock { override fun now() = at }
    private val audit = mutableListOf<AuditEntry>()
    private val seenPageIds = mutableListOf<String?>()
    private var selectedName = "Brand"        // who accounts_list says is selected
    private var fail = false
    private val db = createDatabase(JvmDatabaseDriverFactory())
    private val accounts = AccountManager(db, CredentialStore(InMemorySecretStore()))
    private val account = accounts.addGoogle(GoogleIdentity("T", "t@example.com", null, "UCx"), CookieJar(listOf(Cookie("SID", "s", ".google.com"), Cookie("SAPISID", "AbCdEfGhIjKlMnOp/12345", ".youtube.com"))))
    private val registry = GovernorRegistry(FixedClock(), { GovernorLimits(minSpacingMs = 0, browsePerMinute = 100, writesPerHour = 100) }, audit = { audit += it })
    private val store = ProfileStore(InMemorySettingsStore())
    private val accountsList = { name: String -> """{"actions":[{"x":[{"accountItem":{"accountName":{"simpleText":"Main"},"isSelected":${name == "Main"},"datasyncId":"u1||"}},{"accountItem":{"accountName":{"simpleText":"Brand"},"isSelected":${name == "Brand"},"datasyncId":"page9||u1"}}]}]}""" }
    private val transport = FakeTransport { _, url, headers, _ ->
        seenPageIds += headers["X-Goog-PageId"]
        if (fail) RawResponse(500, "", emptyMap(), "") else RawResponse(200, if ("accounts_list" in url) accountsList(if (headers["X-Goog-PageId"] != null) selectedName else "Main") else """{"responseContext":{}}""", emptyMap(), "")
    }
    private val profiles = InnertubeAccountProfiles(accounts, registry, store, transport, WebClient("WEB", "2.0", "en", "US", "UA"), FixedClock())
    private val brand = AccountProfile("Brand", "page9")

    @Test fun aConfirmedProfileIsStoredAndOnlyThenSendsThePageId() = runBlocking<Unit> {
        assertEquals(listOf("Main", "Brand"), profiles.list()!!.map { it.name }); assertEquals(listOf<String?>(null), seenPageIds, "listing never carries a page id")
        assertIs<ActionResult.Ok>(profiles.select(brand))
        assertEquals(ChosenProfile(brand, true), profiles.current()); assertEquals("page9", seenPageIds.last(), "the check itself was made as that profile")
        seenPageIds.clear()
        InnertubeAccountReads(accounts, registry, transport, WebClient("WEB", "2.0", "en", "US", "UA"), FixedClock(), profiles = store).playlists()
        assertTrue(seenPageIds.isNotEmpty() && seenPageIds.all { it == "page9" })
        assertTrue(audit.isNotEmpty() && audit.all { it.accountId == account.id }, "every call went through the account's governor")
    }

    @Test fun aWrongIdentityOrAFailedCheckRevertsAndStoresNothing() = runBlocking<Unit> {
        selectedName = "Someone else"
        val wrong = profiles.select(brand)
        assertIs<ActionResult.Failed>(wrong); assertTrue("was not selected" in wrong.message); assertNull(profiles.current())
        selectedName = "Brand"; fail = true
        assertIs<ActionResult.Failed>(profiles.select(brand)); assertNull(profiles.current())
    }

    @Test fun theDefaultProfileSendsNoPageIdAndChoosingItClearsTheChoice() = runBlocking<Unit> {
        profiles.select(brand); assertNotNull(profiles.current())
        assertIs<ActionResult.Ok>(profiles.select(null)); assertNull(profiles.current()); seenPageIds.clear()
        InnertubeAccountReads(accounts, registry, transport, WebClient("WEB", "2.0", "en", "US", "UA"), FixedClock(), profiles = store).playlists()
        assertTrue(seenPageIds.all { it == null })
    }

    @Test fun nothingIsWrittenAsAnUnverifiedProfileButTheDefaultAndAVerifiedOneMayWrite() = runBlocking<Unit> {
        val actions = InnertubeAccountActions(accounts, registry, transport, WebClient("WEB", "2.0", "en", "US", "UA"), FixedClock(), profiles = store)
        store.save(account.id, ChosenProfile(brand, verified = false))
        val blocked = actions.subscribe("UC1"); assertIs<ActionResult.Failed>(blocked); assertTrue("not verified" in blocked.message)
        assertTrue(audit.none { it.kind == CallKind.WRITE.name }, "no write was even attempted")
        store.save(account.id, ChosenProfile(brand, verified = true)); assertIs<ActionResult.Ok>(actions.subscribe("UC1"))
        store.clear(account.id); assertIs<ActionResult.Ok>(actions.subscribe("UC2"))
    }

    @Test fun theGovernorStillRefusesAProfileCheckWhenTheAccountIsPaused() = runBlocking<Unit> {
        registry.forAccount(account.id).trip(BlockSignal.HTTP_429)
        val r = profiles.select(brand)
        assertIs<ActionResult.Failed>(r); assertNull(profiles.current())
    }
}
