package com.greenaltambe.vela.core.http

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class FakeClock(var now: Instant = Instant.fromEpochSeconds(1_800_000_000)) : Clock {
    override fun now() = now
    fun advance(by: Duration) { now += by }
}

/** Scripted HTTP: [handler] decides each response (or throws [TransportException]); every request is recorded. */
class FakeTransport(var handler: (HttpRequest) -> HttpResponse = { HttpResponse(200, "") }) : HttpTransport {
    val requests = mutableListOf<HttpRequest>()
    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        return handler(request)
    }
}

/** An [ApiClient] whose waits are recorded instead of slept, so retry tests run instantly and can assert the backoff. */
class TestClient(
    val transport: FakeTransport = FakeTransport(),
    val clock: FakeClock = FakeClock(),
    val cache: ApiCache = InMemoryApiCache(),
    retries: Int = 2,
) {
    val sleeps = mutableListOf<Duration>()
    val client = ApiClient(transport, cache, clock, retries = retries, sleep = { sleeps += it })
}
