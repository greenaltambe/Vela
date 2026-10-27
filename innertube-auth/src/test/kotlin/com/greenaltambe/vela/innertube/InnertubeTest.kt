package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.*
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.createDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

private fun fixture(name: String) = InnertubeTest::class.java.getResource("/fixtures/$name")!!.readText()

class FakeTransport(val responder: (String, String, Map<String, String>, String?) -> RawResponse) : AuthTransport {
    val calls = mutableListOf<Triple<String, String, Map<String, String>>>()
    override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): RawResponse {
        calls += Triple(method, url, headers)
        return responder(method, url, headers, body)
    }
}
private fun ok(body: String, vararg setCookie: String) = RawResponse(200, body, if (setCookie.isEmpty()) emptyMap() else mapOf("Set-Cookie" to setCookie.toList()), "")
private class FixedClock(val at: Instant = Instant.fromEpochSeconds(1_700_000_000)) : Clock { override fun now() = at }

private fun jar(vararg extra: Cookie) = CookieJar(listOf(
    Cookie("SID", "sid-v", ".google.com"), Cookie("SAPISID", "AbCdEfGhIjKlMnOp/1234567890", ".youtube.com"),
    Cookie("__Secure-1PSIDTS", "old-ts", ".youtube.com")) + extra)

class InnertubeTest {
    private fun json(name: String): JsonElement = lenientJson.parseToJsonElement(fixture(name))

    @Test fun accountInfoIsReadFromTheRecordedMenu() {
        val info = InnertubeParsers.accountInfo(json("account_menu.json"))!!
        assertEquals("Test Person", info.displayName)
        assertEquals("test.person@example.com", info.email)
        assertTrue(info.avatarUrl!!.startsWith("https://example.invalid/"))
    }

    @Test fun aMenuWithoutAnActiveAccountIsSignedOut() {
        assertNull(InnertubeParsers.accountInfo(lenientJson.parseToJsonElement("""{"actions":[{"openPopupAction":{"popup":{"multiPageMenuRenderer":{"sections":[]}}}}]}""")))
    }

    @Test fun subscriptionsAreFoundInTheRecordedFeed() {
        val channels = InnertubeParsers.channels(json("subscriptions.json"))
        assertTrue(channels.size > 50, "found ${channels.size}")
        assertTrue(channels.all { it.channelId.startsWith("UCscrubbed") && it.title.isNotBlank() })
    }

    @Test fun parsersSurviveMissingAndRenamedFields() {
        val odd = lenientJson.parseToJsonElement("""{"gridChannelRenderer":{"channelId":"UC1"},"x":[{"gridChannelRenderer":{"title":{"simpleText":"T"}}}],"channelRenderer":{"channelId":"UC2","title":{"runs":[{"text":"A"},{"text":"B"}]},"thumbnail":{"thumbnails":[{"url":"//img/a.png"}]}}}""")
        val got = InnertubeParsers.channels(odd)
        assertEquals(listOf("UC2"), got.map { it.channelId })
        assertEquals("AB", got.single().title)
        assertEquals("https://img/a.png", got.single().avatarUrl)
        assertEquals(emptyList(), InnertubeParsers.channels(lenientJson.parseToJsonElement("[1,\"a\",null]")))
    }

    @Test fun sessionSignsRequestsAsTheBrowserDoes() {
        val t = FakeTransport { _, _, _, _ -> ok("{}") }
        AuthSession(jar(), t, FixedClock(), minGapMs = 0).send("POST", "https://www.youtube.com/youtubei/v1/browse", "{}")
        val h = t.calls.single().third
        assertEquals("SAPISIDHASH 1700000000_6aeb9e08815c5ecdb84affddc7278a326b39e88e", h["Authorization"])
        assertEquals("0", h["X-Goog-AuthUser"]); assertEquals("https://www.youtube.com", h["Origin"]); assertEquals("https://www.youtube.com", h["X-Origin"])
        assertTrue(h["Cookie"]!!.contains("SAPISID=") && h["Cookie"]!!.contains("SID="))
    }

    @Test fun rotatedCookiesFromSetCookieAreKeptAndReported() {
        var saved: CookieJar? = null
        val t = FakeTransport { _, _, _, _ -> ok("{}", "__Secure-1PSIDTS=new-ts; Domain=.youtube.com; Path=/; Secure; HttpOnly; Expires=Wed, 21 Oct 2026 07:28:00 GMT") }
        val s = AuthSession(jar(), t, FixedClock(), onCookiesChanged = { saved = it }, minGapMs = 0)
        s.send("GET", "https://www.youtube.com/x")
        assertEquals("new-ts", s.jar.get("__Secure-1PSIDTS"))
        assertEquals("new-ts", saved!!.get("__Secure-1PSIDTS"))
    }

    @Test fun http429BacksOffAndIsReportedNotRetriedBlindly() {
        val sleeps = mutableListOf<Long>(); val clock = FixedClock()
        val t = FakeTransport { _, _, _, _ -> RawResponse(429, "", mapOf("Retry-After" to listOf("7")), "") }
        val s = AuthSession(jar(), t, clock, minGapMs = 0, sleep = { sleeps += it })
        val e = assertFailsWith<AuthFailure.RateLimited> { s.send("GET", "https://www.youtube.com/x") }
        assertEquals(7000, e.retryAfterMs)
        assertFailsWith<AuthFailure.RateLimited> { s.send("GET", "https://www.youtube.com/x") }
        assertTrue(sleeps.isNotEmpty() && sleeps.all { it > 0 }, "the second call waited out the backoff: $sleeps")
    }

    @Test fun callsAreSpacedWithJitter() {
        val sleeps = mutableListOf<Long>()
        var now = Instant.fromEpochSeconds(1_700_000_000)
        val clock = object : Clock { override fun now() = now }
        val t = FakeTransport { _, _, _, _ -> ok("{}") }
        val s = AuthSession(jar(), t, clock, minGapMs = 900, sleep = { sleeps += it; now += kotlin.time.Duration.parse("${it}ms") })
        repeat(3) { s.send("GET", "https://www.youtube.com/x") }
        assertEquals(2, sleeps.size)
        assertTrue(sleeps.all { it in 900..1200 }, "gap plus up to a third jitter: $sleeps")
    }

    @Test fun unauthorizedMeansLoggedOut() {
        val s = AuthSession(jar(), FakeTransport { _, _, _, _ -> RawResponse(401, "", emptyMap(), "") }, FixedClock(), minGapMs = 0)
        assertFailsWith<AuthFailure.LoggedOut> { s.send("GET", "https://www.youtube.com/x") }
    }

    @Test fun clientVersionIsScrapedFromYtcfg() {
        assertEquals("2.20261001.05.00", WebClient.versionFrom("""foo "INNERTUBE_CLIENT_VERSION":"2.20261001.05.00","bar"""))
        assertNull(WebClient.versionFrom("<html></html>"))
        assertEquals("WEB", WebClient.bundled().clientName)
    }

    // --- login -----------------------------------------------------------------------------------------------

    private val db = createDatabase(JvmDatabaseDriverFactory())
    private val secrets = InMemorySecretStore()
    private val accounts = AccountManager(db, CredentialStore(secrets))
    private fun login(t: AuthTransport) = InnertubeAccountLogin(accounts, t, clock = FixedClock())

    @Test fun importingValidCookiesCreatesAndActivatesTheAccountAndStoresOnlyGoogleCookies() = runBlocking {
        val file = jar(Cookie("unrelated", "x", ".example.com")).toNetscape()
        val r = login(FakeTransport { _, _, _, _ -> ok(fixture("account_menu.json")) }).importCookies(file)
        val account = assertIs<LoginResult.Success>(r).account
        assertEquals(account.id, accounts.active.value.id)
        assertEquals("Test Person", account.displayName)
        assertNull(accounts.cookies(account.id)!!.get("unrelated"))
        assertEquals(setOf("cookies.${account.id}"), secrets.keys())
    }

    @Test fun expiredCookiesAreRejectedWithAClearMessageAndStoreNothing() = runBlocking {
        val signedOutMenu = """{"actions":[{"openPopupAction":{"popup":{"multiPageMenuRenderer":{"sections":[]}}}}]}"""
        val r = login(FakeTransport { _, _, _, _ -> ok(signedOutMenu) }).importCookies(jar().toNetscape())
        assertTrue(assertIs<LoginResult.Failure>(r).message.contains("signed out or expired"))
        assertTrue(secrets.keys().isEmpty())
        assertEquals(1, accounts.all.value.size)
    }

    @Test fun garbageAndCookielessFilesAreRejectedWithoutANetworkCall() = runBlocking {
        val t = FakeTransport { _, _, _, _ -> error("must not be called") }
        assertIs<LoginResult.Failure>(login(t).importCookies("hello"))
        assertIs<LoginResult.Failure>(login(t).importCookies(CookieJar(listOf(Cookie("PREF", "x", ".youtube.com"))).toNetscape()))
        assertTrue(t.calls.isEmpty())
    }

    @Test fun checkSessionMarksReloginWhenSignedOutAndKeepsStateOnNetworkErrors() = runBlocking {
        val ok = login(FakeTransport { _, _, _, _ -> ok(fixture("account_menu.json")) })
        val id = (ok.importCookies(jar().toNetscape()) as LoginResult.Success).account.id
        assertEquals(AuthState.OK, ok.checkSession(id))
        val out = login(FakeTransport { _, _, _, _ -> ok("""{"actions":[]}""") })
        assertEquals(AuthState.NEEDS_RELOGIN, out.checkSession(id))
        assertEquals(AuthState.NEEDS_RELOGIN, accounts.get(id)!!.authState)
        val flaky = login(FakeTransport { _, _, _, _ -> throw java.io.IOException("offline") })
        assertEquals(AuthState.NEEDS_RELOGIN, flaky.checkSession(id), "a network error changes nothing")
    }
}

class SyncTest {
    private val db = createDatabase(JvmDatabaseDriverFactory())
    private val accounts = AccountManager(db, CredentialStore(InMemorySecretStore()))
    private val g = accounts.addGoogle(GoogleIdentity("T", "t@example.com", null, "UCx"), jar())
    private fun sync(t: AuthTransport) = InnertubeAccountSync(accounts, db, t, clock = FixedClock())
    private val feed = FakeTransport { _, _, _, _ -> ok(fixture("subscriptions.json")) }

    @Test fun pullIsIdempotentAndNeverDeletesLocalRows() = runBlocking {
        val repo = com.greenaltambe.vela.data.SubscriptionRepository(db, g.id)
        repo.subscribe("UClocalOnly", "Local only", null, Instant.fromEpochSeconds(1))
        val first = sync(feed).pullSubscriptions()
        assertNull(first.failure); assertTrue(first.added > 50)
        val second = sync(feed).pullSubscriptions()
        assertEquals(0, second.added); assertEquals(first.added, second.alreadyHad)
        assertTrue(repo.isSubscribed("UClocalOnly"))
        assertEquals(first.added + 1, repo.all().size)
    }

    @Test fun otherAccountsRowsAreUntouched() = runBlocking {
        val local = com.greenaltambe.vela.data.SubscriptionRepository(db)
        sync(feed).pullSubscriptions()
        assertEquals(0, local.all().size)
    }

    @Test fun failedPullKeepsExistingRowsAndSignedOutMarksRelogin() = runBlocking {
        val repo = com.greenaltambe.vela.data.SubscriptionRepository(db, g.id)
        repo.subscribe("UC1", "One", null, Instant.fromEpochSeconds(1))
        val offline = sync(FakeTransport { _, _, _, _ -> throw java.io.IOException("offline") }).pullSubscriptions()
        assertNotNull(offline.failure); assertEquals(1, repo.all().size)
        val empty = sync(FakeTransport { _, _, _, _ -> ok("{}") }).pullSubscriptions()
        assertNotNull(empty.failure); assertEquals(1, repo.all().size, "an empty answer must not wipe or replace anything")
        val out = sync(FakeTransport { _, _, _, _ -> RawResponse(401, "", emptyMap(), "") }).pullSubscriptions()
        assertTrue(out.failure!!.contains("Signed out"))
        assertEquals(AuthState.NEEDS_RELOGIN, accounts.get(g.id)!!.authState)
        assertEquals(1, repo.all().size)
    }

    @Test fun noSignedInAccountMeansNothingHappens() = runBlocking {
        accounts.switchTo("local")
        val t = FakeTransport { _, _, _, _ -> error("no call expected") }
        assertNotNull(sync(t).pullSubscriptions().failure); assertTrue(t.calls.isEmpty())
    }
}
