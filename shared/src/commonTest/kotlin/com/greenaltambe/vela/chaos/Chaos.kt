package com.greenaltambe.vela.chaos

import com.greenaltambe.vela.extractor.*

/** What a scripted call does. Mirrors what the real tiers map their failures to. */
enum class Fault {
    OK, FORBIDDEN_403, RATE_LIMITED_429, SORRY_CAPTCHA, LOGIN_REQUIRED, UNPLAYABLE, OFFLINE, TIMEOUT, MALFORMED_JSON, TRUNCATED_BODY;

    fun error(): ExtractorError? = when (this) {
        OK -> null
        FORBIDDEN_403 -> ExtractorError.Blocked("HTTP 403 page")
        RATE_LIMITED_429 -> ExtractorError.RateLimited("HTTP 429 Too Many Requests")
        SORRY_CAPTCHA -> ExtractorError.Blocked("redirected to google.com/sorry (captcha)")
        LOGIN_REQUIRED -> ExtractorError.SignInRequired("Sign in to confirm you're not a bot")
        UNPLAYABLE -> ExtractorError.Unplayable("This video is unavailable")
        OFFLINE -> ExtractorError.Network("Unable to resolve host")
        TIMEOUT -> ExtractorError.Network("timed out after 60s")
        MALFORMED_JSON -> ExtractorError.ParseFailure("malformed JSON")
        TRUNCATED_BODY -> ExtractorError.ParseFailure("truncated body")
    }
}

/**
 * An Extractor whose every call follows a script: the n-th call does the n-th fault, the last one repeats. Counts calls, so
 * a test can assert a tier was (not) used. Scriptable per tier by giving each tier its own instance.
 */
class ChaosExtractor(private vararg val script: Fault) : Extractor {
    private val ok = StubExtractor()
    var calls = 0
        private set

    private suspend fun <T> run(success: suspend () -> ExtractorResult<T>): ExtractorResult<T> {
        val fault = script[minOf(calls, script.lastIndex)]; calls++
        return fault.error()?.let { ExtractorResult.Failure(it) } ?: success()
    }

    override suspend fun search(query: String, continuation: String?) = run { ok.search(query, continuation) }
    override suspend fun videoInfo(videoId: String) = run { ok.videoInfo(videoId) }
    override suspend fun streams(videoId: String) = run { ok.streams(videoId) }
    override suspend fun channel(channelId: String) = run { ok.channel(channelId) }
    override suspend fun channelVideos(channelId: String, continuation: String?) = run { ok.channelVideos(channelId, continuation) }
    override suspend fun comments(videoId: String, sort: CommentSort, continuation: String?) = run { ok.comments(videoId, sort, continuation) }
    override suspend fun replies(videoId: String, commentId: String, continuation: String?) = run { ok.replies(videoId, commentId, continuation) }
}
