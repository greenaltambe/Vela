package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.*
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.extractor.ExtractorResult
import kotlinx.coroutines.runBlocking
import kotlin.test.*

private fun markerFixture(name: String) = CredentialLeakAuditTest::class.java.getResource("/fixtures/$name")!!.readText()

/**
 * Runs the account flows with marker cookies and checks where the markers went. Allowed: the YouTube sign-in/API hosts, and
 * only from the session itself. Never: the alt client (a different, anonymous transport), any log, audit line or message.
 */
class CredentialLeakAuditTest {
    private val markers = listOf("MARKER_SID_91ac", "MARKER_SAPISID_77be", "MARKER_PSIDTS_3d20")
    private val allowedHosts = setOf("www.youtube.com", "youtubei.googleapis.com", "accounts.google.com", "music.youtube.com")
    private val jar = CookieJar(listOf(
        Cookie("SID", markers[0], ".google.com"), Cookie("SAPISID", markers[1], ".youtube.com"), Cookie("__Secure-1PSIDTS", markers[2], ".youtube.com"),
        Cookie("__Secure-3PSID", markers[0], ".google.com"),
    ))

    private class Recorder(val respond: (String) -> RawResponse) : AuthTransport {
        val sent = mutableListOf<Triple<String, String, Map<String, String>>>()   // method, url, headers
        val bodies = mutableListOf<String?>()
        override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): RawResponse {
            sent += Triple(method, url, headers); bodies += body; return respond(url)
        }
    }

    private fun carriesMarker(t: Triple<String, String, Map<String, String>>, body: String?): Boolean =
        markers.any { m -> t.second.contains(m) || t.third.values.any { it.contains(m) } || body?.contains(m) == true }
    private val sapisidHashOf = { h: Map<String, String> -> h["Authorization"]?.startsWith("SAPISIDHASH") == true }

    @Test fun accountFlowsSendCredentialsOnlyToAllowListedHosts() = runBlocking {
        val db = createDatabase(JvmDatabaseDriverFactory())
        val accounts = AccountManager(db, CredentialStore(InMemorySecretStore()))
        val audit = DbAuditLog(db)
        val registry = GovernorRegistry(limits = { GovernorLimits(minSpacingMs = 0, browsePerMinute = 1000) }, store = DbBreakerStore(db), audit = audit, sleeper = {})
        val t = Recorder { url -> RawResponse(200, if (url.contains("account_menu")) markerFixture("account_menu.json") else markerFixture("subscriptions.json"), emptyMap(), url) }
        val login = InnertubeAccountLogin(accounts, registry, t)
        val result = login.importCookies(jar.toNetscape()) as LoginResult.Success
        login.checkSession(result.account.id)
        InnertubeAccountSync(accounts, registry, db, t).pullSubscriptions()

        val credentialed = t.sent.indices.filter { sapisidHashOf(t.sent[it].third) || carriesMarker(t.sent[it], t.bodies[it]) }
        assertTrue(credentialed.isNotEmpty() && credentialed.size == t.sent.size, "every account call is signed")
        for (i in credentialed) assertTrue(AuthSession.hostOf(t.sent[i].second) in allowedHosts, "credentials sent to ${t.sent[i].second.substringBefore('?')}")
        assertTrue(t.sent.none { markers.any { m -> it.second.contains(m) } }, "never in a URL")
        assertTrue(t.bodies.none { b -> markers.any { b?.contains(it) == true } }, "never in a body")

        val logged = audit.recent(result.account.id).joinToString("\n") { "${it.kind} ${it.host} ${it.result} ${it.decision}" }
        assertTrue(markers.none { logged.contains(it) }, "audit log is clean")
        assertTrue(markers.none { result.account.toString().contains(it) || result.account.label.contains(it) })
        assertTrue(markers.none { jar.toString().contains(it) }, "CookieJar.toString shows names only")
    }

    @Test fun failureMessagesNeverCarryCredentials() = runBlocking {
        val db = createDatabase(JvmDatabaseDriverFactory())
        val accounts = AccountManager(db, CredentialStore(InMemorySecretStore()))
        val registry = GovernorRegistry(limits = { GovernorLimits(minSpacingMs = 0) }, sleeper = {})
        val leaky = Recorder { throw java.io.IOException("connect failed, request was Cookie: SAPISID=${markers[1]}; SID=${markers[0]} Authorization: SAPISIDHASH 1_abc") }
        val failure = InnertubeAccountLogin(accounts, registry, leaky).importCookies(jar.toNetscape()) as LoginResult.Failure
        assertTrue(markers.none { failure.message.contains(it) }, failure.message)
    }

    @Test fun theAlternateClientTierIsStructurallyAnonymous() = runBlocking {
        val t = Recorder { RawResponse(200, """{"playabilityStatus":{"status":"UNPLAYABLE","reason":"x"}}""", emptyMap(), "") }
        val x = AltClientExtractor(t)
        x.streams("v"); x.videoInfo("v")
        assertTrue(t.sent.isNotEmpty())
        assertTrue(t.sent.all { (_, _, h) -> h.keys.none { it.equals("Cookie", true) || it.equals("Authorization", true) || it.equals("X-Goog-AuthUser", true) } }, "no credential header on any alt-client call")
    }
}
