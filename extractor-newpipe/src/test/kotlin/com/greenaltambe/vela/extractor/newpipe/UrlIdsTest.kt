package com.greenaltambe.vela.extractor.newpipe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class UrlIdsTest {
    @Test fun videoIds() {
        assertEquals("abc123", videoIdFromUrl("https://www.youtube.com/watch?v=abc123"))
        assertEquals("abc123", videoIdFromUrl("https://www.youtube.com/watch?list=x&v=abc123&t=5"))
        assertEquals("abc123", videoIdFromUrl("https://www.youtube.com/shorts/abc123"))
        assertEquals("abc123", videoIdFromUrl("https://youtu.be/abc123?t=3"))
        assertNull(videoIdFromUrl("https://www.youtube.com/channel/UC1"))
    }

    @Test fun channelIds() {
        assertEquals("UC123", channelIdFromUrl("https://www.youtube.com/channel/UC123"))
        assertEquals("@handle", channelIdFromUrl("https://www.youtube.com/@handle"))
        assertNull(channelIdFromUrl(null))
        assertNull(channelIdFromUrl(""))
    }

    @Test fun expiryFromUrl() {
        assertEquals(Instant.fromEpochSeconds(1_700_000_000), expiryOf("https://r1.googlevideo.com/videoplayback?a=b&expire=1700000000&c=WEB"))
        assertNull(expiryOf("https://r1.googlevideo.com/videoplayback?a=b"))
        assertNull(expiryOf("https://r1.googlevideo.com/videoplayback?expire=soon"))
    }
}
