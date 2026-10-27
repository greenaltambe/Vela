package com.greenaltambe.vela.extractor.ytdlp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.time.Duration

/** The slice of [Process] the runner needs; lets tests drive the kill logic without real processes. */
interface RunningProcess {
    val stdout: InputStream
    val stderr: InputStream
    fun waitFor(): Int
    fun isAlive(): Boolean
    fun destroyForcibly()
}

private class JavaRunningProcess(private val process: Process) : RunningProcess {
    override val stdout: InputStream get() = process.inputStream
    override val stderr: InputStream get() = process.errorStream
    override fun waitFor() = process.waitFor()
    override fun isAlive() = process.isAlive
    override fun destroyForcibly() { process.destroyForcibly() }
}

fun startJavaProcess(command: List<String>): RunningProcess {
    val process = try {
        ProcessBuilder(command).start()
    } catch (e: java.io.IOException) {
        throw ProcessStartException("Cannot start ${command.firstOrNull()}: ${e.message}", e)
    }
    process.outputStream.close() // nothing is ever sent on stdin; yt-dlp must not wait for input
    return JavaRunningProcess(process)
}


/**
 * Runs a real process. The process is destroyed (forcibly) on cancellation, timeout, output overflow and any other
 * way out, so a stuck yt-dlp can never outlive its request.
 */
class SystemProcessRunner(
    private val start: (List<String>) -> RunningProcess = ::startJavaProcess,
    private val maxStderrBytes: Int = 256 * 1024,
) : ProcessRunner {

    override suspend fun run(command: List<String>, timeout: Duration, maxStdoutBytes: Int): ProcessOutput {
        val process = withContext(Dispatchers.IO) { start(command) }
        try {
            return withTimeoutOrNull(timeout) {
                coroutineScope {
                    // Cancelling the scope for any reason (caller, timeout, overflow) must kill the process *before*
                    // the scope waits for its readers, otherwise they sit on a pipe that never closes.
                    val killer = launch {
                        try { awaitCancellation() } finally { if (process.isAlive()) process.destroyForcibly() }
                    }
                    val out = async(Dispatchers.IO) { process.stdout.readLimited(maxStdoutBytes, failOnOverflow = true) }
                    val err = async(Dispatchers.IO) { process.stderr.readLimited(maxStderrBytes, failOnOverflow = false) }
                    val code = runInterruptible(Dispatchers.IO) { process.waitFor() }
                    val result = ProcessOutput(code, out.await(), err.await())
                    killer.cancel()
                    result
                }
            } ?: throw ProcessTimeoutException(timeout)
        } finally {
            if (process.isAlive()) process.destroyForcibly()
        }
    }
}

/** Reads up to [limit] bytes as UTF-8; beyond that either throws or keeps draining (so the writer never blocks). */
private fun InputStream.readLimited(limit: Int, failOnOverflow: Boolean): String {
    val kept = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    use { input ->
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            val room = limit - kept.size()
            if (n > room) {
                if (failOnOverflow) throw OutputTooLargeException(limit)
                if (room > 0) kept.write(buffer, 0, room)
            } else {
                kept.write(buffer, 0, n)
            }
        }
    }
    return kept.toString(Charsets.UTF_8)
}
