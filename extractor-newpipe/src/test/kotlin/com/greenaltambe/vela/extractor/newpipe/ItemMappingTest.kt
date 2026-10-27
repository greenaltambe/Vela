package com.greenaltambe.vela.extractor.newpipe

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItemMappingTest {
    private fun item(url: String) = StreamInfoItem(0, url, "Title", StreamType.VIDEO_STREAM).apply {
        uploaderName = "Chan"
        uploaderUrl = "https://www.youtube.com/channel/UC9"
        duration = 61
        viewCount = -1
        thumbnails = listOf(Image("t", 90, 120, Image.ResolutionLevel.LOW))
    }

    @Test fun mapsSearchResult() {
        val s = item("https://www.youtube.com/watch?v=abc").apply { setShortFormContent(false) }.toVideoSummary()!!
        assertEquals("abc", s.id)
        assertEquals("UC9", s.channelId)
        assertEquals("Chan", s.channelName)
        assertEquals(61L, s.durationSeconds)
        assertNull(s.viewCount)
        assertEquals("t", s.thumbnailUrl)
    }

    @Test fun shortsAreFlagged() {
        assertTrue(item("https://www.youtube.com/shorts/xyz").apply { setShortFormContent(true) }.toVideoSummary()!!.isShort)
    }

    @Test fun itemsWithoutAVideoIdAreDropped() {
        assertNull(item("https://www.youtube.com/playlist?list=PL1").toVideoSummary())
        assertEquals(1, listOf(item("https://www.youtube.com/watch?v=a"), item("https://x/none")).toVideoSummaries().size)
    }

    @Test fun commentsCarryTheirRepliesContinuation() {
        val with = CommentsInfoItem(0, "u", "n").apply { replies = org.schabi.newpipe.extractor.Page("https://u", "reply-token") }.toComment()
        assertTrue(with.repliesContinuation!!.startsWith("newpipe:"))
        assertNull(CommentsInfoItem(0, "u", "n").toComment().repliesContinuation)
    }

    @Test fun mapsComments() {
        val c = CommentsInfoItem(0, "https://www.youtube.com/watch?v=abc&lc=1", "name").apply {
            commentId = "c1"
            uploaderName = "Bob"
            commentText = Description("Nice", Description.PLAIN_TEXT)
            likeCount = -1
            textualUploadDate = "2 days ago"
            replyCount = 4
            isPinned = true
        }.toComment()
        assertEquals("c1", c.id)
        assertEquals("Bob", c.author)
        assertEquals("Nice", c.text)
        assertEquals(0L, c.likeCount)
        assertEquals("2 days ago", c.publishedText)
        assertEquals(4, c.replyCount)
        assertTrue(c.isPinned)
    }
}
