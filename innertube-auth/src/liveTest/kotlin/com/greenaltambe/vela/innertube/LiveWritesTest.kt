package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Round trips on the THROWAWAY account (VELA_TEST_COOKIES). Each one reads the original state first and puts it back; every
 * call goes through the governor (writes budget 20/hour here) and the audit entries are printed so the calls can be counted.
 */
class LiveWritesTest {
    private val jar = LiveCookies.jarOrSkip()
    private val audit = mutableListOf<AuditEntry>()
    private val governor = AccountGovernor("live-writes", limits = { GovernorLimits(writesPerHour = 20, browsePerMinute = 20) }, audit = { audit += it })
    private val client = InnertubeClient(AuthSession(jar, governor))

    private fun summary() = println("AUDIT " + audit.groupBy { "${it.kind}:${it.result}" }.mapValues { it.value.size })

    @Test fun libraryReadsPlaylistsAndWatchLater() {
        val lists = client.playlists()
        println("PLAYLISTS count=${lists.size} titles=${lists.map { it.title.take(20) }}")
        val wl = client.playlistVideos("WL", maxPages = 2)
        println("WATCHLATER videos=${wl.size}")
        val liked = client.playlistVideos("LL", maxPages = 2)
        println("LIKED videos=${liked.size}")
        summary()
    }

    private fun settle() = Thread.sleep(3000)

    @Test fun subscribeAndUnsubscribeRestoreTheOriginalState() {
        val ch = "UC_x5XG1OV2P6uZZ5FSM9Ttw"
        val original = client.isSubscribed(ch) ?: return println("SKIP: the channel page has no subscription state")
        if (original) { client.unsubscribe(ch); settle() } else { client.subscribe(ch); settle() }
        val flipped = client.isSubscribed(ch)
        val listed = if (!original) client.subscriptions().map { it.channelId } else null
        if (original) client.subscribe(ch) else client.unsubscribe(ch)
        settle()
        val restored = client.isSubscribed(ch)
        println("SUBSCRIBE original=$original flipped=$flipped restored=$restored listedWhileSubscribed=${listed?.contains(ch)} listSize=${listed?.size}")
        summary()
        assertEquals(!original, flipped); assertEquals(original, restored)
        if (listed != null) assertTrue(ch in listed, "a real subscription is listed by the All subscriptions page")
    }

    @Test fun watchLaterAddAndRemoveRestoresTheOriginalList() {
        val video = "jNQXAC9IVRw"
        val before = client.playlistVideos("WL", maxPages = 3).map { it.videoId }
        if (video in before) return println("SKIP: already in Watch Later")
        client.addToPlaylist("WL", video)
        val during = client.playlistVideos("WL", maxPages = 3).map { it.videoId }
        client.removeFromPlaylist("WL", video)
        val after = client.playlistVideos("WL", maxPages = 3).map { it.videoId }
        println("WATCHLATER before=${before.size} during=${during.size} after=${after.size}")
        summary()
        assertTrue(video in during); assertEquals(before.toSet(), after.toSet())
    }

    @Test fun playlistCreateAddRenameDelete() {
        val id = client.createPlaylist("vela-live-test")
        try {
            client.addToPlaylist(id, "jNQXAC9IVRw")
            client.renamePlaylist(id, "vela-live-test-renamed")
            val vids = client.playlistVideos(id).map { it.videoId }
            val listed = client.playlists().firstOrNull { it.id == id }?.title
            println("PLAYLIST created=${id.take(4)}… videos=$vids listedAs=$listed")
            assertEquals(listOf("jNQXAC9IVRw"), vids)
        } finally { client.deletePlaylist(id) }
        assertTrue(client.playlists().none { it.id == id }, "deleted")
        summary()
    }

    @Test fun likeAndUnlikeRestoreTheOriginalRating() {
        val video = "jNQXAC9IVRw"
        val original = client.rating(video) ?: return println("SKIP: no like status in the watch data")
        client.rate(video, if (original == Rating.LIKE) Rating.NONE else Rating.LIKE); settle()
        val changed = client.rating(video)
        client.rate(video, original); settle()
        val restored = client.rating(video)
        println("LIKE original=$original changed=$changed restored=$restored")
        summary()
        assertTrue(changed != original); assertEquals(original, restored)
    }
}
