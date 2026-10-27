package com.greenaltambe.vela.extractor.newpipe

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.*
import java.time.Instant
import kotlin.test.*
import kotlin.time.Instant as KInstant
import org.schabi.newpipe.extractor.stream.AudioStream as NpAudio
import org.schabi.newpipe.extractor.stream.VideoStream as NpVideo

class StreamMappingTest {
    private fun url(itag: Int, expire: Long?, client: String = "WEB", extra: String = "") =
        "https://r1.googlevideo.com/videoplayback?itag=$itag&c=$client${expire?.let { "&expire=$it" }.orEmpty()}$extra"

    private fun video(
        itag: Int, address: String, videoOnly: Boolean, method: DeliveryMethod = DeliveryMethod.PROGRESSIVE_HTTP,
        length: Long? = null,
    ): NpVideo {
        val item = ItagItem.getItag(itag).also { i -> length?.let { i.contentLength = it } }
        return NpVideo.Builder().setId(itag.toString()).setContent(address, true)
            .setMediaFormat(MediaFormat.MPEG_4).setDeliveryMethod(method)
            .setIsVideoOnly(videoOnly).setResolution("360p").setItagItem(item).build()
    }

    private fun audio(itag: Int, address: String, drc: Boolean = false, length: Long? = null): NpAudio {
        val item = ItagItem.getItag(itag).also { i -> i.setIsDrc(drc); length?.let { i.contentLength = it } }
        return NpAudio.Builder().setId(itag.toString()).setContent(address, true)
            .setMediaFormat(MediaFormat.M4A).setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
            .setAverageBitrate(128).setItagItem(item).build()
    }

    private fun info() = StreamInfo(0, "https://www.youtube.com/watch?v=vid", "https://www.youtube.com/watch?v=vid", StreamType.VIDEO_STREAM, "vid", "Title", 0)

    @Test
    fun mapsMuxedVideoOnlyAndAudioStreams() {
        val info = info().apply {
            videoStreams = listOf(video(18, url(18, 2_000_000_000), videoOnly = false))
            videoOnlyStreams = listOf(video(137, url(137, 1_900_000_000), videoOnly = true, length = 5_000))
            audioStreams = listOf(audio(140, url(140, 2_100_000_000, extra = "&clen=777"), drc = true))
            hlsUrl = "https://manifest.googlevideo.com/hls"
        }
        val set = info.toStreamSet()

        assertEquals(listOf(true, false), set.videoStreams.map { it.hasAudio })
        assertEquals(listOf(18, 137), set.videoStreams.map { it.itag })
        assertEquals(5_000L, set.videoStreams[1].contentLength)
        assertNull(set.videoStreams[0].contentLength)
        assertEquals("video/mp4", set.videoStreams[0].mimeType)
        assertEquals(1, set.audioStreams.size)
        assertEquals(140, set.audioStreams[0].itag)
        assertTrue(set.audioStreams[0].isDrc)
        assertEquals(777L, set.audioStreams[0].contentLength, "falls back to the clen URL parameter")
        assertTrue(set.audioStreams[0].bitrate > 0)
        assertEquals("https://manifest.googlevideo.com/hls", set.hlsUrl)
        assertEquals("newpipe", set.client)
        assertEquals(KInstant.fromEpochSeconds(1_900_000_000), set.expiresAt, "earliest expiry wins")
        assertTrue("User-Agent" in set.headers && "Referer" in set.headers)
    }

    @Test
    fun skipsStreamsThatAreNotPlainUrls() {
        val info = info().apply {
            videoStreams = listOf(
                video(18, url(18, null), videoOnly = false),
                video(22, url(22, null), videoOnly = false, method = DeliveryMethod.DASH),
            )
            hlsUrl = ""
        }
        val set = info.toStreamSet()
        assertEquals(listOf(18), set.videoStreams.map { it.itag })
        assertNull(set.hlsUrl)
        assertNull(set.expiresAt)
    }

    @Test
    fun androidAndIosUrlsGetTheirOwnUserAgent() {
        val info = info().apply {
            videoStreams = listOf(
                video(18, url(18, null, "ANDROID"), videoOnly = false),
                video(22, url(22, null, "IOS"), videoOnly = false),
                video(37, url(37, null, "WEB"), videoOnly = false),
                video(36, url(36, null, "VISIONOS"), videoOnly = false),
            )
        }
        val headers = info.toStreamSet().videoStreams.map { it.headers["User-Agent"] }
        assertTrue(headers[0]!!.startsWith("com.google.android.youtube/"))
        assertTrue(headers[1]!!.startsWith("com.google.ios.youtube/"))
        assertNull(headers[2], "web streams inherit the set-level user agent")
        assertTrue(headers[3]!!.isNotBlank() && !headers[3]!!.startsWith("Mozilla"))
    }

    @Test
    fun mapsVideoInfo() {
        val info = info().apply {
            description = Description("About", Description.PLAIN_TEXT)
            uploaderName = "Chan"
            uploaderUrl = "https://www.youtube.com/channel/UC42"
            duration = 212
            viewCount = 10
            likeCount = -1
            thumbnails = listOf(Image("small", 90, 120, Image.ResolutionLevel.LOW), Image("big", 720, 1280, Image.ResolutionLevel.HIGH))
            streamSegments = listOf(StreamSegment("Intro", 0), StreamSegment("Verse", 30))
            uploadDate = DateWrapper(Instant.ofEpochSecond(1_600_000_000))
            ageLimit = 18
            setShortFormContent(true)
        }
        val v = info.toVideoInfo()
        assertEquals("vid", v.id)
        assertEquals("Title", v.title)
        assertEquals("About", v.description)
        assertEquals("UC42", v.channelId)
        assertEquals("Chan", v.channelName)
        assertEquals(212, v.durationSeconds)
        assertEquals(10L, v.viewCount)
        assertNull(v.likeCount)
        assertEquals("big", v.thumbnailUrl)
        assertEquals(listOf("Intro" to 0L, "Verse" to 30L), v.chapters.map { it.title to it.startSeconds })
        assertEquals(KInstant.fromEpochSeconds(1_600_000_000), v.uploadDate)
        assertTrue(v.ageRestricted)
        assertTrue(v.isShort)
        assertFalse(v.isLive)
    }

    @Test
    fun liveStreamsAreFlagged() {
        val live = StreamInfo(0, "u", "u", StreamType.LIVE_STREAM, "vid", "Live", 0)
        assertTrue(live.toVideoInfo().isLive)
        assertFalse(live.toVideoInfo().ageRestricted)
    }
}
