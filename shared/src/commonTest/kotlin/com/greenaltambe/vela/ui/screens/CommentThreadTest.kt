package com.greenaltambe.vela.ui.screens

import com.greenaltambe.vela.extractor.Comment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommentThreadTest {
    private fun c(id: String, author: String, text: String) = Comment(id, author, text)

    @Test fun repliesThatAnswerSomeoneHangUnderThem() {
        val rows = threadRows(listOf(
            c("1", "@ann", "first"),
            c("2", "@bob", "@ann agreed"),
            c("3", "@cy", "unrelated"),
            c("4", "@ann", "@bob thanks"),
            c("5", "@dee", "@cy, nope"),
        ))
        assertEquals(listOf("1" to 0, "2" to 1, "4" to 2, "3" to 0, "5" to 1), rows.map { it.comment.id to it.depth })
    }

    @Test fun anAnswerToSomeoneWhoHasNotSpokenAnswersTheComment() {
        val rows = threadRows(listOf(c("1", "@ann", "@zed hi")))
        assertEquals(listOf(0), rows.map { it.depth })
    }

    @Test fun aNameAfterInvisibleCharactersStillCounts() {
        val rows = threadRows(listOf(c("1", "@ann", "first"), c("2", "@bob", "\u200B\u00A0@ann yes")))
        assertEquals(listOf(0, 1), rows.map { it.depth })
    }

    @Test fun depthIsCapped() {
        val chain = (1..8).map { i -> c("$i", "@u$i", if (i == 1) "start" else "@u${i - 1} next") }
        assertEquals(4, threadRows(chain).maxOf { it.depth })
    }

    @Test fun findsTheFirstTimestamp() {
        assertEquals(222L, firstTimestamp("love 3:42 and 5:10"))
        assertNull(firstTimestamp("no moment here"))
    }
}
