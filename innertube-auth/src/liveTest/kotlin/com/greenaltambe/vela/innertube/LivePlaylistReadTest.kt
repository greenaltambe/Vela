package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.AccountGovernor
import org.junit.Test
import kotlin.test.assertTrue

/** Read-only: the account's own playlist reader against both layouts YouTube serves. Prints counts only. */
class LivePlaylistReadTest {
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
