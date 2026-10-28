package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.AccountGovernor
import com.greenaltambe.vela.auth.BlockPatterns
import com.greenaltambe.vela.auth.BlockSignal
import com.greenaltambe.vela.auth.BreakerSignal
import com.greenaltambe.vela.auth.CallKind
import com.greenaltambe.vela.auth.CookieJar
import com.greenaltambe.vela.auth.SapisidHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock

/** Why an authenticated call did not return data. */
sealed class AuthFailure(message: String) : Exception(message) {
    /** The cookies are no longer a signed-in session. The account must log in again. */
    class LoggedOut(why: String) : AuthFailure("signed out: $why"), BreakerSignal { override val signal = BlockSignal.COOKIES_INVALIDATED }
    class RateLimited(val retryAfterMs: Long) : AuthFailure("rate limited, retry after ${retryAfterMs}ms"), BreakerSignal { override val signal = BlockSignal.HTTP_429 }
    /** A captcha, "unusual traffic" or verification page instead of the data. */
    class Blocked(override val signal: BlockSignal) : AuthFailure("blocked: ${signal.label}"), BreakerSignal
    class Http(val code: Int) : AuthFailure("HTTP $code")
    class Unparseable(why: String) : AuthFailure("unparseable response: $why")
}

/**
 * One account's signed requests, and the ONLY place a Cookie or SAPISIDHASH header is built. Every request goes through
 * the account's [governor] (serialised, spaced, budgeted, breaker, audit), so there is no way to send credentials that
 * bypasses it. Set-Cookie rotations are folded back into the jar (reported through [onCookiesChanged] so they are persisted).
 */
class AuthSession(
    initial: CookieJar,
    private val governor: AccountGovernor,
    private val transport: AuthTransport = OkHttpAuthTransport(),
    private val clock: Clock = Clock.System,
    private val onCookiesChanged: (CookieJar) -> Unit = {},
    /** The chosen brand profile's page id, or null for the main one: sent as `X-Goog-PageId` so history and recommendations are that profile's. Nothing sets it yet. */
    private val pageId: () -> String? = { null },
) {
    @Volatile var jar: CookieJar = initial
        private set

    val signedIn: Boolean get() = jar.looksSignedIn

    /** Headers for a request to [url]. [origin] is what SAPISIDHASH signs (https://www.youtube.com for the website client). */
    internal fun headersFor(url: String, origin: String = YOUTUBE_ORIGIN, extra: Map<String, String> = emptyMap()): Map<String, String> {
        val host = hostOf(url)
        val now = clock.now()
        val h = LinkedHashMap<String, String>()
        jar.headerFor(host, now)?.let { h["Cookie"] = it }
        jar.sapisid?.let { h["Authorization"] = SapisidHash.authorization(it, origin, now.epochSeconds) }
        h["X-Goog-AuthUser"] = "0"
        pageId()?.let { h["X-Goog-PageId"] = it }
        h["Origin"] = origin
        h["X-Origin"] = origin
        h["Referer"] = "$origin/"
        h.putAll(extra)
        return h
    }

    /** Sends one signed request through the governor. Throws `GovernorDenied` without sending when it refuses. */
    fun send(kind: CallKind, method: String, url: String, body: String? = null, origin: String = YOUTUBE_ORIGIN, extra: Map<String, String> = emptyMap(), patience: kotlin.time.Duration = kotlin.time.Duration.ZERO): RawResponse = runBlocking(Dispatchers.IO) { // the request itself never runs on the caller's thread
        governor.call(kind, hostOf(url), patience = patience) {
            val response = transport.execute(method, url, headersFor(url, origin, extra), body)
            absorb(response, hostOf(url))
            when {
                response.code == 429 -> throw AuthFailure.RateLimited(response.header("Retry-After")?.toLongOrNull()?.times(1000) ?: 60_000)
                response.code == 401 -> throw AuthFailure.LoggedOut("HTTP 401")
                response.code == 403 && !signedIn -> throw AuthFailure.LoggedOut("HTTP 403 with no valid session cookies")
            }
            pageBlock(response)?.let { throw AuthFailure.Blocked(it) }
            if (response.code !in 200..299) throw AuthFailure.Http(response.code)
            response
        }
    }

    /** A captcha redirect or "unusual traffic" page. JSON answers are never text-scanned (their config blobs mention captchas). */
    private fun pageBlock(r: RawResponse): BlockSignal? {
        if (r.finalUrl.contains("/sorry/")) return BlockSignal.CAPTCHA
        if (!r.body.trimStart().startsWith("{")) return BlockPatterns.scan(r.body.take(20_000))?.takeIf { it != BlockSignal.HTTP_429 }
        return null
    }

    private fun absorb(r: RawResponse, host: String) {
        val setCookies = r.headerValues("Set-Cookie")
        if (setCookies.isEmpty()) return
        val updated = jar.withSetCookies(setCookies, host, clock.now())
        if (updated.toNetscape() != jar.toNetscape()) { jar = updated; onCookiesChanged(updated) }
    }

    companion object {
        const val YOUTUBE_ORIGIN = "https://www.youtube.com"
        fun hostOf(url: String): String = url.substringAfter("://").substringBefore('/').substringBefore(':')
    }
}
