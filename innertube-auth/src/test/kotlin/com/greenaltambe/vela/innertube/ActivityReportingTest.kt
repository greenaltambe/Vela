package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.*
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.player.ActivityAddon
import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.player.PlayerState
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ActivityReportingTest {
    private class TestClock(var at: Instant = Instant.fromEpochSeconds(1_700_000_000)) : Clock { override fun now() = at }
    private val clock = TestClock()
    private val db = createDatabase(JvmDatabaseDriverFactory())
    private val accounts = AccountManager(db, CredentialStore(InMemorySecretStore()))
    private val notes = mutableListOf<String>()
    private val urls = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val audits = java.util.Collections.synchronizedList(mutableListOf<AuditEntry>())
    private var tracking = true
    private var failPings = false
    private var on = true
    private val registry = GovernorRegistry(clock, { GovernorLimits(minSpacingMs = 0) }, audit = { audits += it }, sleeper = {})
    private val transport = FakeTransport { _, url, _, _ ->
        urls += url
        when {
            "/player" in url -> RawResponse(200, if (tracking) """{"playbackTracking":{"videostatsPlaybackUrl":{"baseUrl":"https://s.youtube.com/api/stats/playback?docid=v1&ei=E&ptk=P"},"videostatsWatchtimeUrl":{"baseUrl":"https://s.youtube.com/api/stats/watchtime?docid=v1&ei=E&ptk=P"}}}""" else """{"playabilityStatus":{}}""", emptyMap(), "")
            failPings -> RawResponse(500, "", emptyMap(), "")
            else -> RawResponse(204, "", emptyMap(), "")
        }
    }
    private fun reporter(enabled: () -> Boolean = { on }, offlineOk: Boolean = false) =
        InnertubeAccountActivity(accounts, registry, enabled, { offlineOk }, { notes += it }, transport, WebClient("WEB", "2.0", "en", "US", "UA"), clock)
    private fun signIn() = accounts.addGoogle(GoogleIdentity("T", "t@example.com", null, "UCx"), CookieJar(listOf(Cookie("SID", "sid-secret", ".google.com"), Cookie("SAPISID", "AbCdEfGhIjKlMnOp/12345", ".youtube.com"))))
    private fun pings(kind: String) = synchronized(urls) { urls.filter { "/stats/$kind" in it } }
    private fun waitFor(what: String, check: () -> Boolean) { val end = System.currentTimeMillis() + 5_000; while (!check()) { if (System.currentTimeMillis() > end) fail("timed out: $what; urls=${urls.map { it.substringBefore('?') }}"); Thread.sleep(10) } }
    private fun q(url: String, name: String) = url.substringAfter('?').split('&').firstOrNull { it.startsWith("$name=") }?.substringAfter('=')

    /** Plays [id] from 0 to the end of a 100 s video with a seek, driving the add-on like a session would. */
    private fun drive(r: InnertubeAccountActivity, end: Boolean) {
        val addon = ActivityAddon(r, clock, 30.seconds)
        addon.onVideo("v1", "", false)
        fun st(status: PlaybackStatus, pos: Long) = addon.onPlayerState(PlayerState(status, pos, 100_000))
        st(PlaybackStatus.PLAYING, 0)
        for (s in 1..35) { clock.at += 1.seconds; st(PlaybackStatus.PLAYING, s * 1000L) }   // 35 s: one periodic report
        st(PlaybackStatus.PAUSED, 35_000)
        st(PlaybackStatus.PLAYING, 80_000)                                                   // seek while paused
        clock.at += 1.seconds; st(PlaybackStatus.PLAYING, 81_000)
        if (end) st(PlaybackStatus.ENDED, 100_000) else addon.onStop()
    }

    @Test fun aWatchedVideoSendsAPlaybackPingThenWatchtimePingsEndingInFinalAtTheRealEnd() {
        signIn()
        drive(reporter(), end = true)
        waitFor("final ping") { pings("watchtime").any { "final=1" in it } }
        assertEquals(1, pings("playback").size)
        val wt = pings("watchtime")
        assertEquals(3, wt.size, "periodic (30 s), pause, end")
        assertEquals("playing", q(wt[0], "state")); assertEquals("paused", q(wt[1], "state"))
        assertTrue("final=1" in wt.last() && q(wt.last(), "cmt") == "100.000", "the final ping is at the real end")
        assertEquals("0.000", q(wt[0], "st")); assertEquals("30.000", q(wt[0], "et"))
        assertTrue(q(wt.last(), "st")!!.startsWith("80.000") || q(wt.last(), "st")!!.contains("80.000"), "the seek started a new segment at 80 s")
        assertTrue(wt.all { q(it, "cpn")!!.length == 16 && q(it, "ver") == "2" })
        assertTrue(audits.all { it.kind == CallKind.REPORT.name || it.kind.contains("REPORT") })
    }

    @Test fun stoppingMidVideoSendsTheLastPositionOnce() {
        signIn()
        drive(reporter(), end = false)
        waitFor("final ping") { pings("watchtime").any { "final=1" in it } }
        assertEquals(1, pings("watchtime").count { "final=1" in it })
        assertEquals("81.000", q(pings("watchtime").last(), "cmt"))
    }

    @Test fun nothingIsSentWhenAnyGateIsOff() {
        drive(reporter(), end = true); Thread.sleep(150)               // not signed in
        assertTrue(urls.isEmpty()); assertTrue(notes.any { "not signed in" in it })
        signIn(); notes.clear()
        drive(reporter(enabled = { false }), end = true); Thread.sleep(150)    // setting off / history kept on the device
        assertTrue(urls.isEmpty()); assertTrue(notes.isEmpty(), "off is not a problem to report")
        val addon = ActivityAddon(reporter(), clock); addon.onVideo("v1", "", true)   // a downloaded file
        addon.onPlayerState(PlayerState(PlaybackStatus.PLAYING, 0, 1000)); Thread.sleep(150)
        assertTrue(urls.isEmpty()); assertTrue(notes.any { "downloaded" in it })
    }

    @Test fun noTrackingAddressSkipsTheVideoWithOneNoteAndNoMoreRequests() {
        signIn(); tracking = false
        drive(reporter(), end = true)
        waitFor("note") { notes.any { "no tracking address" in it } }
        Thread.sleep(150)
        assertEquals(1, urls.size, "only the /player request; no pings"); assertEquals(1, notes.size)
    }

    @Test fun threeFailuresInARowStopReportingAndNoSecretIsEverLogged() {
        signIn(); failPings = true
        drive(reporter(), end = true)
        waitFor("stopped") { notes.any { "Stopped reporting" in it } }
        assertTrue(notes.none { "sid-secret" in it || "ptk=" in it || "https://" in it || "AbCdEf" in it }, notes.toString())
        assertTrue(audits.none { "sid-secret" in it.toString() || "AbCdEfGhIjKlMnOp" in it.toString() || "ptk=" in it.toString() })
    }

    @Test fun parsersAndHelpers() {
        assertEquals("a=1&b=2&ver=2", InnertubeAccountActivity.withQuery("https://x/y?a=1&ver=1&b=2", mapOf("ver" to "2")).substringAfter('?'))
        assertEquals("12.345", InnertubeAccountActivity.seconds(12_345)); assertEquals("0.000", InnertubeAccountActivity.seconds(-5))
        assertEquals(16, InnertubeAccountActivity.newCpn().length)
        assertNull(InnertubeAccountActivity.parseTracking(lenientJson.parseToJsonElement("""{"playbackTracking":{}}""")))
    }
}
