package com.greenaltambe.vela.extractor

import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

class CaptchaTest {
    private class Gated(var captchas: Int) : Extractor by StubExtractor() {
        var calls = 0
        override suspend fun trending(continuation: String?): ExtractorResult<Page<VideoSummary>> {
            calls++
            return if (captchas-- > 0) ExtractorResult.Failure(ExtractorError.Captcha("captcha", "https://www.google.com/recaptcha/x")) else ExtractorResult.Success(Page(emptyList(), null))
        }
    }

    @Test fun aSolvedCaptchaRetriesTheCallExactlyOnce() = runTest {
        val inner = Gated(1); val c = CaptchaCoordinator(); val x = CaptchaAwareExtractor(inner, c)
        val r = async { x.trending(null) }
        runCurrent()
        assertEquals("https://www.google.com/recaptcha/x", c.pending.value, "the banner has something to show")
        c.solved(); advanceUntilIdle()
        assertIs<ExtractorResult.Success<*>>(r.await()); assertEquals(2, inner.calls); assertNull(c.pending.value)
    }

    @Test fun aSecondCaptchaDismissalOrTimeoutReturnsTheOriginalFailureWithoutLooping() = runTest {
        val twice = Gated(2); val c = CaptchaCoordinator()
        val a = async { CaptchaAwareExtractor(twice, c).trending(null) }; runCurrent(); c.solved(); advanceUntilIdle()
        assertIs<ExtractorError.Captcha>((a.await() as ExtractorResult.Failure).error); assertEquals(2, twice.calls, "one retry, no loop")
        val dismissed = Gated(1)
        val b = async { CaptchaAwareExtractor(dismissed, c).trending(null) }; runCurrent(); c.dismiss(); advanceUntilIdle()
        assertIs<ExtractorResult.Failure>(b.await()); assertEquals(1, dismissed.calls)
        val slow = Gated(1)
        val d = async { CaptchaAwareExtractor(slow, c).trending(null) }; runCurrent(); advanceTimeBy(4.minutes); advanceUntilIdle()
        assertIs<ExtractorResult.Failure>(d.await()); assertNull(c.pending.value, "a timed-out challenge is withdrawn")
    }

    @Test fun aCaptchaWithoutAPageIsNotOfferedAndOtherErrorsPassThrough() = runTest {
        val noUrl = object : Extractor by StubExtractor() { override suspend fun trending(continuation: String?): ExtractorResult<Page<VideoSummary>> = ExtractorResult.Failure(ExtractorError.Captcha("captcha")) }
        val c = CaptchaCoordinator()
        assertIs<ExtractorResult.Failure>(CaptchaAwareExtractor(noUrl, c).trending(null)); assertNull(c.pending.value)
    }

    @Test fun theExemptionCookieGoesOnlyToYouTubeApiHostsAndExpires() {
        var now = kotlin.time.Instant.fromEpochSeconds(1_000_000)
        val clock = object : kotlin.time.Clock { override fun now() = now }
        val c = SettingsExemptionCookie(com.greenaltambe.vela.core.settings.InMemorySettingsStore(), clock, 1.minutes * 60 * 6)
        assertNull(c.headerFor("www.youtube.com"), "nothing stored yet")
        c.save("abc123")
        assertEquals("GOOGLE_ABUSE_EXEMPTION=abc123", c.headerFor("www.youtube.com")); assertEquals("GOOGLE_ABUSE_EXEMPTION=abc123", c.headerFor("youtubei.googleapis.com"))
        assertNull(c.headerFor("rr1---sn.googlevideo.com"), "never to stream hosts"); assertNull(c.headerFor("i.ytimg.com")); assertNull(c.headerFor("sponsor.ajay.app")); assertNull(c.headerFor("evilyoutube.com"))
        now += 7.minutes * 60
        assertNull(c.headerFor("www.youtube.com"), "expired")
    }
}
