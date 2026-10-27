package com.greenaltambe.vela.core.http

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

enum class FailureKind { OFFLINE, NOT_FOUND, RATE_LIMITED, SERVER, REJECTED }

sealed interface ApiResult {
    /** [stale] when the cache answered because the network could not (past its TTL). [fromCache] for any cache hit. */
    data class Ok(val status: Int, val body: String, val fromCache: Boolean = false, val stale: Boolean = false) : ApiResult
    data class Failed(val kind: FailureKind, val status: Int?, val message: String) : ApiResult
}

/**
 * The one HTTP client for the third-party APIs (SponsorBlock, Return YouTube Dislike, DeArrow).
 *
 * - **Identifies itself** with [userAgent].
 * - **Retries with backoff** on connection failures, 429 and 5xx: up to [retries] more attempts, waiting
 *   `baseDelay * 2^n` (+ jitter from [jitter]), honouring nothing else; 404 and other 4xx are answers, not failures to retry.
 * - **Caches** successful GETs for the caller's `ttl` in [cache]. A fresh entry is returned without a request. When the
 *   request fails and an older entry exists, that is returned marked [ApiResult.Ok.stale]: offline degrades to
 *   "old data", not to "no feature".
 * - **Never throws** (except cancellation): every outcome is an [ApiResult], so callers cannot take playback down.
 */
class ApiClient(
    private val transport: HttpTransport,
    private val cache: ApiCache,
    private val clock: Clock = Clock.System,
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val retries: Int = 2,
    private val baseDelay: Duration = 400.milliseconds,
    private val jitter: () -> Duration = { Duration.ZERO },
    private val sleep: suspend (Duration) -> Unit = { delay(it) },
) {
    /** [cacheNegative]: also cache 404 answers for [ttl] (SponsorBlock says "no segments" with a 404). */
    suspend fun get(url: String, ttl: Duration = 1.hours, headers: Map<String, String> = emptyMap(), cacheNegative: Boolean = false): ApiResult {
        val cached = cache.get(url)
        val age = cached?.let { clock.now() - it.fetchedAt }
        if (cached != null && age != null && age < ttl) return cached.asResult(stale = false)

        val result = send(HttpRequest(url, headers = headers))
        return when {
            result is ApiResult.Ok -> result.also { cache.put(url, CachedResponse(it.status, it.body, clock.now())) }
            result is ApiResult.Failed && result.kind == FailureKind.NOT_FOUND && cacheNegative -> {
                cache.put(url, CachedResponse(404, "", clock.now()))
                result
            }
            cached != null && cached.status in 200..299 && result is ApiResult.Failed && result.kind != FailureKind.REJECTED -> cached.asResult(stale = true)
            else -> result
        }
    }

    /** A write: never cached, never retried after a response (a retried POST could submit twice); connection failures are retried. */
    suspend fun post(url: String, jsonBody: String, headers: Map<String, String> = emptyMap()): ApiResult =
        send(HttpRequest(url, "POST", headers, jsonBody, "application/json"), retryOnResponse = false)

    private fun CachedResponse.asResult(stale: Boolean): ApiResult =
        if (status == 404) ApiResult.Failed(FailureKind.NOT_FOUND, 404, "cached: not found")
        else ApiResult.Ok(status, body, fromCache = true, stale = stale)

    private suspend fun send(request: HttpRequest, retryOnResponse: Boolean = true): ApiResult {
        val withAgent = HttpRequest(request.url, request.method, request.headers + ("User-Agent" to userAgent), request.body, request.contentType)
        var attempt = 0
        while (true) {
            val outcome: ApiResult = try {
                classify(transport.execute(withAgent))
            } catch (e: CancellationException) {
                throw e
            } catch (e: TransportException) {
                ApiResult.Failed(FailureKind.OFFLINE, null, e.message ?: "connection failed")
            } catch (e: Exception) {
                ApiResult.Failed(FailureKind.OFFLINE, null, "${e::class.simpleName}: ${e.message}")
            }
            val retryable = outcome is ApiResult.Failed &&
                (outcome.kind == FailureKind.OFFLINE || (retryOnResponse && (outcome.kind == FailureKind.RATE_LIMITED || outcome.kind == FailureKind.SERVER)))
            if (!retryable || attempt >= retries) return outcome
            sleep(baseDelay * (1 shl attempt) + jitter())
            attempt++
        }
    }

    private fun classify(r: HttpResponse): ApiResult = when {
        r.status in 200..299 -> ApiResult.Ok(r.status, r.body)
        r.status == 404 -> ApiResult.Failed(FailureKind.NOT_FOUND, 404, "not found")
        r.status == 429 -> ApiResult.Failed(FailureKind.RATE_LIMITED, 429, "rate limited")
        r.status >= 500 -> ApiResult.Failed(FailureKind.SERVER, r.status, "server error ${r.status}")
        else -> ApiResult.Failed(FailureKind.REJECTED, r.status, "HTTP ${r.status}: ${r.body.take(120)}")
    }

    companion object {
        const val DEFAULT_USER_AGENT = "Vela/1.0 (personal-use YouTube client)"
    }
}
