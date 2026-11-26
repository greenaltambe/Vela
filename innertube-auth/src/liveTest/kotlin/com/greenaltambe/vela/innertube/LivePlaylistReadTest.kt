package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.AccountGovernor
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.PlaylistContents
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Read-only: playlist reading, signed in and out, against both layouts YouTube serves. Prints counts and yes/no only. */
class LivePlaylistReadTest {
    private val public = "PLai5B987bZ9CoVR-QEIN9foz4QCJ0H2Y8"
    private val long = "UUsBjURrPoezykLs9EqgamOA"

    private fun describe(name: String, c: PlaylistContents) {
        val d = c.details
        println("PAGE $name: ${c.videos.size} videos, ${c.unreadCount} unread, next=${c.continuation != null}, views on ${c.videos.count { it.viewCount != null }}, thumbnails ${c.videos.count { it.thumbnailUrl != null }}")
        if (d != null) println("PAGE $name header: title=${d.title.isNotBlank()} owner=${d.ownerName != null} ownerId=${d.ownerChannelId != null} avatar=${d.ownerAvatarUrl != null} cover=${d.coverUrl != null} " +
            "count=${d.videoCount} views=${d.viewCount != null} description=${d.description != null} canBeSaved=${d.canBeSaved} saved=${d.savedOnAccount}")
    }

    @Test fun thePlaylistPageWithoutAnAccount() = runBlocking {
        val browse = ChannelBrowse()
        for ((name, id) in listOf("public" to public, "long" to long)) {
            val first = assertIs<ExtractorResult.Success<PlaylistContents>>(browse.playlist(id)).value
            describe("anonymous $name", first)
            assertTrue(first.videos.isNotEmpty() && first.details?.title?.isNotBlank() == true && first.details?.savedOnAccount == null)
            first.continuation?.let { token ->
                val more = assertIs<ExtractorResult.Success<PlaylistContents>>(browse.playlist(id, token)).value
                describe("anonymous $name, page 2", more)
                assertTrue(more.videos.isNotEmpty() && more.videos.first().id !in first.videos.map { it.id })
            }
        }
        val missing = browse.playlist("PL0000000000000000000000000000000000")
        println("PAGE a missing playlist: ${(missing as? ExtractorResult.Failure)?.error?.reason}")
        assertIs<ExtractorResult.Failure>(missing)
        Unit
    }

    @Test fun thePlaylistPageAsTheAccount() {
        val c = InnertubeClient(AuthSession(LiveCookies.jarOrSkip(), AccountGovernor("live-playlist-page")))
        for ((name, id) in listOf("public" to public, "Watch later" to "WL", "Liked videos" to "LL")) {
            val page = c.browse("VL$id", kotlin.time.Duration.parse("70s"))
            assertTrue(!InnertubeParsers.isSignedOutPage(page), "$name read as signed out")
            val contents = assertNotNull(PlaylistPageParser.contents(page, id, first = true, signedIn = true) { it })
            describe("account $name", contents)
        }
    }

    /** What library sync goes by: each list in the account's library says whose it is. Only your own channel's lists offer no "save". */
    @Test fun everyListInTheLibrarySaysWhoseItIs() {
        val c = InnertubeClient(AuthSession(LiveCookies.jarOrSkip(), AccountGovernor("live-playlist-owner")))
        val me = assertNotNull(c.accountInfo().channelId, "the account's channel id")
        c.playlists().forEachIndexed { i, listed ->
            val read = c.readPlaylist(listed.id, maxPages = 2)
            val d = assertNotNull(PlaylistPageParser.details(read.firstPage, listed.id, signedIn = true))
            val own = d.ownerChannelId == me
            println("OWNER list #$i (${listed.id.take(2)}): own=$own canBeSaved=${d.canBeSaved} saved=${d.savedOnAccount} videos=${read.videos.size}")
            assertTrue(d.canBeSaved == !own, "list #$i: own=$own but canBeSaved=${d.canBeSaved}")
            if (!own) assertTrue(d.savedOnAccount == true, "list #$i is in the library but its page does not say saved")
        }
    }

    @Test fun bothLayoutsAndFurtherPagesAreRead() {
        val c = InnertubeClient(AuthSession(LiveCookies.jarOrSkip(), AccountGovernor("live-playlist")))
        fun read(name: String, id: String, maxPages: Int = 60): Pair<List<AccountPlaylistVideo>, Boolean> = c.playlistListing(id, maxPages).also { (v, complete) ->
            println("PLAYLIST $name: ${v.size} videos, complete=$complete, with channel name ${v.count { !it.channelName.isNullOrBlank() }}, with channel id ${v.count { it.channelId.isNotBlank() }}, with duration ${v.count { it.durationSeconds != null }}")
        }
        read("Watch later", "WL")
        read("Liked videos", "LL")
        assertTrue(read("a public playlist", "PLai5B987bZ9CoVR-QEIN9foz4QCJ0H2Y8").first.isNotEmpty(), "a public playlist of 44 videos read as empty")
        val long = read("a long public list, two pages", "UUsBjURrPoezykLs9EqgamOA", maxPages = 2)
        assertTrue(long.first.size > 100 && !long.second, "the second page was not read, or a cut list was called complete")
    }
}
