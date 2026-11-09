package com.greenaltambe.vela.ui.screens

import com.greenaltambe.vela.extractor.ChannelTabKind
import com.greenaltambe.vela.extractor.ExtractorError
import com.greenaltambe.vela.extractor.NOT_SUPPORTED
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A tab no source can show gets a calm "open in browser" state; a network or server problem keeps its red banner and Retry. */
class ChannelTabUnsupportedTest {
    @Test fun anExtractorThatCannotDoItIsUnsupported() =
        assertTrue(isTabUnsupported(ChannelTabKind.PODCASTS, ExtractorError.Unplayable(NOT_SUPPORTED)))

    @Test fun theFallbackTierSayingNotAvailableIsUnsupported() =
        assertTrue(isTabUnsupported(ChannelTabKind.SHOWS, ExtractorError.TierUnavailable("Tab SHOWS is not available on fallback")))

    @Test fun anUnparsableExoticTabIsUnsupportedButAnUnparsableVideosTabIsAFailure() {
        assertTrue(isTabUnsupported(ChannelTabKind.RELEASES, ExtractorError.ParseFailure("renderers: x")))
        assertFalse(isTabUnsupported(ChannelTabKind.VIDEOS, ExtractorError.ParseFailure("renderers: x")))
    }

    @Test fun networkAndServerProblemsKeepTheirRetry() {
        assertFalse(isTabUnsupported(ChannelTabKind.PODCASTS, ExtractorError.Network("offline", null)))
        assertFalse(isTabUnsupported(ChannelTabKind.PODCASTS, ExtractorError.RateLimited("HTTP 429")))
        assertFalse(isTabUnsupported(ChannelTabKind.PODCASTS, ExtractorError.Blocked("HTTP 403")))
        assertFalse(isTabUnsupported(ChannelTabKind.VIDEOS, ExtractorError.TierUnavailable("HTTP 503")))
        assertFalse(isTabUnsupported(ChannelTabKind.VIDEOS, ExtractorError.Unplayable("HTTP 404")))
    }
}
