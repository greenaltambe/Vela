package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.extractor.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AltClientTest {
    private fun player(status: String, reason: String = "", streams: Boolean = true) = RawResponse(200, buildString {
        append("""{"playabilityStatus":{"status":"$status","reason":"$reason"},"videoDetails":{"videoId":"abc","title":"T","author":"A","channelId":"UC1","lengthSeconds":"60"}""")
        if (streams) append(""","streamingData":{"expiresInSeconds":"21540","formats":[{"itag":18,"url":"https://rr.example/v18","mimeType":"video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"","bitrate":500000,"width":640,"height":360,"fps":30,"contentLength":"1000"}],"adaptiveFormats":[{"itag":137,"url":"https://rr.example/v137","mimeType":"video/mp4; codecs=\"avc1.640028\"","bitrate":4000000,"width":1920,"height":1080,"fps":30},{"itag":140,"url":"https://rr.example/a140","mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":130000,"audioTrack":{"id":"en.4","audioIsDefault":true}},{"itag":999,"signatureCipher":"s=abc"}]}""")
        append("}")
    }, emptyMap(), "")

    private fun fake(vararg byClient: Pair<String, RawResponse>, seen: MutableList<String> = mutableListOf()) = FakeTransport { _, _, h, body ->
        val name = Regex("\"clientName\":\"([A-Z_0-9]+)\"").find(body!!)!!.groupValues[1]; seen += name
        byClient.toMap()[name] ?: RawResponse(500, "", emptyMap(), "")
    }

    private fun profiles(vararg n: String, enabled: Set<String> = n.toSet()) = n.map { ClientProfile(it, 1, "UA-$it", kotlinx.serialization.json.buildJsonObject { put("clientName", kotlinx.serialization.json.JsonPrimitive(it)); put("clientVersion", kotlinx.serialization.json.JsonPrimitive("1.0")) }, it in enabled) }

    private fun x(t: AuthTransport, p: List<ClientProfile>, onProfile: (String) -> Unit = {}) = AltClientExtractor(t, { p }, onProfile = onProfile)

    @Test fun parsesDirectUrlsSkipsCipheredOnesAndKeepsTheClientUserAgent() = runTest {
        val set = (x(fake("IOS" to player("OK")), profiles("IOS")).streams("abc") as ExtractorResult.Success).value
        assertEquals(listOf(1080, 360), set.videoStreams.map { it.height })
        assertTrue(set.videoStreams.first { it.itag == 18 }.hasAudio)
        assertEquals(listOf(140), set.audioStreams.map { it.itag })
        assertEquals("en", set.audioStreams.single().languageCode)
        assertEquals("UA-IOS", set.headers["User-Agent"]); assertEquals("ios", set.client)
        assertNotNull(set.expiresAt)
    }

    @Test fun triesProfilesInOrderAndSkipsDisabledOnes() = runTest {
        val seen = mutableListOf<String>(); val notes = mutableListOf<String>()
        val t = fake("A" to player("UNPLAYABLE", "no"), "B" to player("OK"), "C" to player("OK"), seen = seen)
        val r = x(t, profiles("A", "OFF", "B", "C", enabled = setOf("A", "B", "C")), notes::add).streams("abc")
        assertIs<ExtractorResult.Success<*>>(r)
        assertEquals(listOf("A", "B"), seen)
        assertEquals("B served abc", notes.last())
    }

    @Test fun mapsPlayabilityStatusesToOurErrors() = runTest {
        suspend fun err(resp: RawResponse) = (x(fake("P" to resp), profiles("P")).streams("abc") as ExtractorResult.Failure).error
        assertIs<ExtractorError.SignInRequired>(err(player("LOGIN_REQUIRED", "inappropriate", streams = false)))
        assertIs<ExtractorError.Unplayable>(err(player("UNPLAYABLE", "gone", streams = false)))
        assertIs<ExtractorError.Unplayable>(err(player("ERROR", "x", streams = false)))
        assertIs<ExtractorError.RateLimited>(err(RawResponse(429, "", emptyMap(), "")))
        assertIs<ExtractorError.Blocked>(err(RawResponse(403, "", emptyMap(), "")))
        assertIs<ExtractorError.RateLimited>(err(player("LIVE_STREAM_OFFLINE", "rate limited, try later", streams = false)))
    }

    @Test fun okWithoutStreamsIsNotASuccess() = runTest {
        assertIs<ExtractorResult.Failure>(x(fake("P" to player("OK", streams = false)), profiles("P")).streams("abc"))
    }

    @Test fun noEnabledProfileIsTierUnavailable() = runTest {
        val e = (x(fake(), profiles("A", enabled = emptySet())).streams("abc") as ExtractorResult.Failure).error
        assertIs<ExtractorError.TierUnavailable>(e)
    }

    @Test fun poTokenIsAppendedOnlyWhenAProviderSupplies() = runTest {
        val with = AltClientExtractor(fake("P" to player("OK")), { profiles("P") }, poTokens = { _, _ -> "TOKEN" })
        assertTrue((with.streams("abc") as ExtractorResult.Success).value.audioStreams.single().url.endsWith("&pot=TOKEN"))
        val without = x(fake("P" to player("OK")), profiles("P"))
        assertFalse((without.streams("abc") as ExtractorResult.Success).value.audioStreams.single().url.contains("pot="))
    }

    @Test fun bundledProfilesLoadAndAnOverrideFileWins() {
        val bundled = ClientProfile.load()
        assertEquals(listOf("IOS", "ANDROID"), bundled.filter { it.enabled }.map { it.name }, "the clients measured to work are the enabled ones")
        val f = java.io.File.createTempFile("profiles", ".json").apply { writeText("""{"profiles":[{"name":"X","clientNameId":9,"userAgent":"u","client":{"clientName":"X","clientVersion":"1"}}]}""") }
        assertEquals(listOf("X"), ClientProfile.load(f).map { it.name }); f.delete()
        assertEquals(bundled.map { it.name }, ClientProfile.load(java.io.File("/nonexistent")).map { it.name })
    }

    @Test fun contentWarningChecksFollowTheControversialSetting() = runTest {
        val bodies = mutableListOf<String>()
        val t = FakeTransport { _, _, _, body -> bodies += body!!; player("OK") }
        AltClientExtractor(t, { profiles("P") }, contentChecks = { false }).streams("abc")
        AltClientExtractor(t, { profiles("P") }, contentChecks = { true }).streams("abc")
        assertTrue("\"contentCheckOk\":false" in bodies[0] && "\"racyCheckOk\":false" in bodies[0])
        assertTrue("\"contentCheckOk\":true" in bodies[1] && "\"racyCheckOk\":true" in bodies[1])
    }
}

