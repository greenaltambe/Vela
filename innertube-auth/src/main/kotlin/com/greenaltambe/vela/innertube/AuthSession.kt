package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.CookieJar
import com.greenaltambe.vela.auth.SapisidHash
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/** Why an authenticated call did not return data. */
sealed class AuthFailure(message: String) : Exception(message) {
    /** The cookies are no longer a signed-in session. The account must log in again. */
    class LoggedOut(why: String) : AuthFailure("signed out: $why")
    class RateLimited(val retryAfterMs: Long) : AuthFailure("rate limited, retry after ${retryAfterMs}ms")
    class Http(val code: Int) : AuthFailure("HTTP $code")
    class Unparseable(why: String) : AuthFailure("unparseable response: $why")
}

/**
 * One account's signed requests: builds the Cookie and SAPISIDHASH headers, folds Set-Cookie rotations back into the
 * jar (reported through [onCookiesChanged] so they are persisted), spaces calls out with jitter, and backs off on 429.
 */
class AuthSession(
    initial: CookieJar,
    private val transport: AuthTransport = OkHttpAuthTransport(),
    private val clock: Clock = Clock.System,
    private val onCookiesChanged: (CookieJar) -> Unit = {},
    /** Minimum gap between two calls of this account; a jittered extra up to a third of it is added. */
    private val minGapMs: Long = 400,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val random: Random = Random.Default,
) {
    @Volatile var jar: CookieJar = initial
        private set
    private var lastCallAt = 0L
    private var backoffUntil = 0L
    private var consecutive429 = 0

    val signedIn: Boolean get() = jar.looksSignedIn

    /** Headers for a request to [url]. [origin] is what SAPISIDHASH signs (https://www.youtube.com for the website client). */
    fun headersFor(url: String, origin: String = YOUTUBE_ORIGIN, extra: Map<String, String> = emptyMap()): Map<String, String> {
        val host = hostOf(url)
        val now = clock.now()
        val h = LinkedHashMap<String, String>()
        jar.headerFor(host, now)?.let { h["Cookie"] = it }
        jar.sapisid?.let { h["Authorization"] = SapisidHash.authorization(it, origin, now.epochSeconds) }
        h["X-Goog-AuthUser"] = "0"
        h["Origin"] = origin
        h["X-Origin"] = origin
        h["Referer"] = "$origin/"
        h.putAll(extra)
        return h
    }

    /** Sends one signed request, applying pacing, backoff, cookie updates and logged-out detection. */
    fun send(method: String, url: String, body: String? = null, origin: String = YOUTUBE_ORIGIN, extra: Map<String, String> = emptyMap()): RawResponse {
        pace()
        val response = transport.execute(method, url, headersFor(url, origin, extra), body)
        lastCallAt = clock.now().toEpochMilliseconds()
        absorb(response, hostOf(url))
        when {
            response.code == 429 -> {
                consecutive429++
                val wait = (response.header("Retry-After")?.toLongOrNull()?.times(1000)) ?: (1000L shl (consecutive429.coerceAtMost(6)))
                backoffUntil = lastCallAt + wait
                throw AuthFailure.RateLimited(wait)
            }
            response.code == 401 -> throw AuthFailure.LoggedOut("HTTP 401")
            response.code == 403 && !signedIn -> throw AuthFailure.LoggedOut("HTTP 403 with no valid session cookies")
            response.code !in 200..299 -> throw AuthFailure.Http(response.code)
        }
        consecutive429 = 0
        return response
    }

    private fun absorb(r: RawResponse, host: String) {
        val setCookies = r.headerValues("Set-Cookie")
        if (setCookies.isEmpty()) return
        val updated = jar.withSetCookies(setCookies, host, clock.now())
        if (updated.toNetscape() != jar.toNetscape()) { jar = updated; onCookiesChanged(updated) }
    }

    private fun pace() {
        val now = clock.now().toEpochMilliseconds()
        val earliest = maxOf(lastCallAt + minGapMs + random.nextLong(0, minGapMs / 3 + 1), backoffUntil)
        if (earliest > now) sleep(earliest - now)
    }

    companion object {
        const val YOUTUBE_ORIGIN = "https://www.youtube.com"
        fun hostOf(url: String): String = url.substringAfter("://").substringBefore('/').substringBefore(':')
    }
}
