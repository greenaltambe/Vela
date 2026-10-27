package com.greenaltambe.vela.player

import com.greenaltambe.vela.core.network.NetworkType
import com.greenaltambe.vela.core.settings.*
import com.greenaltambe.vela.extractor.AudioStream
import com.greenaltambe.vela.extractor.StreamSet
import com.greenaltambe.vela.extractor.VideoStream
import kotlin.test.*

class StreamSelectorTest {
    private fun video(itag: Int, h: Int, codec: String, muxed: Boolean = false, bitrate: Int = h * 1000, headers: Map<String, String> = emptyMap()) =
        VideoStream("https://v/$itag", "video/mp4", codec, bitrate, h * 16 / 9, h, 30, hasAudio = muxed, itag = itag, headers = headers)

    private fun audio(itag: Int, bitrate: Int, lang: String? = null, drc: Boolean = false, original: Boolean = false, headers: Map<String, String> = emptyMap()) =
        AudioStream("https://a/$itag", "audio/webm", "opus", bitrate, lang, itag, isDrc = drc, isOriginalTrack = original, headers = headers)

    private val avc = "avc1.640028"
    private val vp9 = "vp9"
    private val av1 = "av01.0.12M.08"

    private fun set(
        video: List<VideoStream>, audio: List<AudioStream> = listOf(audio(251, 128_000)), hls: String? = null,
        headers: Map<String, String> = mapOf("User-Agent" to "ua"),
    ) = StreamSet(video, audio, hls, headers = headers)

    private val ladder = listOf(
        video(1, 2160, av1), video(2, 2160, vp9), video(3, 1080, avc), video(4, 1080, vp9), video(5, 1080, av1),
        video(6, 720, avc), video(7, 720, vp9), video(8, 360, avc), video(9, 360, vp9),
    )

    private fun plan(s: StreamSet, prefs: PlaybackPrefs = PlaybackPrefs(), c: PlaybackConstraints = PlaybackConstraints()) = StreamSelector.plan(s, prefs, c)
    private fun separate(p: PlaybackPlan) = assertIs<PlaybackPlan.Separate>(p)

    // ---- codec ----

    @Test fun defaultPrefersVp9AtTheBestResolution() {
        val p = separate(plan(set(ladder)))
        assertEquals(2, p.video.itag)
        assertEquals(2160, p.video.height)
    }

    @Test fun codecFallbackOrderFromEachPreference() {
        assertEquals(listOf(VideoCodec.VP9, VideoCodec.AVC, VideoCodec.AV1, VideoCodec.OTHER), StreamSelector.codecOrder(PreferredCodec.VP9))
        assertEquals(listOf(VideoCodec.AV1, VideoCodec.VP9, VideoCodec.AVC, VideoCodec.OTHER), StreamSelector.codecOrder(PreferredCodec.AV1))
        assertEquals(listOf(VideoCodec.AVC, VideoCodec.VP9, VideoCodec.AV1, VideoCodec.OTHER), StreamSelector.codecOrder(PreferredCodec.AVC))
    }

    @Test fun preferredCodecWinsEvenAgainstAHigherResolutionInAnother() {
        assertEquals(3, separate(plan(set(ladder), PlaybackPrefs(preferredCodec = PreferredCodec.AVC))).video.itag, "avc tops out at 1080p")
        assertEquals(1, separate(plan(set(ladder), PlaybackPrefs(preferredCodec = PreferredCodec.AV1))).video.itag)
    }

    @Test fun missingPreferredCodecFallsDownTheList() {
        val noVp9 = ladder.filter { StreamSelector.codecOf(it.codec) != VideoCodec.VP9 }
        assertEquals(VideoCodec.AVC, StreamSelector.codecOf(separate(plan(set(noVp9))).video.codec), "VP9 -> AVC before AV1")
        val onlyAv1 = ladder.filter { StreamSelector.codecOf(it.codec) == VideoCodec.AV1 }
        assertEquals(VideoCodec.AV1, StreamSelector.codecOf(separate(plan(set(onlyAv1))).video.codec))
    }

    @Test fun codecsTheDeviceCannotDecodeAreNeverChosen() {
        val noAv1Vp9 = PlaybackConstraints(supportedCodecs = setOf(VideoCodec.AVC))
        val p = separate(plan(set(ladder), PlaybackPrefs(preferredCodec = PreferredCodec.AV1), noAv1Vp9))
        assertEquals(VideoCodec.AVC, StreamSelector.codecOf(p.video.codec))
        assertEquals(1080, p.video.height)
    }

    @Test fun codecStringsAreClassified() {
        assertEquals(VideoCodec.AV1, StreamSelector.codecOf("av01.0.05M.08"))
        assertEquals(VideoCodec.VP9, StreamSelector.codecOf("vp09.00.40.08"))
        assertEquals(VideoCodec.VP9, StreamSelector.codecOf("vp9"))
        assertEquals(VideoCodec.AVC, StreamSelector.codecOf("avc1.4D401F"))
        assertEquals(VideoCodec.OTHER, StreamSelector.codecOf("hev1"))
        assertEquals(VideoCodec.OTHER, StreamSelector.codecOf(""))
    }

    // ---- quality cap ----

    @Test fun capPicksTheBestHeightNotAboveIt() {
        assertEquals(1080, separate(plan(set(ladder), PlaybackPrefs(maxHeight = 1080))).video.height)
        assertEquals(720, separate(plan(set(ladder), PlaybackPrefs(maxHeight = 900))).video.height)
        assertEquals(360, separate(plan(set(ladder), PlaybackPrefs(maxHeight = 360))).video.height)
    }

    @Test fun capBelowEverythingTakesTheLowestAvailable() {
        val p = separate(plan(set(ladder.filter { it.height >= 720 }), PlaybackPrefs(maxHeight = 240)))
        assertEquals(720, p.video.height)
    }

    @Test fun aCodecWithOnlyStreamsAboveTheCapDoesNotBeatOneUnderIt() {
        val s = set(listOf(video(1, 1080, vp9), video(2, 480, avc)))
        assertEquals(2, separate(plan(s, PlaybackPrefs(maxHeight = 720))).video.itag)
    }

    @Test fun maxQualitySettingMapsToAHeight() {
        assertEquals(listOf(360, 480, 720, 1080, 1440, 2160, null), MaxQuality.entries.map { it.height })
    }

    @Test fun sameHeightAndCodecPicksTheHigherBitrate() {
        val s = set(listOf(video(1, 1080, vp9, bitrate = 1_000), video(2, 1080, vp9, bitrate = 3_000)))
        assertEquals(2, separate(plan(s)).video.itag)
    }

    @Test fun inputOrderDoesNotMatter() {
        assertEquals(plan(set(ladder)), plan(set(ladder.reversed())))
    }

    // ---- separate vs muxed ----

    @Test fun separateStreamsBeatMuxedEvenAtAHigherMuxedResolution() {
        val s = set(listOf(video(18, 360, avc, muxed = true), video(22, 720, avc, muxed = true), video(137, 480, avc)))
        assertIs<PlaybackPlan.Separate>(plan(s))
    }

    @Test fun muxedIsUsedWhenNoVideoOnlyStreamQualifies() {
        val s = set(listOf(video(18, 360, avc, muxed = true), video(22, 720, avc, muxed = true)))
        val p = assertIs<PlaybackPlan.Muxed>(plan(s))
        assertEquals(22, p.stream.itag)
    }

    @Test fun muxedIsUsedWhenThereIsNoAudioStreamToPairWith() {
        val s = set(listOf(video(18, 360, avc, muxed = true), video(137, 1080, avc)), audio = emptyList())
        assertEquals(18, assertIs<PlaybackPlan.Muxed>(plan(s)).stream.itag)
    }

    @Test fun muxedIsUsedWhenVideoOnlyCodecsAreUnsupported() {
        val s = set(listOf(video(18, 360, avc, muxed = true), video(313, 2160, vp9)))
        val p = plan(s, c = PlaybackConstraints(supportedCodecs = setOf(VideoCodec.AVC)))
        assertEquals(18, assertIs<PlaybackPlan.Muxed>(p).stream.itag)
    }

    @Test fun muxedRespectsTheCapToo() {
        val s = set(listOf(video(18, 360, avc, muxed = true), video(22, 720, avc, muxed = true)), audio = emptyList())
        assertEquals(18, assertIs<PlaybackPlan.Muxed>(plan(s, PlaybackPrefs(maxHeight = 480))).stream.itag)
    }

    // ---- hls / fallbacks ----

    @Test fun hlsWhenNothingProgressiveQualifies() {
        val p = assertIs<PlaybackPlan.Hls>(plan(set(emptyList(), audio = emptyList(), hls = "https://m/index.m3u8")))
        assertEquals("https://m/index.m3u8", p.url)
        assertEquals(mapOf("User-Agent" to "ua"), p.headers)
    }

    @Test fun hlsIsSkippedWhenTheBackendCannotPlayIt() {
        val p = plan(set(emptyList(), audio = emptyList(), hls = "https://m"), c = PlaybackConstraints(supportsHls = false))
        assertIs<PlaybackPlan.NothingPlayable>(p)
    }

    @Test fun progressiveBeatsHls() {
        assertIs<PlaybackPlan.Separate>(plan(set(ladder, hls = "https://m")))
    }

    @Test fun audioOnlyIsTheLastResortBeforeGivingUp() {
        val p = assertIs<PlaybackPlan.AudioOnly>(plan(set(emptyList())))
        assertEquals(251, p.audio.itag)
    }

    // ---- audio ----

    @Test fun audioOnlyPreferenceSkipsVideoEntirely() {
        val p = assertIs<PlaybackPlan.AudioOnly>(plan(set(ladder), PlaybackPrefs(audioOnly = true)))
        assertEquals(251, p.audio.itag)
    }

    @Test fun audioOnlyPreferenceWithNoAudioStreamsStillPlaysVideo() {
        assertIs<PlaybackPlan.Muxed>(plan(set(listOf(video(18, 360, avc, muxed = true)), audio = emptyList()), PlaybackPrefs(audioOnly = true)))
    }

    @Test fun nonDrcBeatsDrcEvenAtALowerBitrate() {
        val s = set(ladder, audio = listOf(audio(1, 256_000, drc = true), audio(2, 128_000), audio(3, 48_000, drc = true)))
        assertEquals(2, separate(plan(s)).audio.itag)
    }

    @Test fun drcIsUsedWhenItIsAllThereIs() {
        val s = set(ladder, audio = listOf(audio(1, 256_000, drc = true), audio(3, 48_000, drc = true)))
        assertEquals(1, separate(plan(s)).audio.itag)
    }

    @Test fun audioPrefersTheOriginalTrackThenBitrate() {
        val s = set(ladder, audio = listOf(audio(1, 256_000, "fr"), audio(2, 128_000, "en-US", original = true), audio(3, 96_000, "en-US", original = true)))
        assertEquals(2, separate(plan(s)).audio.itag)
    }

    @Test fun audioLanguagePreferenceBeatsTheOriginalTrackAndMatchesTheLanguagePrefix() {
        val s = set(ladder, audio = listOf(audio(1, 128_000, "en-US", original = true), audio(2, 64_000, "fr"), audio(3, 128_000, "fr-CA")))
        assertEquals(3, separate(plan(s, PlaybackPrefs(audioLanguage = "fr"))).audio.itag)
        assertEquals(3, separate(plan(s, PlaybackPrefs(audioLanguage = "FR"))).audio.itag, "case-insensitive")
    }

    @Test fun anUnavailableLanguageFallsBackToTheOriginal() {
        val s = set(ladder, audio = listOf(audio(1, 128_000, "en-US", original = true), audio(2, 128_000, "fr")))
        assertEquals(1, separate(plan(s, PlaybackPrefs(audioLanguage = "ja"))).audio.itag)
    }

    @Test fun languageOutranksDrcAvoidance() {
        val s = set(ladder, audio = listOf(audio(1, 128_000, "en"), audio(2, 128_000, "fr", drc = true)))
        assertEquals(2, separate(plan(s, PlaybackPrefs(audioLanguage = "fr"))).audio.itag)
    }

    // ---- headers ----

    @Test fun streamHeadersOverrideSetHeadersAndEachStreamKeepsItsOwn() {
        val s = StreamSet(
            listOf(video(1, 1080, vp9, headers = mapOf("User-Agent" to "android-ua", "X-V" to "1"))),
            listOf(audio(2, 128_000, headers = mapOf("X-A" to "1"))),
            headers = mapOf("User-Agent" to "web-ua", "Referer" to "r"),
        )
        val p = separate(plan(s))
        assertEquals(mapOf("User-Agent" to "android-ua", "Referer" to "r", "X-V" to "1"), p.videoHeaders)
        assertEquals(mapOf("User-Agent" to "web-ua", "Referer" to "r", "X-A" to "1"), p.audioHeaders)
    }

    @Test fun muxedAndAudioOnlyCarryResolvedHeadersToo() {
        val m = StreamSet(listOf(video(18, 360, avc, muxed = true, headers = mapOf("A" to "stream"))), emptyList(), headers = mapOf("A" to "set", "B" to "b"))
        assertEquals(mapOf("A" to "stream", "B" to "b"), assertIs<PlaybackPlan.Muxed>(plan(m)).headers)
        val a = StreamSet(emptyList(), listOf(audio(1, 1, headers = mapOf("A" to "stream"))), headers = mapOf("A" to "set"))
        assertEquals(mapOf("A" to "stream"), assertIs<PlaybackPlan.AudioOnly>(plan(a)).headers)
    }

    // ---- nothing playable ----

    @Test fun emptySetIsNothingPlayable() {
        val p = assertIs<PlaybackPlan.NothingPlayable>(plan(StreamSet(emptyList(), emptyList())))
        assertEquals("The stream set is empty", p.reason)
    }

    @Test fun onlyUndecodableVideoAndNoAudioIsNothingPlayable() {
        val s = StreamSet(listOf(video(1, 1080, av1)), emptyList())
        assertIs<PlaybackPlan.NothingPlayable>(plan(s, c = PlaybackConstraints(supportedCodecs = setOf(VideoCodec.AVC))))
    }

    // ---- quality menu / settings ----

    @Test fun availableHeightsAreDistinctBestFirstAndHonourDecoders() {
        assertEquals(listOf(2160, 1080, 720, 360), StreamSelector.availableHeights(set(ladder)))
        assertEquals(listOf(1080, 720, 360), StreamSelector.availableHeights(set(ladder), PlaybackConstraints(supportedCodecs = setOf(VideoCodec.AVC, VideoCodec.VP9))).filter { it <= 1080 })
        assertEquals(listOf(1080, 720, 360), StreamSelector.availableHeights(set(ladder), PlaybackConstraints(supportedCodecs = setOf(VideoCodec.AVC))))
    }

    @Test fun prefsComeFromSettingsWithChannelOverrides() {
        val settings = SettingsResolver(InMemorySettingsStore())
        assertEquals(PlaybackPrefs(null, 1080, 720, PreferredCodec.VP9, false), PlaybackPrefs.from(settings, "UC1"), "defaults: 1080p on Wi-Fi, 720p on mobile data")
        settings.set(Settings.maxQualityUnmetered, MaxQuality.BEST)
        settings.set(Settings.preferredCodec, PreferredCodec.AVC, SettingLayer.channel("UC1"))
        settings.set(Settings.audioOnly, true, SettingLayer.channel("UC1"))
        settings.set(Settings.maxQualityMetered, MaxQuality.P360, SettingLayer.channel("UC1"))
        assertEquals(PlaybackPrefs(null, null, 360, PreferredCodec.AVC, true), PlaybackPrefs.from(settings, "UC1"))
        assertEquals(PlaybackPrefs(null, null, 720, PreferredCodec.VP9, false), PlaybackPrefs.from(settings, "UC2"))
    }

    // ---- network ----

    private fun onNetwork(n: NetworkType, prefs: PlaybackPrefs = PlaybackPrefs(maxHeightUnmetered = 1080, maxHeightMetered = 720)) =
        separate(plan(set(ladder), prefs, PlaybackConstraints(network = n))).video.height

    @Test fun theSettingForTheCurrentConnectionCapsTheQuality() {
        assertEquals(1080, onNetwork(NetworkType.UNMETERED))
        assertEquals(720, onNetwork(NetworkType.METERED))
        assertEquals(720, onNetwork(NetworkType.OFFLINE), "offline counts as metered")
    }

    @Test fun anExplicitPickBeatsTheNetworkSettings() {
        val picked = PlaybackPrefs(maxHeight = 2160, maxHeightUnmetered = 1080, maxHeightMetered = 720)
        assertEquals(2160, onNetwork(NetworkType.METERED, picked))
        assertEquals(360, onNetwork(NetworkType.UNMETERED, picked.copy(maxHeight = 360)))
    }

    @Test fun noCapOnTheCurrentConnectionMeansBest() {
        assertEquals(2160, onNetwork(NetworkType.UNMETERED, PlaybackPrefs(maxHeightUnmetered = null, maxHeightMetered = 720)))
        assertEquals(2160, onNetwork(NetworkType.METERED, PlaybackPrefs(maxHeightUnmetered = 360, maxHeightMetered = null)))
    }

    @Test fun defaultsAvoid4k() {
        val defaults = PlaybackPrefs.from(SettingsResolver(InMemorySettingsStore()), null)
        assertTrue(listOf(NetworkType.UNMETERED, NetworkType.METERED).all { (defaults.effectiveMaxHeight(it) ?: Int.MAX_VALUE) <= 1080 })
        assertEquals(1080, separate(plan(set(ladder), defaults)).video.height)
    }

    @Test fun newSettingsAreScopedGlobalToChannelAndFindable() {
        listOf(Settings.maxQualityUnmetered, Settings.maxQualityMetered, Settings.preferredCodec, Settings.audioOnly).forEach {
            assertEquals(SettingScope.CHANNEL, it.scope, it.key)
            assertTrue(it in Settings.all)
        }
        assertEquals("playback.maxQualityWifi", Settings.maxQualityUnmetered.key)
        assertEquals("playback.maxQualityCellular", Settings.maxQualityMetered.key)
        assertEquals(MaxQuality.P1080, Settings.maxQualityUnmetered.default)
        assertEquals(MaxQuality.P720, Settings.maxQualityMetered.default)
        assertEquals(7, Settings.maxQualityMetered.choices.size)
        assertEquals(PreferredCodec.VP9, Settings.preferredCodec.default)
        assertFalse(Settings.audioOnly.default)
        assertTrue(Settings.search("codec").contains(Settings.preferredCodec))
        assertFailsWith<IllegalArgumentException> { SettingsResolver(InMemorySettingsStore()).set(Settings.maxQualityMetered, MaxQuality.P720, SettingLayer.video("v")) }
    }
}
