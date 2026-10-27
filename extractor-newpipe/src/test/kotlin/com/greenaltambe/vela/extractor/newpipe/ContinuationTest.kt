package com.greenaltambe.vela.extractor.newpipe

import org.schabi.newpipe.extractor.Page
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContinuationTest {
    @Test fun pageSurvivesARoundTrip() {
        val page = Page("https://www.youtube.com/youtubei/v1/browse", "id-1", listOf("a", "b"), mapOf("k" to "v"), byteArrayOf(0, 1, -2, 127))
        val back = pageFromContinuationToken(page.toContinuationToken())!!
        assertEquals(page.url, back.url)
        assertEquals(page.id, back.id)
        assertEquals(page.ids, back.ids)
        assertEquals(page.cookies, back.cookies)
        assertContentEquals(page.body, back.body)
    }

    @Test fun urlOnlyPageSurvives() {
        val back = pageFromContinuationToken(Page("https://x").toContinuationToken())!!
        assertEquals("https://x", back.url)
        assertNull(back.body)
    }

    @Test fun tokensAreStampedWithTheTier() {
        assertTrue(Page("https://x").toContinuationToken().startsWith("newpipe:"))
    }

    @Test fun tokensFromAnotherTierAreRejected() {
        assertNull(pageFromContinuationToken("ytdlp:41"))
        assertNull(pageFromContinuationToken("newpipe:%%%not-base64%%%"))
    }

    @Test fun garbageIsRejected() {
        assertNull(pageFromContinuationToken("not a token"))
        assertNull(pageFromContinuationToken(""))
    }

    @Test fun missingOrInvalidNextPageGivesNoToken() {
        assertNull((null as Page?).toTokenOrNull())
        assertNull(Page(null as String?).toTokenOrNull())
    }
}
