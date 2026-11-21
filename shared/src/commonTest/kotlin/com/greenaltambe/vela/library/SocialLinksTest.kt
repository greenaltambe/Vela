package com.greenaltambe.vela.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SocialLinksTest {
    @Test fun knownNetworksIncludingSubdomains() {
        assertEquals(Social.TIKTOK, socialOf("https://www.tiktok.com/@peacocktv"))
        assertEquals(Social.FACEBOOK, socialOf("https://m.facebook.com/monk"))
        assertEquals(Social.X, socialOf("https://twitter.com/monk_usa"))
        assertEquals(Social.INSTAGRAM, socialOf("http://instagram.com/peacocktv?hl=en"))
        assertNull(socialOf("https://example.com/facebook.com"))
        assertNull(socialOf("https://notx.com/a"))
    }

    @Test fun handleComesFromTheLabelOrThePath() {
        assertEquals("peacocktv", socialHandle("TikTok: peacocktv", "https://www.tiktok.com/@peacocktv"))
        assertEquals("monk_usa", socialHandle("https://twitter.com/monk_usa", "https://twitter.com/monk_usa"))
        assertEquals("peacocktv", socialHandle("", "https://www.tiktok.com/@peacocktv/"))
    }

    @Test fun anchorsInADescriptionStayLinks() {
        val parts = parseDescription("""Follow: <a href="https://www.youtube.com/redirect?q=https%3A%2F%2Fwww.tiktok.com%2F%40peacocktv">TikTok: peacocktv</a>""")
        val link = parts.filterIsInstance<DescriptionPart.Link>().single()
        assertEquals("https://www.tiktok.com/@peacocktv", link.url)
    }
}
