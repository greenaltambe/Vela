package com.greenaltambe.vela.player.mpv

import com.greenaltambe.vela.extractor.AudioStream
import com.greenaltambe.vela.extractor.VideoStream
import com.greenaltambe.vela.player.PlaybackMetadata
import com.greenaltambe.vela.player.PlaybackPlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MpvOptionsTest {
    private val video = VideoStream("https://v/1", "video/webm", "vp9", 2_000_000, 1920, 1080, 60)
    private val audio = AudioStream("https://a/1", "audio/webm", "opus", 128_000)
    private val ua = mapOf("User-Agent" to "UA/1", "Accept-Language" to "en-US,en;q=0.9")

    private val route: (StreamRole, String, Map<String, String>) -> String = { role, url, _ -> "proxy/$role/${url.substringAfterLast('/')}" }

    @Test
    fun separatePlanPutsVideoInTheUrlAndAudioInAudioFile() {
        val load = MpvOptions.build(PlaybackPlan.Separate(video, audio, ua, ua), 0, true, PlaybackMetadata(), route, chunked = true)!!
        assertEquals("proxy/Video/1", load.url)
        assertEquals("proxy/Audio/1", load.fileOptions["audio-file"])
        assertEquals("no", load.fileOptions["pause"])
        assertNull(load.fileOptions["start"])
        // Behind the proxy the proxy sends the headers; mpv gets none.
        assertTrue(load.headerFields.isEmpty())
        assertNull(load.userAgent)
    }

    @Test
    fun startAndPauseBecomeFileOptions() {
        val load = MpvOptions.build(PlaybackPlan.Muxed(video, ua), 12_345, false, PlaybackMetadata(title = "T"), route, chunked = true)!!
        assertEquals("12.345", load.fileOptions["start"])
        assertEquals("yes", load.fileOptions["pause"])
        assertEquals("T", load.fileOptions["force-media-title"])
        assertEquals("proxy/Muxed/1", load.url)
    }

    @Test
    fun hlsIsNeverRoutedAndCarriesHeadersItself() {
        val load = MpvOptions.build(PlaybackPlan.Hls("https://h/m.m3u8", ua), 0, true, PlaybackMetadata(), route, chunked = true)!!
        assertEquals("https://h/m.m3u8", load.url)
        assertEquals("UA/1", load.userAgent)
        assertEquals(listOf("Accept-Language: en-US,en;q=0.9"), load.headerFields)
    }

    @Test
    fun withChunkingOffHeadersGoToMpv() {
        val load = MpvOptions.build(PlaybackPlan.Muxed(video, ua), 0, true, PlaybackMetadata(), { _, u, _ -> u }, chunked = false)!!
        assertEquals("UA/1", load.userAgent)
        assertEquals(1, load.headerFields.size)
    }

    @Test
    fun audioOnlyDisablesVideo() {
        val load = MpvOptions.build(PlaybackPlan.AudioOnly(audio, ua), 0, true, PlaybackMetadata(), route, chunked = true)!!
        assertEquals("no", load.fileOptions["vid"])
        assertEquals("proxy/Audio/1", load.url)
    }

    @Test
    fun nothingPlayableHasNoLoad() {
        assertNull(MpvOptions.build(PlaybackPlan.NothingPlayable("x"), 0, true, PlaybackMetadata(), route, chunked = true))
    }

    @Test
    fun optionValuesAreLengthPrefixedSoCommasAndEqualsSurvive() {
        assertEquals("%11%a=b,c d,e=f", escapeOptionValue("a=b,c d,e=f"))
        assertEquals("%2%é", escapeOptionValue("é")) // two bytes in UTF-8
        val load = MpvLoad("u", linkedMapOf("audio-file" to "http://x/a?b=1,2", "pause" to "no"), emptyList(), null, null)
        assertEquals(listOf("loadfile", "u", "replace", "-1", "audio-file=%16%http://x/a?b=1,2,pause=%2%no"), load.loadfileArgs().toList())
    }

    @Test
    fun initOptionsKeepMpvInert() {
        val o = MpvOptions.initOptions(MpvConfig(hwdec = "vaapi-copy", extraOptions = mapOf("sws-scaler" to "bilinear"))).toMap()
        assertEquals("libmpv", o["vo"]); assertEquals("no", o["ytdl"]); assertEquals("no", o["config"])
        assertEquals("yes", o["keep-open"]); assertEquals("vaapi-copy", o["hwdec"]); assertEquals("bilinear", o["sws-scaler"])
    }
}

class MpvCodecsTest {
    @Test
    fun ffmpegFreeHasNoH264() {
        val c = constraintsFromDecoderList("""[{"codec":"vp9","driver":"vp9"},{"codec":"av1","driver":"libdav1d"},{"codec":"opus","driver":"opus"}]""")
        assertEquals(setOf(com.greenaltambe.vela.player.VideoCodec.VP9, com.greenaltambe.vela.player.VideoCodec.AV1), c.supportedCodecs)
        assertEquals(false, c.supportsHls)
    }

    @Test
    fun fullFfmpegHasEverything() {
        val c = constraintsFromDecoderList("""[{"codec":"h264","driver":"h264"},{"codec":"vp9"},{"codec": "av1"}]""")
        assertEquals(3, c.supportedCodecs.size); assertEquals(true, c.supportsHls)
    }

    @Test
    fun unknownListAssumesEverything() {
        assertEquals(com.greenaltambe.vela.player.VideoCodec.entries.toSet(), constraintsFromDecoderList(null).supportedCodecs)
    }
}
