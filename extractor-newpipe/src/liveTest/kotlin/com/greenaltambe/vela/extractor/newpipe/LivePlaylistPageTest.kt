package com.greenaltambe.vela.extractor.newpipe

import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.PlaylistContents
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The fallback for the playlist page. Prints counts and yes/no only. */
class LivePlaylistPageTest {
    @Test fun aPublicPlaylistPageAndItsNextPage() = runBlocking {
        val x = NewPipeExtractor()
        for ((name, id) in listOf("public" to "PLai5B987bZ9CoVR-QEIN9foz4QCJ0H2Y8", "long" to "UUsBjURrPoezykLs9EqgamOA")) {
            val first = assertIs<ExtractorResult.Success<PlaylistContents>>(x.playlistPage(id)).value
            val d = first.details!!
            println("PAGE newpipe $name: ${first.videos.size} videos, next=${first.continuation != null}; title=${d.title.isNotBlank()} owner=${d.ownerName != null} ownerId=${d.ownerChannelId != null} avatar=${d.ownerAvatarUrl != null} cover=${d.coverUrl != null} count=${d.videoCount} description=${d.description != null}")
            assertTrue(first.videos.isNotEmpty() && d.title.isNotBlank())
            first.continuation?.let { token ->
                val more = assertIs<ExtractorResult.Success<PlaylistContents>>(x.playlistPage(id, token)).value
                println("PAGE newpipe $name, page 2: ${more.videos.size} videos")
                assertTrue(more.videos.isNotEmpty())
            }
        }
    }
}
