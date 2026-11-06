package com.greenaltambe.vela.extractor.newpipe

import com.greenaltambe.vela.extractor.CommentSort
import com.greenaltambe.vela.extractor.ExtractorResult
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/** Comments in "Newest first" order, and the new commenter fields (picture, creator pill), against the real YouTube. */
class LiveCommentsSortTest {
    private val extractor = NewPipeExtractor()
    private val videoId = "dQw4w9WgXcQ"

    private fun <T> ExtractorResult<T>.value(): T = when (this) {
        is ExtractorResult.Success -> value
        is ExtractorResult.Failure -> fail("${error.reason}: ${error.message}")
    }

    @Test
    fun newestFirstDiffersFromTopAndHasAvatars() = runBlocking {
        assumeTrue("SKIPPED: www.youtube.com is unreachable", runCatching { Socket().use { it.connect(InetSocketAddress("www.youtube.com", 443), 5_000) } }.isSuccess)
        val top = extractor.comments(videoId, CommentSort.TOP).value()
        val newest = extractor.comments(videoId, CommentSort.NEWEST).value()
        println("top: total=${top.totalCount} first=${top.comments.take(3).map { it.publishedText }} avatars=${top.comments.count { it.authorAvatarUrl != null }}/${top.comments.size} creator=${top.comments.count { it.isByCreator }} hearted=${top.comments.count { it.isHearted }}")
        println("newest avatars=${newest.comments.count { it.authorAvatarUrl != null }}/${newest.comments.size}"); println("newest: first=${newest.comments.take(5).map { it.publishedText }} next=${newest.continuation != null}")
        assertTrue(newest.comments.isNotEmpty(), "no newest comments")
        assertTrue(newest.appliedSort == CommentSort.NEWEST && top.appliedSort == CommentSort.TOP, "applied sort not reported: ${newest.appliedSort} / ${top.appliedSort}")
        assertTrue(newest.comments.map { it.id } != top.comments.map { it.id }, "newest equals top")
        assertTrue(top.comments.any { it.authorAvatarUrl != null }, "no avatars")
        val more = extractor.comments(videoId, CommentSort.NEWEST, newest.continuation).value()
        assertTrue(more.comments.isNotEmpty(), "second newest page empty")
        val withReplies = top.comments.firstOrNull { it.repliesContinuation != null }
        if (withReplies != null) {
            val r = extractor.replies(videoId, withReplies.id, withReplies.repliesContinuation).value()
            println("replies: ${r.comments.size} next=${r.continuation != null} avatars=${r.comments.count { it.authorAvatarUrl != null }} sample=${r.comments.take(2).map { it.text.take(40) }}")
        }
    }
}
