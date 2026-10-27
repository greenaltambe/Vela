package com.greenaltambe.vela.playback

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import com.greenaltambe.vela.player.PlaybackError
import com.greenaltambe.vela.player.PlaybackErrorKind

/**
 * HTTP statuses that mean "this URL is no good any more": signature or `expire` rejected (401, 403), gone (404, 410)
 * or a range that no longer fits (416). Re-resolving the streams is the cure, and the session bounds the retries.
 */
private val deadUrlStatuses = setOf(401, 403, 404, 410, 416)

private val decoderCodes = setOf(
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
    PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
)

private val networkCodes = setOf(
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, // other than the dead-URL statuses: 429, 5xx...
    PlaybackException.ERROR_CODE_TIMEOUT,
)

/** Pure so it can be unit tested without building a [PlaybackException]. */
internal fun classifyPlayerError(errorCode: Int, httpStatus: Int?): PlaybackErrorKind = when {
    httpStatus != null && httpStatus in deadUrlStatuses -> PlaybackErrorKind.StreamExpiredOrForbidden
    errorCode in decoderCodes -> PlaybackErrorKind.Decoder
    errorCode in networkCodes -> PlaybackErrorKind.Network
    else -> PlaybackErrorKind.Other
}

/** The HTTP status buried in the cause chain, if the failure was an HTTP response. */
internal fun httpStatusOf(error: Throwable): Int? {
    var cause: Throwable? = error
    var depth = 0
    while (cause != null && depth++ < 10) {
        if (cause is HttpDataSource.InvalidResponseCodeException) return cause.responseCode
        cause = cause.cause
    }
    return null
}

internal fun PlaybackException.toPlaybackError(): PlaybackError {
    val status = httpStatusOf(this)
    return PlaybackError(
        kind = classifyPlayerError(errorCode, status),
        message = "$errorCodeName${status?.let { " (HTTP $it)" }.orEmpty()}: ${message.orEmpty()}",
        httpStatus = status,
        cause = this,
    )
}
