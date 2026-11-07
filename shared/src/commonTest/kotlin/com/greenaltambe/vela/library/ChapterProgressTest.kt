package com.greenaltambe.vela.library

import com.greenaltambe.vela.extractor.Chapter
import kotlin.test.Test
import kotlin.test.assertEquals

class ChapterProgressTest {
    private val ch = listOf(Chapter("a", 0), Chapter("b", 100), Chapter("c", 160))

    @Test fun progressIsTheShareOfTheChapterPlayed() {
        assertEquals(0.5f, chapterProgress(ch, 1, 130_000, 400_000))
        assertEquals(0f, chapterProgress(ch, 1, 90_000, 400_000))
        assertEquals(1f, chapterProgress(ch, 0, 250_000, 400_000))
    }

    @Test fun theLastChapterEndsWithTheVideo() {
        assertEquals(400L, chapterEndSeconds(ch, 2, 400_000))
        assertEquals(0.25f, chapterProgress(ch, 2, 220_000, 400_000))
    }
}
