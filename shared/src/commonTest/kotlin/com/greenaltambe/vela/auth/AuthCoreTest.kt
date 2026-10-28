package com.greenaltambe.vela.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class Sha1AndSapisidTest {
    @Test fun knownSha1Vectors() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", Sha1.hex("abc"))
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", Sha1.hex(""))
        assertEquals("291e9a6c66994949b57ba5e650361e98fc36b1ba", Sha1.hex("a".repeat(1000)))
        assertEquals("4d055f5334ac4bca50260deff4707cd8d4fc1454", Sha1.hex("b".repeat(55)))
        assertEquals("049adea016c8a8e3b7a9054aeeaa8643453bebd9", Sha1.hex("b".repeat(56)))
    }

    @Test fun sapisidHashVectors() {
        // Digests computed with Python's hashlib over "<ts> <sapisid> <origin>".
        assertEquals("SAPISIDHASH 1700000000_6aeb9e08815c5ecdb84affddc7278a326b39e88e", SapisidHash.authorization("AbCdEfGhIjKlMnOp/1234567890", "https://www.youtube.com", 1700000000))
        assertEquals("SAPISIDHASH 1_2c59541a1cab73270e40fb052e27ec4a25b16212", SapisidHash.authorization("x", "https://music.youtube.com", 1))
        assertEquals("SAPISIDHASH 1893456000_0850fd51606b54c6be53c03fb4eef4f342b50adc", SapisidHash.authorization("a-b_c", "https://www.youtube.com", 1893456000))
    }

    @Test fun theOriginIsPartOfTheHash() =
        assertTrue(SapisidHash.authorization("s", "https://www.youtube.com", 5) != SapisidHash.authorization("s", "https://music.youtube.com", 5))
}

class RedactionTest {
    private fun clean(s: String) = Redaction.redact(s)

    @Test fun cookieHeadersLoseTheirValues() {
        val out = clean("GET /x HTTP/1.1\nCookie: SID=abc123; __Secure-3PSID=def456; PREF=f6=40000000\nHost: www.youtube.com")
        assertFalse("abc123" in out || "def456" in out || "40000000" in out, out)
        assertTrue("Cookie: [redacted]" in out && "Host: www.youtube.com" in out, out)
    }

    @Test fun setCookieAndAuthorizationAreRedacted() {
        val out = clean("Set-Cookie: __Secure-1PSIDTS=sidts-ROTATED-VALUE; Path=/; Secure\nAuthorization: SAPISIDHASH 1700000000_6aeb9e08815c5ecdb84affddc7278a326b39e88e")
        assertFalse("ROTATED" in out || "6aeb9e08815c" in out, out)
    }

    @Test fun sessionCookiePairsInsideFreeTextAreRedacted() {
        val out = clean("failed with SAPISID=secretsapisid/xyz and LOGIN_INFO=AFmmF2swRQIh-long-token plus ST-abc123=1;")
        assertFalse("secretsapisid" in out || "AFmmF2" in out, out)
        assertTrue("SAPISID=[redacted]" in out, out)
    }

    @Test fun sapisidhashAndBearerTokensAreRedactedAnywhere() {
        val out = clean("sent SAPISIDHASH 1700000000_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa and Bearer ya29.a0AfH6SMBxxxxxxxxxxxxxx ok")
        assertFalse("aaaaaaaaaaaaaaaaaaaa" in out || "ya29" in out, out)
    }

    @Test fun jsonSessionFieldsAreRedactedButStructureStays() {
        val out = clean("""{"context":{"client":{"visitorData":"CgtSZWFsVmlzaXRvcg%3D%3D","clientName":"WEB"}},"datasyncId":"1234||5678","refresh_token":"1//0gabc"}""")
        assertFalse("CgtSZWFs" in out || "1234||5678" in out || "1//0gabc" in out, out)
        assertTrue("\"clientName\":\"WEB\"" in out, out)
    }

    @Test fun emailsAreMaskedToTheFirstLetterAndDomain() {
        assertEquals("signed in as a***@gmail.com now", clean("signed in as alice.smith@gmail.com now"))
        assertEquals("j***@example.co.uk", Redaction.maskEmail("jane@example.co.uk"))
        assertNull(Redaction.maskEmail(null))
    }

    @Test fun urlsKeepTheirShapeButLoseSecretQueryParameters() {
        val out = clean("https://youtubei.googleapis.com/youtubei/v1/player?key=AIzaSyAbCdEf&prettyPrint=false&pot=xyz")
        assertFalse("AIzaSy" in out || "=xyz" in out, out)
        assertTrue("prettyPrint=false" in out, out)
    }

    @Test fun ordinaryTextIsUntouched() {
        val text = "tier ANONYMOUS failed: Sign in to confirm you're not a bot (HTTP 403) for dQw4w9WgXcQ"
        assertEquals(text, clean(text))
    }

    @Test fun aCookieJarNeverPrintsItsValues() {
        val jar = CookieJar(listOf(Cookie("SID", "topsecretvalue", ".google.com")))
        assertFalse("topsecretvalue" in jar.toString() || "topsecretvalue" in jar.cookies.first().toString())
    }

    @Test fun looksSecretCatchesWhatRedactionWouldRemove() {
        assertTrue(Redaction.looksSecret("SAPISID=abc"))
        assertTrue(Redaction.looksSecret("me@gmail.com"))
        assertFalse(Redaction.looksSecret(clean("SAPISID=abc and me@gmail.com")))
    }
}

class CookieJarTest {
    private val now = Instant.fromEpochSeconds(1_800_000_000)
    private val netscape = """# Netscape HTTP Cookie File
.youtube.com	TRUE	/	TRUE	1900000000	SAPISID	sap-value
#HttpOnly_.youtube.com	TRUE	/	TRUE	1900000000	__Secure-3PSID	psid-value
.youtube.com	TRUE	/	TRUE	0	YSC	session-value
.google.com	TRUE	/	TRUE	1700000000	OLD	expired-value
"""

    @Test fun parsesNetscapeIncludingHttpOnlyAndSessionCookies() {
        val jar = CookieJar.parseNetscape(netscape)
        assertEquals(4, jar.cookies.size)
        assertEquals("sap-value", jar.sapisid)
        assertTrue(jar.cookies.first { it.name == "__Secure-3PSID" }.httpOnly)
        assertNull(jar.cookies.first { it.name == "YSC" }.expiresEpochSeconds, "0 is a session cookie")
        assertTrue(jar.looksSignedIn)
    }

    @Test fun theCookieHeaderHasOnlyLiveCookiesForTheHost() {
        val jar = CookieJar.parseNetscape(netscape)
        val header = jar.headerFor("www.youtube.com", now)!!
        assertTrue("SAPISID=sap-value" in header && "YSC=session-value" in header)
        assertFalse("OLD" in header, "expired cookies are not sent")
        assertNull(jar.headerFor("example.org", now))
        assertTrue("OLD" !in (jar.headerFor("accounts.google.com", now) ?: ""))
    }

    @Test fun netscapeRoundTripsWithoutLosingAnything() {
        val jar = CookieJar.parseNetscape(netscape)
        val again = CookieJar.parseNetscape(jar.toNetscape())
        assertEquals(jar.cookies, again.cookies)
    }

    @Test fun setCookieRotatesValuesAndExpiresThem() {
        val jar = CookieJar.parseNetscape(netscape)
        val rotated = jar.withSetCookies(
            listOf(
                "SAPISID=new-sap; Domain=.youtube.com; Path=/; Secure; Max-Age=31536000",
                "__Secure-1PSIDTS=sidts-new; Domain=.youtube.com; Path=/; Secure; HttpOnly",
                "YSC=; Domain=.youtube.com; Path=/; Max-Age=0",
            ),
            "www.youtube.com", now,
        )
        assertEquals("new-sap", rotated.sapisid)
        assertEquals("sidts-new", rotated.get("__Secure-1PSIDTS"))
        assertNull(rotated.get("YSC"), "Max-Age=0 deletes the cookie")
        assertEquals(1, rotated.cookies.count { it.name == "SAPISID" }, "replaced, not duplicated")
        assertEquals("psid-value", rotated.get("__Secure-3PSID"), "untouched cookies stay")
    }

    @Test fun setCookieExpiresDateInThePastDeletesToo() {
        val jar = CookieJar.parseNetscape(netscape)
        val out = jar.withSetCookies(listOf("SAPISID=x; Domain=.youtube.com; Path=/; Expires=Thu, 01 Jan 1970 00:00:01 GMT"), "www.youtube.com", now)
        assertNull(out.get("SAPISID"))
    }

    @Test fun httpDatesParse() {
        assertEquals(1792567680L, CookieJar.parseHttpDate("Wed, 21 Oct 2026 07:28:00 GMT"))
        assertEquals(1L, CookieJar.parseHttpDate("Thu, 01 Jan 1970 00:00:01 GMT"))
        assertNull(CookieJar.parseHttpDate("tomorrow"))
    }

    @Test fun anEmptyOrForeignFileIsNotSignedIn() {
        assertFalse(CookieJar.parseNetscape("").looksSignedIn)
        assertFalse(CookieJar.parseNetscape(".example.com\tTRUE\t/\tTRUE\t0\tfoo\tbar").looksSignedIn)
        assertNotNull(CookieJar.parseNetscape(netscape).sapisid)
    }

    @Test fun credentialStoreKeepsPerAccountJarsAndWipesAll() {
        val store = CredentialStore(InMemorySecretStore())
        store.saveCookies("a", CookieJar.parseNetscape(netscape)); store.saveCookies("b", CookieJar.parseNetscape(netscape))
        assertEquals("sap-value", store.cookies("a")?.sapisid)
        store.delete("a")
        assertNull(store.cookies("a"))
        assertNotNull(store.cookies("b"))
        store.deleteAll()
        assertNull(store.cookies("b"))
    }
}

class AccountIdentityTest {
    private val clock = object : kotlin.time.Clock { var t = kotlin.time.Instant.fromEpochSeconds(1_000_000); override fun now() = t }
    private val store = InMemorySecretStore()
    private val ids = AccountIdentities(store, clock)
    private var fills = 0
    private fun fill() = Triple("visitor${++fills}", "2.2026", "UA/1")

    @kotlin.test.Test fun isCreatedOnceAndReusedPerAccount() {
        val a = ids.getOrCreate("a", ::fill); val again = ids.getOrCreate("a", ::fill)
        kotlin.test.assertEquals(a, again); kotlin.test.assertEquals(1, fills)
        val b = ids.getOrCreate("b", ::fill)
        kotlin.test.assertNotEquals(a.deviceId, b.deviceId, "switching account swaps the identity")
    }

    @kotlin.test.Test fun staleOrFailedRefreshKeepsTheDeviceIdButNewVisitorData() {
        val a = ids.getOrCreate("a", ::fill)
        clock.t = clock.t + kotlin.time.Duration.parse("31d")
        val r = ids.getOrCreate("a", ::fill)
        kotlin.test.assertEquals(a.deviceId, r.deviceId); kotlin.test.assertEquals("visitor2", r.visitorData); kotlin.test.assertEquals(a.createdAt, r.createdAt)
        kotlin.test.assertEquals("visitor3", ids.refresh("a", ::fill).visitorData)
    }

    @kotlin.test.Test fun survivesRestartAndIsRemovedWithTheAccountAndSignOutEverywhere() {
        val a = ids.getOrCreate("a", ::fill)
        kotlin.test.assertEquals(a, AccountIdentities(store, clock).current("a"))
        CredentialStore(store).deleteAll()
        kotlin.test.assertNull(ids.current("a"))
        ids.getOrCreate("a", ::fill); ids.delete("a"); kotlin.test.assertNull(ids.current("a"))
    }
}
