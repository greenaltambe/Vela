package com.greenaltambe.vela.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VttTest {
    /** Shaped like the file measured on a Tsoding video (2026-11): a STYLE block, a chat overlay in a box at the top right, messages spread over lines. */
    private val chat = """WEBVTT
Kind: captions
Language: en
Style:
::cue(c.blue) { color: blue; }
::cue(c.color008000) { color: rgb(0,128,0);
 }
::cue(c.color8A2BE2) { color: rgb(138,43,226); }
::cue(c.colorFEFEFE) { color: rgb(254,254,254); }
##

00:00:03.000 --> 00:00:07.000 align:start size:75% position:25% line:0%
<c.color008000>literallygambling</c><c.colorFEFEFE>​: I'll
do it
</c>

00:00:07.000 --> 00:00:08.000 align:start size:75% position:25% line:0%
<c.color008000>literallygambling</c><c.colorFEFEFE>​: I'll
do it
</c><c.color8A2BE2>tlb_miss</c><c.colorFEFEFE>​: fuck you
youtube
</c>
"""

    @Test fun placementIsReadFromTheCueSettings() {
        val c = Vtt.parse(chat).first()
        assertEquals(CuePlacement(line = 0f, position = 0.25f, size = 0.75f, align = CueAlign.START), c.placement)
    }

    @Test fun userNamesKeepTheirColoursAndMessagesKeepTheirLines() {
        val c = Vtt.parse(chat)[1]
        assertEquals(4, c.lines.size)
        val firstLine = c.lines[0]
        assertEquals("literallygambling", firstLine[0].text)
        assertEquals(0xFF008000.toInt(), firstLine[0].color)
        assertEquals(0xFFFEFEFE.toInt(), firstLine[1].color)
        assertEquals(0xFF8A2BE2.toInt(), c.lines[2][0].color)
        assertEquals("tlb_miss", c.lines[2][0].text)
    }

    @Test fun thePlainTextHasNoTagsAndAMessageSplitOverLinesStaysTogether() {
        val c = Vtt.parse(chat)[1]
        assertTrue("<" !in c.text)
        assertTrue(c.text.contains("do it"))
        assertEquals("literallygambling​: I'll", c.lines[0].joinToString("") { it.text })
    }

    @Test fun colourClassesNotInTheStyleBlockStillWork() {
        val c = Vtt.parse("WEBVTT\n\n00:01.000 --> 00:02.000\n<c.yellow>hello</c> <c.colorFF0000>red</c>").single()
        assertEquals(0xFFFFFF00.toInt(), c.lines[0][0].color)
        assertEquals(0xFFFF0000.toInt(), c.lines[0].last().color)
    }

    @Test fun boldItalicAndUnderlineAreKeptAndNest() {
        val c = Vtt.parse("WEBVTT\n\n00:01.000 --> 00:02.000\n<b>bold <i>both</i></b> <u>under</u> plain").single()
        val runs = c.lines.single()
        assertTrue(runs.first { it.text.startsWith("bold") }.bold)
        assertTrue(runs.first { it.text == "both" }.let { it.bold && it.italic })
        assertTrue(runs.first { it.text == "under" }.underline)
        assertTrue(runs.last().let { !it.bold && !it.italic && !it.underline })
    }

    @Test fun anOrdinaryCueHasNoPlacementAndWordTimestampsAreDropped() {
        val c = Vtt.parse("WEBVTT\n\n00:01.000 --> 00:03.000 align:center\nthis <00:00:01.500><c>is</c> <00:00:02.000>fine").single()
        assertNull(c.placement)
        assertEquals("this is fine", c.text)
    }

    @Test fun rollingAutomaticCaptionsStillDropTheRepeatedLine() {
        val v = "WEBVTT\n\n00:01.000 --> 00:03.000\nfirst line\nsecond line\n\n00:03.000 --> 00:05.000\nsecond line\nthird line\n"
        assertEquals(listOf("first line\nsecond line", "third line"), Vtt.parse(v).map { it.text })
    }

    @Test fun activeShowsOnePlainCueAndEveryPlacedBoxButOnlyTheLatestInEach() {
        val cues = Vtt.parse(chat) + Cue(5_000, 9_000, "speech", listOf(listOf(CaptionRun("speech"))), null)
        val at = Vtt.active(cues.sortedBy { it.startMs }, 7_500)
        assertEquals(1, at.count { it.placement != null }, "the two chat cues share a box: only the latest")
        assertEquals(7_000L, at.first { it.placement != null }.startMs)
        assertEquals("speech", at.first { it.placement == null }.text)
        assertTrue(Vtt.active(cues, 20_000).isEmpty())
    }

    @Test fun cssColoursInAllTheirForms() {
        assertEquals(0xFF008000.toInt(), CaptionColors.parse("rgb(0,128,0)"))
        assertEquals(0xFF112233.toInt(), CaptionColors.parse("#112233"))
        assertEquals(0xFFAABBCC.toInt(), CaptionColors.parse("#abc"))
        assertEquals(0xFF0000FF.toInt(), CaptionColors.parse("blue"))
        assertNull(CaptionColors.parse("nonsense"))
    }

    @Test fun darkCreatorColoursAreLightenedAndLightOnesLeftAlone() {
        val brown = 0xFF4C3131.toInt()
        val fixed = CaptionColors.readableOnDark(brown)
        assertTrue(fixed != brown)
        assertTrue((fixed shr 16 and 255) > (brown shr 16 and 255))
        assertEquals(0xFFFEFEFE.toInt(), CaptionColors.readableOnDark(0xFFFEFEFE.toInt()))
        assertEquals(0xFFFFB4AA.toInt(), CaptionColors.readableOnDark(0xFFFFB4AA.toInt()))
        assertNotNull(CaptionColors.readableOnDark(0xFF0000FF.toInt()))
    }

    @Test fun theAlignAndPositionYouTubeWritesOnEveryAutomaticCaptionIsNotAPlacement() {
        val c = Vtt.parse("WEBVTT\n\n00:01.000 --> 00:03.000 align:start position:0%\nhello there").single()
        assertNull(c.placement)
    }

    @Test fun aBoxNarrowerThanThePictureOrALineIsAPlacementEvenWithoutPosition() {
        assertNotNull(Vtt.parse("WEBVTT\n\n00:01.000 --> 00:03.000 size:50%\nhi").single().placement)
        assertNotNull(Vtt.parse("WEBVTT\n\n00:01.000 --> 00:03.000 line:10%\nhi").single().placement)
    }
}
