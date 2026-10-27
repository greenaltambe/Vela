package com.greenaltambe.vela.player.mpv

import com.greenaltambe.vela.player.PlaybackError
import com.greenaltambe.vela.player.PlaybackErrorKind

/** `mpv_error` values that `end-file` reports. */
internal object MpvErrorCode {
    const val LOADING_FAILED = -13
    const val AO_INIT_FAILED = -14
    const val VO_INIT_FAILED = -15
    const val NOTHING_TO_PLAY = -16
    const val UNKNOWN_FORMAT = -17
    const val UNSUPPORTED = -18
}

/** Same set as the Android mapping: the URL is no good any more, so re-resolving is the cure. */
private val deadUrlStatuses = setOf(401, 403, 404, 410, 416)

private val httpStatusPattern = Regex("""HTTP error (\d{3})""", RegexOption.IGNORE_CASE)

/** The HTTP status in ffmpeg's `http: HTTP error 403 Forbidden` log line (or our proxy's own message), if any. */
internal fun httpStatusFromLog(line: String): Int? = httpStatusPattern.find(line)?.groupValues?.get(1)?.toIntOrNull()

internal fun classifyMpvError(errorCode: Int, httpStatus: Int?, logText: String = ""): PlaybackErrorKind = when {
    httpStatus != null && httpStatus in deadUrlStatuses -> PlaybackErrorKind.StreamExpiredOrForbidden
    httpStatus != null -> PlaybackErrorKind.Network // 429, 5xx...
    errorCode == MpvErrorCode.UNKNOWN_FORMAT || errorCode == MpvErrorCode.UNSUPPORTED ||
        errorCode == MpvErrorCode.AO_INIT_FAILED || errorCode == MpvErrorCode.VO_INIT_FAILED -> PlaybackErrorKind.Decoder
    logText.contains("decoder", ignoreCase = true) && logText.contains("fail", ignoreCase = true) -> PlaybackErrorKind.Decoder
    errorCode == MpvErrorCode.LOADING_FAILED || errorCode == MpvErrorCode.NOTHING_TO_PLAY ->
        if (logText.contains("timed out", true) || logText.contains("connection", true) || logText.contains("resolve", true)) PlaybackErrorKind.Network
        else PlaybackErrorKind.Other
    else -> PlaybackErrorKind.Other
}

/**
 * Builds the error for a failed `end-file`. [recentLog] is the warn/error lines mpv logged since the load began;
 * [upstreamStatus] is what the chunking proxy saw from the server (it knows even when mpv only says "loading failed").
 */
/** Stream URLs carry signatures and the client IP; keep the scheme and host so a message is still useful. */
internal fun redactUrls(text: String): String = Regex("""(https?://[^/\s?]+)[^\s,|]*""").replace(text) { it.groupValues[1] + "/..." }

internal fun mpvPlaybackError(errorCode: Int, errorText: String, recentLog: List<String>, upstreamStatus: Int?): PlaybackError {
    val status = upstreamStatus ?: recentLog.asReversed().firstNotNullOfOrNull(::httpStatusFromLog)
    val text = redactUrls(recentLog.takeLast(3).joinToString(" | "))
    return PlaybackError(
        kind = classifyMpvError(errorCode, status, text),
        message = "$errorText${status?.let { " (HTTP $it)" }.orEmpty()}${if (text.isNotEmpty()) ": $text" else ""}",
        httpStatus = status,
    )
}
