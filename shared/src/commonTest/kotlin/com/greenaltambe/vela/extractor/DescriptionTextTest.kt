package com.greenaltambe.vela.extractor

import kotlin.test.Test
import kotlin.test.assertEquals

class DescriptionTextTest {
    @Test fun plainTextIsUnchanged() = assertEquals("Hello\nworld https://a.b", plainDescription("Hello\nworld https://a.b"))

    @Test fun aLinkKeepsItsAddressWhenTheTextIsCut() = assertEquals(
        "See https://example.com/page now",
        plainDescription("See <a href=\"https://example.com/page\">https://example.com/pa…</a> now"),
    )

    @Test fun aRedirectLinkGivesTheRealAddress() = assertEquals(
        "https://example.com/a b",
        plainDescription("<a href=\"https://www.youtube.com/redirect?q=https%3A%2F%2Fexample.com%2Fa+b&amp;v=x\">https://example.com/a…</a>"),
    )

    @Test fun breaksAndEntitiesBecomeText() = assertEquals("a\nb & c <d>", plainDescription("a<br>b &amp; c &lt;d&gt;"))

    @Test fun aLabelledLinkKeepsItsLabel() = assertEquals("0:10 Intro", plainDescription("<a href=\"https://www.youtube.com/watch?v=x&t=10\">0:10</a> Intro"))
}
