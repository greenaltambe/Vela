package com.greenaltambe.vela.extractor.ytdlp

import java.io.IOException
import kotlin.time.Duration

class ProcessOutput(val exitCode: Int, val stdout: String, val stderr: String)

/** The binary doesn't exist, isn't executable, or the OS refused to start it. */
class ProcessStartException(message: String, cause: Throwable? = null) : IOException(message, cause)

class ProcessTimeoutException(val timeout: Duration) : IOException("Process timed out after $timeout")

class OutputTooLargeException(val limitBytes: Int) : IOException("Process output exceeded $limitBytes bytes")

/** Runs an external command to completion. A seam so tests can fake yt-dlp without a binary. */
interface ProcessRunner {
    /**
     * Captures stdout and stderr. Throws [ProcessStartException], [ProcessTimeoutException] (after killing the
     * process) or [OutputTooLargeException] (stdout over [maxStdoutBytes]; also kills it). Cancelling the caller
     * kills the process.
     */
    suspend fun run(command: List<String>, timeout: Duration, maxStdoutBytes: Int): ProcessOutput
}
