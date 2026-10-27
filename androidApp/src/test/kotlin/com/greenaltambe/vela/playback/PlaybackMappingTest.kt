package com.greenaltambe.vela.playback

import androidx.media3.common.PlaybackException
import com.greenaltambe.vela.player.PlaybackErrorKind
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackMappingTest {
    private val badStatus = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS

    @Test fun deadUrlStatusesMeanReResolve() {
        listOf(401, 403, 404, 410, 416).forEach {
            assertEquals(PlaybackErrorKind.StreamExpiredOrForbidden, classifyPlayerError(badStatus, it), "HTTP $it")
        }
    }

    @Test fun otherHttpStatusesAreNetworkErrors() {
        listOf(429, 500, 502, 503).forEach { assertEquals(PlaybackErrorKind.Network, classifyPlayerError(badStatus, it), "HTTP $it") }
        assertEquals(PlaybackErrorKind.Network, classifyPlayerError(badStatus, null))
    }

    @Test fun connectivityProblemsAreNetworkErrors() {
        listOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        ).forEach { assertEquals(PlaybackErrorKind.Network, classifyPlayerError(it, null), "code $it") }
    }

    @Test fun decoderAndAudioSinkFailuresAreDecoderErrors() {
        listOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        ).forEach { assertEquals(PlaybackErrorKind.Decoder, classifyPlayerError(it, null), "code $it") }
    }

    @Test fun everythingElseIsOther() {
        assertEquals(PlaybackErrorKind.Other, classifyPlayerError(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, null))
        assertEquals(PlaybackErrorKind.Other, classifyPlayerError(PlaybackException.ERROR_CODE_UNSPECIFIED, null))
    }

    @Test fun aDeadStatusWinsOverTheErrorCode() {
        assertEquals(PlaybackErrorKind.StreamExpiredOrForbidden, classifyPlayerError(PlaybackException.ERROR_CODE_DECODING_FAILED, 403))
    }

    @Test fun httpStatusIsFoundNowhereInAPlainCauseChain() {
        assertEquals(null, httpStatusOf(RuntimeException("a", IllegalStateException("b"))))
    }

    @Test fun userAgentIsSplitOutCaseInsensitivelyAndTheRestKept() {
        val split = splitHeaders(mapOf("user-agent" to "UA/1", "Referer" to "r", "Origin" to "o"))
        assertEquals("UA/1", split.userAgent)
        assertEquals(mapOf("Referer" to "r", "Origin" to "o"), split.rest)
    }

    @Test fun aMissingUserAgentGetsABrowserOne() {
        val split = splitHeaders(mapOf("Referer" to "r"))
        assertEquals(true, split.userAgent.startsWith("Mozilla/5.0"))
        assertEquals(mapOf("Referer" to "r"), split.rest)
    }
}

class NetworkClassificationTest {
    @Test fun noInternetIsOffline() = assertEquals(com.greenaltambe.vela.core.network.NetworkType.OFFLINE, classifyNetwork(hasInternet = false, notMetered = true))
    @Test fun notMeteredIsUnmetered() = assertEquals(com.greenaltambe.vela.core.network.NetworkType.UNMETERED, classifyNetwork(hasInternet = true, notMetered = true))
    @Test fun meteredIsMetered() = assertEquals(com.greenaltambe.vela.core.network.NetworkType.METERED, classifyNetwork(hasInternet = true, notMetered = false))
}
