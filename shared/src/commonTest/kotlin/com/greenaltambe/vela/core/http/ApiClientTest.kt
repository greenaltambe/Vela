package com.greenaltambe.vela.core.http

import com.greenaltambe.vela.core.Sha256
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class Sha256Test {
    @Test fun knownVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.hex(""))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.hex("abc"))
        assertEquals("248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1", Sha256.hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"))
    }

    @Test fun crossesBlockBoundaries() {
        // 55, 56 and 64 bytes straddle the padding edge cases; the digests are from hashlib.
        assertEquals("eb2c86e932179f4ba13fe8715a26124b77d6bad290b9b4c1cc140cf633300c19", Sha256.hex("b".repeat(55)))
        assertEquals("a5fc6e203a4c2b657d0d153885932414b2ffc6a93f0f8bf8b3183315e5a7212c", Sha256.hex("b".repeat(56)))
        assertEquals("a0fab1377f49a759b57f63318262ebe89fabfc990e8e93ceac2984561482b9d4", Sha256.hex("b".repeat(64)))
        assertEquals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0", Sha256.hex("a".repeat(1_000_000)))
    }

    @Test fun videoIdPrefixMatchesWhatTheApiReturned() {
        // Captured fixtures: the DeArrow answer for this video came from this prefix.
        assertEquals("efd7", Sha256.prefix("DN953UW8iJ8"))
    }
}

class ApiClientTest {
    private fun ok(body: String = "{}") = HttpResponse(200, body)

    @Test fun sendsTheUserAgentAndCachesWithinTheTtl() = runTest {
        val t = TestClient()
        t.transport.handler = { ok("v1") }
        assertEquals("v1", (t.client.get("https://x/a", ttl = 1.hours) as ApiResult.Ok).body)
        t.clock.advance(30.minutes)
        val second = t.client.get("https://x/a", ttl = 1.hours) as ApiResult.Ok
        assertTrue(second.fromCache && !second.stale)
        assertEquals(1, t.transport.requests.size, "a fresh cache entry needs no request")
        assertTrue(t.transport.requests.single().headers["User-Agent"]!!.startsWith("Vela/"))
    }

    @Test fun anExpiredEntryIsRefetched() = runTest {
        val t = TestClient()
        t.transport.handler = { ok("v${t.transport.requests.size}") }
        t.client.get("https://x/a", ttl = 1.hours)
        t.clock.advance(2.hours)
        assertEquals("v2", (t.client.get("https://x/a", ttl = 1.hours) as ApiResult.Ok).body)
    }

    @Test fun offlineServesTheStaleCopyInsteadOfNothing() = runTest {
        val t = TestClient()
        t.transport.handler = { ok("old") }
        t.client.get("https://x/a", ttl = 1.hours)
        t.clock.advance(5.hours)
        t.transport.handler = { throw TransportException("no route") }
        val r = t.client.get("https://x/a", ttl = 1.hours) as ApiResult.Ok
        assertEquals("old", r.body)
        assertTrue(r.stale && r.fromCache)
    }

    @Test fun offlineWithNothingCachedIsAFailureNotAnException() = runTest {
        val t = TestClient()
        t.transport.handler = { throw TransportException("no route") }
        val r = assertIs<ApiResult.Failed>(t.client.get("https://x/a"))
        assertEquals(FailureKind.OFFLINE, r.kind)
        assertEquals(3, t.transport.requests.size, "1 try + 2 retries")
    }

    @Test fun retriesBackOffExponentially() = runTest {
        val t = TestClient(retries = 3)
        t.transport.handler = { HttpResponse(503, "") }
        t.client.get("https://x/a")
        assertEquals(listOf(400.milliseconds, 800.milliseconds, 1600.milliseconds), t.sleeps)
        assertEquals(4, t.transport.requests.size)
    }

    @Test fun aRetrySucceedsAndTheFailureIsNotSurfaced() = runTest {
        val t = TestClient()
        var n = 0
        t.transport.handler = { if (n++ < 2) HttpResponse(429, "") else ok("fine") }
        assertEquals("fine", (t.client.get("https://x/a") as ApiResult.Ok).body)
        assertEquals(2, t.sleeps.size)
    }

    @Test fun notFoundAndOtherClientErrorsAreAnswersNotRetried() = runTest {
        val t = TestClient()
        t.transport.handler = { HttpResponse(404, "") }
        assertEquals(FailureKind.NOT_FOUND, (t.client.get("https://x/a") as ApiResult.Failed).kind)
        t.transport.handler = { HttpResponse(400, "bad") }
        assertEquals(FailureKind.REJECTED, (t.client.get("https://x/b") as ApiResult.Failed).kind)
        assertEquals(2, t.transport.requests.size)
        assertTrue(t.sleeps.isEmpty())
    }

    @Test fun notFoundCanBeCachedSoTheServerIsNotAskedAgain() = runTest {
        val t = TestClient()
        t.transport.handler = { HttpResponse(404, "") }
        t.client.get("https://x/a", cacheNegative = true)
        val again = assertIs<ApiResult.Failed>(t.client.get("https://x/a", cacheNegative = true))
        assertEquals(FailureKind.NOT_FOUND, again.kind)
        assertEquals(1, t.transport.requests.size)
    }

    @Test fun aRejectionIsNotReplacedByStaleData() = runTest {
        val t = TestClient()
        t.transport.handler = { ok("old") }
        t.client.get("https://x/a", ttl = 1.hours)
        t.clock.advance(2.hours)
        t.transport.handler = { HttpResponse(403, "no") }
        assertEquals(FailureKind.REJECTED, (t.client.get("https://x/a", ttl = 1.hours) as ApiResult.Failed).kind)
    }

    @Test fun postIsNeverCachedAndNotRetriedAfterAResponse() = runTest {
        val t = TestClient()
        t.transport.handler = { HttpResponse(500, "") }
        assertIs<ApiResult.Failed>(t.client.post("https://x/p", "{}"))
        assertEquals(1, t.transport.requests.size, "a retried POST could submit twice")
        assertEquals("POST", t.transport.requests.single().method)
        assertEquals("application/json", t.transport.requests.single().contentType)
    }

    @Test fun postRetriesWhenNothingReachedTheServer() = runTest {
        val t = TestClient()
        t.transport.handler = { throw TransportException("reset") }
        t.client.post("https://x/p", "{}")
        assertEquals(3, t.transport.requests.size)
    }

    @Test fun inMemoryCacheEvictsTheLeastRecentlyUsed() {
        val cache = InMemoryApiCache(capacity = 2)
        val now = FakeClock().now
        cache.put("a", CachedResponse(200, "a", now)); cache.put("b", CachedResponse(200, "b", now))
        cache.get("a") // a is now the most recent
        cache.put("c", CachedResponse(200, "c", now))
        assertEquals(null, cache.get("b"))
        assertEquals("a", cache.get("a")?.body)
        assertEquals("c", cache.get("c")?.body)
    }
}
