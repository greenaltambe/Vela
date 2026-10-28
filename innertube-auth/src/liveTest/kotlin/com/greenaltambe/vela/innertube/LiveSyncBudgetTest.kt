package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.*
import org.junit.Test
import kotlin.test.assertTrue

/** The default budget (6 browse calls a minute): a full sync must finish by waiting, not fail with "budget used up". */
class LiveSyncBudgetTest {
    private val jar = LiveCookies.jarOrSkip()
    private val audit = mutableListOf<AuditEntry>()
    private val client = InnertubeClient(AuthSession(jar, AccountGovernor("live-sync", audit = { audit += it })))

    @Test fun aFullSyncUnderTheDefaultBudgetFinishesByWaiting() {
        val t0 = System.currentTimeMillis()
        val subs = client.subscriptions()
        val t1 = System.currentTimeMillis()
        val lists = client.playlists()
        val wl = client.playlistVideos("WL", 2)
        val home = client.browse("FEwhat_to_watch", kotlin.time.Duration.parse("70s"))
        val t2 = System.currentTimeMillis()
        println("SYNC subs=${subs.size} in ${(t1 - t0) / 1000}s; playlists=${lists.size} wl=${wl.size} home=${InnertubeParsers.videos(home).size} videos; total ${(t2 - t0) / 1000}s")
        println("AUDIT " + audit.groupBy { it.result }.mapValues { it.value.size })
        assertTrue(audit.none { it.result == "refused" }, "no refusals: $audit")
        assertTrue(subs.size < 20, "no suggested channels")
    }
}
