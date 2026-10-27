package com.greenaltambe.vela.downloads

import kotlin.test.Test
import kotlin.test.assertEquals

class ContainersTest {
    @Test fun h264WithAacIsMp4() = assertEquals(Container.MP4, Containers.forCodecs("avc1.640028", "mp4a.40.2"))

    @Test fun vp9AndAv1WithOpusAreWebm() {
        assertEquals(Container.WEBM, Containers.forCodecs("vp9", "opus"))
        assertEquals(Container.WEBM, Containers.forCodecs("vp09.00.40.08", "opus"))
        assertEquals(Container.WEBM, Containers.forCodecs("av01.0.12M.08", "opus"))
        assertEquals(Container.WEBM, Containers.forCodecs("vp9", "vorbis"))
    }

    @Test fun mixedPairsThatNoStandardContainerLikesAreMkv() {
        assertEquals(Container.MKV, Containers.forCodecs("avc1.640028", "opus"))
        assertEquals(Container.MKV, Containers.forCodecs("vp9", "mp4a.40.2"))
        assertEquals(Container.MKV, Containers.forCodecs("hev1", "ec-3"))
    }

    @Test fun aMergerThatCannotWriteTheIdealContainerGetsTheNextBestOne() {
        val androidMuxer = setOf(Container.MP4, Container.WEBM)
        assertEquals(Container.MP4, Containers.forCodecs("avc1", "mp4a.40.2", androidMuxer))
        assertEquals(Container.WEBM, Containers.forCodecs("vp9", "opus", androidMuxer))
        assertEquals(Container.MP4, Containers.forCodecs("vp9", "mp4a.40.2", androidMuxer), "MKV is out: try MP4 first")
        assertEquals(Container.MP4, Containers.forCodecs("avc1", "opus", androidMuxer))
        assertEquals(Container.MKV, Containers.forCodecs("avc1", "opus", emptySet()), "nothing allowed: the ideal one, which then fails clearly")
        assertEquals(Container.MKV, Containers.forCodecs("avc1", "opus", Container.entries.toSet()))
    }

    @Test fun singleStreamExtensionsFollowTheMimeType() {
        assertEquals("m4a", Containers.extensionFor("audio/mp4", audioOnly = true))
        assertEquals("webm", Containers.extensionFor("audio/webm", audioOnly = true))
        assertEquals("webm", Containers.extensionFor("video/webm", audioOnly = false))
        assertEquals("mp4", Containers.extensionFor("video/mp4", audioOnly = false))
        assertEquals("bin", Containers.extensionFor("application/x-unknown", audioOnly = false))
    }

    @Test fun androidsMuxerRulesFollowItsDocumentedTable() {
        assertEquals(Container.MP4, Containers.forAndroidMuxer("avc1.640028", "mp4a.40.2"), "H.264 + AAC")
        assertEquals(Container.WEBM, Containers.forAndroidMuxer("vp9", "opus"), "the one pair WebM is right for")
        assertEquals(Container.MP4, Containers.forAndroidMuxer("av01.0.12M.08", "opus"), "the muxer's AV1 goes into MP4 only (WebM elsewhere)")
        assertEquals(Container.MP4, Containers.forAndroidMuxer("vp9", "mp4a.40.2"), "AAC is MP4-only")
        assertEquals(Container.MP4, Containers.forAndroidMuxer("avc1", "opus"), "Opus in MP4 needs Android 10")
    }
}
