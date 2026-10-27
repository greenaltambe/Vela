package com.greenaltambe.vela.extractor.ytdlp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.*
import kotlin.time.Instant

class YtDlpMappingTest {
    private fun fixture(name: String) = Json.parseToJsonElement(Fixtures.text(name)).jsonObject
    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    // ---- streams (real capture: dQw4w9WgXcQ) ----

    private val set = fixture("video.json").toStreamSet()

    @Test fun keepsOnlyPlainHttpsMediaAndSkipsStoryboardsAndHls() {
        // The capture also holds sb0 (mhtml), 233 (HLS audio, acodec null) and 270 (HLS video).
        assertEquals(listOf(401, 137, 248), set.videoStreams.map { it.itag }, "best first: height, then bitrate (137 avc 3038k > 248 vp9 1158k)")
        assertEquals(listOf(140, 251, 139, 249), set.audioStreams.map { it.itag }, "bitrate descending")
    }

    @Test fun realVideoOnlyFormatsAreNotMuxed() {
        assertEquals(listOf(false, false, false), set.videoStreams.map { it.hasAudio })
    }

    @Test fun mapsVideoFields() {
        val v = set.videoStreams.first { it.itag == 137 }
        assertEquals("video/mp4", v.mimeType)
        assertEquals("avc1.640028", v.codec)
        assertEquals(1920 to 1080, v.width to v.height)
        assertEquals(25, v.fps)
        assertEquals(3_038_377, v.bitrate, "vbr == tbr here, kbit/s -> bit/s")
        assertEquals(80_911_999L, v.contentLength, "filesize wins over filesize_approx")
        val vp9 = set.videoStreams.first { it.itag == 248 }
        assertEquals("video/webm", vp9.mimeType)
        assertEquals("vp9", vp9.codec)
        assertEquals("av01.0.12M.08", set.videoStreams.first { it.itag == 401 }.codec)
    }

    @Test fun mapsAudioFields() {
        val a = set.audioStreams.first { it.itag == 140 }
        assertEquals("audio/mp4", a.mimeType, "m4a is audio/mp4")
        assertEquals("mp4a.40.2", a.codec)
        assertEquals(129_502, a.bitrate)
        assertEquals("en", a.languageCode)
        assertEquals(3_449_447L, a.contentLength)
        val opus = set.audioStreams.first { it.itag == 251 }
        assertEquals("audio/webm", opus.mimeType)
        assertEquals("opus", opus.codec)
        assertEquals(128_930, opus.bitrate)
        assertFalse(set.audioStreams.any { it.isDrc })
    }

    @Test fun filesizeApproxIsUsedWhenThereIsNoExactSize() {
        val s = json("""{"formats":[{"format_id":"1","protocol":"https","vcodec":"vp9","acodec":"none","url":"https://x/a","filesize":null,"filesize_approx":777}]}""").toStreamSet()
        assertEquals(777L, s.videoStreams.single().contentLength)
    }

    @Test fun setHeadersAreWhatEveryFormatSharesBecauseDumpJsonHasNoTopLevelOnes() {
        assertNull(fixture("video.json")["http_headers"], "real -J output has no top-level http_headers")
        assertEquals(
            setOf("User-Agent", "Accept", "Accept-Language", "Sec-Fetch-Mode"),
            set.headers.keys,
        )
        assertTrue("Chrome" in set.headers.getValue("User-Agent"))
        assertTrue(set.videoStreams.all { it.headers.isEmpty() } && set.audioStreams.all { it.headers.isEmpty() }, "identical to the set-level headers")
    }

    @Test fun aFormatWithDifferingHeadersKeepsOnlyTheDifference() {
        val s = json("""{"formats":[
            {"format_id":"1","protocol":"https","vcodec":"vp9","acodec":"none","url":"https://x/a","http_headers":{"A":"1","B":"2"}},
            {"format_id":"2","protocol":"https","vcodec":"none","acodec":"opus","url":"https://x/b","http_headers":{"A":"1","B":"3"}}]}""").toStreamSet()
        assertEquals(mapOf("A" to "1"), s.headers)
        assertEquals(mapOf("B" to "2"), s.videoStreams.single().headers)
        assertEquals(mapOf("B" to "3"), s.audioStreams.single().headers)
    }

    @Test fun topLevelHeadersWinWhenPresent() {
        val s = json("""{"http_headers":{"A":"top"},"formats":[{"format_id":"1","protocol":"https","vcodec":"vp9","url":"https://x/a","http_headers":{"A":"top","B":"2"}}]}""").toStreamSet()
        assertEquals(mapOf("A" to "top"), s.headers)
        assertEquals(mapOf("B" to "2"), s.videoStreams.single().headers)
    }

    @Test fun hlsUrlComesFromTheManifestUrlOfAnM3u8Format() {
        assertTrue(set.hlsUrl!!.startsWith("https://manifest.googlevideo.com/api/manifest/hls_variant/expire/1793115364/"), set.hlsUrl)
    }

    @Test fun expiryIsTheEarliestExpireParameter() {
        assertEquals(Instant.fromEpochSeconds(1793115364), set.expiresAt)
        val s = json("""{"formats":[
            {"format_id":"1","protocol":"https","vcodec":"vp9","url":"https://x/videoplayback?expire=2000&ei=x"},
            {"format_id":"2","protocol":"https","acodec":"opus","vcodec":"none","url":"https://x/videoplayback?a=b&expire=1000"}]}""").toStreamSet()
        assertEquals(Instant.fromEpochSeconds(1000), s.expiresAt)
    }

    @Test fun availableAtIsTheLatestAvailableAtOfTheFormatsWeKeep() {
        // Real capture: the TV client's formats carry fetch time + 4 s ("Sleeping 4.00 seconds as required by the site").
        val latest = fixture("video.json").objects("formats").filter { it.str("protocol") == "https" }.mapNotNull { it.long("available_at") }.max()
        assertEquals(Instant.fromEpochSeconds(latest), set.availableAt)
        assertNull(json("""{"formats":[{"format_id":"1","protocol":"https","vcodec":"vp9","url":"https://x/a"}]}""").toStreamSet().availableAt)
    }

    @Test fun clientLabel() { assertEquals("yt-dlp", set.client) }

    @Test fun everyRealFixtureComesOutBestFirst() {
        listOf("video.json", "short.json", "audio_drc.json", "audio_multilang.json").forEach { name ->
            val s = fixture(name).toStreamSet()
            assertEquals(s.videoStreams.sortedWith(compareByDescending<com.greenaltambe.vela.extractor.VideoStream> { it.height }.thenByDescending { it.bitrate }), s.videoStreams, name)
            assertEquals(
                s.audioStreams.sortedWith(compareByDescending<com.greenaltambe.vela.extractor.AudioStream> { it.isOriginalTrack }.thenByDescending { it.bitrate }),
                s.audioStreams, name,
            )
        }
    }

    // Real capture of a video with `-drc` variants (jNQXAC9IVRw): same itag, flagged, listed next to the plain one.
    @Test fun drcVariantsAreFlaggedAndKeepTheirItag() {
        val audio = fixture("audio_drc.json").toStreamSet().audioStreams
        assertEquals(listOf<Int?>(140, 140, 251, 251), audio.map { it.itag }.sortedBy { it })
        assertEquals(2, audio.count { it.isDrc })
        assertTrue(audio.filter { it.isDrc }.all { it.itag in listOf(140, 251) })
        assertEquals(2, audio.count { !it.isDrc })
    }

    // Real capture of a dubbed video (0e3GPea1Tyg): ids are 251-0 .. 251-23 and the original track sorts last in yt-dlp's list.
    @Test fun multiLanguageAudioPutsTheDefaultTrackFirstAndKeepsTheItag() {
        val audio = fixture("audio_multilang.json").toStreamSet().audioStreams
        assertEquals(4, audio.size)
        val opus = audio.filter { it.itag == 251 }
        assertEquals(listOf("ml", "fr", "en-US"), listOf("ml", "fr", "en-US").filter { l -> opus.any { it.languageCode == l } }, "fixture has all three")
        assertEquals("en-US", opus.first().languageCode, "yt-dlp lists 251-0 (ml) first; the default track must come first")
        assertTrue(audio.all { it.itag == 251 || it.itag == 140 })
        assertEquals(setOf("en-US", "ml", "fr"), audio.mapNotNull { it.languageCode }.toSet())
    }

    @Test fun itagParsing() {
        assertEquals(251, itagOf("251"))
        assertEquals(251, itagOf("251-drc"))
        assertEquals(137, itagOf("137-1"))
        assertNull(itagOf("hls-1080p"))
        assertNull(itagOf("sb0"))
        assertNull(itagOf(null))
        assertNull(itagOf(""))
    }

    @Test fun formatsWithoutAnyCodecOrWithoutUrlAreIgnored() {
        val s = json("""{"formats":[
            {"format_id":"1","protocol":"https","vcodec":"none","acodec":"none","url":"https://x/a"},
            {"format_id":"2","protocol":"https","vcodec":"avc1","acodec":"none"},
            {"format_id":"3","protocol":"https","vcodec":"avc1","acodec":null,"url":"https://x/c","ext":"mp4"}]}""").toStreamSet()
        assertEquals(listOf(3), s.videoStreams.map { it.itag })
        assertFalse(s.videoStreams.single().hasAudio, "unknown audio codec is not claimed as muxed")
        assertNull(s.expiresAt)
        assertNull(s.hlsUrl)
    }

    // ---- video info ----

    private val info = fixture("video.json").toVideoInfo()

    @Test fun mapsVideoInfo() {
        assertEquals("dQw4w9WgXcQ", info.id)
        assertEquals("Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)", info.title)
        assertEquals("UCuAXFkgsw1L7xaCfnd5JJOw", info.channelId)
        assertEquals("Rick Astley", info.channelName)
        assertEquals(213, info.durationSeconds)
        assertEquals(1_830_928_306L, info.viewCount)
        assertEquals(19_493_471L, info.likeCount)
        assertEquals("https://i.ytimg.com/vi_webp/dQw4w9WgXcQ/maxresdefault.webp", info.thumbnailUrl)
        assertTrue(info.chapters.isEmpty(), "chapters is JSON null")
        assertFalse(info.isLive || info.isShort || info.ageRestricted)
    }

    @Test fun chaptersWhenPresent() {
        val v = json("""{"chapters":[{"title":"Intro","start_time":0.0,"end_time":43.0},{"title":"Verse","start_time":43.5}]}""").toVideoInfo()
        assertEquals(listOf("Intro" to 0L, "Verse" to 43L), v.chapters.map { it.title to it.startSeconds })
    }

    @Test fun uploadInstantUsesTheExactTimestamp() {
        assertEquals(Instant.fromEpochSeconds(1256453853), info.uploadDate, "timestamp 2009-10-25T06:57:33Z, not midnight of upload_date")
    }

    @Test fun uploadDateIsTheFallbackWhenThereIsNoTimestamp() {
        assertEquals(Instant.parse("2009-10-25T00:00:00Z"), json("""{"upload_date":"20091025","timestamp":null}""").uploadInstant())
    }

    // Real capture of a Short asked for through /watch?v= (what the extractor does): yt-dlp reports /watch in both URLs.
    @Test fun aShortIsRecognisedByMediaTypeNotByUrl() {
        val short = fixture("short.json")
        assertTrue("/shorts/" !in short["webpage_url"].toString() && "/shorts/" !in short["original_url"].toString())
        val v = short.toVideoInfo()
        assertTrue(v.isShort)
        assertEquals(29, v.durationSeconds)
        assertFalse(info.isShort)
    }

    @Test fun timestampBeatsUploadDate() {
        assertEquals(Instant.fromEpochSeconds(1_600_000_000), json("""{"timestamp":1600000000,"upload_date":"20091025"}""").uploadInstant())
        assertNull(json("""{"upload_date":"2009"}""").uploadInstant())
        assertNull(json("{}").uploadInstant())
    }

    @Test fun flagsFromVariants() {
        val v = json("""{"id":"x","title":"t","age_limit":18,"is_live":true,"webpage_url":"https://www.youtube.com/shorts/x","duration":null}""").toVideoInfo()
        assertTrue(v.ageRestricted && v.isLive && v.isShort, "an explicit /shorts/ URL still counts")
        assertEquals(0, v.durationSeconds)
        assertTrue(json("""{"live_status":"is_live"}""").toVideoInfo().isLive)
    }

    @Test fun thumbnailFallsBackToTheBestListedOne() {
        val v = json("""{"thumbnails":[{"url":"https://a/small"},{"url":"https://a/big"}]}""").toVideoInfo()
        assertEquals("https://a/big", v.thumbnailUrl)
    }

    // ---- flat listings ----

    @Test fun searchEntriesBecomeVideosAndDropTheChannelEntry() {
        val videos = fixture("flat_search.json").flatVideos()
        // The real result list for "rick astley" contains a channel (UCuAXFk...) between the videos.
        assertEquals(listOf("dQw4w9WgXcQ", "yPYZpwSpKmA", "tRZqf-r2jb4", "DLzxrzFCyOs"), videos.map { it.id })
        val first = videos[0]
        assertEquals("UCuAXFkgsw1L7xaCfnd5JJOw", first.channelId)
        assertEquals("Rick Astley", first.channelName)
        assertEquals(214L, first.durationSeconds)
        assertEquals(1_830_928_306L, first.viewCount)
        assertTrue(first.thumbnailUrl!!.startsWith("https://i.ytimg.com/vi/dQw4w9WgXcQ/hq720.jpg"), "best (last) thumbnail")
        assertFalse(first.isShort)
        assertEquals("Old Music Hits", videos[2].channelName)
    }

    @Test fun missingFieldsAndShortsInFlatEntriesAreHandled() {
        val short = json("""{"entries":[{"_type":"url","id":"s1","url":"https://www.youtube.com/shorts/s1","title":"t","uploader":"Someone","uploader_url":"https://www.youtube.com/channel/UCshort","view_count":null,"thumbnails":null,"duration":null}]}""").flatVideos().single()
        assertTrue(short.isShort)
        assertEquals("Someone", short.channelName, "falls back to uploader")
        assertEquals("UCshort", short.channelId, "derived from uploader_url")
        assertNull(short.viewCount)
        assertNull(short.thumbnailUrl)
        assertNull(short.durationSeconds)
    }

    @Test fun channelListingEntriesInheritTheChannelFromTheListing() {
        val videos = fixture("flat_channel.json").flatVideos()
        assertEquals(listOf("PXC_PYeB6F8", "LaOUkDBDjW8", "wvr7-pDJUOA"), videos.map { it.id })
        assertNull(fixture("flat_channel.json").objects("entries").first().str("channel_id"), "real entries have no channel fields")
        assertTrue(videos.all { it.channelId == "UCuAXFkgsw1L7xaCfnd5JJOw" && it.channelName == "Rick Astley" })
        assertEquals(listOf(231L, 234L, 248L), videos.map { it.durationSeconds })
    }

    @Test fun mapsChannelHeader() {
        val page = fixture("flat_channel.json").toChannelPage("UCuAXFkgsw1L7xaCfnd5JJOw", continuation = "ytdlp:31")
        assertEquals("Rick Astley", page.name)
        assertEquals("UCuAXFkgsw1L7xaCfnd5JJOw", page.id)
        assertEquals(4_550_000L, page.subscriberCount)
        assertTrue(page.avatarUrl!!.startsWith("https://yt3.googleusercontent.com/_ShEGOdg"), "the avatar_uncropped thumbnail, not a banner")
        assertEquals(3, page.videos.size)
        assertEquals("ytdlp:31", page.continuation)
    }

    @Test fun emptyOrOddJsonDoesNotThrow() {
        assertTrue(JsonObject(emptyMap()).flatVideos().isEmpty())
        assertTrue(json("""{"entries":[null,1,"x",{}]}""").flatVideos().isEmpty())
    }
}
