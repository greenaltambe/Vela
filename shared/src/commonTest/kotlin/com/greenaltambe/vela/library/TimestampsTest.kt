package com.greenaltambe.vela.library

import com.greenaltambe.vela.extractor.Chapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimestampsTest {
    @Test
    fun findsMinuteAndHourTimestamps() {
        val found = findTimestamps("see 1:23 and 01:02:03 or 12:05.")
        assertEquals(listOf(83L, 3723L, 725L), found.map { it.seconds })
    }

    @Test
    fun ignoresImpossibleAndEmbeddedNumbers() {
        assertTrue(findTimestamps("ratio 1:75 or 3:4:5 or 10:61").isEmpty())
        assertTrue(findTimestamps("version 2.10:30pm").size <= 1)
    }

    @Test
    fun descriptionChaptersNeedZeroStartThreeEntriesAndIncreasingTimes() {
        val ok = "Intro\n0:00 Start\n1:30 Middle\n- 5:00 - End\nthanks"
        assertEquals(listOf(Chapter("Start", 0), Chapter("Middle", 90), Chapter("End", 300)), chaptersFromDescription(ok))
        assertTrue(chaptersFromDescription("0:00 Start\n1:30 Middle").isEmpty(), "two is a mention, not a list")
        assertTrue(chaptersFromDescription("0:30 a\n1:00 b\n2:00 c").isEmpty(), "must start at 0:00")
    }

    @Test
    fun nonIncreasingLinesAreDropped() {
        val text = "0:00 a\n2:00 b\n1:00 stray\n3:00 c\n4:00 d"
        assertEquals(listOf("a", "b", "c", "d"), chaptersFromDescription(text).map { it.title })
    }

    @Test
    fun chaptersPastTheEndAreDropped() {
        val text = "0:00 a\n1:00 b\n2:00 c\n9:00 beyond"
        assertEquals(3, chaptersFromDescription(text, durationSeconds = 300).size)
    }

    @Test
    fun sourceChaptersWinOverDescription() {
        val source = listOf(Chapter("x", 0))
        assertEquals(source, effectiveChapters(source, "0:00 a\n1:00 b\n2:00 c", 600))
        assertEquals(3, effectiveChapters(emptyList(), "0:00 a\n1:00 b\n2:00 c", 600).size)
    }

    @Test
    fun currentChapterFollowsPosition() {
        val chapters = listOf(Chapter("a", 0), Chapter("b", 60), Chapter("c", 120))
        assertEquals("a", currentChapter(chapters, 0)?.title)
        assertEquals("a", currentChapter(chapters, 59_999)?.title)
        assertEquals("b", currentChapter(chapters, 60_000)?.title)
        assertEquals("c", currentChapter(chapters, 9_999_999)?.title)
        assertEquals(null, currentChapter(emptyList(), 5))
    }

    @Test
    fun formatsLikeAPlayer() {
        assertEquals("0:05", formatTimestamp(5))
        assertEquals("1:05", formatTimestamp(65))
        assertEquals("1:01:05", formatTimestamp(3665))
    }

    @Test
    fun descriptionSplitsIntoTextLinksAndTimes() {
        val parts = parseDescription("Watch 1:23 now https://example.com/a?t=1:23, thanks")
        assertEquals(
            listOf(
                DescriptionPart.Text("Watch "),
                DescriptionPart.Time("1:23", 83),
                DescriptionPart.Text(" now "),
                DescriptionPart.Link("https://example.com/a?t=1:23", "https://example.com/a?t=1:23"),
                DescriptionPart.Text(", thanks"),
            ),
            parts,
        )
    }

    @Test
    fun htmlAnchorsAndEntitiesAreReadable() {
        val parts = parseDescription("""Links:<br><a href="https://example.com/x">my site</a> &amp; more""")
        val text = parts.joinToString("") { when (it) { is DescriptionPart.Text -> it.text; is DescriptionPart.Link -> it.text; is DescriptionPart.Time -> it.text } }
        assertTrue(text.contains("my site"))
        assertTrue(text.contains("& more"))
        assertTrue(parts.any { it is DescriptionPart.Link && it.url == "https://example.com/x" })
    }
}
