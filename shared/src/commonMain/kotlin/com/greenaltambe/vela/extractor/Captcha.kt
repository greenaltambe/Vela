package com.greenaltambe.vela.extractor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * One captcha challenge at a time. A failing request publishes the challenge page's [url] here; the UI shows a banner, the viewer solves it
 * in a WebView (Android) or the browser, and the platform calls [solved] with the cookies that came out of it, or [dismiss]. The request
 * that hit the captcha waits for that (bounded) and is retried once.
 */
class CaptchaCoordinator {
    private val _pending = MutableStateFlow<String?>(null)
    /** The challenge page to show, or null. */
    val pending: StateFlow<String?> = _pending
    private var waiting: CompletableDeferred<Boolean>? = null

    /** Called by the extractor wrapper: publishes [url] and waits up to [wait] for the viewer. True when the challenge was solved. */
    suspend fun request(url: String, wait: Duration = 3.minutes): Boolean {
        val d = waiting?.takeIf { _pending.value == url } ?: CompletableDeferred<Boolean>().also { waiting = it; _pending.value = url }
        return withTimeoutOrNull(wait) { d.await() } ?: false.also { if (waiting === d) { waiting = null; _pending.value = null } }
    }

    fun solved() { _pending.value = null; waiting?.complete(true); waiting = null }
    fun dismiss() { _pending.value = null; waiting?.complete(false); waiting = null }
}

/** The cookie a solved captcha yields (`GOOGLE_ABUSE_EXEMPTION`), sent on anonymous requests to YouTube hosts only, until it expires. */
interface ExemptionCookie {
    /** The `Cookie` header value for a request to [host], or null. */
    fun headerFor(host: String): String?
}

/**
 * Wraps an extractor: when a call fails with [ExtractorError.Captcha] that carries a page, asks the [coordinator] for the viewer to solve it and,
 * if they did, repeats the call ONCE. A second captcha, a dismissal or a timeout returns the original failure.
 */
class CaptchaAwareExtractor(private val inner: Extractor, private val coordinator: CaptchaCoordinator) : Extractor {
    override val capabilities get() = inner.capabilities

    private suspend fun <T> guard(call: suspend () -> ExtractorResult<T>): ExtractorResult<T> {
        val first = call()
        val url = ((first as? ExtractorResult.Failure)?.error as? ExtractorError.Captcha)?.url ?: return first
        return if (coordinator.request(url)) call() else first
    }

    override suspend fun search(query: String, continuation: String?) = guard { inner.search(query, continuation) }
    override suspend fun videoInfo(videoId: String) = guard { inner.videoInfo(videoId) }
    override suspend fun streams(videoId: String) = guard { inner.streams(videoId) }
    override suspend fun streamsPreferringOther(videoId: String) = guard { inner.streamsPreferringOther(videoId) }
    override suspend fun channel(channelId: String) = guard { inner.channel(channelId) }
    override suspend fun channelVideos(channelId: String, continuation: String?) = guard { inner.channelVideos(channelId, continuation) }
    override suspend fun comments(videoId: String, sort: CommentSort, continuation: String?) = guard { inner.comments(videoId, sort, continuation) }
    override suspend fun replies(videoId: String, commentId: String, continuation: String?) = guard { inner.replies(videoId, commentId, continuation) }
    override suspend fun trending(continuation: String?) = guard { inner.trending(continuation) }
    override suspend fun related(videoId: String) = guard { inner.related(videoId) }
    override suspend fun channelShorts(channelId: String, continuation: String?) = guard { inner.channelShorts(channelId, continuation) }
    override suspend fun suggestions(query: String) = guard { inner.suggestions(query) }
    override suspend fun recentUploads(channelId: String) = guard { inner.recentUploads(channelId) }
}

/** Keeps the exemption cookie's value and expiry as one settings row; sent only to YouTube API hosts. */
class SettingsExemptionCookie(private val store: com.greenaltambe.vela.core.settings.SettingsStore, private val clock: kotlin.time.Clock = kotlin.time.Clock.System, private val lifetime: Duration = kotlin.time.Duration.parse("6h")) : ExemptionCookie {
    private val key = "captcha.exemption"
    fun save(value: String) = store.put(key, com.greenaltambe.vela.core.settings.SettingLayer.Global, "$value|${(clock.now() + lifetime).toEpochMilliseconds()}")
    fun clear() = store.remove(key, com.greenaltambe.vela.core.settings.SettingLayer.Global)
    override fun headerFor(host: String): String? {
        val raw = store.get(key, com.greenaltambe.vela.core.settings.SettingLayer.Global) ?: return null
        val value = raw.substringBeforeLast('|'); val until = raw.substringAfterLast('|').toLongOrNull() ?: return null
        if (clock.now().toEpochMilliseconds() >= until) { clear(); return null }
        val h = host.lowercase()
        return if (h == "youtube.com" || h.endsWith(".youtube.com") || h == "youtubei.googleapis.com") "GOOGLE_ABUSE_EXEMPTION=$value" else null
    }
}
