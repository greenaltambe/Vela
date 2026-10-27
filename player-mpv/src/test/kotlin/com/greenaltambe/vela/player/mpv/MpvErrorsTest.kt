package com.greenaltambe.vela.player.mpv

import com.greenaltambe.vela.player.PlaybackErrorKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class MpvErrorsTest {
    @Test
    fun parsesTheStatusFromFfmpegsLogLine() {
        assertEquals(403, httpStatusFromLog("ffmpeg: https: HTTP error 403 Forbidden"))
        assertEquals(410, httpStatusFromLog("[ffmpeg] http: HTTP error 410 Gone"))
        assertNull(httpStatusFromLog("stream: Failed to open"))
    }

    @Test
    fun deadUrlStatusesMeanReResolve() {
        for (s in listOf(401, 403, 404, 410, 416)) assertEquals(PlaybackErrorKind.StreamExpiredOrForbidden, classifyMpvError(MpvErrorCode.LOADING_FAILED, s), "$s")
        assertEquals(PlaybackErrorKind.Network, classifyMpvError(MpvErrorCode.LOADING_FAILED, 429))
        assertEquals(PlaybackErrorKind.Network, classifyMpvError(MpvErrorCode.LOADING_FAILED, 503))
    }

    @Test
    fun codecAndDeviceFailuresAreDecoderErrors() {
        assertEquals(PlaybackErrorKind.Decoder, classifyMpvError(MpvErrorCode.UNKNOWN_FORMAT, null))
        assertEquals(PlaybackErrorKind.Decoder, classifyMpvError(MpvErrorCode.UNSUPPORTED, null))
        assertEquals(PlaybackErrorKind.Decoder, classifyMpvError(MpvErrorCode.AO_INIT_FAILED, null))
        assertEquals(PlaybackErrorKind.Decoder, classifyMpvError(MpvErrorCode.LOADING_FAILED, null, "vd: decoder init failed"))
    }

    @Test
    fun plainLoadFailureIsNetworkOnlyWhenTheLogSaysSo() {
        assertEquals(PlaybackErrorKind.Network, classifyMpvError(MpvErrorCode.LOADING_FAILED, null, "ffmpeg: Connection timed out"))
        assertEquals(PlaybackErrorKind.Other, classifyMpvError(MpvErrorCode.LOADING_FAILED, null, ""))
    }

    @Test
    fun upstreamStatusBeatsTheLogAndUrlsAreRedacted() {
        val e = mpvPlaybackError(
            MpvErrorCode.LOADING_FAILED, "loading failed",
            listOf("stream: Failed to open https://rr3.googlevideo.com/videoplayback?sig=SECRET&ip=1.2.3.4."), upstreamStatus = 403,
        )
        assertEquals(PlaybackErrorKind.StreamExpiredOrForbidden, e.kind)
        assertEquals(403, e.httpStatus)
        assertFalse(e.message.contains("SECRET")); assertFalse(e.message.contains("1.2.3.4"))
        assertEquals(true, e.message.contains("rr3.googlevideo.com"))
    }

    @Test
    fun statusFallsBackToTheLogWhenTheProxyWasNotInvolved() {
        val e = mpvPlaybackError(MpvErrorCode.LOADING_FAILED, "loading failed", listOf("ffmpeg: https: HTTP error 410 Gone"), null)
        assertEquals(410, e.httpStatus); assertEquals(PlaybackErrorKind.StreamExpiredOrForbidden, e.kind)
    }
}
