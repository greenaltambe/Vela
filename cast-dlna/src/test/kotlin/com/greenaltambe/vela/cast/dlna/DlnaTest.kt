package com.greenaltambe.vela.cast.dlna

import com.greenaltambe.vela.extractor.VideoStream
import com.greenaltambe.vela.player.PlaybackMetadata
import com.greenaltambe.vela.player.PlaybackPlan
import com.greenaltambe.vela.player.PlaybackStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A TV that records what it is sent and answers the transport queries from fields the test sets. */
private class FakeTv : UpnpHttp {
    val actions = mutableListOf<Pair<String, String>>()
    @Volatile var transportState = "STOPPED"
    @Volatile var rel = "0:00:00"
    @Volatile var failPosts = false
    var description = ""
    override fun get(url: String) = description
    override fun postSoap(url: String, soapAction: String, body: String): String {
        if (failPosts) throw UpnpException("down")
        val action = soapAction.substringAfter('#')
        synchronized(actions) { actions += action to body }
        if (action == "Play") transportState = "PLAYING"
        if (action == "Pause") transportState = "PAUSED_PLAYBACK"
        if (action == "Stop") transportState = "STOPPED"
        val inner = when (action) {
            "GetTransportInfo" -> "<CurrentTransportState>$transportState</CurrentTransportState>"
            "GetPositionInfo" -> "<RelTime>$rel</RelTime><TrackDuration>0:10:00</TrackDuration>"
            else -> ""
        }
        return "<s:Envelope xmlns:s=\"x\"><s:Body><u:${action}Response xmlns:u=\"y\">$inner</u:${action}Response></s:Body></s:Envelope>"
    }
    fun names() = synchronized(actions) { actions.map { it.first } }
    fun body(action: String) = synchronized(actions) { actions.last { it.first == action }.second }
}

class DlnaTest {
    private val desc = """<?xml version="1.0"?><root xmlns="urn:schemas-upnp-org:device-1-0"><URLBase>http://192.168.1.50:8080/</URLBase>
        <device><friendlyName>Living room TV</friendlyName><UDN>uuid:abc</UDN><serviceList>
        <service><serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType><controlURL>/rc</controlURL></service>
        <service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType><controlURL>/avt/control</controlURL></service>
        </serviceList></device></root>"""

    private fun muxed(url: String = "https://v.example/video?a=1&b=2") =
        PlaybackPlan.Muxed(VideoStream(url, "video/mp4; codecs=\"avc1\"", "avc1", 1000, 640, 360, 30, hasAudio = true), emptyMap())

    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private suspend fun until(timeoutMs: Long = 5000, cond: () -> Boolean) = withTimeout(timeoutMs) { while (!cond()) delay(20) }

    @Test fun descriptionGivesNameAndAbsoluteControlUrl() {
        val r = assertNotNull(parseDeviceDescription(desc, "http://192.168.1.50:8080/desc.xml"))
        assertEquals("Living room TV", r.name)
        assertEquals("uuid:abc", r.udn)
        assertEquals("http://192.168.1.50:8080/avt/control", r.avTransportControlUrl)
    }

    @Test fun deviceWithoutAvTransportIsNotATarget() {
        assertNull(parseDeviceDescription(desc.replace("AVTransport", "Other"), "http://x/desc.xml"))
    }

    @Test fun locationHeaderIsParsed() {
        assertEquals("http://1.2.3.4/d.xml", parseLocation("HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nLocation: http://1.2.3.4/d.xml\r\nST: x\r\n\r\n"))
        assertNull(parseLocation("HTTP/1.1 200 OK\r\n\r\n"))
    }

    @Test fun timeRoundTrips() {
        assertEquals("1:02:03", formatTime(3_723_000)); assertEquals(3_723_000, parseTime("1:02:03.500")); assertNull(parseTime("NOT_IMPLEMENTED"))
    }

    @Test fun discoveryListsRenderersOnceAndSkipsBrokenOnes() {
        val tv = FakeTv().apply { description = desc }
        val found = DlnaDiscovery({ setOf("http://a/d.xml", "http://b/d.xml") }, tv).discover()
        assertEquals(listOf("Living room TV"), found.map { it.name })
        val broken = object : UpnpHttp { override fun get(url: String): String = throw UpnpException("x"); override fun postSoap(url: String, soapAction: String, body: String) = "" }
        assertTrue(DlnaDiscovery({ setOf("http://a/d.xml") }, broken).discover().isEmpty())
    }

    @Test fun loadSendsEscapedUriThenPlaysAndSeeksAndReportsPosition() = runBlocking {
        val tv = FakeTv(); val s = scope()
        val c = DlnaController(AvTransport("http://tv/avt", tv), s, pollMs = 20)
        tv.rel = "0:01:00"
        c.load(muxed(), startPositionMs = 60_000, playWhenReady = true, metadata = PlaybackMetadata(title = "A & B"))
        until { c.state.value.status == PlaybackStatus.PLAYING && c.state.value.positionMs == 60_000L }
        val names = tv.names()
        assertTrue(names.indexOf("SetAVTransportURI") < names.indexOf("Play"))
        assertTrue("Seek" in names)
        assertTrue(tv.body("Seek").contains("0:01:00"))
        val set = tv.body("SetAVTransportURI")
        assertTrue(set.contains("a=1&amp;amp;b=2"), "URL is escaped once for the DIDL and once for the SOAP body: $set")
        assertTrue(set.contains("A &amp;amp; B"))
        assertEquals(600_000L, c.state.value.durationMs)
        c.release(); s.cancel()
    }

    @Test fun pauseAndSeekAreSentAndStopEndsTheSession() = runBlocking {
        val tv = FakeTv(); val s = scope()
        val c = DlnaController(AvTransport("http://tv/avt", tv), s, pollMs = 20)
        c.load(muxed(), 0, true)
        until { c.state.value.status == PlaybackStatus.PLAYING }
        c.pause(); until { "Pause" in tv.names() }
        c.seekTo(90_000); until { "Seek" in tv.names() }
        assertTrue(tv.body("Seek").contains("0:01:30"))
        c.stop(); until { "Stop" in tv.names() }
        assertEquals(PlaybackStatus.IDLE, c.state.value.status)
        s.cancel()
    }

    @Test fun finishedPlaybackIsReportedAsEnded() = runBlocking {
        val tv = FakeTv(); val s = scope()
        val c = DlnaController(AvTransport("http://tv/avt", tv), s, pollMs = 20)
        c.load(muxed(), 0, true)
        until { c.state.value.status == PlaybackStatus.PLAYING }
        tv.transportState = "STOPPED"
        until { c.state.value.status == PlaybackStatus.ENDED }
        s.cancel()
    }

    @Test fun plansATvCannotFetchAreErrorsNotSilence() = runBlocking {
        val tv = FakeTv(); val s = scope()
        val c = DlnaController(AvTransport("http://tv/avt", tv), s, pollMs = 20)
        c.load(PlaybackPlan.Local("/x.mp4", true), 0, true)
        assertEquals(PlaybackStatus.ERROR, c.state.value.status)
        c.load(PlaybackPlan.Muxed(VideoStream("https://v", "video/mp4", "avc1", 1, 1, 1, 1, hasAudio = true), mapOf("Cookie" to "x")), 0, true)
        assertEquals(PlaybackStatus.ERROR, c.state.value.status)
        assertTrue(tv.names().isEmpty())
        s.cancel()
    }

    @Test fun aTvThatStopsAnsweringIsReportedAsLost() = runBlocking {
        val tv = FakeTv(); val s = scope()
        val c = DlnaController(AvTransport("http://tv/avt", tv), s, pollMs = 10)
        c.load(muxed(), 0, true)
        until { c.state.value.status == PlaybackStatus.PLAYING }
        tv.failPosts = true
        until { c.state.value.status == PlaybackStatus.ERROR }
        s.cancel()
    }
}
